/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.beacon.sync.historical;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * Service responsible for syncing missing historical blocks. Blocks are pulled in order from the
 * newest unknown block back to genesis.
 *
 * <p>CAUTION: this API is unstable and primarily intended for debugging and testing purposes this
 * API might be changed in any version in backward incompatible way
 */
public class HistoricalBlockSyncService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration RETRY_TIMEOUT = Duration.ofMinutes(1);

  private final Spec spec;
  private final BlobSidecarManager blobSidecarManager;
  private final SettableGauge historicSyncGauge;
  private final StorageUpdateChannel storageUpdateChannel;
  private final AsyncRunner asyncRunner;
  private final P2PNetwork<Eth2Peer> network;
  private final CombinedChainDataClient chainData;
  private final SyncStateProvider syncStateProvider;
  private final UInt64 batchSize;

  private final AtomicLong syncStateSubscription = new AtomicLong(-1);
  private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

  private final AsyncBLSSignatureVerifier signatureVerifier;
  private volatile BeaconBlockSummary earliestBlock;
  private volatile Optional<SignedBeaconBlock> earliestFullBlock = Optional.empty();
  final Set<NodeId> badPeerCache;

  private final Optional<ReconstructHistoricalStatesService> reconstructHistoricalStatesService;
  private final boolean fetchAllHistoricBlocks;

  @VisibleForTesting
  protected HistoricalBlockSyncService(
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final MetricsSystem metricsSystem,
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final CombinedChainDataClient chainData,
      final SyncStateProvider syncStateProvider,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final UInt64 batchSize,
      final Optional<ReconstructHistoricalStatesService> reconstructHistoricalStatesService,
      final boolean fetchAllHistoricBlocks) {
    this.spec = spec;
    this.blobSidecarManager = blobSidecarManager;
    this.storageUpdateChannel = storageUpdateChannel;
    this.asyncRunner = asyncRunner;
    this.network = network;
    this.chainData = chainData;
    this.syncStateProvider = syncStateProvider;
    this.batchSize = batchSize;
    this.signatureVerifier = signatureVerifier;
    this.reconstructHistoricalStatesService = reconstructHistoricalStatesService;
    this.fetchAllHistoricBlocks = fetchAllHistoricBlocks;

    this.badPeerCache =
        Collections.newSetFromMap(
            CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5))
                .removalListener(__ -> logBadPeerCacheSize(false))
                .<NodeId, Boolean>build()
                .asMap());

    this.historicSyncGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "historical_block_sync_earliest_block",
            "The slot of the earliest block retrieved by the historical block sync service");
  }

  public static HistoricalBlockSyncService create(
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem,
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final CombinedChainDataClient chainData,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final SyncStateProvider syncStateProvider,
      final boolean reconstructHistoricStatesEnabled,
      final Optional<String> genesisStateResource,
      final boolean fetchAllHistoricBlocks,
      final int batchSize) {
    final Optional<ReconstructHistoricalStatesService> reconstructHistoricalStatesService =
        reconstructHistoricStatesEnabled
            ? Optional.of(
                new ReconstructHistoricalStatesService(
                    storageUpdateChannel,
                    chainData,
                    spec,
                    timeProvider,
                    metricsSystem,
                    genesisStateResource))
            : Optional.empty();

    return new HistoricalBlockSyncService(
        spec,
        blobSidecarManager,
        metricsSystem,
        storageUpdateChannel,
        asyncRunner,
        network,
        chainData,
        syncStateProvider,
        signatureVerifier,
        UInt64.valueOf(batchSize),
        reconstructHistoricalStatesService,
        fetchAllHistoricBlocks);
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.debug("Start {}", getClass().getSimpleName());
    return initialize().thenRun(this::fetchBlocks);
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stop {}", getClass().getSimpleName());
    syncStateProvider.unsubscribeFromSyncStateChanges(syncStateSubscription.get());
    badPeerCache.clear();
    return reconstructHistoricalStatesService.isPresent()
        ? reconstructHistoricalStatesService.get().stop()
        : SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> initialize() {
    return chainData
        .getEarliestAvailableBlockSummary()
        .thenCompose(
            beaconBlockSummary -> {
              this.earliestBlock =
                  beaconBlockSummary.orElseThrow(
                      () -> new IllegalStateException("Unable to retrieve earliest block"));
              return chainData.getBlockByBlockRoot(earliestBlock.getRoot());
            })
        .thenAccept(
            earliestFullBlock -> {
              this.earliestFullBlock = earliestFullBlock;
              final UInt64 terminalSlot = getTerminalSlot();
              if (earliestBlock.getSlot().isGreaterThan(terminalSlot)) {
                LOG.info(
                    "Begin historical sync of blocks from slot {} to slot {}",
                    earliestBlock.getSlot(),
                    terminalSlot);
                updateSyncMetrics();
              }
              syncStateSubscription.set(
                  syncStateProvider.subscribeToSyncStateChanges(__ -> fetchBlocks()));
            });
  }

  private UInt64 getTerminalSlot() {
    if (fetchAllHistoricBlocks) {
      return UInt64.ZERO;
    } else {
      final UInt64 earliestRequiredEpoch =
          chainData
              .getLatestFinalized()
              .orElseThrow()
              .getCheckpoint()
              .getEpoch()
              .minusMinZero(
                  spec.getSpecConfig(chainData.getCurrentEpoch()).getMinEpochsForBlockRequests());
      return spec.computeStartSlotAtEpoch(earliestRequiredEpoch).minusMinZero(1);
    }
  }

  private void updateSyncMetrics() {
    earliestBlock
        .getBeaconBlock()
        .ifPresent(__ -> historicSyncGauge.set(earliestBlock.getSlot().doubleValue()));
  }

  private void fetchBlocks() {
    SafeFuture.asyncDoWhile(this::findPeerAndRequestBlocks)
        .always(
            () -> {
              if (isSyncDone()) {
                stop().finishStackTrace();

                reconstructHistoricalStatesService.ifPresent(
                    service ->
                        service
                            .start()
                            .finish(STATUS_LOG::reconstructHistoricalStatesServiceFailedStartup));
              }
            });
  }

  private SafeFuture<Boolean> findPeerAndRequestBlocks() {
    final Optional<MaxMissingBlockParams> blockParams = getMaxMissingBlockParams();
    if (blockParams.isPresent() && isActive() && requestInProgress.compareAndSet(false, true)) {
      return findPeer()
          .map(peer -> requestBlocks(peer, blockParams.get()))
          .orElseGet(this::waitToRetry)
          .alwaysRun(() -> requestInProgress.set(false))
          .thenApply(__ -> true);
    } else {
      return SafeFuture.completedFuture(false);
    }
  }

  private boolean isActive() {
    return isRunning() && syncStateProvider.getCurrentSyncState().isInSync();
  }

  private SafeFuture<Void> requestBlocks(final Eth2Peer peer, final MaxMissingBlockParams params) {
    return createFetcher(peer, params)
        .run()
        .exceptionally(
            (err) -> {
              // We ran into trouble with this peer - ignore it for a while
              LOG.debug(
                  "Encountered a problem requesting historical blocks from peer: " + peer, err);
              if (peer.isConnected()) {
                // If we didn't disconnect the peer altogether, avoid making new requests for a
                // while
                badPeerCache.add(peer.getId());
                logBadPeerCacheSize(true);
              }
              return null;
            })
        .thenAccept(
            newValue -> {
              if (newValue != null && newValue.getSlot().isLessThanOrEqualTo(params.maxSlot())) {
                LOG.trace("Synced historical blocks to slot {}", newValue.getSlot());
                earliestBlock = newValue;
                earliestFullBlock = Optional.of(newValue);
                updateSyncMetrics();
                if (isSyncDone()) {
                  LOG.info("Historical block sync is complete");
                }
              }
            });
  }

  private HistoricalBatchFetcher createFetcher(
      final Eth2Peer peer, final MaxMissingBlockParams params) {
    return new HistoricalBatchFetcher(
        storageUpdateChannel,
        signatureVerifier,
        chainData,
        spec,
        blobSidecarManager,
        peer,
        params.maxSlot(),
        params.blockRoot(),
        batchSize,
        earliestFullBlock);
  }

  private boolean isSyncDone() {
    return earliestBlock
        .getBeaconBlock()
        .map(block -> block.getSlot().isLessThanOrEqualTo(getTerminalSlot()))
        .orElse(false);
  }

  private Optional<MaxMissingBlockParams> getMaxMissingBlockParams() {
    final UInt64 maxSlot;
    final Bytes32 lastBlockRoot;
    if (earliestBlock.getBeaconBlock().isPresent()) {
      if (earliestBlock.getSlot().isLessThanOrEqualTo(getTerminalSlot())) {
        // Nothing left to request
        return Optional.empty();
      }
      maxSlot = earliestBlock.getSlot().minus(1);
      lastBlockRoot = earliestBlock.getParentRoot();
    } else {
      maxSlot = earliestBlock.getSlot();
      lastBlockRoot = earliestBlock.getRoot();
    }

    return Optional.of(new MaxMissingBlockParams(lastBlockRoot, maxSlot));
  }

  private SafeFuture<Void> waitToRetry() {
    return asyncRunner.getDelayedFuture(RETRY_TIMEOUT);
  }

  private Optional<Eth2Peer> findPeer() {
    return network
        .streamPeers()
        .filter(p -> !badPeerCache.contains(p.getId()))
        .filter(
            p ->
                p.getStatus()
                    .getFinalizedCheckpoint()
                    .getEpochStartSlot(spec)
                    .isGreaterThanOrEqualTo(earliestBlock.getSlot()))
        .findAny();
  }

  private void logBadPeerCacheSize(final boolean peerAdded) {
    if (peerAdded) {
      LOG.trace("Peer added to bad peer cache, current size: {}", badPeerCache.size());
    } else {
      LOG.trace("Peer removed from bad peer cache, current size: {}", badPeerCache.size());
    }
  }

  private record MaxMissingBlockParams(Bytes32 blockRoot, UInt64 maxSlot) {}
}

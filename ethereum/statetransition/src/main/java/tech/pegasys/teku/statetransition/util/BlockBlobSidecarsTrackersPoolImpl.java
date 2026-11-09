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

package tech.pegasys.teku.statetransition.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getRootCauseMessage;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.LOCAL_EL;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.LOCAL_PROPOSAL;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackerFactory;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockBlobSidecarsTrackersPoolImpl extends AbstractIgnoringFutureHistoricalSlot
    implements BlockBlobSidecarsTrackersPool {
  private static final Logger LOG = LogManager.getLogger();

  static final String COUNTER_BLOCK_TYPE = "block";
  static final String COUNTER_SIDECAR_TYPE = "blob_sidecar";

  static final String COUNTER_GOSSIP_SUBTYPE = "gossip";
  static final String COUNTER_LOCAL_EL_SUBTYPE = "local_el";
  static final String COUNTER_LOCAL_PROPOSAL_SUBTYPE = "local_proposal";
  static final String COUNTER_RPC_SUBTYPE = "rpc";
  static final String COUNTER_RECOVERED_SUBTYPE = "recovered";
  static final String COUNTER_GOSSIP_DUPLICATE_SUBTYPE = "gossip_duplicate";
  static final String COUNTER_RPC_DUPLICATE_SUBTYPE = "rpc_duplicate";
  static final String COUNTER_LOCAL_EL_DUPLICATE_SUBTYPE = "local_el_duplicate";
  static final String COUNTER_LOCAL_PROPOSAL_DUPLICATE_SUBTYPE = "local_proposal_duplicate";
  static final String COUNTER_RECOVERED_DUPLICATE_SUBTYPE = "recovered_duplicate";

  static final String COUNTER_RPC_FETCH_SUBTYPE = "rpc_fetch";
  static final String COUNTER_LOCAL_EL_FETCH_SUBTYPE = "local_el_fetch";

  static final String GAUGE_BLOB_SIDECARS_LABEL = "blob_sidecars";
  static final String GAUGE_BLOB_SIDECARS_TRACKERS_LABEL = "blob_sidecars_trackers";

  private final SettableLabelledGauge sizeGauge;
  private final LabelledMetric<Counter> poolStatsCounters;
  private final Map<Bytes32, BlockBlobSidecarsTracker> blockBlobSidecarsTrackers = new HashMap<>();
  private final NavigableSet<SlotAndBlockRoot> orderedBlobSidecarsTrackers = new TreeSet<>();
  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayer;
  private final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier;
  private final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher;
  private final int maxTrackers;

  private final BlockBlobSidecarsTrackerFactory trackerFactory;

  private final Subscribers<RequiredBlockRootSubscriber> requiredBlockRootSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlockRootDroppedSubscriber>
      requiredBlockRootDroppedSubscribers = Subscribers.create(true);

  private final Subscribers<RequiredBlobSidecarSubscriber> requiredBlobSidecarSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlobSidecarDroppedSubscriber>
      requiredBlobSidecarDroppedSubscribers = Subscribers.create(true);

  private final Subscribers<NewBlobSidecarSubscriber> newBlobSidecarSubscribers =
      Subscribers.create(true);

  private int totalBlobSidecars;

  private final BlockImportChannel blockImportChannel;

  private final RPCFetchDelayProvider rpcFetchDelayProvider;

  BlockBlobSidecarsTrackersPoolImpl(
      final BlockImportChannel blockImportChannel,
      final SettableLabelledGauge sizeGauge,
      final LabelledMetric<Counter> poolStatsCounters,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher,
      final RPCFetchDelayProvider rpcFetchDelayProvider,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.blockImportChannel = blockImportChannel;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.gossipValidatorSupplier = gossipValidatorSupplier;
    this.blobSidecarGossipPublisher = blobSidecarGossipPublisher;
    this.rpcFetchDelayProvider = rpcFetchDelayProvider;
    this.maxTrackers = maxTrackers;
    this.sizeGauge = sizeGauge;
    this.poolStatsCounters = poolStatsCounters;
    this.trackerFactory = BlockBlobSidecarsTracker::new;

    initMetrics(sizeGauge, poolStatsCounters);
  }

  @VisibleForTesting
  BlockBlobSidecarsTrackersPoolImpl(
      final BlockImportChannel blockImportChannel,
      final SettableLabelledGauge sizeGauge,
      final LabelledMetric<Counter> poolStatsCounters,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher,
      final RPCFetchDelayProvider rpcFetchDelayProvider,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers,
      final BlockBlobSidecarsTrackerFactory trackerFactory) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.blockImportChannel = blockImportChannel;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.gossipValidatorSupplier = gossipValidatorSupplier;
    this.blobSidecarGossipPublisher = blobSidecarGossipPublisher;
    this.rpcFetchDelayProvider = rpcFetchDelayProvider;
    this.maxTrackers = maxTrackers;
    this.sizeGauge = sizeGauge;
    this.poolStatsCounters = poolStatsCounters;
    this.trackerFactory = trackerFactory;

    initMetrics(sizeGauge, poolStatsCounters);
  }

  private static void initMetrics(
      final SettableLabelledGauge sizeGauge, final LabelledMetric<Counter> poolStatsCounters) {
    // Init the label so it appears in metrics immediately
    sizeGauge.set(0, GAUGE_BLOB_SIDECARS_LABEL);
    sizeGauge.set(0, GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);

    Stream.of(COUNTER_BLOCK_TYPE, COUNTER_SIDECAR_TYPE)
        .forEach(
            type -> {
              poolStatsCounters.labels(type, COUNTER_GOSSIP_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_RPC_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_GOSSIP_DUPLICATE_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_RPC_DUPLICATE_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_RPC_FETCH_SUBTYPE);
            });
  }

  @Override
  public synchronized void onNewBlobSidecar(
      final BlobSidecar blobSidecar, final RemoteOrigin remoteOrigin) {
    if (spec.atSlot(blobSidecar.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return;
    }
    if (recentChainData.containsBlock(blobSidecar.getBlockRoot())) {
      return;
    }
    if (shouldIgnoreItemAtSlot(blobSidecar.getSlot())) {
      return;
    }

    final SlotAndBlockRoot slotAndBlockRoot = blobSidecar.getSlotAndBlockRoot();

    getOrCreateBlobSidecarsTracker(
        slotAndBlockRoot,
        newTracker -> {
          addBlobSidecarToTracker(newTracker, slotAndBlockRoot, blobSidecar, remoteOrigin);
          onFirstSeen(slotAndBlockRoot, Optional.of(remoteOrigin));
        },
        existingTracker ->
            addBlobSidecarToTracker(existingTracker, slotAndBlockRoot, blobSidecar, remoteOrigin));

    if (orderedBlobSidecarsTrackers.add(slotAndBlockRoot)) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  private void addBlobSidecarToTracker(
      final BlockBlobSidecarsTracker blobSidecarsTracker,
      final SlotAndBlockRoot slotAndBlockRoot,
      final BlobSidecar blobSidecar,
      final RemoteOrigin remoteOrigin) {
    if (blobSidecarsTracker.add(blobSidecar)) {
      sizeGauge.set(++totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);
      countBlobSidecar(remoteOrigin);
      newBlobSidecarSubscribers.deliver(NewBlobSidecarSubscriber::onNewBlobSidecar, blobSidecar);
      if (remoteOrigin.equals(LOCAL_EL) && slotAndBlockRoot.getSlot().equals(getCurrentSlot())) {
        publishRecoveredBlobSidecar(blobSidecar);
      }
    } else {
      countDuplicateBlobSidecar(remoteOrigin);
    }
  }

  private void publishRecoveredBlobSidecar(final BlobSidecar blobSidecar) {
    LOG.debug("Publishing recovered blob sidecar {}", blobSidecar::toLogString);
    gossipValidatorSupplier.get().markForEquivocation(blobSidecar);
    blobSidecarGossipPublisher.apply(blobSidecar).finishStackTrace();
  }

  private void countBlobSidecar(final RemoteOrigin origin) {
    switch (origin) {
      case RPC -> poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RPC_SUBTYPE).inc();
      case GOSSIP -> poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_GOSSIP_SUBTYPE).inc();
      case LOCAL_EL ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_EL_SUBTYPE).inc();
      case LOCAL_PROPOSAL ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_PROPOSAL_SUBTYPE).inc();
      case RECOVERED ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RECOVERED_SUBTYPE).inc();
      case CUSTODY -> {} // Not applicable for blocks\blobs
    }
  }

  private void countDuplicateBlobSidecar(final RemoteOrigin origin) {
    switch (origin) {
      case RPC ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RPC_DUPLICATE_SUBTYPE).inc();
      case GOSSIP ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_GOSSIP_DUPLICATE_SUBTYPE).inc();
      case LOCAL_EL ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_EL_DUPLICATE_SUBTYPE).inc();
      case LOCAL_PROPOSAL ->
          poolStatsCounters
              .labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_PROPOSAL_DUPLICATE_SUBTYPE)
              .inc();
      case RECOVERED ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RECOVERED_DUPLICATE_SUBTYPE).inc();
      case CUSTODY -> {} // Not applicable for blocks\blobs
    }
  }

  @Override
  public synchronized void onNewBlock(
      final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    if (shouldIgnoreItemAtSlot(block.getSlot())) {
      return;
    }
    internalOnNewBlock(block, remoteOrigin);
  }

  @Override
  public synchronized BlockBlobSidecarsTracker getOrCreateBlockBlobSidecarsTracker(
      final SignedBeaconBlock block) {
    return internalOnNewBlock(block, Optional.empty());
  }

  @Override
  public synchronized Optional<BlockBlobSidecarsTracker> getBlockBlobSidecarsTracker(
      final SignedBeaconBlock block) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(block.getRoot()));
  }

  @Override
  public synchronized void onCompletedBlockAndBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    if (recentChainData.containsBlock(block.getRoot())) {
      return;
    }
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blobSidecarsTracker =
        getOrCreateBlobSidecarsTracker(slotAndBlockRoot, __ -> {}, __ -> {});

    blobSidecarsTracker.setBlock(block);

    long addedBlobs =
        blobSidecars.stream()
            .filter(
                blobSidecar -> {
                  final boolean isNew = blobSidecarsTracker.add(blobSidecar);
                  if (isNew) {
                    newBlobSidecarSubscribers.deliver(
                        NewBlobSidecarSubscriber::onNewBlobSidecar, blobSidecar);
                  }
                  return isNew;
                })
            .count();
    totalBlobSidecars += (int) addedBlobs;
    sizeGauge.set(totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);

    if (!blobSidecarsTracker.isComplete()) {
      LOG.error(
          "Tracker for block {} is supposed to be completed but it is not. Missing blob sidecars: {}",
          block.toLogString(),
          blobSidecarsTracker.getMissingBlobSidecars().count());
    }

    if (orderedBlobSidecarsTrackers.add(slotAndBlockRoot)) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  @Override
  public synchronized void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    final BlockBlobSidecarsTracker removedTracker =
        blockBlobSidecarsTrackers.remove(slotAndBlockRoot.getBlockRoot());

    if (removedTracker != null) {
      orderedBlobSidecarsTrackers.remove(removedTracker.getSlotAndBlockRoot());

      dropMissingContent(removedTracker);

      totalBlobSidecars -= removedTracker.blobSidecarsCount();
      sizeGauge.set(totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);
      sizeGauge.set(blockBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  @VisibleForTesting
  BlockBlobSidecarsTracker getBlobSidecarsTracker(final SlotAndBlockRoot slotAndBlockRoot) {
    return blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);

    LOG.trace(
        "Trackers: {}",
        () -> {
          synchronized (this) {
            return blockBlobSidecarsTrackers.toString();
          }
        });
  }

  @VisibleForTesting
  @Override
  protected synchronized void prune(final UInt64 slotLimit) {
    final List<SlotAndBlockRoot> toRemove = new ArrayList<>();
    for (SlotAndBlockRoot slotAndBlockRoot : orderedBlobSidecarsTrackers) {
      if (slotAndBlockRoot.getSlot().isGreaterThan(slotLimit)) {
        break;
      }
      toRemove.add(slotAndBlockRoot);
    }

    toRemove.forEach(this::removeAllForBlock);
  }

  @Override
  public synchronized boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blobIdentifier.getBlockRoot()))
        .map(tracker -> tracker.containsBlobSidecar(blobIdentifier))
        .orElse(false);
  }

  @Override
  public synchronized Optional<BlobSidecar> getBlobSidecar(
      final Bytes32 blockRoot, final UInt64 index) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blockRoot))
        .flatMap(tracker -> tracker.getBlobSidecar(index));
  }

  @Override
  public synchronized boolean containsBlock(final Bytes32 blockRoot) {
    return getBlock(blockRoot).isPresent();
  }

  @Override
  public synchronized Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blockRoot))
        .flatMap(BlockBlobSidecarsTracker::getBlock);
  }

  @Override
  public synchronized Set<BlobIdentifier> getAllRequiredBlobSidecars() {
    return blockBlobSidecarsTrackers.values().stream()
        .flatMap(
            tracker -> {
              if (tracker.getBlock().isEmpty()) {
                return Stream.empty();
              }
              return tracker.getMissingBlobSidecars();
            })
        .collect(Collectors.toSet());
  }

  @Override
  public synchronized void enableBlockImportOnCompletion(final SignedBeaconBlock block) {
    Optional.ofNullable(blockBlobSidecarsTrackers.get(block.getRoot()))
        .ifPresent(tracker -> tracker.enableBlockImportOnCompletion(blockImportChannel));
  }

  @Override
  public void subscribeRequiredBlobSidecar(
      final RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber) {
    requiredBlobSidecarSubscribers.subscribe(requiredBlobSidecarSubscriber);
  }

  @Override
  public void subscribeRequiredBlobSidecarDropped(
      final RequiredBlobSidecarDroppedSubscriber requiredBlobSidecarDroppedSubscriber) {
    requiredBlobSidecarDroppedSubscribers.subscribe(requiredBlobSidecarDroppedSubscriber);
  }

  @Override
  public void subscribeRequiredBlockRoot(
      final RequiredBlockRootSubscriber requiredBlockRootSubscriber) {
    requiredBlockRootSubscribers.subscribe(requiredBlockRootSubscriber);
  }

  @Override
  public void subscribeRequiredBlockRootDropped(
      final RequiredBlockRootDroppedSubscriber requiredBlockRootDroppedSubscriber) {
    requiredBlockRootDroppedSubscribers.subscribe(requiredBlockRootDroppedSubscriber);
  }

  @Override
  public void subscribeNewBlobSidecar(final NewBlobSidecarSubscriber newBlobSidecarSubscriber) {
    newBlobSidecarSubscribers.subscribe(newBlobSidecarSubscriber);
  }

  public synchronized int getTotalBlobSidecars() {
    return totalBlobSidecars;
  }

  public synchronized int getTotalBlobSidecarsTrackers() {
    return blockBlobSidecarsTrackers.size();
  }

  private BlockBlobSidecarsTracker internalOnNewBlock(
      final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker tracker =
        getOrCreateBlobSidecarsTracker(
            slotAndBlockRoot,
            newTracker -> {
              newTracker.setBlock(block);
              countBlock(remoteOrigin);
              onFirstSeen(slotAndBlockRoot, remoteOrigin);
            },
            existingTracker -> {
              if (!existingTracker.setBlock(block)) {
                // block was already set
                countDuplicateBlock(remoteOrigin);
                return;
              }

              countBlock(remoteOrigin);

              if (!existingTracker.isComplete()) {
                // we missed the opportunity to complete the blob sidecars via local EL and RPC
                // (since the block is required to be known) Let's try now
                asyncRunner
                    .runAsync(
                        () ->
                            fetchMissingBlobsFromLocalEL(slotAndBlockRoot)
                                .handleException(this::logLocalElBlobsLookupFailure)
                                .thenRun(
                                    () -> {
                                      // only run if RPC block fetch has happened
                                      // (no blobs RPC fetch has occurred)
                                      if (existingTracker.isRpcBlockFetchTriggered()) {
                                        fetchMissingBlockOrBlobsFromRPC(slotAndBlockRoot);
                                      }
                                    })
                                .handleException(this::logBlockOrBlobsRPCFailure))
                    .finishStackTrace();
              }
            });

    if (orderedBlobSidecarsTrackers.add(slotAndBlockRoot)) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }

    return tracker;
  }

  private void countBlock(final Optional<RemoteOrigin> maybeRemoteOrigin) {
    maybeRemoteOrigin.ifPresent(
        remoteOrigin -> {
          switch (remoteOrigin) {
            case RPC -> poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RPC_SUBTYPE).inc();
            case GOSSIP ->
                poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_GOSSIP_SUBTYPE).inc();
            case LOCAL_EL -> {} // only possible for blobs
            case LOCAL_PROPOSAL ->
                poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_LOCAL_PROPOSAL_SUBTYPE).inc();
            case RECOVERED ->
                poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RECOVERED_SUBTYPE).inc();
            case CUSTODY -> {} // Not applicable for blocks\blobs
          }
        });
  }

  private void countDuplicateBlock(final Optional<RemoteOrigin> maybeRemoteOrigin) {
    maybeRemoteOrigin.ifPresent(
        remoteOrigin -> {
          switch (remoteOrigin) {
            case RPC ->
                poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RPC_DUPLICATE_SUBTYPE).inc();
            case GOSSIP ->
                poolStatsCounters
                    .labels(COUNTER_BLOCK_TYPE, COUNTER_GOSSIP_DUPLICATE_SUBTYPE)
                    .inc();
            case LOCAL_PROPOSAL ->
                poolStatsCounters
                    .labels(COUNTER_BLOCK_TYPE, COUNTER_LOCAL_PROPOSAL_DUPLICATE_SUBTYPE)
                    .inc();
            case RECOVERED ->
                poolStatsCounters
                    .labels(COUNTER_BLOCK_TYPE, COUNTER_RECOVERED_DUPLICATE_SUBTYPE)
                    .inc();
            case LOCAL_EL -> {} // only possible for blobs
            case CUSTODY -> {} // Not applicable for blocks\blobs
          }
        });
  }

  private BlockBlobSidecarsTracker getOrCreateBlobSidecarsTracker(
      final SlotAndBlockRoot slotAndBlockRoot,
      final Consumer<BlockBlobSidecarsTracker> onNew,
      final Consumer<BlockBlobSidecarsTracker> onExisting) {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());
    if (blockBlobSidecarsTracker == null) {
      makeRoomForNewTracker();
      blockBlobSidecarsTracker = trackerFactory.create(slotAndBlockRoot);
      blockBlobSidecarsTrackers.put(slotAndBlockRoot.getBlockRoot(), blockBlobSidecarsTracker);
      onNew.accept(blockBlobSidecarsTracker);
    } else {
      onExisting.accept(blockBlobSidecarsTracker);
    }
    return blockBlobSidecarsTracker;
  }

  private void makeRoomForNewTracker() {
    while (blockBlobSidecarsTrackers.size() > maxTrackers - 1) {
      final SlotAndBlockRoot toRemove = orderedBlobSidecarsTrackers.pollFirst();
      if (toRemove == null) {
        break;
      }
      removeAllForBlock(toRemove);
    }
  }

  private void onFirstSeen(
      final SlotAndBlockRoot slotAndBlockRoot, final Optional<RemoteOrigin> remoteOrigin) {
    final boolean isLocalBlockProduction =
        remoteOrigin.map(ro -> ro.equals(LOCAL_PROPOSAL)).orElse(false);
    if (isLocalBlockProduction) {
      return;
    }
    // delay RPC fetching
    final SafeFuture<Void> rpcFetchDelay =
        asyncRunner.getDelayedFuture(rpcFetchDelayProvider.calculate(slotAndBlockRoot.getSlot()));

    asyncRunner
        .runAsync(
            () ->
                // fetch blobs from EL with no delay
                fetchMissingBlobsFromLocalEL(slotAndBlockRoot)
                    .handleException(this::logLocalElBlobsLookupFailure)
                    .thenCompose(__ -> rpcFetchDelay)
                    .thenRun(() -> fetchMissingBlockOrBlobsFromRPC(slotAndBlockRoot))
                    .handleException(this::logBlockOrBlobsRPCFailure))
        .finishStackTrace();
  }

  private synchronized SafeFuture<Void> fetchMissingBlobsFromLocalEL(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());

    if (blockBlobSidecarsTracker == null
        || blockBlobSidecarsTracker.isComplete()
        || blockBlobSidecarsTracker.getBlock().isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    final List<BlobIdentifier> missingBlobsIdentifiers =
        blockBlobSidecarsTracker.getMissingBlobSidecars().toList();

    final SpecVersion specVersion = spec.atSlot(slotAndBlockRoot.getSlot());
    final MiscHelpersDeneb miscHelpersDeneb =
        specVersion.miscHelpers().toVersionDeneb().orElseThrow();
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        blockBlobSidecarsTracker.getBlock().get().asHeader();
    final BeaconBlockBodyDeneb beaconBlockBodyDeneb =
        blockBlobSidecarsTracker
            .getBlock()
            .get()
            .getMessage()
            .getBody()
            .toVersionDeneb()
            .orElseThrow();

    final SszList<SszKZGCommitment> sszKZGCommitments =
        beaconBlockBodyDeneb.getBlobKzgCommitments();

    final List<VersionedHash> versionedHashes =
        missingBlobsIdentifiers.stream()
            .map(
                blobIdentifier ->
                    miscHelpersDeneb.kzgCommitmentToVersionedHash(
                        sszKZGCommitments
                            .get(blobIdentifier.getIndex().intValue())
                            .getKZGCommitment()))
            .toList();

    blockBlobSidecarsTracker.setLocalElBlobsFetchTriggered();

    poolStatsCounters
        .labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_EL_FETCH_SUBTYPE)
        .inc(versionedHashes.size());

    return executionLayer
        .engineGetBlobAndProofs(versionedHashes, slotAndBlockRoot.getSlot())
        .thenAccept(
            blobAndProofs -> {
              checkArgument(
                  blobAndProofs.size() == versionedHashes.size(),
                  "Queried %s versionedHashed but got %s blobAndProofs",
                  versionedHashes.size(),
                  blobAndProofs.size());

              for (int index = 0; index < blobAndProofs.size(); index++) {
                final Optional<BlobAndProof> blobAndProof = blobAndProofs.get(index);
                final BlobIdentifier blobIdentifier = missingBlobsIdentifiers.get(index);
                if (blobAndProof.isEmpty()) {
                  LOG.trace("Blob not found on local EL: {}", blobIdentifier);
                  continue;
                }

                final BlobSidecar blobSidecar =
                    miscHelpersDeneb.constructBlobSidecarFromBlobAndProof(
                        blobIdentifier,
                        blobAndProof.get(),
                        beaconBlockBodyDeneb,
                        signedBeaconBlockHeader);
                onNewBlobSidecar(blobSidecar, LOCAL_EL);
              }
            });
  }

  private void logLocalElBlobsLookupFailure(final Throwable error) {
    LOG.warn("Local EL blobs lookup failed: {}", getRootCauseMessage(error));
  }

  private synchronized void fetchMissingBlockOrBlobsFromRPC(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());

    if (blockBlobSidecarsTracker == null || blockBlobSidecarsTracker.isComplete()) {
      return;
    }
    if (spec.atSlot(slotAndBlockRoot.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return;
    }

    if (blockBlobSidecarsTracker.getBlock().isEmpty()) {

      blockBlobSidecarsTracker.setRpcBlockFetchTriggered();

      poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RPC_FETCH_SUBTYPE).inc();
      requiredBlockRootSubscribers.deliver(
          RequiredBlockRootSubscriber::onRequiredBlockRoot,
          blockBlobSidecarsTracker.getSlotAndBlockRoot().getBlockRoot());
      return;
    }

    blockBlobSidecarsTracker.setRpcBlobsFetchTriggered();

    blockBlobSidecarsTracker
        .getMissingBlobSidecars()
        .forEach(
            blobIdentifier -> {
              poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RPC_FETCH_SUBTYPE).inc();
              requiredBlobSidecarSubscribers.deliver(
                  RequiredBlobSidecarSubscriber::onRequiredBlobSidecar, blobIdentifier);
            });
  }

  private void logBlockOrBlobsRPCFailure(final Throwable error) {
    LOG.error("An error occurred while attempting to fetch block or blobs via RPC.", error);
  }

  private void dropMissingContent(final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {

    if (blockBlobSidecarsTracker.isRpcBlockFetchTriggered()
        && blockBlobSidecarsTracker.getBlock().isEmpty()) {
      requiredBlockRootDroppedSubscribers.deliver(
          RequiredBlockRootDroppedSubscriber::onRequiredBlockRootDropped,
          blockBlobSidecarsTracker.getSlotAndBlockRoot().getBlockRoot());
    }

    if (blockBlobSidecarsTracker.isRpcBlobsFetchTriggered()) {
      blockBlobSidecarsTracker
          .getMissingBlobSidecars()
          .forEach(
              blobIdentifier ->
                  requiredBlobSidecarDroppedSubscribers.deliver(
                      RequiredBlobSidecarDroppedSubscriber::onRequiredBlobSidecarDropped,
                      blobIdentifier));
    }
  }
}

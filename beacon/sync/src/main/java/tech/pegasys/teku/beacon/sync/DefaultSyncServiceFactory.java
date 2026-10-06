/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beacon.sync;

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.events.SyncPreImportBlockChannel;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.beacon.sync.events.SyncStateTracker;
import tech.pegasys.teku.beacon.sync.fetch.DefaultFetchTaskFactory;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.beacon.sync.forward.multipeer.MultipeerSyncService;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.SinglePeerSyncServiceFactory;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetchService;
import tech.pegasys.teku.beacon.sync.gossip.executionpayloads.RecentExecutionPayloadsFetcher;
import tech.pegasys.teku.beacon.sync.historical.HistoricalBlockSyncService;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class DefaultSyncServiceFactory implements SyncServiceFactory {

  private static final Logger LOG = LogManager.getLogger();

  private final SyncConfig syncConfig;
  private final Optional<String> genesisStateResource;
  private final MetricsSystem metrics;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final RecentChainData recentChainData;
  private final CombinedChainDataClient combinedChainDataClient;
  private final StorageUpdateChannel storageUpdateChannel;
  private final SyncPreImportBlockChannel syncPreImportBlockChannel;
  private final Eth2P2PNetwork p2pNetwork;
  private final BlockImporter blockImporter;
  private final BlobSidecarManager blobSidecarManager;
  private final ExecutionPayloadManager executionPayloadManager;
  private final PayloadAttestationPool payloadAttestationPool;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final PendingPool<ValidatableAttestation> pendingAttestations;
  private final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final DasSamplerBasic dasSamplerBasic;
  private final int getStartupTargetPeerCount;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final Duration startupTimeout;
  private final Spec spec;

  public DefaultSyncServiceFactory(
      final SyncConfig syncConfig,
      final Optional<String> genesisStateResource,
      final MetricsSystem metrics,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final StorageUpdateChannel storageUpdateChannel,
      final SyncPreImportBlockChannel syncPreImportBlockChannel,
      final Eth2P2PNetwork p2pNetwork,
      final BlockImporter blockImporter,
      final BlobSidecarManager blobSidecarManager,
      final ExecutionPayloadManager executionPayloadManager,
      final PayloadAttestationPool payloadAttestationPool,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final PendingPool<ValidatableAttestation> pendingAttestations,
      final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final DasSamplerBasic dasSamplerBasic,
      final int getStartupTargetPeerCount,
      final SignatureVerificationService signatureVerifier,
      final Duration startupTimeout,
      final Spec spec) {
    this.syncConfig = syncConfig;
    this.genesisStateResource = genesisStateResource;
    this.metrics = metrics;
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.recentChainData = recentChainData;
    this.combinedChainDataClient = combinedChainDataClient;
    this.storageUpdateChannel = storageUpdateChannel;
    this.syncPreImportBlockChannel = syncPreImportBlockChannel;
    this.p2pNetwork = p2pNetwork;
    this.blockImporter = blockImporter;
    this.blobSidecarManager = blobSidecarManager;
    this.executionPayloadManager = executionPayloadManager;
    this.payloadAttestationPool = payloadAttestationPool;
    this.pendingBlocks = pendingBlocks;
    this.pendingAttestations = pendingAttestations;
    this.pendingPayloadAttestations = pendingPayloadAttestations;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.dasSamplerBasic = dasSamplerBasic;
    this.getStartupTargetPeerCount = getStartupTargetPeerCount;
    this.signatureVerifier = signatureVerifier;
    this.startupTimeout = startupTimeout;
    this.spec = spec;
  }

  @Override
  public SyncService create(final EventChannels eventChannels) {
    if (!syncConfig.isSyncEnabled()) {
      return new NoopSyncService();
    }

    final ForwardSyncService forwardSyncService = createForwardSyncService();

    final FetchTaskFactory fetchTaskFactory = new DefaultFetchTaskFactory(p2pNetwork);

    final RecentBlocksFetchService recentBlocksFetchService =
        RecentBlocksFetchService.create(
            asyncRunner,
            pendingBlocks,
            pendingAttestations,
            pendingPayloadAttestations,
            blockBlobSidecarsTrackersPool,
            dasSamplerBasic,
            forwardSyncService,
            fetchTaskFactory);
    final RecentBlobSidecarsFetcher recentBlobSidecarsFetcher =
        RecentBlobSidecarsFetcher.create(
            spec, asyncRunner, blockBlobSidecarsTrackersPool, forwardSyncService, fetchTaskFactory);
    final RecentExecutionPayloadsFetcher recentExecutionPayloadsFetcher =
        RecentExecutionPayloadsFetcher.create(
            spec,
            asyncRunner,
            forwardSyncService,
            fetchTaskFactory,
            executionPayloadManager,
            payloadAttestationPool,
            pendingPayloadAttestations);

    final SyncStateTracker syncStateTracker = createSyncStateTracker(forwardSyncService);
    eventChannels.subscribe(SlotEventsChannel.class, syncStateTracker);

    final HistoricalBlockSyncService historicalBlockSyncService =
        createHistoricalSyncService(syncStateTracker);

    return new DefaultSyncService(
        forwardSyncService,
        recentBlocksFetchService,
        recentBlobSidecarsFetcher,
        recentExecutionPayloadsFetcher,
        syncStateTracker,
        historicalBlockSyncService);
  }

  protected HistoricalBlockSyncService createHistoricalSyncService(
      final SyncStateProvider syncStateProvider) {
    final AsyncRunner asyncRunner =
        asyncRunnerFactory.create(HistoricalBlockSyncService.class.getSimpleName(), 1);
    return HistoricalBlockSyncService.create(
        spec,
        blobSidecarManager,
        timeProvider,
        metrics,
        storageUpdateChannel,
        asyncRunner,
        p2pNetwork,
        combinedChainDataClient,
        signatureVerifier,
        syncStateProvider,
        syncConfig.isReconstructHistoricStatesEnabled(),
        genesisStateResource,
        syncConfig.fetchAllHistoricBlocks(),
        syncConfig.getHistoricalSyncBatchSize());
  }

  protected SyncStateTracker createSyncStateTracker(final ForwardSync forwardSync) {
    return new SyncStateTracker(
        asyncRunner,
        forwardSync,
        p2pNetwork,
        recentChainData,
        getStartupTargetPeerCount,
        startupTimeout,
        metrics);
  }

  protected ForwardSyncService createForwardSyncService() {
    final ForwardSyncService forwardSync;
    if (syncConfig.isMultiPeerSyncEnabled()) {
      LOG.info("Using multipeer sync");
      forwardSync =
          MultipeerSyncService.create(
              metrics,
              asyncRunnerFactory,
              asyncRunner,
              timeProvider,
              recentChainData,
              pendingBlocks,
              p2pNetwork,
              blockImporter,
              blobSidecarManager,
              blockBlobSidecarsTrackersPool,
              executionPayloadManager,
              syncPreImportBlockChannel,
              syncConfig.getForwardSyncBatchSize(),
              syncConfig.getForwardSyncMaxPendingBatches(),
              syncConfig.getForwardSyncMaxBlocksPerMinute(),
              syncConfig.getForwardSyncMaxBlobSidecarsPerMinute(),
              syncConfig.getForwardSyncMaxDistanceFromHead(),
              spec);
    } else {
      LOG.info("Using single peer sync");
      forwardSync =
          SinglePeerSyncServiceFactory.create(
              metrics,
              asyncRunner,
              p2pNetwork,
              recentChainData,
              blockImporter,
              blobSidecarManager,
              blockBlobSidecarsTrackersPool,
              executionPayloadManager,
              syncConfig.getForwardSyncBatchSize(),
              syncConfig.getForwardSyncMaxDistanceFromHead(),
              spec);
    }
    return forwardSync;
  }
}

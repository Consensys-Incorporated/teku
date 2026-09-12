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

import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.events.TestExceptionHandler.TEST_EXCEPTION_HANDLER;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_MAX_QUEUE_PENDING_ATTESTATIONS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.fetch.DefaultFetchTaskFactory;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.SinglePeerSyncService;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.SyncManager;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetchService;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.blobs.BlockEventsListenerRouter;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingAttestationPool;
import tech.pegasys.teku.statetransition.util.PendingBlockPool;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityFactory;

public class SyncingNodeManager {
  private final EventChannels eventChannels;
  private final RecentChainData recentChainData;
  private final ChainBuilder chainBuilder;
  private final ChainUpdater chainUpdater;
  private final Eth2P2PNetwork eth2P2PNetwork;
  private final ForwardSync syncService;
  private final BlockGossipChannel blockGossipChannel;

  private SyncingNodeManager(
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels,
      final RecentChainData recentChainData,
      final ChainBuilder chainBuilder,
      final ChainUpdater chainUpdater,
      final Eth2P2PNetwork eth2P2PNetwork,
      final ForwardSync syncService) {
    this.eventChannels = eventChannels;
    this.recentChainData = recentChainData;
    this.chainBuilder = chainBuilder;
    this.chainUpdater = chainUpdater;
    this.eth2P2PNetwork = eth2P2PNetwork;
    this.syncService = syncService;
    this.blockGossipChannel = eventChannels.getPublisher(BlockGossipChannel.class, asyncRunner);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public static SyncingNodeManager create(
      final AsyncRunner asyncRunner,
      final Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      final Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(TEST_EXCEPTION_HANDLER, new NoOpMetricsSystem());
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);

    final ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    final ChainUpdater chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    chainUpdater.initializeGenesis();

    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData);

    final MetricsSystem metricsSystem = new StubMetricsSystem();
    final PoolFactory poolFactory = new PoolFactory(metricsSystem);

    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            recentChainData,
            new NoopForkChoiceNotifier(),
            transitionBlockValidator,
            metricsSystem);

    final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher =
        eventChannels.getPublisher(ReceivedBlockEventsChannel.class);

    final BlockGossipValidator blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, metricsSystem),
            receivedBlockEventsChannelPublisher,
            recentChainData.getProposerEquivocationTracker());
    final BlockValidator blockValidator = new BlockValidator(blockGossipValidator);

    final TimeProvider timeProvider = new SystemTimeProvider();
    final PendingBlockPool pendingBlockPool = poolFactory.createPendingBlockPool(spec);
    final PendingPool<SignedBeaconBlock> pendingBlocks =
        pendingBlockPool.getBlocksWaitingForParent();
    final PendingAttestationPool pendingAttestationPool =
        poolFactory.createPendingAttestationPool(spec, DEFAULT_MAX_QUEUE_PENDING_ATTESTATIONS);
    final PendingPool<ValidatableAttestation> pendingAttestations =
        pendingAttestationPool.getAttestationsWaitingForBlock();
    final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations =
        poolFactory.createPendingPoolForPayloadAttestations(spec, 100);
    final FutureItems<SignedBeaconBlock> futureBlocks =
        FutureItems.create(SignedBeaconBlock::getSlot, mock(SettableLabelledGauge.class), "blocks");
    final Map<Bytes32, BlockImportResult> invalidBlockRoots = LimitedMap.createSynchronizedLRU(500);

    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            recentChainData,
            forkChoice,
            WeakSubjectivityFactory.lenientValidator(),
            new ExecutionLayerChannelStub(spec, false));
    final BlockEventsListenerRouter blockEventsListenerRouter =
        new BlockEventsListenerRouter(
            BlockBlobSidecarsTrackersPool.NOOP,
            () -> DataAvailabilitySampler.NOOP,
            recentChainData,
            spec);

    final BlockManager blockManager =
        new BlockManager(
            recentChainData,
            blockImporter,
            blockEventsListenerRouter,
            () -> DataAvailabilitySampler.NOOP,
            pendingBlockPool,
            futureBlocks,
            invalidBlockRoots,
            blockValidator,
            timeProvider,
            EVENT_LOG,
            Optional.empty());

    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager)
        .subscribe(ReceivedBlockEventsChannel.class, blockManager)
        .subscribe(ReceivedExecutionPayloadEventsChannel.class, blockManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingBlockPool)
        .subscribe(SlotEventsChannel.class, pendingBlockPool)
        .subscribe(FinalizedCheckpointChannel.class, pendingAttestationPool)
        .subscribe(SlotEventsChannel.class, pendingAttestationPool);

    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory
            .builder()
            .spec(spec)
            .eventChannels(eventChannels)
            .recentChainData(recentChainData)
            .gossipedBlockProcessor(blockManager::validateAndImportBlock);

    configureNetwork.accept(networkBuilder);

    final Eth2P2PNetwork eth2P2PNetwork = networkBuilder.startNetwork();

    final SyncManager syncManager =
        SyncManager.create(
            asyncRunner,
            eth2P2PNetwork,
            recentChainData,
            blockImporter,
            BlobSidecarManager.NOOP,
            BlockBlobSidecarsTrackersPool.NOOP,
            ExecutionPayloadManager.NOOP,
            new NoOpMetricsSystem(),
            SyncConfig.DEFAULT_FORWARD_SYNC_BATCH_SIZE,
            OptionalInt.empty(),
            spec);

    final ForwardSyncService syncService = new SinglePeerSyncService(syncManager, recentChainData);

    final FetchTaskFactory fetchBlockTaskFactory = new DefaultFetchTaskFactory(eth2P2PNetwork);

    final RecentBlocksFetchService recentBlocksFetcher =
        RecentBlocksFetchService.create(
            asyncRunner,
            pendingBlocks,
            pendingAttestations,
            pendingPayloadAttestations,
            BlockBlobSidecarsTrackersPool.NOOP,
            DasSamplerBasic.NOOP,
            syncService,
            fetchBlockTaskFactory);
    recentBlocksFetcher.subscribeBlockFetched(blockManager::importBlock);
    eventChannels.subscribe(ReceivedBlockEventsChannel.class, recentBlocksFetcher);

    recentBlocksFetcher.start().join();
    blockManager.start().join();
    syncService.start().join();

    return new SyncingNodeManager(
        asyncRunner,
        eventChannels,
        recentChainData,
        chainBuilder,
        chainUpdater,
        eth2P2PNetwork,
        syncService);
  }

  public SafeFuture<Peer> connect(final SyncingNodeManager peer) {
    final PeerAddress peerAddress =
        eth2P2PNetwork.createPeerAddress(peer.network().getNodeAddresses().get(0));
    return eth2P2PNetwork.connect(peerAddress);
  }

  public EventChannels eventChannels() {
    return eventChannels;
  }

  public Eth2P2PNetwork network() {
    return eth2P2PNetwork;
  }

  public ChainBuilder getChainBuilder() {
    return chainBuilder;
  }

  public ChainUpdater getChainUpdater() {
    return chainUpdater;
  }

  public RecentChainData recentChainData() {
    return recentChainData;
  }

  public ForwardSync syncService() {
    return syncService;
  }

  public void setSlot(final UInt64 slot) {
    eventChannels().getPublisher(SlotEventsChannel.class).onSlot(slot);
    getChainUpdater().setCurrentSlot(slot);
  }

  public void gossipBlock(final SignedBeaconBlock block) {
    blockGossipChannel.publishBlock(block).finishStackTrace();
  }
}

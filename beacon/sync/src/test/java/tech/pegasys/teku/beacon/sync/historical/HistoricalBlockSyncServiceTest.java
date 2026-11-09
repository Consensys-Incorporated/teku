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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class HistoricalBlockSyncServiceTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final SignatureVerificationService signatureVerificationService =
      mock(SignatureVerificationService.class);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Eth2Peer> network = mock(P2PNetwork.class);

  private final SyncStateProvider syncStateProvider = mock(SyncStateProvider.class);

  private final CombinedChainDataClient chainData = mock(CombinedChainDataClient.class);
  private final Optional<String> genesisStateResource =
      Optional.of("https://example.com/state.ssz");
  private final ReconstructHistoricalStatesService reconstructHistoricalStatesService =
      new ReconstructHistoricalStatesService(
          storageUpdateChannel, chainData, spec, timeProvider, metricsSystem, genesisStateResource);

  private final UInt64 batchSize = UInt64.valueOf(5);
  private final HistoricalBlockSyncService service =
      new HistoricalBlockSyncService(
          spec,
          blobSidecarManager,
          metricsSystem,
          storageUpdateChannel,
          asyncRunner,
          network,
          storageSystem.combinedChainDataClient(),
          syncStateProvider,
          signatureVerificationService,
          batchSize,
          Optional.of(reconstructHistoricalStatesService),
          false);
  private final Subscribers<SyncStateProvider.SyncStateSubscriber> syncStateSubscribers =
      Subscribers.create(false);

  private final AtomicReference<SyncState> currentSyncState =
      new AtomicReference<>(SyncState.START_UP);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);

  @SuppressWarnings("unchecked")
  @BeforeEach
  public void setup() {
    when(storageUpdateChannel.onFinalizedBlocks(any(), any(), any(), any()))
        .thenReturn(SafeFuture.COMPLETE);
    when(syncStateProvider.subscribeToSyncStateChanges(any()))
        .thenAnswer((i) -> syncStateSubscribers.subscribe(i.getArgument(0)));
    when(syncStateProvider.unsubscribeFromSyncStateChanges(anyLong()))
        .thenAnswer((i) -> syncStateSubscribers.unsubscribe(i.getArgument(0)));
    when(syncStateProvider.getCurrentSyncState()).thenAnswer(i -> currentSyncState.get());
    when(signatureVerificationService.verify(any(), any(), (List<BLSSignature>) any()))
        .thenReturn(SafeFuture.completedFuture(true));
  }

  @Test
  public void shouldCompletedImmediatelyWhenAlreadySyncedToGenesis() {
    currentSyncState.set(SyncState.IN_SYNC);

    // Setup chain
    storageSystem.chainUpdater().initializeGenesis();
    storageSystem.chainUpdater().advanceChainUntil(10);

    startService();

    // Service should complete immediately
    assertServiceFinished();
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());
  }

  @Test
  public void shouldWaitToRunTillNodeIsInSync() {
    currentSyncState.set(SyncState.SYNCING);

    // Setup chain
    final long epochHeight = 10;
    storageSystem.chainBuilder().generateGenesis();
    storageSystem.chainBuilder().generateBlocksUpToSlot(slotsPerEpoch * epochHeight + 3);
    final AnchorPoint anchor =
        initializeChainAtEpoch(storageSystem.chainBuilder().getLatestEpoch());
    final List<SignedBeaconBlock> expectedBlocks =
        storageSystem
            .chainBuilder()
            .streamBlocksAndStates(0, anchor.getBlockSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    // Set up a peer to respond
    final RespondingEth2Peer peer = RespondingEth2Peer.create(spec, storageSystem.chainBuilder());
    peer.updateStatus(
        spec,
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO),
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO));
    when(network.streamPeers()).thenAnswer(i -> Stream.of(peer));

    startService();

    // We should be waiting to actually start the historic sync
    assertServiceNotActive(peer);
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());

    // When we switch to in sync, the service should run and complete
    updateSyncState(SyncState.IN_SYNC);

    // We should start sending requests to pull data
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    finishSyncing(peer, expectedBlocks);
  }

  @Test
  public void shouldRetryIfNoPeersAvailable() {
    currentSyncState.set(SyncState.IN_SYNC);

    // Setup chain
    final long epochHeight = 10;
    storageSystem.chainBuilder().generateGenesis();
    storageSystem.chainBuilder().generateBlocksUpToSlot(slotsPerEpoch * epochHeight + 3);
    final AnchorPoint anchor =
        initializeChainAtEpoch(storageSystem.chainBuilder().getLatestEpoch());
    final List<SignedBeaconBlock> expectedBlocks =
        storageSystem
            .chainBuilder()
            .streamBlocksAndStates(0, anchor.getBlockSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    // Set up a peer to respond
    final RespondingEth2Peer peer = RespondingEth2Peer.create(spec, storageSystem.chainBuilder());
    when(network.streamPeers()).thenAnswer(i -> Stream.of(peer));

    startService();

    // We should be waiting to actually start the historic sync
    assertServiceIsWaitingForPeers(peer);
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any(), any());

    // When should succeed on the next retry
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    peer.updateStatus(
        spec,
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO),
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO));
    asyncRunner.executeQueuedActions();

    // We should start sending requests to pull data
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    finishSyncing(peer, expectedBlocks);
  }

  @Test
  public void shouldStopFetchingIfNodeStartsSyncing() {
    currentSyncState.set(SyncState.IN_SYNC);

    // Setup chain
    final long epochHeight = 10;
    storageSystem.chainBuilder().generateGenesis();
    storageSystem.chainBuilder().generateBlocksUpToSlot(slotsPerEpoch * epochHeight + 3);
    final AnchorPoint anchor =
        initializeChainAtEpoch(storageSystem.chainBuilder().getLatestEpoch());
    final List<SignedBeaconBlock> expectedBlocks =
        storageSystem
            .chainBuilder()
            .streamBlocksAndStates(0, anchor.getBlockSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    // Set up a peer to respond
    final RespondingEth2Peer peer = RespondingEth2Peer.create(spec, storageSystem.chainBuilder());
    peer.updateStatus(
        spec,
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO),
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO));
    when(network.streamPeers()).thenAnswer(i -> Stream.of(peer));

    startService();

    // We should start sending requests to pull data
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);

    updateSyncState(SyncState.SYNCING);

    // Fulfill pending request
    peer.completePendingRequests();
    // No further requests should be made
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    // When we switch back to in sync we should start sending requests again
    updateSyncState(SyncState.IN_SYNC);
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    finishSyncing(peer, expectedBlocks);
  }

  @Test
  public void shouldRetrieveGenesisWhenMissing() {
    currentSyncState.set(SyncState.IN_SYNC);

    // Setup chain
    final int epochHeight = 10;
    final SignedBlockAndState genesis = storageSystem.chainBuilder().generateGenesis();
    storageSystem.chainBuilder().generateBlocksUpToSlot(6);
    initializeChainAtEpoch(UInt64.ZERO);
    final List<SignedBeaconBlock> expectedBlocks = List.of(genesis.getBlock());

    // Set up a peer to respond
    final RespondingEth2Peer peer = RespondingEth2Peer.create(spec, storageSystem.chainBuilder());
    peer.updateStatus(
        spec,
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO),
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO));
    when(network.streamPeers()).thenAnswer(i -> Stream.of(peer));

    startService();

    // We should start sending requests to pull data
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    finishSyncing(peer, expectedBlocks);
  }

  @Test
  public void shouldOnlySendOneRequestAtATime() {
    currentSyncState.set(SyncState.IN_SYNC);

    // Setup chain
    final long epochHeight = 10;
    storageSystem.chainBuilder().generateGenesis();
    storageSystem.chainBuilder().generateBlocksUpToSlot(slotsPerEpoch * epochHeight + 3);
    final AnchorPoint anchor =
        initializeChainAtEpoch(storageSystem.chainBuilder().getLatestEpoch());
    final List<SignedBeaconBlock> expectedBlocks =
        storageSystem
            .chainBuilder()
            .streamBlocksAndStates(0, anchor.getBlockSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    // Set up a peer to respond
    final RespondingEth2Peer peer = RespondingEth2Peer.create(spec, storageSystem.chainBuilder());
    peer.updateStatus(
        spec,
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO),
        new Checkpoint(UInt64.valueOf(epochHeight * 2), Bytes32.ZERO));
    when(network.streamPeers()).thenAnswer(i -> Stream.of(peer));

    startService();

    final int maxRequests =
        storageSystem.chainBuilder().getLatestSlot().dividedBy(batchSize).plus(1).intValue();
    int requestCount = 0;
    while (service.isRunning() && requestCount <= maxRequests) {
      // Trigger some sync events
      updateSyncState(SyncState.SYNCING);
      updateSyncState(SyncState.IN_SYNC);

      // Peer should only have 1 outstanding request
      assertThat(peer.getOutstandingRequests()).isEqualTo(1);
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
      peer.completePendingRequests();
      requestCount++;
    }

    assertServiceFinished();
    assertBlocksSaved(expectedBlocks);
  }

  private void finishSyncing(
      final RespondingEth2Peer peer, final List<SignedBeaconBlock> expectedBlocks) {
    final int maxRequests =
        storageSystem.chainBuilder().getLatestSlot().dividedBy(batchSize).plus(1).intValue();
    int requestCount = 0;
    while (peer.getOutstandingRequests() == 1 && requestCount <= maxRequests) {
      peer.completePendingRequests();
      requestCount++;
    }

    assertServiceFinished();
    assertBlocksSaved(expectedBlocks);
  }

  private void startService() {
    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
  }

  private void assertServiceFinished() {
    assertThat(service.isRunning()).isFalse();
    assertThat(syncStateSubscribers.getSubscriberCount()).isEqualTo(0);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
  }

  private void assertServiceNotActive(final RespondingEth2Peer... peers) {
    assertThat(service.isRunning()).isTrue();
    assertThat(syncStateSubscribers.getSubscriberCount()).isEqualTo(1);
    for (RespondingEth2Peer peer : peers) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    }
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
  }

  private void assertServiceIsWaitingForPeers(final RespondingEth2Peer... peers) {
    assertThat(service.isRunning()).isTrue();
    assertThat(syncStateSubscribers.getSubscriberCount()).isEqualTo(1);
    for (RespondingEth2Peer peer : peers) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    }
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
  }

  private AnchorPoint initializeChainAtEpoch(final UInt64 epoch) {
    return initializeChainAtEpoch(epoch, false);
  }

  private AnchorPoint initializeChainAtEpoch(final UInt64 epoch, final boolean includeAnchorBlock) {
    final Checkpoint anchorCheckpoint =
        storageSystem.chainBuilder().getCurrentCheckpointForEpoch(epoch);
    final SignedBlockAndState anchorStateAndBlock =
        storageSystem.chainBuilder().getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();
    final Optional<SignedBeaconBlock> block =
        includeAnchorBlock ? Optional.of(anchorStateAndBlock.getBlock()) : Optional.empty();
    final AnchorPoint anchorPoint =
        AnchorPoint.create(spec, anchorCheckpoint, anchorStateAndBlock.getState(), block);
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, UInt64.ZERO);

    return anchorPoint;
  }

  private void assertBlocksSaved(final List<SignedBeaconBlock> expectedBlocks) {
    verify(storageUpdateChannel, atLeastOnce())
        .onFinalizedBlocks(blockCaptor.capture(), any(), any(), any());
    final List<SignedBeaconBlock> allBlocks =
        blockCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    assertThat(allBlocks).containsExactlyInAnyOrderElementsOf(expectedBlocks);
  }

  private void updateSyncState(final SyncState syncState) {
    currentSyncState.set(syncState);
    syncStateSubscribers.deliver(
        SyncStateProvider.SyncStateSubscriber::onSyncStateChange, syncState);
  }
}

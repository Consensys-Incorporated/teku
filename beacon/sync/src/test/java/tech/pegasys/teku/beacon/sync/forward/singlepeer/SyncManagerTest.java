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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncManagerTest {

  private static final long SUBSCRIPTION_ID = 3423;
  private static final Bytes32 PEER_HEAD_BLOCK_ROOT = Bytes32.fromHexString("0x1234");
  private static final UInt64 PEER_FINALIZED_EPOCH = UInt64.valueOf(3);

  private final Spec spec = TestSpecFactory.createDefault();
  private final UInt64 peerHeadSlot = UInt64.valueOf(spec.getSlotsPerEpoch(UInt64.ZERO) * 5L);
  private final PeerStatus peerStatus =
      PeerStatus.fromStatusMessage(
          new StatusMessagePhase0(
              Bytes4.leftPad(Bytes.EMPTY),
              Bytes32.ZERO,
              PEER_FINALIZED_EPOCH,
              PEER_HEAD_BLOCK_ROOT,
              peerHeadSlot));

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Eth2P2PNetwork network = mock(Eth2P2PNetwork.class);
  private final PeerSync peerSync = mock(PeerSync.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final SyncManager syncManager =
      new SyncManager(asyncRunner, network, recentChainData, peerSync, spec);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final SyncSubscriber syncSubscriber = mock(SyncSubscriber.class);

  private final AtomicReference<UInt64> localSlot = new AtomicReference<>(peerHeadSlot);
  private final AtomicReference<UInt64> localHeadSlot = new AtomicReference<>(UInt64.ZERO);
  private final AtomicReference<UInt64> localFinalizedEpoch = new AtomicReference<>(UInt64.ZERO);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<PeerConnectedSubscriber<Eth2Peer>> onConnectionListener =
      ArgumentCaptor.forClass(PeerConnectedSubscriber.class);

  @BeforeEach
  public void setUp() {
    when(network.subscribeConnect(any())).thenReturn(SUBSCRIPTION_ID);
    when(recentChainData.getFinalizedEpoch()).thenAnswer((__) -> localFinalizedEpoch.get());
    when(recentChainData.getCurrentSlot()).thenAnswer((__) -> Optional.ofNullable(localSlot.get()));
    when(recentChainData.getHeadSlot()).thenAnswer((__) -> localHeadSlot.get());
    when(peer.getStatus()).thenReturn(peerStatus);
  }

  @Test
  void sync_noPeers() {
    when(network.streamPeers()).thenReturn(Stream.empty());
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_noSuitablePeers() {
    // We're already in sync with the peer
    setLocalChainState(peerStatus.getHeadSlot(), peerStatus.getFinalizedEpoch());

    when(network.streamPeers()).thenReturn(Stream.of(peer));
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_noSuitablePeers_almostInSync() {
    // We're almost in sync with the peer
    final UInt64 oldHeadSlot = peerStatus.getHeadSlot().minus(spec.getSlotsPerEpoch(UInt64.ZERO));
    setLocalChainState(oldHeadSlot, peerStatus.getFinalizedEpoch().minus(1));

    when(network.streamPeers()).thenReturn(Stream.of(peer));
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_noSuitablePeers_remoteEpochInTheFuture() {
    // Remote peer finalized epoch is too far ahead
    final UInt64 headSlot = spec.computeStartSlotAtEpoch(PEER_FINALIZED_EPOCH).minus(1);
    localSlot.set(headSlot);

    when(network.streamPeers()).thenReturn(Stream.of(peer));
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_noSuitablePeers_remoteHeadSlotInTheFuture() {
    // Remote peer head slot is too far ahead
    final UInt64 headSlot = peerHeadSlot.minus(2);
    localSlot.set(headSlot);

    when(network.streamPeers()).thenReturn(Stream.of(peer));
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);
  }

  @Test
  void sync_existingPeers() {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // Signal the peer sync is complete
    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_existingPeers_remoteHeadSlotIsAheadButWithinErrorThreshold() {
    final UInt64 headSlot = peerHeadSlot.minus(1);
    localSlot.set(headSlot);

    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // Signal the peer sync is complete
    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_existingPeers_peerFinalizedEpochMoreThan1EpochAhead() {
    setLocalChainState(peerStatus.getHeadSlot(), peerStatus.getFinalizedEpoch().minus(2));
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // Signal the peer sync is complete
    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_existingPeerWithSameFinalizedEpochButMuchBetterHeadSlot() {
    when(network.streamPeers()).thenReturn(Stream.of(peer));
    final UInt64 oldHeadSlot =
        peerStatus.getHeadSlot().minus(spec.getSlotsPerEpoch(UInt64.ZERO) + 1);
    setLocalChainState(oldHeadSlot, peerStatus.getFinalizedEpoch());

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // Signal the peer sync is complete
    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_retrySyncIfNotSuccessful() {
    when(network.streamPeers()).thenReturn(Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);

    // The sync didn't complete correctly so we should start a new one with a new peer
    final Eth2Peer peer2 = mock(Eth2Peer.class);
    when(peer2.getStatus()).thenReturn(peerStatus);
    when(network.streamPeers()).thenReturn(Stream.of(peer2));
    when(peerSync.sync(peer2)).thenReturn(new SafeFuture<>());
    syncFuture.complete(PeerSyncResult.FAULTY_ADVERTISEMENT);

    asyncRunner.executeQueuedActions();
    verify(peerSync).sync(peer2);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void sync_newPeer() {
    assertThat(syncManager.start()).isCompleted();
    // No peers initially so sync doesn't start.
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(network).subscribeConnect(onConnectionListener.capture());
    final PeerConnectedSubscriber<Eth2Peer> subscriber = onConnectionListener.getValue();

    final SafeFuture<PeerSyncResult> syncFuture1 = new SafeFuture<>();
    final SafeFuture<PeerSyncResult> syncFuture2 = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture1);

    when(network.streamPeers()).thenReturn(Stream.of(peer));
    subscriber.onConnected(peer);

    // Sync is activated by first peer joining.
    verify(peerSync).sync(peer);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    // Second peer connecting causes another sync to be scheduled.
    final Eth2Peer peer2 = mock(Eth2Peer.class);
    when(peer2.getStatus()).thenReturn(peerStatus);
    when(network.streamPeers()).thenReturn(Stream.of(peer2));
    when(peerSync.sync(peer2)).thenReturn(syncFuture2);

    subscriber.onConnected(peer2);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isTrue();

    // First sync completes and should kick off the second sync.
    syncFuture1.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    verify(peerSync).sync(peer2);
    assertThat(syncManager.isSyncActive()).isTrue();
    assertThat(syncManager.isSyncQueued()).isFalse();

    // Stop syncing when second sync completes.
    syncFuture2.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
  }

  @Test
  void stop_shouldStopPeerSyncAndRemoveListener() {
    assertThat(syncManager.start()).isCompleted();

    assertThat(syncManager.stop()).isCompleted();
    assertThat(syncManager.isSyncQueued()).isFalse();
    assertThat(syncManager.isSyncActive()).isFalse();
    verify(peerSync).stop();
    verify(network).unsubscribeConnect(SUBSCRIPTION_ID);
  }

  @Test
  void sync_syncStatus() {
    // stream needs to be used more than once
    when(network.streamPeers()).then(i -> Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);
    UInt64 startingSlot = UInt64.valueOf(11);
    when(peerSync.getStartingSlot()).thenReturn(startingSlot);

    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isTrue();

    UInt64 headSlot = UInt64.valueOf(17);
    localHeadSlot.set(headSlot);

    SyncingStatus syncingStatus = syncManager.getSyncStatus();
    assertThat(syncingStatus.getCurrentSlot()).isEqualTo(headSlot);
    assertThat(syncingStatus.getStartingSlot()).isEqualTo(Optional.of(startingSlot));
    assertThat(syncingStatus.getHighestSlot()).isEqualTo(Optional.of(peerHeadSlot));

    assertThat(syncManager.isSyncQueued()).isFalse();

    verify(peerSync).sync(peer);
  }

  @Test
  void sync_isSyncing_noPeers() {
    when(network.streamPeers()).thenReturn(Stream.empty());
    // Should be immediately completed as there is nothing to do.
    assertThat(syncManager.start()).isCompleted();
    assertThat(syncManager.isSyncActive()).isFalse();
    assertThat(syncManager.isSyncQueued()).isFalse();
    verifyNoInteractions(peerSync);

    assertThat(syncManager.isSyncQueued()).isFalse();
    SyncingStatus syncingStatus = syncManager.getSyncStatus();
    assertThat(syncingStatus.isSyncing()).isFalse();
    assertThat(syncingStatus.getCurrentSlot()).isNotNull();
    assertThat(syncingStatus.getHighestSlot()).isEmpty();
    assertThat(syncingStatus.getStartingSlot()).isEmpty();
  }

  @Test
  void subscribeToSyncChanges_notifiedWhenFirstSyncStarts() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).then(i -> Stream.of(peer));

    when(peerSync.sync(peer)).thenReturn(new SafeFuture<>());
    assertThat(syncManager.start()).isCompleted();

    verify(syncSubscriber).onSyncingChange(true);
    verifyNoMoreInteractions(syncSubscriber);
  }

  @Test
  void subscribeToSyncChanges_notifiedWhenSyncCompletes() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).then(i -> Stream.of(peer));

    final SafeFuture<PeerSyncResult> syncFuture = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(syncFuture);
    assertThat(syncManager.start()).isCompleted();
    verify(syncSubscriber).onSyncingChange(true);
    verifyNoMoreInteractions(syncSubscriber);

    syncFuture.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    verify(syncSubscriber).onSyncingChange(false);
    verifyNoMoreInteractions(syncSubscriber);
  }

  @Test
  void subscribeToSyncChanges_notNotifiedWhenSyncCompletesAndImmediatelyStartsAgain() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).then(i -> Stream.of(peer));

    final SafeFuture<PeerSyncResult> sync1Future = new SafeFuture<>();
    final SafeFuture<PeerSyncResult> sync2Future = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(sync1Future).thenReturn(sync2Future);
    assertThat(syncManager.start()).isCompleted();
    verify(syncSubscriber).onSyncingChange(true);
    verifyNoMoreInteractions(syncSubscriber);

    verify(network).subscribeConnect(onConnectionListener.capture());
    final PeerConnectedSubscriber<Eth2Peer> peerConnectedSubscriber =
        onConnectionListener.getValue();

    // Another peer connects while we're syncing to the first queuing up another sync.
    peerConnectedSubscriber.onConnected(peer);

    // The first sync completes but we should immediately start syncing to the second peer.
    sync1Future.complete(PeerSyncResult.SUCCESSFUL_SYNC);
    assertThat(syncManager.isSyncActive()).isTrue();
    verifyNoMoreInteractions(syncSubscriber);
  }

  @Test
  void subscribeToSyncChanges_notNotifiedWhenSyncFailsToFindPeersToSyncTo() {
    syncManager.subscribeToSyncChanges(syncSubscriber);

    when(network.streamPeers()).thenReturn(Stream.empty());

    final SafeFuture<PeerSyncResult> sync1Future = new SafeFuture<>();
    when(peerSync.sync(peer)).thenReturn(sync1Future);
    assertThat(syncManager.start()).isCompleted();
    verifyNoInteractions(syncSubscriber);
  }

  private void setLocalChainState(final UInt64 headSlot, final UInt64 finalizedEpoch) {
    localHeadSlot.set(headSlot);
    localFinalizedEpoch.set(finalizedEpoch);
  }
}

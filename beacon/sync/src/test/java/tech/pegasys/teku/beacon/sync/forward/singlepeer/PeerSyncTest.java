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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.singlepeer.PeerSync.MAX_THROTTLED_REQUESTS;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DecompressFailedException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;

public class PeerSyncTest extends AbstractSyncTest {

  private static final UInt64 DENEB_FORK_EPOCH = UInt64.valueOf(100);
  private static final UInt64 GLOAS_FORK_EPOCH = DENEB_FORK_EPOCH.plus(300);

  private static final UInt64 FORWARD_SYNC_BATCH_SIZE =
      UInt64.valueOf(SyncConfig.DEFAULT_FORWARD_SYNC_BATCH_SIZE);

  private static final Bytes32 PEER_HEAD_BLOCK_ROOT = Bytes32.fromHexString("0x1234");
  private static final UInt64 PEER_HEAD_SLOT = UInt64.valueOf(20);
  private static final UInt64 PEER_FINALIZED_EPOCH = UInt64.valueOf(3);

  private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();

  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);

  private static final PeerStatus PEER_STATUS =
      PeerStatus.fromStatusMessage(
          new StatusMessagePhase0(
              Bytes4.leftPad(Bytes.EMPTY),
              Bytes32.ZERO,
              PEER_FINALIZED_EPOCH,
              PEER_HEAD_BLOCK_ROOT,
              PEER_HEAD_SLOT));

  private final UInt64 denebPeerSlotsAhead = UInt64.valueOf(20);
  private final UInt64 denebFirstSlot = spec.computeStartSlotAtEpoch(DENEB_FORK_EPOCH);
  private final UInt64 denebPeerHeadSlot = denebFirstSlot.plus(denebPeerSlotsAhead);
  private final UInt64 denebPeerFinalizedEpoch = spec.computeEpochAtSlot(denebPeerHeadSlot);

  private final UInt64 gloasPeerSlotsAhead = UInt64.valueOf(20);
  private final UInt64 gloasFirstSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH);
  private final UInt64 gloasPeerHeadSlot = gloasFirstSlot.plus(gloasPeerSlotsAhead);
  private final UInt64 gloasPeerFinalizedEpoch = spec.computeEpochAtSlot(gloasPeerHeadSlot);

  private PeerSync peerSync;

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalGloas(
        builder ->
            builder
                .denebForkEpoch(DENEB_FORK_EPOCH)
                .electraForkEpoch(DENEB_FORK_EPOCH.plus(100))
                .fuluForkEpoch(DENEB_FORK_EPOCH.plus(200))
                .gloasForkEpoch(GLOAS_FORK_EPOCH));
  }

  @BeforeEach
  public void setUp() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(recentChainData.getHeadSlot()).thenReturn(UInt64.ONE);
    when(peer.getStatus()).thenReturn(PEER_STATUS);
    when(peer.disconnectCleanly(any())).thenReturn(SafeFuture.COMPLETE);
    // By default, set up imports to succeed
    when(blockImporter.importBlock(any()))
        .thenAnswer(
            invocationOnMock ->
                SafeFuture.completedFuture(
                    BlockImportResult.successful(invocationOnMock.getArgument(0))));
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
    when(executionPayloadManager.importExecutionPayload(any(), anyBoolean()))
        .thenAnswer(
            invocationOnMock ->
                SafeFuture.completedFuture(
                    ExecutionPayloadImportResult.successful(invocationOnMock.getArgument(0))));

    peerSync =
        new PeerSync(
            asyncRunner,
            recentChainData,
            blockImporter,
            blobSidecarManager,
            blockBlobSidecarsTrackersPool,
            executionPayloadManager,
            FORWARD_SYNC_BATCH_SIZE.intValue(),
            OptionalInt.empty(),
            new NoOpMetricsSystem());
  }

  @Test
  void sync_failedImport_stateTransitionError() {
    final BlockImportResult importResult =
        BlockImportResult.failedStateTransition(new StateTransitionException());
    testFailedBlockImport(() -> importResult, true);
  }

  @Test
  void sync_failedImport_unknownParent_fromFinalizedRange() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_UNKNOWN_PARENT, true);
  }

  @Test
  void sync_failedImport_unknownParent_fromNonFinalRange() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(
            PEER_STATUS.getFinalizedCheckpoint().getEpochStartSlot(spec).plus(1));
    testFailedBlockImport(() -> BlockImportResult.FAILED_UNKNOWN_PARENT, false, block);
  }

  @Test
  void sync_failedImport_failedWeakSubjectivityChecks() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_WEAK_SUBJECTIVITY_CHECKS, true);
  }

  @Test
  void sync_failedImport_unknownAncestry() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_INVALID_ANCESTRY, false);
  }

  @Test
  void sync_failedImport_unknownBlockIsFromFuture() {
    testFailedBlockImport(() -> BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE, false);
  }

  void testFailedBlockImport(
      final Supplier<BlockImportResult> importResult, final boolean shouldDisconnect) {
    testFailedBlockImport(importResult, shouldDisconnect, block);
  }

  void testFailedBlockImport(
      final Supplier<BlockImportResult> importResult,
      final boolean shouldDisconnect,
      final SignedBeaconBlock block) {
    final SafeFuture<Void> requestFuture = new SafeFuture<>();

    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer).requestBlocksByRange(any(), any(), blockResponseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final RpcResponseListener<SignedBeaconBlock> responseListener =
        blockResponseListenerArgumentCaptor.getValue();

    // Importing the returned block fails
    when(blockImporter.importBlock(block))
        .thenReturn(SafeFuture.completedFuture(importResult.get()));
    // Probably want to have a specific exception type to indicate bad data.
    try {
      responseListener.onResponse(block).join();
      fail("Should have thrown an error to indicate the response was bad");
    } catch (final Exception e) {
      // RpcMessageHandler will consider the request complete if there's an error processing a
      // response
      assertThat(e).hasCauseInstanceOf(FailedBlockImportException.class);
      requestFuture.completeExceptionally(e);
    }

    assertThat(syncFuture).isCompleted();
    final PeerSyncResult result = safeJoin(syncFuture);
    if (shouldDisconnect) {
      verify(peer).disconnectCleanly(DisconnectReason.REMOTE_FAULT);
      assertThat(result).isEqualByComparingTo(PeerSyncResult.BAD_BLOCK);
    } else {
      verify(peer, never()).disconnectCleanly(any());
      assertThat(result).isEqualByComparingTo(PeerSyncResult.BLOCK_IMPORT_FAILED);
    }
  }

  @Test
  void sync_stoppedBeforeBlockImport() {
    final UInt64 startHere = UInt64.ONE;
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer).requestBlocksByRange(any(), any(), blockResponseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final RpcResponseListener<SignedBeaconBlock> responseListener =
        blockResponseListenerArgumentCaptor.getValue();

    // Stop the sync, no further blocks should be imported
    peerSync.stop();

    try {
      responseListener.onResponse(block).join();
      fail("Should have thrown an error to indicate the sync was stopped");
    } catch (final CancellationException e) {
      // RpcMessageHandler will consider the request complete if there's an error processing a
      // response
      requestFuture.completeExceptionally(e);
    }

    // Should not disconnect the peer as it wasn't their fault
    verify(peer, never()).disconnectCleanly(any());
    verifyNoInteractions(blockImporter);
    assertThat(syncFuture).isCompleted();
    PeerSyncResult result = safeJoin(syncFuture);
    assertThat(result).isEqualByComparingTo(PeerSyncResult.CANCELLED);

    // check startingSlot
    assertThat(peerSync.getStartingSlot()).isEqualTo(startHere);
  }

  @Test
  void sync_badAdvertisedFinalizedEpoch() {
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer).requestBlocksByRange(any(), any(), blockResponseListenerArgumentCaptor.capture());

    // Respond with blocks and check they're passed to the block importer.
    final RpcResponseListener<SignedBeaconBlock> responseListener =
        blockResponseListenerArgumentCaptor.getValue();
    final List<SignedBeaconBlock> blocks =
        respondWithBlocksAtSlots(requestFuture, responseListener, UInt64.ONE, PEER_HEAD_SLOT);
    for (SignedBeaconBlock block : blocks) {
      verify(blockImporter).importBlock(block);
    }
    assertThat(syncFuture).isNotDone();

    // Now that we've imported the block, our finalized epoch has updated but hasn't reached what
    // the peer claimed
    when(recentChainData.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH.minus(UInt64.ONE));

    // Signal the request for data from the peer is complete.
    requestFuture.complete(null);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer).disconnectCleanly(DisconnectReason.REMOTE_FAULT);
  }

  @Test
  void sync_longSyncWithTwoRequests() {
    final UInt64 secondRequestSize = UInt64.ONE;
    final UInt64 peerHeadSlot = FORWARD_SYNC_BATCH_SIZE.plus(secondRequestSize);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    final UInt64 startSlot = UInt64.ONE;

    verify(peer).requestBlocksByRange(eq(startSlot), eq(FORWARD_SYNC_BATCH_SIZE), any());

    completeRequestWithBlocksAtSlots(requestFuture1, startSlot, FORWARD_SYNC_BATCH_SIZE);

    final UInt64 nextSlotStart = peerHeadSlot.minus(secondRequestSize).plus(1);

    verify(peer).requestBlocksByRange(eq(nextSlotStart), eq(secondRequestSize), any());

    when(recentChainData.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);
    // Respond with blocks and check they are passed to the block importer.
    completeRequestWithBlocksAtSlots(requestFuture2, nextSlotStart, secondRequestSize);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_withPeerStatusUpdatedWhileSyncing() {
    final UInt64 initialPeerHeadSlot = PEER_HEAD_SLOT;

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    final UInt64 startSlot = UInt64.ONE;
    verify(peer).requestBlocksByRange(eq(startSlot), eq(initialPeerHeadSlot), any());

    // Update peer's status before completing first request, which should prompt a second request
    final UInt64 secondRequestSize = UInt64.valueOf(5);
    UInt64 updatedPeerHeadSlot = initialPeerHeadSlot.plus(secondRequestSize);
    withPeerHeadSlot(updatedPeerHeadSlot);

    completeRequestWithBlocksAtSlots(requestFuture1, startSlot, initialPeerHeadSlot);

    final UInt64 nextSlotStart = initialPeerHeadSlot.plus(1);

    verify(peer).requestBlocksByRange(eq(nextSlotStart), eq(secondRequestSize), any());

    when(recentChainData.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);

    // Respond with blocks and check they're passed to the block importer.
    completeRequestWithBlocksAtSlots(requestFuture2, nextSlotStart, secondRequestSize);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_handleEmptyResponse() {
    final UInt64 secondRequestSize = UInt64.valueOf(5);
    final UInt64 peerHeadSlot = FORWARD_SYNC_BATCH_SIZE.plus(secondRequestSize);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture3 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2)
        .thenReturn(requestFuture3);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    final UInt64 startSlot = UInt64.ONE;
    verify(peer).requestBlocksByRange(eq(startSlot), eq(FORWARD_SYNC_BATCH_SIZE), any());

    // Complete request with no returned blocks
    requestFuture1.complete(null);
    verify(blockImporter, never()).importBlock(any());

    // check startingSlot
    final UInt64 syncStatusStartingSlot = peerSync.getStartingSlot();
    assertThat(syncStatusStartingSlot).isEqualTo(startSlot);

    asyncRunner.executeQueuedActions();
    final UInt64 nextSlotStart = startSlot.plus(FORWARD_SYNC_BATCH_SIZE);

    when(recentChainData.getFinalizedEpoch()).thenReturn(PEER_FINALIZED_EPOCH);

    verify(peer).requestBlocksByRange(eq(nextSlotStart), eq(secondRequestSize), any());

    completeRequestWithBlocksAtSlots(requestFuture2, nextSlotStart, secondRequestSize);

    // Check that the sync is done
    assertThat(syncFuture).isCompleted();

    // check startingSlot is still where it was
    final UInt64 syncStatusStartingSlot2 = peerSync.getStartingSlot();
    assertThat(syncStatusStartingSlot2).isEqualTo(startSlot);

    // do another sync and check that things are further along.
    UInt64 thirdRequestSize = UInt64.valueOf(6);
    withPeerHeadSlot(peerHeadSlot.plus(thirdRequestSize));
    final SafeFuture<PeerSyncResult> syncFuture2 = peerSync.sync(peer);
    assertThat(syncFuture2).isNotDone();

    // first non-finalized slot after syncing with peer
    final UInt64 secondSyncStartingSlot =
        PEER_FINALIZED_EPOCH.times(slotsPerEpoch).plus(UInt64.ONE);

    verify(peer).requestBlocksByRange(eq(secondSyncStartingSlot), any(), any());

    // Signal that second sync is complete
    requestFuture3.complete(null);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture2).isCompleted();
    verify(peer, never()).disconnectCleanly(any());

    // check that starting slot for second sync is the first slot after peer's finalized epoch
    final UInt64 syncStatusStartingSlot3 = peerSync.getStartingSlot();
    assertThat(syncStatusStartingSlot3).isEqualTo(secondSyncStartingSlot);
  }

  @Test
  void sync_failSyncIfPeerThrottlesTooAggressively() {
    final UInt64 startSlot = UInt64.ONE;
    final UInt64 minPeerSlot = FORWARD_SYNC_BATCH_SIZE.plus(startSlot);
    withPeerFinalizedEpoch(spec.computeEpochAtSlot(minPeerSlot));

    final List<SafeFuture<Void>> requestFutures = new ArrayList<>();
    OngoingStubbing<SafeFuture<Void>> requestStub =
        when(peer.requestBlocksByRange(any(), any(), any()));
    for (int i = 0; i < MAX_THROTTLED_REQUESTS + 1; i++) {
      final SafeFuture<Void> future = new SafeFuture<>();
      requestStub = requestStub.thenReturn(future);
      requestFutures.add(future);
    }

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer).requestBlocksByRange(eq(startSlot), eq(FORWARD_SYNC_BATCH_SIZE), any());

    // Peer only returns one block for each request
    UInt64 nextBlock = startSlot;
    for (int i = 0; i < MAX_THROTTLED_REQUESTS; i++) {
      completeRequestWithBlockAtSlot(requestFutures.get(i), nextBlock);
      nextBlock = nextBlock.increment();
    }

    // We haven't hit our limit yet
    assertThat(syncFuture).isNotDone();

    // Next request hits our limit
    completeRequestWithBlockAtSlot(requestFutures.get(MAX_THROTTLED_REQUESTS), nextBlock);

    // We hit our limit
    assertThat(syncFuture).isCompletedWithValue(PeerSyncResult.EXCESSIVE_THROTTLING);
    // We don't disconnect the peer, the SyncManager just excludes the peer as a sync target for a
    // period
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_stopSyncIfPeerSendsBlocksInWrongOrder() {
    final UInt64 startSlot = UInt64.ONE;
    UInt64 peerHeadSlot = UInt64.valueOf(1000);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(requestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    verify(peer)
        .requestBlocksByRange(
            eq(startSlot),
            eq(FORWARD_SYNC_BATCH_SIZE),
            blockResponseListenerArgumentCaptor.capture());

    requestFuture.completeExceptionally(
        new BlocksByRangeResponseInvalidResponseException(
            peer,
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT));

    // Peer returns some blocks but they are not ordered
    assertThat(syncFuture).isCompletedWithValue(PeerSyncResult.INVALID_RESPONSE);

    verify(peer).disconnectCleanly(any());
  }

  @Test
  void sync_continueSyncIfPeerThrottlesAReasonableAmount() {
    final UInt64 startSlot = UInt64.ONE;
    UInt64 peerHeadSlot = UInt64.valueOf(1000);

    withPeerHeadSlot(peerHeadSlot);

    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    final SafeFuture<Void> requestFuture2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), any(), any()))
        .thenReturn(requestFuture1)
        .thenReturn(requestFuture2);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isNotDone();

    // Peer only returns some blocks but not as many as were requested
    final int lastReceivedBlockSlot = 30;

    verify(peer).requestBlocksByRange(eq(startSlot), eq(FORWARD_SYNC_BATCH_SIZE), any());

    completeRequestWithBlocksAtSlots(
        requestFuture1, startSlot, UInt64.valueOf(lastReceivedBlockSlot));

    assertThat(syncFuture).isNotDone();

    // Next request should start after the last received block
    verify(peer)
        .requestBlocksByRange(
            eq(UInt64.valueOf(lastReceivedBlockSlot + 1)), eq(FORWARD_SYNC_BATCH_SIZE), any());
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_invalidResponseResultWhenMalformedResponse() {
    when(peer.requestBlocksByRange(any(), any(), any()))
        .thenReturn(SafeFuture.failedFuture(new DecompressFailedException()));

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);
    assertThat(syncFuture).isCompletedWithValue(PeerSyncResult.INVALID_RESPONSE);
  }

  @Test
  void sync_blocksAndBlobSidecarsForDeneb() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(DENEB_FORK_EPOCH);
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    final UInt64 denebSecondSlot = denebFirstSlot.plus(1);

    withDenebPeerHeadSlot();

    final SafeFuture<Void> blocksRequestFuture = new SafeFuture<>();
    final SafeFuture<Void> blobSidecarsRequestFuture = new SafeFuture<>();

    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(blocksRequestFuture);
    when(peer.requestBlobSidecarsByRange(any(), any(), any()))
        .thenReturn(blobSidecarsRequestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);

    assertThat(syncFuture).isNotDone();

    // update the chain with the peer finalized epoch to ensure next time the sync completes
    when(recentChainData.getFinalizedEpoch()).thenReturn(denebPeerFinalizedEpoch);

    verify(peer).requestBlobSidecarsByRange(eq(denebSecondSlot), eq(denebPeerSlotsAhead), any());

    final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlot =
        completeRequestWithBlobSidecarsAtSlots(
            blobSidecarsRequestFuture, denebSecondSlot, denebPeerSlotsAhead);

    verify(peer).requestBlocksByRange(eq(denebSecondSlot), eq(denebPeerSlotsAhead), any());

    completeRequestWithBlocksAtSlots(blocksRequestFuture, denebSecondSlot, denebPeerSlotsAhead);

    verifyBlobSidecarsAddedToPool(denebSecondSlot, denebPeerSlotsAhead, blobSidecarsBySlot);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
  }

  @Test
  void sync_blobSidecarsWhenEndSlotInEpochBeforeFulu() {
    final UInt64 fuluEpoch = UInt64.valueOf(101);
    final Spec spec =
        TestSpecFactory.createMinimalFulu(
            builder ->
                builder
                    .denebForkEpoch(DENEB_FORK_EPOCH)
                    .electraForkEpoch(DENEB_FORK_EPOCH)
                    .fuluForkEpoch(fuluEpoch));
    when(recentChainData.getFinalizedEpoch()).thenReturn(DENEB_FORK_EPOCH);
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any()))
        .thenAnswer(
            args -> {
              final UInt64 slot = args.getArgument(0);
              if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
                return true;
              }
              return false;
            });

    final UInt64 denebSecondSlot = denebFirstSlot.plus(1);

    withDenebPeerHeadSlot();

    final SafeFuture<Void> blocksRequestFuture = new SafeFuture<>();
    final SafeFuture<Void> blobSidecarsRequestFuture = new SafeFuture<>();

    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(blocksRequestFuture);
    when(peer.requestBlobSidecarsByRange(any(), any(), any()))
        .thenReturn(blobSidecarsRequestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);

    assertThat(syncFuture).isNotDone();

    // update the chain with the peer finalized epoch to ensure next time the sync completes
    when(recentChainData.getFinalizedEpoch()).thenReturn(denebPeerFinalizedEpoch);

    final UInt64 slotsPerEpochMinimal = UInt64.valueOf(8);
    final UInt64 denebLastSlot = denebFirstSlot.plus(slotsPerEpochMinimal);
    assertThat(spec.atSlot(denebLastSlot).getMilestone().equals(SpecMilestone.FULU)).isTrue();
    assertThat(denebPeerSlotsAhead.isGreaterThan(slotsPerEpochMinimal)).isTrue();

    verify(peer).requestBlobSidecarsByRange(eq(denebSecondSlot), eq(denebPeerSlotsAhead), any());

    final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlot =
        completeRequestWithBlobSidecarsAtSlots(
            blobSidecarsRequestFuture, denebSecondSlot, slotsPerEpochMinimal);

    verify(peer).requestBlocksByRange(eq(denebSecondSlot), eq(denebPeerSlotsAhead), any());

    completeRequestWithBlocksAtSlots(blocksRequestFuture, denebSecondSlot, denebPeerSlotsAhead);

    verifyBlobSidecarsAddedToPool(denebSecondSlot, slotsPerEpochMinimal, blobSidecarsBySlot);

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
    verify(peer, never()).disconnectImmediately(any(), anyBoolean());
  }

  @Test
  void sync_blocksAndExecutionPayloadsForGloas() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(GLOAS_FORK_EPOCH);

    final UInt64 gloasSecondSlot = gloasFirstSlot.plus(1);

    withGloasPeerHeadSlot();

    final SafeFuture<Void> blocksRequestFuture = new SafeFuture<>();
    final SafeFuture<Void> executionPayloadsRequestFuture = new SafeFuture<>();

    when(peer.requestBlocksByRange(any(), any(), any())).thenReturn(blocksRequestFuture);
    when(peer.requestExecutionPayloadEnvelopesByRange(any(), any(), any()))
        .thenReturn(executionPayloadsRequestFuture);

    final SafeFuture<PeerSyncResult> syncFuture = peerSync.sync(peer);

    assertThat(syncFuture).isNotDone();

    // update the chain with the peer finalized epoch to ensure next time the sync completes
    when(recentChainData.getFinalizedEpoch()).thenReturn(gloasPeerFinalizedEpoch);

    verify(peer)
        .requestExecutionPayloadEnvelopesByRange(
            eq(gloasSecondSlot), eq(gloasPeerSlotsAhead), any());

    final Map<UInt64, SignedExecutionPayloadEnvelope> executionPayloadsBySlot =
        completeRequestWithExecutionPayloadsAtSlots(
            executionPayloadsRequestFuture, gloasSecondSlot, gloasPeerSlotsAhead);

    verify(peer)
        .requestExecutionPayloadEnvelopesByRange(
            eq(gloasSecondSlot), eq(gloasPeerSlotsAhead), any());

    completeRequestWithBlocksAtSlots(blocksRequestFuture, gloasSecondSlot, gloasPeerSlotsAhead);

    // verify importing of execution payload
    executionPayloadsBySlot
        .values()
        .forEach(
            executionPayload ->
                verify(executionPayloadManager).importExecutionPayload(executionPayload, false));

    // Check that the sync is done and the peer was not disconnected.
    assertThat(syncFuture).isCompleted();
    verify(peer, never()).disconnectCleanly(any());
  }

  private void verifyBlobSidecarsAddedToPool(
      final UInt64 startSlot,
      final UInt64 count,
      final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlot) {
    for (UInt64 slot : getSlotsRange(startSlot, count)) {
      if (!blobSidecarsBySlot.containsKey(slot)) {
        Assertions.fail("Blob sidecars for slot %s is missing", slot);
      }
      verify(blockBlobSidecarsTrackersPool)
          .onCompletedBlockAndBlobSidecars(any(), eq(blobSidecarsBySlot.get(slot)));
    }
  }

  private void withPeerHeadSlot(final UInt64 peerHeadSlot) {
    withPeerHeadSlot(peerHeadSlot, PEER_FINALIZED_EPOCH, PEER_HEAD_BLOCK_ROOT);
  }

  private void withDenebPeerHeadSlot() {
    withPeerHeadSlot(denebPeerHeadSlot, denebPeerFinalizedEpoch, PEER_HEAD_BLOCK_ROOT);
  }

  private void withGloasPeerHeadSlot() {
    withPeerHeadSlot(gloasPeerHeadSlot, gloasPeerFinalizedEpoch, PEER_HEAD_BLOCK_ROOT);
  }

  private void withPeerFinalizedEpoch(final UInt64 finalizedEpoch) {
    final UInt64 headSlot = spec.computeStartSlotAtEpoch(finalizedEpoch).plus(2L * slotsPerEpoch);
    final PeerStatus peerStatus =
        PeerStatus.fromStatusMessage(
            new StatusMessagePhase0(
                Bytes4.leftPad(Bytes.EMPTY),
                Bytes32.ZERO,
                finalizedEpoch,
                PEER_HEAD_BLOCK_ROOT,
                headSlot));

    when(peer.getStatus()).thenReturn(peerStatus);
  }
}

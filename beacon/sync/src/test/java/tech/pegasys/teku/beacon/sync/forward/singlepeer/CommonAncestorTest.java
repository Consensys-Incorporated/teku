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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.singlepeer.CommonAncestor.BLOCK_COUNT_PER_ATTEMPT;
import static tech.pegasys.teku.beacon.sync.forward.singlepeer.CommonAncestor.SLOTS_TO_JUMP_BACK_EXPONENTIAL_BASE;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;

public class CommonAncestorTest extends AbstractSyncTest {
  private final CommonAncestor commonAncestor =
      new CommonAncestor(recentChainData, 4, OptionalInt.empty());
  private final CommonAncestor commonAncestorWithMaxHeadDistance =
      new CommonAncestor(recentChainData, 4, OptionalInt.of(2));

  @Test
  void shouldNotSearchCommonAncestorWithoutSufficientLocalData() {
    final UInt64 firstNonFinalSlot = dataStructureUtil.randomUInt64();
    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.minus(1));
    final PeerStatus status =
        withPeerHeadSlot(
            currentLocalHead,
            spec.computeEpochAtSlot(currentLocalHead),
            dataStructureUtil.randomBytes32());

    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);

    assertThatSafeFuture(
            commonAncestor.getCommonAncestor(peer, firstNonFinalSlot, status.getHeadSlot()))
        .isCompletedWithValue(firstNonFinalSlot);

    verifyNoInteractions(peer);
  }

  @Test
  void shouldSearchStartingFromCurrentCommonHeadSlot() {
    final UInt64 firstNonFinalSlot = dataStructureUtil.randomUInt64();

    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(21));
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(20));
    final UInt64 syncStartSlot = currentRemoteHead.minus(BLOCK_COUNT_PER_ATTEMPT.minus(1));
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlot), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFuture);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            spec.computeEpochAtSlot(currentRemoteHead),
            dataStructureUtil.randomBytes32());

    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);
    when(recentChainData.containsBlock(any())).thenReturn(true);

    final SafeFuture<UInt64> futureSlot =
        commonAncestor.getCommonAncestor(peer, firstNonFinalSlot, status.getHeadSlot());

    assertThat(futureSlot.isDone()).isFalse();

    verifyAndRespond(syncStartSlot, requestFuture);

    // last received slot is the best slot
    assertThatSafeFuture(futureSlot)
        .isCompletedWithValue(syncStartSlot.plus(BLOCK_COUNT_PER_ATTEMPT).minus(1));
  }

  @Test
  void shouldSearchBackExponentiallyUpToMaxAttemptsTimes() {
    final UInt64 firstNonFinalSlot = UInt64.valueOf(10_000);

    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(4_001));
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(4_000));
    final UInt64 syncStartSlot = currentRemoteHead.minus(BLOCK_COUNT_PER_ATTEMPT.minus(1));

    final UInt64 syncStartSlotAttempt1 = syncStartSlot;
    final UInt64 syncStartSlotAttempt2 =
        syncStartSlotAttempt1.minus(SLOTS_TO_JUMP_BACK_EXPONENTIAL_BASE);
    final UInt64 syncStartSlotAttempt3 =
        syncStartSlotAttempt2.minus(SLOTS_TO_JUMP_BACK_EXPONENTIAL_BASE.times(2));
    final UInt64 syncStartSlotAttempt4 =
        syncStartSlotAttempt3.minus(SLOTS_TO_JUMP_BACK_EXPONENTIAL_BASE.times(4));

    final SafeFuture<Void> requestFutureAttempt1 = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlotAttempt1), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFutureAttempt1);
    final SafeFuture<Void> requestFutureAttempt2 = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlotAttempt2), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFutureAttempt2);
    final SafeFuture<Void> requestFutureAttempt3 = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlotAttempt3), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFutureAttempt3);
    final SafeFuture<Void> requestFutureAttempt4 = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlotAttempt4), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFutureAttempt4);

    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            spec.computeEpochAtSlot(currentRemoteHead),
            dataStructureUtil.randomBytes32());

    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);
    when(recentChainData.containsBlock(any())).thenReturn(false);

    final SafeFuture<UInt64> futureSlot =
        commonAncestor.getCommonAncestor(peer, firstNonFinalSlot, status.getHeadSlot());

    assertThat(futureSlot.isDone()).isFalse();

    verifyAndRespond(syncStartSlotAttempt1, requestFutureAttempt1);
    verifyAndRespond(syncStartSlotAttempt2, requestFutureAttempt2);
    verifyAndRespond(syncStartSlotAttempt3, requestFutureAttempt3);
    verifyAndRespond(syncStartSlotAttempt4, requestFutureAttempt4);

    // we ended up on the firstNonFinalSlot
    assertThatSafeFuture(futureSlot).isCompletedWithValue(firstNonFinalSlot);

    verifyNoMoreInteractions(peer);
  }

  @Test
  void shouldStopSearchingIfFirstNonFinalSlotIsReached() {
    final UInt64 firstNonFinalSlot = dataStructureUtil.randomUInt64();

    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(21));
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(20));
    final UInt64 syncStartSlot = currentRemoteHead.minus(BLOCK_COUNT_PER_ATTEMPT.minus(1));
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlot), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFuture);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            spec.computeEpochAtSlot(currentRemoteHead),
            dataStructureUtil.randomBytes32());

    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);
    when(recentChainData.containsBlock(any())).thenReturn(false);

    final SafeFuture<UInt64> futureSlot =
        commonAncestor.getCommonAncestor(peer, firstNonFinalSlot, status.getHeadSlot());

    assertThat(futureSlot.isDone()).isFalse();

    verify(peer)
        .requestBlocksByRange(
            eq(syncStartSlot),
            eq(BLOCK_COUNT_PER_ATTEMPT),
            blockResponseListenerArgumentCaptor.capture());

    requestFuture.complete(null);

    assertThatSafeFuture(
            commonAncestor.getCommonAncestor(peer, firstNonFinalSlot, status.getHeadSlot()))
        .isCompletedWithValue(firstNonFinalSlot);
  }

  @Test
  void shouldCompleteExceptionallyIfMaxHeadDistanceIsReached() {
    final UInt64 firstNonFinalSlot = UInt64.valueOf(1000);

    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(21));
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(20));
    final UInt64 syncStartSlot = currentRemoteHead.minus(BLOCK_COUNT_PER_ATTEMPT.minus(1));
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlot), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFuture);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            spec.computeEpochAtSlot(currentRemoteHead),
            dataStructureUtil.randomBytes32());
    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);
    // Only the first block matches the local chain
    when(recentChainData.containsBlock(any())).thenReturn(true).thenReturn(false);

    final SafeFuture<UInt64> futureSlot =
        commonAncestorWithMaxHeadDistance.getCommonAncestor(
            peer, firstNonFinalSlot, status.getHeadSlot());

    assertThat(futureSlot.isDone()).isFalse();

    verifyAndRespond(syncStartSlot, requestFuture);

    assertThatSafeFuture(futureSlot)
        .isCompletedExceptionallyWithMessage("Max distance from head reached");
  }

  @Test
  void shouldCompleteFindCommonAncestorWhenMaxHeadDistanceIsDefined() {
    final UInt64 firstNonFinalSlot = UInt64.valueOf(1000);

    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(21));
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(20));
    final UInt64 syncStartSlot = currentRemoteHead.minus(BLOCK_COUNT_PER_ATTEMPT.minus(1));
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlot), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFuture);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            spec.computeEpochAtSlot(currentRemoteHead),
            dataStructureUtil.randomBytes32());
    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);
    // Only the first block matches the local chain
    when(recentChainData.containsBlock(any())).thenReturn(true);

    final SafeFuture<UInt64> futureSlot =
        commonAncestorWithMaxHeadDistance.getCommonAncestor(
            peer, firstNonFinalSlot, status.getHeadSlot());

    assertThat(futureSlot.isDone()).isFalse();

    verifyAndRespond(syncStartSlot, requestFuture);

    // last received slot is the best slot
    assertThatSafeFuture(futureSlot)
        .isCompletedWithValue(syncStartSlot.plus(BLOCK_COUNT_PER_ATTEMPT).minus(1));
  }

  @Test
  void shouldStopSearchingAndReturnExceptionallyIfMaxHeadDistanceIsReached() {
    final UInt64 firstNonFinalSlot = UInt64.valueOf(1000);

    final UInt64 currentLocalHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(21));
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(BLOCK_COUNT_PER_ATTEMPT.plus(20));
    final UInt64 syncStartSlot = currentRemoteHead.minus(BLOCK_COUNT_PER_ATTEMPT.minus(1));
    final SafeFuture<Void> requestFuture = new SafeFuture<>();
    when(peer.requestBlocksByRange(eq(syncStartSlot), eq(BLOCK_COUNT_PER_ATTEMPT), any()))
        .thenReturn(requestFuture);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            spec.computeEpochAtSlot(currentRemoteHead),
            dataStructureUtil.randomBytes32());

    when(recentChainData.getHeadSlot()).thenReturn(currentLocalHead);
    when(recentChainData.containsBlock(any())).thenReturn(false);

    final SafeFuture<UInt64> futureSlot =
        commonAncestorWithMaxHeadDistance.getCommonAncestor(
            peer, firstNonFinalSlot, status.getHeadSlot());

    assertThat(futureSlot.isDone()).isFalse();

    verify(peer)
        .requestBlocksByRange(
            eq(syncStartSlot),
            eq(BLOCK_COUNT_PER_ATTEMPT),
            blockResponseListenerArgumentCaptor.capture());

    requestFuture.complete(null);

    assertThatSafeFuture(
            commonAncestorWithMaxHeadDistance.getCommonAncestor(
                peer, firstNonFinalSlot, status.getHeadSlot()))
        .isCompletedExceptionallyWithMessage("Max distance from head reached");
  }

  private void verifyAndRespond(
      final UInt64 syncStartSlot, final SafeFuture<Void> requestFutureAttempt) {
    verify(peer)
        .requestBlocksByRange(
            eq(syncStartSlot),
            eq(BLOCK_COUNT_PER_ATTEMPT),
            blockResponseListenerArgumentCaptor.capture());

    respondWithBlocksAtSlots(
        requestFutureAttempt,
        blockResponseListenerArgumentCaptor.getValue(),
        syncStartSlot,
        BLOCK_COUNT_PER_ATTEMPT);

    requestFutureAttempt.complete(null);
  }
}

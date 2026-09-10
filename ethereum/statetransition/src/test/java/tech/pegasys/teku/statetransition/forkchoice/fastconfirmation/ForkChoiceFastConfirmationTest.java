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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.client.ChainHead;

class ForkChoiceFastConfirmationTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final FastConfirmationTracker tracker = mock(FastConfirmationTracker.class);
  // Inject the stub runner so the segment timeout can be driven deterministically.
  private final ForkChoiceFastConfirmation forkChoiceFastConfirmation =
      new ForkChoiceFastConfirmation(spec, tracker, asyncRunner);

  private final Duration slotDuration = Duration.ofMillis(spec.getSlotDurationMillis(UInt64.ONE));

  private final List<UInt64> onSlotInvocations = new ArrayList<>();

  // Records the order of events so tests can assert the fcU is sent once per slot, after onSlot.
  private final List<String> events = new ArrayList<>();
  private final Supplier<SafeFuture<Void>> sendForkChoiceUpdated =
      () -> {
        events.add("fcU");
        return SafeFuture.COMPLETE;
      };

  ForkChoiceFastConfirmationTest() {
    assertThat(forkChoiceFastConfirmation.start()).isCompleted();
    when(tracker.onSlot(any(), any()))
        .thenAnswer(
            invocation -> {
              final UInt64 slot = invocation.getArgument(0);
              onSlotInvocations.add(slot);
              events.add("onSlot:" + slot);
              return SafeFuture.COMPLETE;
            });
  }

  @Test
  void shouldInvokeOnSlotInSlotOrderEvenWhenAnEarlierSlotProcessHeadIsSlow() {
    // Slot 1's processHead is slow (pending); slot 2's is immediate. Without chaining, slot 2 would
    // reach onSlot first and slot 1 would then be dropped as stale by the tracker.
    final SafeFuture<Optional<ChainHead>> slowHead = new SafeFuture<>();
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot ->
            slot.equals(UInt64.ONE)
                ? slowHead
                : SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2), SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // Slot 1 is still blocked on its processHead, so slot 2 must not have jumped ahead.
    assertThat(onSlotInvocations).isEmpty();

    // Slot 1 completes before its timeout, so both pipelines drain strictly in slot order.
    slowHead.complete(Optional.of(chainHead(Bytes32.random())));

    assertThat(onSlotInvocations).containsExactly(UInt64.ONE, UInt64.valueOf(2));
    // Each slot sends exactly one fcU, after its onSlot.
    assertThat(events).containsExactly("onSlot:1", "fcU", "onSlot:2", "fcU");
  }

  @Test
  void shouldTimeoutAndNotWedgeLaterSlotsWhenAProcessHeadHangs() {
    // Slot 1's processHead never completes; slot 2's is immediate.
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot ->
            slot.equals(UInt64.ONE)
                ? new SafeFuture<>()
                : SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2), SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // Nothing runs while slot 1 hangs and its timeout has not fired.
    assertThat(onSlotInvocations).isEmpty();

    // Advance past the segment timeout and fire it: slot 1 is abandoned and slot 2 proceeds.
    timeProvider.advanceTimeBy(slotDuration.plusMillis(1));
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(onSlotInvocations).containsExactly(UInt64.valueOf(2));
    // Only slot 2 ran, so only slot 2 sent an fcU; the abandoned slot 1 sent none.
    assertThat(events).containsExactly("onSlot:2", "fcU");
  }

  @Test
  void shouldAllowALongerSegmentWhileWarmingUp() {
    // The update that ends the warm-up advances the confirmed root across every block accumulated
    // while it was pinned to finality, which can outrun a one-slot budget.
    when(tracker.isWarmingUp()).thenReturn(true);
    final SafeFuture<Optional<ChainHead>> slowHead = new SafeFuture<>();

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, slot -> slowHead, sendForkChoiceUpdated);

    // Past the normal one-slot bound: still running rather than abandoned.
    timeProvider.advanceTimeBy(slotDuration.plusMillis(1));
    asyncRunner.executeDueActionsRepeatedly();
    slowHead.complete(Optional.of(chainHead(Bytes32.random())));

    assertThat(onSlotInvocations).containsExactly(UInt64.ONE);
  }

  @Test
  void shouldTimeoutTheRelaxedWarmUpSegmentEventually() {
    when(tracker.isWarmingUp()).thenReturn(true);

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, slot -> new SafeFuture<>(), sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2),
        SafeFuture.COMPLETE,
        slot -> SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random()))),
        sendForkChoiceUpdated);

    // Four slots is the warm-up bound, so slot 1 is abandoned there and slot 2 proceeds.
    timeProvider.advanceTimeBy(slotDuration.multipliedBy(4).plusMillis(1));
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(onSlotInvocations).containsExactly(UInt64.valueOf(2));
  }

  @Test
  void shouldContinueProcessingLaterSlotsAfterAnEarlierSlotFails() {
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot ->
            slot.equals(UInt64.ONE)
                ? SafeFuture.failedFuture(new IllegalStateException("processHead failed"))
                : SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);
    forkChoiceFastConfirmation.processForSlot(
        UInt64.valueOf(2), SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // Slot 1 failed (no onSlot), but the chain stayed alive and slot 2 still ran.
    assertThat(onSlotInvocations).containsExactly(UInt64.valueOf(2));
  }

  @Test
  void shouldSkipOnSlotWhenNoHeadIsAvailable() {
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot -> SafeFuture.completedFuture(Optional.empty());

    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    // No head, so neither onSlot nor the fcU runs for this slot.
    assertThat(onSlotInvocations).isEmpty();
    assertThat(events).isEmpty();
  }

  @Test
  void shouldNotScheduleNewSlotWorkAfterStop() {
    final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead =
        slot -> SafeFuture.completedFuture(Optional.of(chainHead(Bytes32.random())));

    assertThat(forkChoiceFastConfirmation.stop()).isCompleted();
    forkChoiceFastConfirmation.processForSlot(
        UInt64.ONE, SafeFuture.COMPLETE, processHead, sendForkChoiceUpdated);

    assertThat(onSlotInvocations).isEmpty();
    assertThat(events).isEmpty();
  }

  @Test
  void shouldNotLogErrorWhenAnInFlightSegmentFailsAfterStop() {
    // The fork-choice event thread is stopped while a segment is in flight, so its work fails with
    // "EventThread not started". That is expected shutdown noise, not an FCR failure.
    final SafeFuture<Optional<ChainHead>> pendingHead = new SafeFuture<>();

    try (final LogCaptor logCaptor = LogCaptor.forClass(ForkChoiceFastConfirmation.class)) {
      forkChoiceFastConfirmation.processForSlot(
          UInt64.ONE, SafeFuture.COMPLETE, slot -> pendingHead, sendForkChoiceUpdated);

      assertThat(forkChoiceFastConfirmation.stop()).isCompleted();
      pendingHead.completeExceptionally(new IllegalStateException("EventThread not started"));

      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getDebugLogs())
          .anyMatch(log -> log.contains("Fast confirmation update for slot 1 abandoned"));
    }
  }

  @Test
  void shouldLogErrorWhenASegmentFailsWhileRunning() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(ForkChoiceFastConfirmation.class)) {
      forkChoiceFastConfirmation.processForSlot(
          UInt64.ONE,
          SafeFuture.COMPLETE,
          slot -> SafeFuture.failedFuture(new IllegalStateException("processHead failed")),
          sendForkChoiceUpdated);

      assertThat(logCaptor.getErrorLogs())
          .anyMatch(log -> log.contains("Fast confirmation update for slot 1 failed"));
    }
  }

  private ChainHead chainHead(final Bytes32 root) {
    final ChainHead chainHead = mock(ChainHead.class);
    when(chainHead.getRoot()).thenReturn(root);
    return chainHead;
  }
}

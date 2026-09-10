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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class FastConfirmationTrackerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private final ReadOnlyForkChoiceStrategy forkChoice = mock(ReadOnlyForkChoiceStrategy.class);
  private final FastConfirmationEventChannel eventChannel =
      mock(FastConfirmationEventChannel.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final Checkpoint finalizedCheckpoint =
      new Checkpoint(UInt64.valueOf(12), Bytes32.random());

  @BeforeEach
  void setUp() {
    // No source states available: get_latest_confirmed is skipped and the confirmed root is left
    // unchanged, so these tests exercise only update_fast_confirmation_variables and the guard.
    when(store.retrieveCheckpointState(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(store.retrieveBlockState(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    // Used by the fast_confirmation event to resolve the confirmed block's slot.
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
    when(forkChoice.blockSlot(any())).thenReturn(Optional.of(finalizedCheckpoint.getEpoch()));
  }

  @Test
  void shouldNotInitializeStoreWhenDisabled() {
    final FastConfirmationTracker tracker = FastConfirmationTracker.NOOP;

    tracker.initialize(store);

    assertThat(tracker.isEnabled()).isFalse();
    assertThat(tracker.getFastConfirmationStore()).isEmpty();
  }

  @Test
  void shouldInitializeStoreFromFinalizedCheckpointWhenEnabled() {
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.empty(), eventChannel, metricsSystem, timeProvider);

    tracker.initialize(store);

    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    assertThat(tracker.isEnabled()).isTrue();
    assertThat(fastConfirmationStore.store()).isSameAs(store);
    assertThat(fastConfirmationStore.confirmedRoot()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.previousEpochObservedJustifiedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.currentEpochObservedJustifiedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.previousSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
  }

  @Test
  void shouldNotScheduleUpdateWhenDisabled() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    final FastConfirmationTracker tracker = FastConfirmationTracker.NOOP;

    final SafeFuture<Void> result = tracker.onSlot(UInt64.valueOf(13), Bytes32.random());

    assertThatSafeFuture(result).isCompleted();
    assertThat(asyncRunner.countDelayedActions()).isZero();
  }

  @Test
  void shouldScheduleUpdateOnAsyncRunnerWhenEnabledAndInitialized() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    final Bytes32 headRoot = Bytes32.random();

    // Slot 13 is a mid-epoch slot (minimal SLOTS_PER_EPOCH == 8), so only the slot heads rotate.
    final SafeFuture<Void> result = tracker.onSlot(UInt64.valueOf(13), headRoot);

    assertThat(result).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isOne();

    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompleted();
    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    assertThat(fastConfirmationStore.previousSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(headRoot);
  }

  @Test
  void shouldSkipUpdateEntirelyWhenHeadIsStale() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    final Bytes32 headRoot = Bytes32.random();

    // The head block slot is stubbed at 12 (epoch 1, minimal SLOTS_PER_EPOCH == 8). Slot 24 is
    // epoch 3, so the head is two epochs behind: get_latest_confirmed could only revert to
    // finalized, so the whole update is skipped this slot.
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(24), headRoot);

    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    // Nothing happens: the store is untouched (confirmed root and slot heads at their initial
    // values), no source states are loaded, no event is emitted, and no fallback is counted.
    assertThat(fastConfirmationStore.confirmedRoot()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    verify(store, never()).retrieveCheckpointState(any());
    verify(store, never()).retrieveBlockState(any(Bytes32.class));
    verify(eventChannel, never()).onFastConfirmation(any(), any(), any());
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON, "fast_confirmation_fallbacks_total"))
        .isZero();
  }

  @Test
  void shouldRunFullConfirmationWhenHeadIsWithinOneEpoch() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);

    // Head block slot 12 (epoch 1); slot 17 is epoch 2 — one epoch behind, which is tolerated, so
    // the full rule runs and the source states are loaded (here empty, so it falls back).
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(17), Bytes32.random());

    verify(store, atLeastOnce()).retrieveCheckpointState(any());
    verify(eventChannel).onFastConfirmation(any(), any(), any());
  }

  @Test
  void shouldEmitFastConfirmationEventWhenAlgorithmRuns() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);

    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());

    // Emitted with the confirmed root (still the finalized root, as no source states are loaded),
    // the confirmed block's slot, and the wall-clock slot the algorithm ran at.
    verify(eventChannel)
        .onFastConfirmation(
            finalizedCheckpoint.getRoot(), finalizedCheckpoint.getEpoch(), UInt64.valueOf(13));
    // The timeout counter is registered and unaffected by a normal (non-timed-out) run.
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON, "fast_confirmation_timeout_total"))
        .isZero();
  }

  @Test
  void shouldNotCountFallbackDuringWarmUpWhenStatesAreUnavailable() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);

    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());

    // The store is seeded from the finalized checkpoint, so being pinned to finality right after
    // initialization is the warm-up state rather than a fallback away from a real confirmation.
    assertThat(fallbacks()).isZero();
    assertThat(restarts()).isZero();
  }

  @Test
  void shouldCountFallbackWhenStatesBecomeUnavailableAfterAConfirmation() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    // A confirmation beyond finality ends the warm-up, so the next pin to finality is a real
    // fallback.
    tracker.recordConfirmationOutcome(
        tracker.getFastConfirmationStore().orElseThrow(),
        Bytes32.random(),
        finalizedCheckpoint.getRoot());

    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());

    assertThat(fallbacks()).isEqualTo(1);
    assertThat(restarts()).isZero();
  }

  @Test
  void shouldIncrementRestartCounterForObservedJustifiedRoot() {
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.empty(), eventChannel, metricsSystem, timeProvider);
    final Bytes32 observedJustifiedRoot = Bytes32.random();
    final Checkpoint observedJustifiedCheckpoint =
        new Checkpoint(UInt64.valueOf(13), observedJustifiedRoot);
    final FastConfirmationStore fastConfirmationStore =
        new FastConfirmationStore(
            store,
            finalizedCheckpoint.getRoot(),
            finalizedCheckpoint,
            observedJustifiedCheckpoint,
            finalizedCheckpoint,
            finalizedCheckpoint.getRoot(),
            finalizedCheckpoint.getRoot());

    tracker.recordConfirmationOutcome(
        fastConfirmationStore, observedJustifiedRoot, finalizedCheckpoint.getRoot());

    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON, "fast_confirmation_restarts_total"))
        .isEqualTo(1);
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON, "fast_confirmation_fallbacks_total"))
        .isZero();
  }

  @Test
  void shouldNotBlockRunnerWhileSourceStateLoadIsPending() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    // The head-state load hangs; the checkpoint-state loads resolve immediately (empty, from
    // setUp). A blocking join() on this would monopolize the single-thread runner; composing does
    // not.
    final SafeFuture<Optional<BeaconState>> pendingHeadState = new SafeFuture<>();
    when(store.retrieveBlockState(any(Bytes32.class))).thenReturn(pendingHeadState);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    final Bytes32 headRoot = Bytes32.random();

    // Head block slot 12 (epoch 1); slot 17 is epoch 2 — one epoch behind, so the full rule runs.
    final SafeFuture<Void> result = tracker.onSlot(UInt64.valueOf(17), headRoot);

    // Draining the runner completes its scheduled action even though the load is still pending: the
    // runner is free (no queued actions left) while onSlot's result waits on the load.
    asyncRunner.executeQueuedActions();
    assertThat(asyncRunner.countDelayedActions()).isZero();
    assertThatSafeFuture(result).isNotCompleted();

    // Once the load resolves, the pipeline finishes off the runner: the store is written (slot head
    // rotated) and the confirmed root falls back to finalized as no states were available.
    pendingHeadState.complete(Optional.empty());

    assertThatSafeFuture(result).isCompleted();
    assertThat(tracker.getFastConfirmationStore().orElseThrow().currentSlotHead())
        .isEqualTo(headRoot);
    // Still the seeded warm-up pin to finality, so no fallback event is counted.
    assertThat(fallbacks()).isZero();
  }

  @Test
  void shouldCommitRotationButAbandonConfirmationWhenSourceStateLoadFails() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    // The head-state load fails, which takes the same abandon branch as a load timeout. The slot's
    // rotation must still be committed in order (so the next slot reads correctly rotated FCR
    // variables), while the confirmation is abandoned: confirmed_root is left unchanged and no
    // event is emitted for this slot.
    when(store.retrieveBlockState(any(Bytes32.class)))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("boom")));
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    final Bytes32 headRoot = Bytes32.random();

    // Head block slot 12 (epoch 1); slot 17 is epoch 2 — one epoch behind, so the full rule runs.
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(17), headRoot);

    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    // Rotation committed despite the failed confirmation.
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(headRoot);
    // Confirmation abandoned: confirmed_root unchanged (still the initial finalized root), no event
    // emitted, and no fallback counted (a fallback is a deliberate transition, not an abandonment).
    assertThat(fastConfirmationStore.confirmedRoot()).isEqualTo(finalizedCheckpoint.getRoot());
    verify(eventChannel, never()).onFastConfirmation(any(), any(), any());
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON, "fast_confirmation_fallbacks_total"))
        .isZero();
  }

  @Test
  void shouldSkipStaleOrDuplicateSlotUpdates() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);

    final Bytes32 headA = Bytes32.random();
    final Bytes32 headB = Bytes32.random();
    final Bytes32 headC = Bytes32.random();

    // Slot 13: rotates current -> previous, sets current = headA
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), headA);
    assertThat(currentSlotHead(tracker)).isEqualTo(headA);
    assertThat(previousSlotHead(tracker)).isEqualTo(finalizedCheckpoint.getRoot());

    // Slot 14: rotates again, previous = headA, current = headB
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(14), headB);
    assertThat(currentSlotHead(tracker)).isEqualTo(headB);
    assertThat(previousSlotHead(tracker)).isEqualTo(headA);

    // Duplicate slot 14 must be ignored (no second rotation)
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(14), headC);
    assertThat(currentSlotHead(tracker)).isEqualTo(headB);
    assertThat(previousSlotHead(tracker)).isEqualTo(headA);

    // Older slot 13 must be ignored as well
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), headC);
    assertThat(currentSlotHead(tracker)).isEqualTo(headB);
    assertThat(previousSlotHead(tracker)).isEqualTo(headA);
  }

  @Test
  void shouldCompareGloasConfirmedRootsAsPendingNodes() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            gloasSpec, Optional.empty(), eventChannel, metricsSystem, timeProvider);
    final Bytes32 previousConfirmedRoot = Bytes32.random();
    final Bytes32 confirmedRoot = Bytes32.random();
    final UInt64 previousConfirmedSlot = UInt64.valueOf(4);
    final UInt64 currentSlot = UInt64.valueOf(6);
    when(forkChoice.blockSlot(previousConfirmedRoot))
        .thenReturn(Optional.of(previousConfirmedSlot));
    when(forkChoice.getAncestorNode(
            ForkChoiceNode.createBase(confirmedRoot), previousConfirmedSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(previousConfirmedRoot)));

    assertThat(
            tracker.isConfirmedRootReorg(
                currentSlot, forkChoice, previousConfirmedRoot, confirmedRoot))
        .isFalse();
    verify(forkChoice)
        .getAncestorNode(ForkChoiceNode.createBase(confirmedRoot), previousConfirmedSlot);
  }

  @Test
  void shouldReportConfirmedRootReorgWhenRootsAreOnDifferentChains() {
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.empty(), eventChannel, metricsSystem, timeProvider);
    final Bytes32 previousConfirmedRoot = Bytes32.random();
    final Bytes32 confirmedRoot = Bytes32.random();
    when(forkChoice.blockSlot(previousConfirmedRoot)).thenReturn(Optional.of(UInt64.valueOf(4)));
    when(forkChoice.blockSlot(confirmedRoot)).thenReturn(Optional.of(UInt64.valueOf(5)));

    assertThat(
            tracker.isConfirmedRootReorg(
                UInt64.valueOf(6), forkChoice, previousConfirmedRoot, confirmedRoot))
        .isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldCountAFallbackHeldOverConsecutiveSlotsOnce(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTrackerAfterWarmUp(trackerSpec);

    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());
    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());
    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());

    // One fallback episode, not one event per slot spent in it.
    assertThat(fallbacks()).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldNotCountANewFallbackWhenFinalizationAdvancesDuringOne(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTrackerAfterWarmUp(trackerSpec);

    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());
    // Finalization advances while the rule is still pinned to finality: confirmed_root moves to the
    // new finalized root, but this is the same fallback episode continuing.
    final Bytes32 advancedFinalizedRoot = Bytes32.random();
    fallBackToFinality(tracker, advancedFinalizedRoot);
    fallBackToFinality(tracker, advancedFinalizedRoot);

    assertThat(fallbacks()).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldCountFallbackAgainAfterAConfirmationInBetween(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTrackerAfterWarmUp(trackerSpec);

    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());
    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());
    // A confirmation beyond finality closes the episode, so falling back again is a new event.
    confirmBeyondFinality(tracker);
    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());
    fallBackToFinality(tracker, finalizedCheckpoint.getRoot());

    assertThat(fallbacks()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldCountARestartHeldOverConsecutiveSlotsOnce(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTrackerAfterWarmUp(trackerSpec);
    final Checkpoint observedJustifiedCheckpoint =
        new Checkpoint(UInt64.valueOf(2), Bytes32.random());

    restartFrom(tracker, observedJustifiedCheckpoint);
    restartFrom(tracker, observedJustifiedCheckpoint);
    restartFrom(tracker, observedJustifiedCheckpoint);

    assertThat(restarts()).isEqualTo(1);
    assertThat(fallbacks()).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldCountANewRestartAfterTheObservedJustifiedCheckpointRotates(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTrackerAfterWarmUp(trackerSpec);
    final Checkpoint firstObservedJustifiedCheckpoint =
        new Checkpoint(UInt64.valueOf(2), Bytes32.random());
    final Checkpoint rotatedObservedJustifiedCheckpoint =
        new Checkpoint(UInt64.valueOf(3), Bytes32.random());

    restartFrom(tracker, firstObservedJustifiedCheckpoint);
    restartFrom(tracker, firstObservedJustifiedCheckpoint);
    // The variables rotated at the epoch boundary, so restarting from the newly observed justified
    // checkpoint is a genuinely new restart.
    restartFrom(tracker, rotatedObservedJustifiedCheckpoint);
    restartFrom(tracker, rotatedObservedJustifiedCheckpoint);

    assertThat(restarts()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldCollapseConsecutiveConfirmedRootChainSwitchesIntoOneReorg(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTracker(trackerSpec);
    // Chain A and chain B fork off a common ancestor; the confirmed root flaps between them on
    // consecutive slots before settling on chain B.
    final Bytes32 chainA1 = knownRoot(UInt64.valueOf(10));
    final Bytes32 chainB1 = knownRoot(UInt64.valueOf(11));
    final Bytes32 chainA2 = knownRoot(UInt64.valueOf(12));
    final Bytes32 chainB2 = knownRoot(UInt64.valueOf(13));
    final Bytes32 chainB3 = knownRoot(UInt64.valueOf(14));
    onSameChain(chainB2, chainB3);

    tracker.recordReorgOutcome(UInt64.valueOf(11), forkChoice, chainA1, chainB1);
    tracker.recordReorgOutcome(UInt64.valueOf(12), forkChoice, chainB1, chainA2);
    tracker.recordReorgOutcome(UInt64.valueOf(13), forkChoice, chainA2, chainB2);
    tracker.recordReorgOutcome(UInt64.valueOf(14), forkChoice, chainB2, chainB3);

    assertThat(reorgs()).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldCountAReorgAgainAfterTheConfirmedRootStabilises(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTracker(trackerSpec);
    final Bytes32 chainA1 = knownRoot(UInt64.valueOf(10));
    final Bytes32 chainB1 = knownRoot(UInt64.valueOf(11));
    final Bytes32 chainB2 = knownRoot(UInt64.valueOf(12));
    final Bytes32 chainA3 = knownRoot(UInt64.valueOf(13));
    onSameChain(chainB1, chainB2);

    tracker.recordReorgOutcome(UInt64.valueOf(11), forkChoice, chainA1, chainB1);
    // Advancing along chain B closes the reorg episode.
    tracker.recordReorgOutcome(UInt64.valueOf(12), forkChoice, chainB1, chainB2);
    tracker.recordReorgOutcome(UInt64.valueOf(13), forkChoice, chainB2, chainA3);

    assertThat(reorgs()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("specs")
  void shouldNotCountAReorgWhenAConfirmedRootIsNotInForkChoice(final Spec trackerSpec) {
    final FastConfirmationTracker tracker = createTracker(trackerSpec);
    // The previous confirmed root has been pruned now that finalization has advanced past it, so it
    // cannot be placed on a chain — a pruning artifact, not a reorg.
    final Bytes32 prunedConfirmedRoot = Bytes32.random();
    final Bytes32 confirmedRoot = knownRoot(UInt64.valueOf(12));
    when(forkChoice.blockSlot(prunedConfirmedRoot)).thenReturn(Optional.empty());

    tracker.recordReorgOutcome(UInt64.valueOf(13), forkChoice, prunedConfirmedRoot, confirmedRoot);

    assertThat(reorgs()).isZero();
  }

  @Test
  void shouldNotSplitAFallbackEpisodeWhenASlotIsAbandoned() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    confirmBeyondFinality(tracker);

    // Slot 13 opens the fallback episode (source states unavailable).
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());
    // Slot 14's state load fails, so its confirmation is abandoned: it produces no outcome and must
    // neither close nor reopen the episode.
    when(store.retrieveBlockState(any(Bytes32.class)))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("boom")));
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(14), Bytes32.random());
    when(store.retrieveBlockState(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(15), Bytes32.random());

    assertThat(fallbacks()).isEqualTo(1);
  }

  @Test
  void shouldMarkEverySlotAsWarmingUpWhileConfirmationFallsBackToFinality() {
    // Minimal SLOTS_PER_EPOCH == 8: slot 15 is the last slot of epoch 1 and 16 starts epoch 2, so
    // the store's variables rotate — but confirmation still falls back to the finalized block
    // (source states are unavailable here), so every slot is still warming up.
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);

    try (final LogCaptor logCaptor = LogCaptor.forClass(FastConfirmationTracker.class)) {
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(15), Bytes32.random());
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(16), Bytes32.random());
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(17), Bytes32.random());

      assertThat(perSlotUpdateLogs(logCaptor))
          .hasSize(4)
          .allMatch(log -> log.contains("(warmup stage)"));
    }
  }

  @Test
  void shouldStopMarkingSlotsOnceAConfirmationLandsBeyondFinality() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);

    try (final LogCaptor logCaptor = LogCaptor.forClass(FastConfirmationTracker.class)) {
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());
      assertThat(perSlotUpdateLogs(logCaptor))
          .hasSize(1)
          .allMatch(log -> log.contains("(warmup stage)"));
      logCaptor.clearLogs();

      // A confirmation beyond the finalized block ends the warm-up, and it never comes back — even
      // though the following slots fall back to finality again.
      tracker.recordConfirmationOutcome(
          tracker.getFastConfirmationStore().orElseThrow(),
          Bytes32.random(),
          finalizedCheckpoint.getRoot());
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(14), Bytes32.random());
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(16), Bytes32.random());

      assertThat(perSlotUpdateLogs(logCaptor))
          .hasSize(2)
          .noneMatch(log -> log.contains("(warmup stage)"));
    }
  }

  @Test
  void shouldReEnterWarmUpStageWhenTheStoreIsReinitialized() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(
            spec, Optional.of(asyncRunner), eventChannel, metricsSystem, timeProvider);
    tracker.initialize(store);
    tracker.recordConfirmationOutcome(
        tracker.getFastConfirmationStore().orElseThrow(),
        Bytes32.random(),
        finalizedCheckpoint.getRoot());

    tracker.initialize(store);

    try (final LogCaptor logCaptor = LogCaptor.forClass(FastConfirmationTracker.class)) {
      applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), Bytes32.random());

      assertThat(perSlotUpdateLogs(logCaptor))
          .hasSize(1)
          .allMatch(log -> log.contains("(warmup stage)"));
    }
  }

  private static Stream<Arguments> specs() {
    return Stream.of(
        Arguments.of(Named.of("fulu", TestSpecFactory.createMinimalFulu())),
        Arguments.of(Named.of("gloas", TestSpecFactory.createMinimalGloas())));
  }

  private FastConfirmationTracker createTracker(final Spec trackerSpec) {
    return FastConfirmationTracker.create(
        trackerSpec, Optional.empty(), eventChannel, metricsSystem, timeProvider);
  }

  /**
   * A tracker whose warm-up has already ended, so a pin to finality counts as a real fallback (see
   * {@link #shouldNotCountFallbackDuringWarmUpWhenStatesAreUnavailable}).
   */
  private FastConfirmationTracker createTrackerAfterWarmUp(final Spec trackerSpec) {
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker = createTracker(trackerSpec);
    tracker.initialize(store);
    confirmBeyondFinality(tracker);
    return tracker;
  }

  /**
   * Records a slot whose confirmed root advanced beyond both finality and the observed justified.
   */
  private void confirmBeyondFinality(final FastConfirmationTracker tracker) {
    tracker.recordConfirmationOutcome(
        tracker.getFastConfirmationStore().orElseThrow(),
        Bytes32.random(),
        finalizedCheckpoint.getRoot());
  }

  /** Records a slot whose confirmed root reverted to the given finalized block. */
  private void fallBackToFinality(
      final FastConfirmationTracker tracker, final Bytes32 finalizedRoot) {
    tracker.recordConfirmationOutcome(
        tracker.getFastConfirmationStore().orElseThrow(), finalizedRoot, finalizedRoot);
  }

  /** Records a slot whose confirmed root restarted from the given observed justified checkpoint. */
  private void restartFrom(
      final FastConfirmationTracker tracker, final Checkpoint observedJustifiedCheckpoint) {
    final FastConfirmationStore fastConfirmationStore =
        new FastConfirmationStore(
            store,
            finalizedCheckpoint.getRoot(),
            finalizedCheckpoint,
            observedJustifiedCheckpoint,
            finalizedCheckpoint,
            finalizedCheckpoint.getRoot(),
            finalizedCheckpoint.getRoot());
    tracker.recordConfirmationOutcome(
        fastConfirmationStore,
        observedJustifiedCheckpoint.getRoot(),
        finalizedCheckpoint.getRoot());
  }

  /** A root fork choice knows, at the given slot, with no ancestry stubbed (so: its own chain). */
  private Bytes32 knownRoot(final UInt64 slot) {
    final Bytes32 root = Bytes32.random();
    when(forkChoice.blockSlot(root)).thenReturn(Optional.of(slot));
    return root;
  }

  /** Makes {@code descendantRoot} resolve to {@code ancestorRoot} on the same chain. */
  private void onSameChain(final Bytes32 ancestorRoot, final Bytes32 descendantRoot) {
    final UInt64 ancestorSlot = forkChoice.blockSlot(ancestorRoot).orElseThrow();
    when(forkChoice.getAncestorNode(ForkChoiceNode.createBase(descendantRoot), ancestorSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(ancestorRoot)));
  }

  private long fallbacks() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.BEACON, "fast_confirmation_fallbacks_total");
  }

  private long restarts() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.BEACON, "fast_confirmation_restarts_total");
  }

  private long reorgs() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.BEACON, "fast_confirmation_reorgs_total");
  }

  private List<String> perSlotUpdateLogs(final LogCaptor logCaptor) {
    return logCaptor.getInfoLogs().stream()
        .filter(log -> log.startsWith("Fast confirmation update for slot"))
        .toList();
  }

  private void applyUpdate(
      final FastConfirmationTracker tracker,
      final StubAsyncRunner asyncRunner,
      final UInt64 slot,
      final Bytes32 headRoot) {
    final SafeFuture<Void> result = tracker.onSlot(slot, headRoot);
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompleted();
  }

  private Bytes32 currentSlotHead(final FastConfirmationTracker tracker) {
    return tracker.getFastConfirmationStore().orElseThrow().currentSlotHead();
  }

  private Bytes32 previousSlotHead(final FastConfirmationTracker tracker) {
    return tracker.getFastConfirmationStore().orElseThrow().previousSlotHead();
  }
}

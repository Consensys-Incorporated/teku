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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;

public class FastConfirmationTracker {
  private static final Logger LOG = LogManager.getLogger();

  public static final FastConfirmationTracker NOOP =
      new FastConfirmationTracker(
          false,
          null,
          Optional.empty(),
          FastConfirmationEventChannel.NOOP,
          new NoOpMetricsSystem(),
          () -> UInt64.ZERO);

  private final boolean enabled;
  private final Spec spec;
  private final FastConfirmationEventChannel fastConfirmationEventChannel;

  /** Slot of the most recent confirmed block * */
  private final AtomicInteger latestConfirmedSlot = new AtomicInteger(0);

  /** Total number of confirmed block reorganizations */
  private final Counter reorgsCounter;

  /** Total number of fallbacks to finality */
  private final Counter fallbacksCounter;

  /** Total number of restarts from a safe unrealized justified block */
  private final Counter restartsCounter;

  /**
   * Where {@link FastConfirmationStore#confirmedRoot() confirmed_root} landed on the most recent
   * slot that produced an outcome, so {@link #fallbacksCounter} and {@link #restartsCounter} count
   * episodes rather than slots: both conditions hold for many consecutive slots once entered. Keyed
   * on the kind of outcome, not on the roots, so a fallback that persists while finalization
   * advances stays one episode. Slots that produce no outcome (stale-head skip, abandoned
   * computation) leave it untouched, so they neither open nor close an episode. Reset to {@link
   * ConfirmationOutcome#FALLBACK} on (re)initialization: the store is seeded from finality, and
   * that warm-up is a seeded state rather than a fallback away from a real confirmation (see {@link
   * #warmedUp}).
   */
  private final AtomicReference<ConfirmationOutcome> lastConfirmationOutcome =
      new AtomicReference<>(ConfirmationOutcome.fallback());

  /**
   * Whether that same slot also switched the confirmed block to another chain, so a stretch of
   * consecutive switching slots counts as the one period of instability it is; the episode closes
   * on the first slot whose confirmed root stays on its chain.
   *
   * <p>Tracked separately from {@link #lastConfirmationOutcome} because the two are orthogonal: a
   * slot can restart from an observed justified checkpoint <i>and</i> switch chains doing so, which
   * must count on both counters. Folding them into one value would force a choice between the two
   * and let one episode's continuation be misread as a new event.
   */
  private final AtomicBoolean previousSlotWasReorg = new AtomicBoolean(false);

  /** Wall-clock duration of a single per-slot fast confirmation run; {@code null} when disabled. */
  private final MetricsHistogram calculationTimer;

  /** Incremented when a per-slot run does not finish within {@link #updateTimeout}. */
  private final Counter timeoutCounter;

  private final AtomicReference<FastConfirmationStore> fastConfirmationStore =
      new AtomicReference<>();

  /**
   * Slot of the most recently applied update. {@code update_fast_confirmation_variables} MUST run
   * exactly once per slot and slot heads rotate in slot order, so an update is applied only when
   * its slot is strictly greater than this value. Slots are chained and each commits before the
   * next starts (see {@link #onSlot}), so this is read and written by a single in-order writer; a
   * stale or duplicate slot is skipped in {@link #processFastConfirmationInput}. {@code null} until
   * the first update after (re)initialization.
   */
  private final AtomicReference<UInt64> lastProcessedSlot = new AtomicReference<>();

  /**
   * Whether a confirmation has landed beyond the finalized block since (re)initialization, i.e. the
   * rule has produced a real result rather than falling back to finality.
   *
   * <p>Used to mark a slot as still warming up. {@link FastConfirmationStore#create} seeds {@code
   * confirmed_root} and every checkpoint from the finalized checkpoint, and the store only sheds
   * those seeded values through an epoch-boundary pipeline: {@code
   * previousEpochGreatestUnrealizedCheckpoint} is written on the last slot of an epoch and folded
   * into {@code currentEpochObservedJustifiedCheckpoint} at the next epoch start. Meanwhile a
   * seeded {@code confirmed_root} that is more than one epoch old cannot be advanced at all ({@code
   * get_latest_confirmed} reverts it to finalized and skips {@code findLatestConfirmedDescendant}),
   * so it stays pinned to finality until an epoch start restarts it from an observed justified
   * checkpoint. In practice that takes up to two epoch boundaries.
   *
   * <p>Deliberately keyed on that observable outcome rather than on the rotation of the underlying
   * variables: the variables can be refreshed a full epoch before {@code confirmed_root} is able to
   * move, which would clear the marker while the node still reports a stale confirmed root.
   */
  private final AtomicBoolean warmedUp = new AtomicBoolean(false);

  private final Optional<AsyncRunner> asyncRunner;

  /**
   * Tail of the per-slot processing chain. Each {@link #onSlot} call composes its work after the
   * previous slot's so the rotating {@link FastConfirmationStore} is read and written in slot
   * order, without ever blocking a thread on a state load. Confined to a single thread: {@code
   * onSlot} is only ever invoked on the fork-choice event thread (see {@code
   * ForkChoiceFastConfirmation}, which composes it onto the event-thread-bound {@code
   * processHead}), so no synchronization is needed to read-and-replace this field.
   */
  private SafeFuture<Void> updateChain = SafeFuture.COMPLETE;

  private FastConfirmationTracker(
      final boolean enabled,
      final Spec spec,
      final Optional<AsyncRunner> asyncRunner,
      final FastConfirmationEventChannel fastConfirmationEventChannel,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this.enabled = enabled;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.fastConfirmationEventChannel = fastConfirmationEventChannel;

    // Setting up metrics
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        "fast_confirmation_slot",
        "Slot of the most recent confirmed block",
        latestConfirmedSlot::get);
    this.reorgsCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "fast_confirmation_reorgs_total",
            "Total number of confirmed block reorganizations (a run of consecutive reorganizing slots counts once)");
    this.fallbacksCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "fast_confirmation_fallbacks_total",
            "Total number of fallbacks to finality (a fallback held over consecutive slots counts once)");
    this.restartsCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "fast_confirmation_restarts_total",
            "Total number of restarts from a safe unrealized justified block (a restart held over consecutive slots counts once)");
    this.calculationTimer =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "fast_confirmation_calculation_seconds",
            "Time taken to run the fast confirmation algorithm (incl. source-state loading) each slot");
    this.timeoutCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "fast_confirmation_timeout_total",
            "Number of slots where the fast confirmation update did not complete within its timeout");
  }

  public static FastConfirmationTracker create(
      final Spec spec,
      final Optional<AsyncRunner> asyncRunner,
      final FastConfirmationEventChannel fastConfirmationEventChannel,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    return new FastConfirmationTracker(
        true, spec, asyncRunner, fastConfirmationEventChannel, metricsSystem, timeProvider);
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Whether the rule is still warming up, i.e. has yet to confirm beyond the finalized block since
   * (re)initialization. See {@link #warmedUp}.
   */
  public boolean isWarmingUp() {
    return enabled && !warmedUp.get();
  }

  public void initialize(final ReadOnlyStore store) {
    if (!enabled) {
      return;
    }
    lastProcessedSlot.set(null);
    warmedUp.set(false);
    lastConfirmationOutcome.set(ConfirmationOutcome.fallback());
    previousSlotWasReorg.set(false);
    fastConfirmationStore.set(FastConfirmationStore.create(store));
  }

  /**
   * Hands off the per-slot head snapshot to the dedicated runner. Only the head root is captured on
   * the (latency-critical) fork-choice thread; the epoch-boundary flags, greatest unrealized
   * justified checkpoint, source states and the confirmation computation are all derived on the
   * runner.
   */
  public SafeFuture<Void> onSlot(final UInt64 slot, final Bytes32 headRoot) {
    if (!enabled) {
      return SafeFuture.COMPLETE;
    }

    if (fastConfirmationStore.get() == null) {
      LOG.debug("Skipping fast confirmation update because store is not initialized");
      return SafeFuture.COMPLETE;
    }

    if (asyncRunner.isEmpty()) {
      LOG.warn("Skipping fast confirmation update because no async runner is configured");
      return SafeFuture.COMPLETE;
    }

    final FastConfirmationInput input = new FastConfirmationInput(slot, headRoot);
    final AsyncRunner runner = asyncRunner.orElseThrow();

    // Chain each slot after the previous one so the rotating store is read, mutated by
    // update_fast_confirmation_variables and written back in strict slot order: the next slot never
    // starts until this one has fully committed, which makes the commit the sole in-order writer
    // (no cross-slot write races). The pipeline is composed rather than join()ed, so the dedicated
    // single-thread runner is freed while the state loads are in flight instead of blocking on
    // them.
    // A slow/hung load is bounded inside the segment (see updateConfirmedRoot) and degrades to the
    // finalized fallback, so the slot still commits its rotation in order rather than being
    // abandoned (which would leave the next slot reading un-rotated FCR variables).
    //
    // onSlot runs exactly once per slot on the fork-choice event thread and is never re-entrant, so
    // this read-and-replace of updateChain needs no synchronization.
    final SafeFuture<Void> thisSlot =
        updateChain.thenCompose(__ -> runner.runAsync(() -> processFastConfirmationInput(input)));
    // Keep the chain alive even if this slot fails unexpectedly, so one error cannot wedge later
    // slots; the caller still observes the failure through the returned future.
    updateChain = thisSlot.exceptionally(error -> null);
    return thisSlot;
  }

  /**
   * Whether the head is too far behind for {@code get_latest_confirmed} to do anything but revert
   * to finalized: {@code true} when the head block is two or more epochs behind the current epoch,
   * i.e. {@code head_epoch + 1 < current_epoch}. In that case the confirmed block (an ancestor of
   * the head) is more than one epoch old (reverts), the observed-justified checkpoint is at most
   * the head's epoch (no restart), and the advance gate (confirmed within one epoch of current)
   * cannot pass — so the result is always the finalized block. One epoch of lag is tolerated so the
   * rule keeps confirming through slow-following and non-finality. Treats a head not in fork choice
   * as stale.
   */
  private boolean isHeadStale(
      final UInt64 slot, final Bytes32 headRoot, final FastConfirmationStore store) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    return store
        .store()
        .getForkChoiceStrategy()
        .blockSlot(headRoot)
        .map(headSlot -> spec.computeEpochAtSlot(headSlot).plus(1).isLessThan(currentEpoch))
        .orElse(true);
  }

  /**
   * Upper bound for the source-state load and {@code get_latest_confirmed} of a single slot: half a
   * slot. The computation should finish well within a slot; this only guards against a hung or very
   * slow state retrieval stalling the per-slot chain. On timeout this slot's confirmation is
   * abandoned (see {@link #computeConfirmedRoot} and {@link #abandonConfirmation}); its already
   * committed rotation is kept. Applied via the default scheduler so it fires independently of the
   * runner thread.
   */
  private Duration updateTimeout(final UInt64 slot) {
    return Duration.ofMillis(spec.getSlotDurationMillis(slot) / 2L);
  }

  /**
   * Runs the {@code on_fast_confirmation} handler for the given slot head immediately, bypassing
   * both the async runner and the once-per-slot guard that {@link #onSlot} applies.
   *
   * <p>Only for the fork choice reference test runner, which must reproduce the call sequence the
   * vectors were generated with: the spec test harness invokes {@code on_fast_confirmation}
   * explicitly, and a few vectors call it twice within a slot or skip a slot entirely, so the
   * production once-per-slot schedule would produce a different number of rotations. Production
   * scheduling (and its once-per-slot guarantee) goes through {@link #onSlot}.
   */
  @VisibleForTesting
  public SafeFuture<Void> onFastConfirmation(final UInt64 slot, final Bytes32 headRoot) {
    final FastConfirmationStore currentStore = fastConfirmationStore.get();
    if (currentStore == null) {
      throw new IllegalStateException("Fast confirmation store is not initialized");
    }
    return runFastConfirmation(new FastConfirmationInput(slot, headRoot), currentStore);
  }

  private SafeFuture<Void> processFastConfirmationInput(final FastConfirmationInput input) {
    final FastConfirmationStore currentStore = fastConfirmationStore.get();
    if (currentStore == null) {
      LOG.debug("Skipping fast confirmation update because store is not initialized");
      return SafeFuture.COMPLETE;
    }

    // Skip a clearly stale or duplicate slot. Because slots are chained and each commits before the
    // next starts (see onSlot), lastProcessedSlot is fully up to date here, so this single check is
    // sufficient — no write-time guard is needed.
    final UInt64 lastSlot = lastProcessedSlot.get();
    if (lastSlot != null && input.slot().isLessThanOrEqualTo(lastSlot)) {
      LOG.debug(
          "Skipping fast confirmation update for slot {}: already processed slot {}",
          input.slot(),
          lastSlot);
      return SafeFuture.COMPLETE;
    }

    // Time the actual per-slot computation (state loading + get_latest_confirmed), which is what
    // the timeout bounds; the cheap guards above are deliberately left out of the measurement.
    final MetricsHistogram.Timer calculationTimerContext = calculationTimer.startTimer();
    return runFastConfirmation(input, currentStore)
        .alwaysRun(() -> calculationTimerContext.closeUnchecked().run());
  }

  private SafeFuture<Void> runFastConfirmation(
      final FastConfirmationInput input, final FastConfirmationStore currentStore) {
    final ReadOnlyStore store = currentStore.store();

    // Stale-head short-circuit (during sync, or whenever the head stops progressing): when the head
    // is two or more epochs behind the current epoch, get_latest_confirmed could only revert to the
    // finalized block, so skip the whole update this slot — no source-state loads, no
    // update_fast_confirmation_variables (including its greatest-unrealized-justified scan over all
    // blocks), no event, no metrics churn. The store is left untouched and re-establishes itself on
    // the first non-stale slot; the FCU safe hash falls back to the justified block while this (now
    // stale) confirmed root is no longer in fork choice (see
    // ForkChoiceStrategy#getForkChoiceState).
    if (isHeadStale(input.slot(), input.headRoot(), currentStore)) {
      LOG.debug(
          "Fast confirmation update for slot {}: head is more than one epoch behind; skipping",
          input.slot());
      return SafeFuture.COMPLETE;
    }

    // Derived off the fork-choice thread. The greatest unrealized justified checkpoint is only
    // needed on the last slot of an epoch (when the next slot starts a new epoch); otherwise it is
    // left at the finalized checkpoint and unused by update_fast_confirmation_variables.
    final boolean currentSlotIsEpochStart =
        FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, input.slot());
    final boolean nextSlotIsEpochStart =
        FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, input.slot().plus(1));
    final Checkpoint greatestUnrealizedJustifiedCheckpoint =
        nextSlotIsEpochStart
            ? FastConfirmationRuleUtil.getGreatestUnrealizedJustifiedCheckpoint(store)
            : store.getFinalizedCheckpoint();

    final FastConfirmationStore withUpdatedVariables =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            currentStore,
            input.headRoot(),
            greatestUnrealizedJustifiedCheckpoint,
            currentSlotIsEpochStart,
            nextSlotIsEpochStart);

    // update_fast_confirmation_variables is cheap and MUST run exactly once per slot in order.
    // Commit the rotation up front — before the abandonable confirmation below, carrying over the
    // previous confirmed_root — so the next slot always reads correctly rotated variables even if
    // this slot's confirmation is abandoned on timeout. Slots are chained (see onSlot), so this and
    // the confirmed-root fold below run as the sole writer in slot order, with no write races.
    applyRotation(input.slot(), withUpdatedVariables);

    // on_fast_confirmation: fcr_store.confirmed_root = get_latest_confirmed(fcr_store). Run the
    // (potentially slow) source-state load and get_latest_confirmed off the runner thread, bounded
    // by updateTimeout. On success the confirmed root is folded onto the rotated store; on timeout
    // (or load error) the computation is abandoned and confirmed_root is left unchanged — the next
    // slot re-derives from it.
    return computeConfirmedRoot(withUpdatedVariables, input.slot(), currentSlotIsEpochStart)
        .thenAccept(confirmedRoot -> applyConfirmedRoot(input, withUpdatedVariables, confirmedRoot))
        .exceptionally(error -> abandonConfirmation(error, input.slot()));
  }

  /**
   * Commits {@code update_fast_confirmation_variables} for the slot (carrying over the previous
   * confirmed root). Runs as the sole writer in strict slot order (slots are chained in {@link
   * #onSlot} so a slot always commits before the next one starts), so no atomic guard is needed
   * here; a clearly stale or duplicate slot was already skipped in {@link
   * #processFastConfirmationInput}.
   */
  private void applyRotation(final UInt64 slot, final FastConfirmationStore rotatedStore) {
    fastConfirmationStore.set(rotatedStore);
    lastProcessedSlot.set(slot);
  }

  /**
   * Folds the computed confirmed root onto this slot's already-committed rotated store and records
   * the derived metrics and the {@code fast_confirmation} event. Serialized by the per-slot chain,
   * so it is the sole writer and {@code rotatedStore} is still the current store.
   */
  private void applyConfirmedRoot(
      final FastConfirmationInput input,
      final FastConfirmationStore rotatedStore,
      final Bytes32 confirmedRoot) {
    fastConfirmationStore.set(rotatedStore.withConfirmedRoot(confirmedRoot));

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        rotatedStore.store().getForkChoiceStrategy();
    final UInt64 confirmedSlot = forkChoiceStrategy.blockSlot(confirmedRoot).orElse(UInt64.ZERO);
    latestConfirmedSlot.set(confirmedSlot.intValue());
    recordReorgOutcome(
        input.slot(), forkChoiceStrategy, rotatedStore.confirmedRoot(), confirmedRoot);

    LOG.info(
        "Fast confirmation update for slot {}: head={}, confirmed_root={}, confirmed_slot={}{}",
        input.slot(),
        input.headRoot(),
        confirmedRoot,
        confirmedSlot,
        warmedUp.get() ? "" : " (warmup stage)");

    // The fast_confirmation event is emitted every time the algorithm runs, regardless of whether
    // the confirmed block changed (per the Beacon API event stream spec).
    fastConfirmationEventChannel.onFastConfirmation(confirmedRoot, confirmedSlot, input.slot());
  }

  /**
   * Abandons this slot's confirmation computation after a timeout (or load error). The rotation was
   * already committed by {@link #applyRotation}; {@code confirmed_root} is left unchanged for the
   * next slot to re-derive from, so nothing is written here.
   */
  private Void abandonConfirmation(final Throwable error, final UInt64 slot) {
    if (ExceptionUtil.hasCause(error, TimeoutException.class)) {
      timeoutCounter.inc();
      LOG.warn(
          "Fast confirmation computation for slot {} timed out after {}; abandoning (confirmed root unchanged)",
          slot,
          updateTimeout(slot));
    } else {
      LOG.warn(
          "Fast confirmation computation for slot {} failed; abandoning (confirmed root unchanged)",
          slot,
          error);
    }
    return null;
  }

  /**
   * Runs {@code get_latest_confirmed} against the loaded source states, returning the new confirmed
   * root. The state load is composed (never join()ed) so the runner thread is not blocked while it
   * is retrieved; in practice the required states are cache-resident (the head state and the
   * justified checkpoint state), so the continuation runs inline. The load is bounded by {@link
   * #updateTimeout}, applied before {@code get_latest_confirmed} so that a load which times out
   * never runs the computation on a late-arriving result; on timeout (or load error) the returned
   * future fails and the caller abandons this slot's confirmation. When the states are genuinely
   * unavailable (loaded, but empty) the confirmed root falls back to the finalized block — a
   * deliberate transition, distinct from a timeout.
   */
  private SafeFuture<Bytes32> computeConfirmedRoot(
      final FastConfirmationStore fcrStore, final UInt64 slot, final boolean atEpochStart) {
    final Bytes32 finalizedRoot = fcrStore.store().getFinalizedCheckpoint().getRoot();
    return FastConfirmationStateLoader.load(fcrStore, fcrStore.currentSlotHead(), atEpochStart)
        .orTimeout(updateTimeout(slot))
        .thenApply(maybeStates -> resolveConfirmedRoot(fcrStore, slot, finalizedRoot, maybeStates));
  }

  private Bytes32 resolveConfirmedRoot(
      final FastConfirmationStore fcrStore,
      final UInt64 slot,
      final Bytes32 finalizedRoot,
      final Optional<FastConfirmationStates> maybeStates) {
    if (maybeStates.isEmpty()) {
      // The source states could not be loaded (e.g. the observed-justified checkpoint state has
      // been pruned, which happens for a short window after startup before that checkpoint has
      // advanced to a still-hot one). We cannot run get_latest_confirmed, so fall back to the
      // finalized block: it is always safe to consider confirmed, its root is known without a state
      // load, and this keeps confirmed_root tracking finality rather than going stale.
      LOG.debug(
          "Fast confirmation for slot {}: source states unavailable, falling back to finalized {}",
          slot,
          finalizedRoot);
      // The same fallback state as the rule reverting to finality, so it goes through the same
      // episode counting: consecutive slots (whatever mix of the two causes) count once.
      recordConfirmationOutcome(fcrStore, finalizedRoot, finalizedRoot);
      return finalizedRoot;
    }

    final Bytes32 confirmedRoot =
        new FastConfirmationCalculator(spec, fcrStore, maybeStates.get(), slot)
            .getLatestConfirmed();
    recordConfirmationOutcome(fcrStore, confirmedRoot, finalizedRoot);

    return confirmedRoot;
  }

  /**
   * Classifies this slot's confirmation outcome and counts it only when it opens a new episode (see
   * {@link #lastConfirmationOutcome}), so a condition that holds for many consecutive slots is
   * reported as the single event it is.
   */
  void recordConfirmationOutcome(
      final FastConfirmationStore fcrStore,
      final Bytes32 confirmedRoot,
      final Bytes32 finalizedRoot) {
    if (confirmedRoot.equals(finalizedRoot)) {
      // A fallback that persists across an advancing finalized checkpoint (confirmed_root moving
      // from the old finalized root to the new one) is the same episode: the outcome carries no
      // root, so it is unchanged.
      if (opensNewEpisode(ConfirmationOutcome.fallback())) {
        fallbacksCounter.inc();
      }
      return;
    }
    // Confirmed beyond finality, so the rule is no longer running on the values seeded at
    // (re)initialization. Set once and never cleared for the rest of this initialization.
    warmedUp.set(true);
    final Bytes32 observedJustifiedRoot =
        fcrStore.currentEpochObservedJustifiedCheckpoint().getRoot();
    if (confirmedRoot.equals(observedJustifiedRoot)) {
      // Restarting from the checkpoint the previous episode already restarted from is that episode
      // continuing; a different checkpoint (the variables rotated at an epoch boundary in between)
      // is a genuinely new restart, as is coming from a fallback or an advance.
      if (opensNewEpisode(ConfirmationOutcome.restart(observedJustifiedRoot))) {
        restartsCounter.inc();
      }
      return;
    }
    lastConfirmationOutcome.set(ConfirmationOutcome.advanced());
  }

  /**
   * Records this slot's confirmation outcome, returning whether it opens a new episode, i.e.
   * differs from the outcome of the last slot that produced one.
   */
  private boolean opensNewEpisode(final ConfirmationOutcome outcome) {
    return !lastConfirmationOutcome.getAndSet(outcome).equals(outcome);
  }

  /**
   * Counts a confirmed block reorganization when this slot switches the confirmed block to another
   * chain and the previous slot did not, so a stretch of consecutive switching slots is reported as
   * one period of instability (see {@link #previousSlotWasReorg}).
   */
  void recordReorgOutcome(
      final UInt64 slot,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 previousConfirmedRoot,
      final Bytes32 confirmedRoot) {
    if (!isConfirmedRootReorg(slot, forkChoiceStrategy, previousConfirmedRoot, confirmedRoot)) {
      previousSlotWasReorg.set(false);
      return;
    }
    if (!previousSlotWasReorg.getAndSet(true)) {
      reorgsCounter.inc();
    }
  }

  /**
   * Returns whether changing between two confirmed block roots switches chains. Advancing to a
   * descendant or falling back to an ancestor is not a reorg, and neither is a change involving a
   * root that fork choice cannot place on a chain.
   *
   * <p>Confirmed roots are resolved through {@code get_node_for_root}, which deliberately produces
   * PENDING nodes under Gloas. Unlike an attestation latest message, a confirmed root has no vote
   * slot or payload hint with which to call {@code get_supported_node}. A PENDING ancestor matches
   * any payload status, so under Gloas this compares the two roots' block ancestry, which is what a
   * confirmed block reorganization means.
   */
  boolean isConfirmedRootReorg(
      final UInt64 slot,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 previousConfirmedRoot,
      final Bytes32 confirmedRoot) {
    if (confirmedRoot.equals(previousConfirmedRoot)) {
      return false;
    }
    // A root fork choice does not know cannot be placed on a chain, so both ancestry checks below
    // would fail and report a reorg that never happened. The usual cause is pruning: the previous
    // confirmed root is dropped once finalization advances past it. Treat it as no reorg observed.
    if (forkChoiceStrategy.blockSlot(previousConfirmedRoot).isEmpty()
        || forkChoiceStrategy.blockSlot(confirmedRoot).isEmpty()) {
      return false;
    }
    return !isAncestor(slot, forkChoiceStrategy, confirmedRoot, previousConfirmedRoot)
        && !isAncestor(slot, forkChoiceStrategy, previousConfirmedRoot, confirmedRoot);
  }

  private boolean isAncestor(
      final UInt64 slot,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 descendantRoot,
      final Bytes32 ancestorRoot) {
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(slot).getForkChoiceUtil();
    return forkChoiceUtil.isAncestor(
        forkChoiceStrategy,
        ForkChoiceNode.createBase(descendantRoot),
        ForkChoiceNode.createBase(ancestorRoot));
  }

  public Optional<FastConfirmationStore> getFastConfirmationStore() {
    return Optional.ofNullable(fastConfirmationStore.get());
  }

  private enum ConfirmationOutcomeKind {
    /** {@code confirmed_root} is a block beyond both finality and the observed justified block. */
    ADVANCED,
    /** {@code confirmed_root} is the finalized block. */
    FALLBACK,
    /** {@code confirmed_root} is the current epoch's observed justified block. */
    RESTART
  }

  /**
   * A slot's confirmation outcome. {@code restartCheckpointRoot} is the observed justified
   * checkpoint root the rule restarted from, and is only set for {@link
   * ConfirmationOutcomeKind#RESTART}.
   */
  private record ConfirmationOutcome(
      ConfirmationOutcomeKind kind, Optional<Bytes32> restartCheckpointRoot) {

    private static final ConfirmationOutcome ADVANCED =
        new ConfirmationOutcome(ConfirmationOutcomeKind.ADVANCED, Optional.empty());
    private static final ConfirmationOutcome FALLBACK =
        new ConfirmationOutcome(ConfirmationOutcomeKind.FALLBACK, Optional.empty());

    static ConfirmationOutcome advanced() {
      return ADVANCED;
    }

    static ConfirmationOutcome fallback() {
      return FALLBACK;
    }

    static ConfirmationOutcome restart(final Bytes32 restartCheckpointRoot) {
      return new ConfirmationOutcome(
          ConfirmationOutcomeKind.RESTART, Optional.of(restartCheckpointRoot));
    }
  }
}

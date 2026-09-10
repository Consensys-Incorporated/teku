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

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.ChainHead;

/**
 * Glue between {@code ForkChoice.onTick} and the fast confirmation side runner.
 *
 * <p>A {@link Service} so its per-slot pipeline has an explicit lifecycle: it is started and
 * stopped by the beacon chain controller, and must be stopped before the fork-choice event thread
 * is (see {@link #doStop}).
 */
public final class ForkChoiceFastConfirmation extends Service {
  private static final Logger LOG = LogManager.getLogger();

  /** Segment timeout, in slots, while the tracker is warming up. See {@link #segmentTimeout}. */
  private static final int WARM_UP_SEGMENT_TIMEOUT_SLOTS = 4;

  private final Spec spec;
  private final FastConfirmationTracker fastConfirmationTracker;

  /**
   * Runner used to schedule the per-slot {@link #segmentTimeout}, independent of the runner that
   * does the FCR work (so it fires even while that runner is busy). Empty in production, where the
   * default delayed executor is used; a test can inject a controllable runner via the
   * package-private constructor to drive the timeout deterministically.
   */
  private final Optional<AsyncRunner> timeoutRunner;

  /**
   * Tail of the per-slot processing chain. Each {@link #processForSlot} call composes its work
   * after the previous slot's so {@link FastConfirmationTracker#onSlot} is invoked in strict slot
   * order, even though each slot's {@code processHead} completes asynchronously with variable
   * latency (a slow checkpoint-state load must not let a later slot's head overtake an earlier
   * one). Confined to a single thread: {@code processForSlot} is only ever called from the serial
   * {@code ForkChoice.onTick}, so no synchronization is needed to read-and-replace this field.
   */
  private SafeFuture<Void> slotChain = SafeFuture.COMPLETE;

  public ForkChoiceFastConfirmation(
      final Spec spec, final FastConfirmationTracker fastConfirmationTracker) {
    this.spec = spec;
    this.fastConfirmationTracker = fastConfirmationTracker;
    this.timeoutRunner = Optional.empty();
  }

  public ForkChoiceFastConfirmation(
      final Spec spec,
      final FastConfirmationTracker fastConfirmationTracker,
      final AsyncRunner timeoutRunner) {
    this.spec = spec;
    this.fastConfirmationTracker = fastConfirmationTracker;
    this.timeoutRunner = Optional.of(timeoutRunner);
  }

  /**
   * Runs the FCR slot-start work once deferred attestations have been applied, entirely without
   * blocking the fork-choice thread. FCR is defined from the post-deferred-attestation head at slot
   * start, so this composes {@code processHead} onto the deferred-attestation future rather than
   * joining it. Everything after the head snapshot (epoch-boundary flags, greatest unrealized
   * justified checkpoint, source states, and the confirmation computation) is handed to the fast
   * confirmation runner via {@link FastConfirmationTracker#onSlot}. Once the confirmed root has
   * been updated, {@code sendForkChoiceUpdated} sends the slot's fcU so its {@code safe_block_hash}
   * reflects this slot's confirmed root; {@code processHead} deliberately does not send it (see
   * {@code ForkChoice#processHeadWithoutForkChoiceUpdate}), so exactly one fcU is sent per slot.
   *
   * <p>Successive slots are chained so {@code onSlot} is invoked in slot order: a slot's pipeline
   * only starts once the previous slot's has fully resolved. In steady state this adds no delay
   * (the next slot's tick is a full slot later), but it prevents a slow {@code processHead} on one
   * slot from letting a later slot's {@code onSlot} run first and drop the earlier slot's
   * once-per-slot rotation.
   *
   * <p>Because serialization means a stuck stage would otherwise block every later slot, each
   * slot's segment is bounded by {@link #segmentTimeout}. This guards the currently-unbounded
   * {@code processHead} (its async checkpoint-state load) and, since {@link
   * FastConfirmationTracker} bounds only its source-state load and not {@code
   * get_latest_confirmed}, the confirmation computation as well. On timeout the slot is abandoned
   * and the chain moves on, so a hung {@code processHead} degrades to "FCR skips that slot" rather
   * than wedging FCR until restart. Note that the timeout does not cancel the work — an abandoned
   * slot's confirmation can still land later.
   */
  public void processForSlot(
      final UInt64 currentSlot,
      final SafeFuture<Void> deferredAttestationsFuture,
      final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead,
      final Supplier<SafeFuture<Void>> sendForkChoiceUpdated) {
    if (!isRunning()) {
      return;
    }
    final SafeFuture<Void> segment =
        slotChain
            .thenCompose(__ -> deferredAttestationsFuture)
            .thenCompose(__ -> processHead.apply(currentSlot))
            .thenCompose(
                maybeHead ->
                    maybeHead
                        .map(
                            head ->
                                fastConfirmationTracker
                                    .onSlot(currentSlot, head.getRoot())
                                    .thenCompose(__ -> sendForkChoiceUpdated.get()))
                        .orElse(SafeFuture.COMPLETE));
    slotChain =
        withSegmentTimeout(segment, currentSlot)
            // Keep the chain alive on any failure (including a timeout), so one stuck or failed
            // slot
            // cannot wedge later slots.
            .exceptionally(
                error -> {
                  if (isRunning()) {
                    LOG.error("Fast confirmation update for slot {} failed", currentSlot, error);
                  } else {
                    LOG.debug(
                        "Fast confirmation update for slot {} abandoned during shutdown",
                        currentSlot,
                        error);
                  }
                  return null;
                });
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.COMPLETE;
  }

  /**
   * Stops scheduling new per-slot work; any segment still in flight is left to fail or time out
   * quietly. Must be called before the fork-choice event thread is stopped, otherwise the in-flight
   * segment's work is rejected with "EventThread not started" and logged as an FCR error on every
   * clean shutdown.
   */
  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> withSegmentTimeout(final SafeFuture<Void> segment, final UInt64 slot) {
    final Duration segmentTimeout = segmentTimeout(slot);
    return timeoutRunner
        .map(runner -> segment.orTimeout(runner, segmentTimeout))
        .orElseGet(() -> segment.orTimeout(segmentTimeout));
  }

  /**
   * One full slot: comfortably longer than the tracker's own (half-slot) confirmation timeout, so
   * in the normal slow-confirmation case that inner timeout fires first (preserving the slot's
   * rotation), and this outer bound only trips when {@code processHead} itself is stuck.
   *
   * <p>Relaxed to {@link #WARM_UP_SEGMENT_TIMEOUT_SLOTS} slots while the tracker is warming up. The
   * update that ends the warm-up advances {@code confirmed_root} across every block accumulated
   * while it was pinned to finality — up to two epochs — and {@code
   * find_latest_confirmed_descendant} scores each of those blocks over the whole active validator
   * set, so that single slot can exceed a one-slot budget on a large network. It is a one-off: in
   * steady state the walk covers a single block. Timing out here would not stop the work (the
   * timeout does not cancel it) but would log it as a failure and send the slot's fcU late, so the
   * bound is widened for the catch-up instead. A stopgap — the real fix is to score the whole chain
   * segment in one pass over the validator set.
   */
  private Duration segmentTimeout(final UInt64 slot) {
    final long slotDurationMillis = spec.getSlotDurationMillis(slot);
    return Duration.ofMillis(
        fastConfirmationTracker.isWarmingUp()
            ? slotDurationMillis * WARM_UP_SEGMENT_TIMEOUT_SLOTS
            : slotDurationMillis);
  }
}

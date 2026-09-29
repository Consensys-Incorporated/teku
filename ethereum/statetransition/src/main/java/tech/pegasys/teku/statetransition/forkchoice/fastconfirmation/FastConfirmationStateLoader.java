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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Retrieves the source states the Fast Confirmation Rule needs for a slot.
 *
 * <p>The three retrievals run concurrently on the store's own runners and are composed
 * asynchronously, so no state load ever blocks the (single-thread) fast confirmation runner. If any
 * required state is unavailable (e.g. pruned), the result is empty and the caller skips the slot.
 */
final class FastConfirmationStateLoader {

  private FastConfirmationStateLoader() {}

  /**
   * @param includePreviousBalanceSource whether to also retrieve {@code
   *     get_previous_balance_source}. It is only read by reconfirmation, which runs at epoch-start
   *     slots, so callers pass {@code is_start_slot_at_epoch(currentSlot)} to avoid an unnecessary
   *     (and often uncached) fetch of the previous epoch's checkpoint state on other slots.
   */
  static SafeFuture<Optional<FastConfirmationStates>> load(
      final Spec spec,
      final FastConfirmationStore fcrStore,
      final Bytes32 head,
      final UInt64 currentSlot,
      final boolean includePreviousBalanceSource) {
    final ReadOnlyStore store = fcrStore.store();
    final SafeFuture<Optional<BeaconState>> previousBalanceSource =
        includePreviousBalanceSource
            ? store.retrieveCheckpointState(fcrStore.previousEpochObservedJustifiedCheckpoint())
            : SafeFuture.completedFuture(Optional.empty());
    final SafeFuture<Optional<BeaconState>> currentBalanceSource =
        store.retrieveCheckpointState(fcrStore.currentEpochObservedJustifiedCheckpoint());
    final SafeFuture<Optional<BeaconState>> pulledUpHeadState =
        retrievePulledUpHeadState(spec, store, head, currentSlot);

    // All three retrievals were kicked off above and are already in flight on the store's runners.
    // The nested thenCompose/thenApply below only joins their results; it does NOT serialize the
    // I/O — each store call ran concurrently, and composing (rather than join()) keeps the fast
    // confirmation runner unblocked while they complete.
    return previousBalanceSource.thenCompose(
        previous ->
            currentBalanceSource.thenCompose(
                current ->
                    pulledUpHeadState.thenApply(
                        pulledUp ->
                            combine(includePreviousBalanceSource, previous, current, pulledUp))));
  }

  /**
   * Implements {@code get_pulled_up_head_state}: the head state advanced to the current epoch start
   * when the head block lags behind, otherwise the head block state as-is.
   *
   * <p>The lagging case is resolved through the store's checkpoint-state cache rather than by
   * replaying {@code process_slots} on the fast confirmation runner: the checkpoint {@code
   * (current_epoch, head)} is exactly the epoch-boundary state that attestation processing needs
   * for current-epoch attestations targeting the head chain, so it is computed once and shared. The
   * checkpoint path is only valid for a head block before the epoch start slot (the task rejects
   * states already past the checkpoint slot), so a current-epoch head — or one whose slot is
   * unknown — loads its block state directly.
   */
  private static SafeFuture<Optional<BeaconState>> retrievePulledUpHeadState(
      final Spec spec, final ReadOnlyStore store, final Bytes32 head, final UInt64 currentSlot) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final Optional<UInt64> headSlot = store.getForkChoiceStrategy().blockSlot(head);
    if (headSlot.isPresent() && headSlot.get().isLessThan(epochStartSlot)) {
      return store.retrieveCheckpointState(new Checkpoint(currentEpoch, head));
    }
    return store.retrieveBlockState(head);
  }

  private static Optional<FastConfirmationStates> combine(
      final boolean includePreviousBalanceSource,
      final Optional<BeaconState> previousBalanceSource,
      final Optional<BeaconState> currentBalanceSource,
      final Optional<BeaconState> pulledUpHeadState) {
    // The mandatory sources must be present; the previous balance source only when requested.
    if (currentBalanceSource.isEmpty()
        || pulledUpHeadState.isEmpty()
        || (includePreviousBalanceSource && previousBalanceSource.isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(
        new FastConfirmationStates(
            previousBalanceSource, currentBalanceSource.get(), pulledUpHeadState.get()));
  }
}

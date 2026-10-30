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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public final class FastConfirmationRuleUtil {

  /**
   * {@code COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR}: per mille value added to the committee
   * weight estimate over a slot range that spans an epoch boundary without covering a full epoch,
   * to ensure the safety of the confirmation rule with high probability. Defined as a preset
   * constant ({@code uint64(5)}) in the Fast Confirmation spec.
   */
  static final UInt64 COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR = UInt64.valueOf(5);

  /**
   * {@code CONFIRMATION_BYZANTINE_THRESHOLD}: assumed maximum percentage of Byzantine validators.
   * The spec exposes it as configuration with a maximum of {@code 25}; both the mainnet and minimal
   * presets set it to {@code 25}, so it is treated here as a fixed constant to avoid touching
   * {@code SpecConfig}. If a future network ever deviates from {@code 25} this constant must be
   * updated manually (or promoted into {@code SpecConfig}), otherwise the confirmation rule will
   * silently use the wrong threshold.
   */
  static final int CONFIRMATION_BYZANTINE_THRESHOLD = 25;

  private FastConfirmationRuleUtil() {}

  public static boolean isStartSlotAtEpoch(final Spec spec, final UInt64 slot) {
    return spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot)).equals(slot);
  }

  /**
   * Implements {@code block_should_be_finalized} from the Fast Confirmation spec: whether, at
   * {@code currentSlot}, the block at {@code blockSlot} is old enough to have been finalized
   * already, i.e. the start of the second epoch after the first checkpoint at or after it has been
   * reached.
   */
  static boolean blockShouldBeFinalized(
      final Spec spec, final UInt64 blockSlot, final UInt64 currentSlot) {
    final UInt64 nextCheckpointEpoch =
        spec.computeEpochAtSlot(blockSlot.plus(spec.getSlotsPerEpoch(blockSlot) - 1));
    final UInt64 earliestFinalitySlot = spec.computeStartSlotAtEpoch(nextCheckpointEpoch.plus(2));
    return earliestFinalitySlot.isLessThanOrEqualTo(currentSlot);
  }

  /** Implements {@code compute_slots_since_epoch_start} from the Consensus Spec. */
  static UInt64 computeSlotsSinceEpochStart(final Spec spec, final UInt64 slot) {
    return slot.minus(spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot)));
  }

  /**
   * Implements {@code is_full_validator_set_covered} from the Fast Confirmation spec: returns
   * {@code true} if the inclusive range {@code [startSlot, endSlot]} includes an entire epoch.
   */
  static boolean isFullValidatorSetCovered(
      final Spec spec, final UInt64 startSlot, final UInt64 endSlot) {
    final int slotsPerEpoch = spec.getSlotsPerEpoch(startSlot);
    final UInt64 startFullEpoch = spec.computeEpochAtSlot(startSlot.plus(slotsPerEpoch - 1));
    final UInt64 endFullEpoch = spec.computeEpochAtSlot(endSlot.plus(1));
    return startFullEpoch.isLessThan(endFullEpoch);
  }

  /**
   * Implements {@code adjust_committee_weight_estimate_to_ensure_safety} from the Fast Confirmation
   * spec.
   */
  static UInt64 adjustCommitteeWeightEstimateToEnsureSafety(final UInt64 estimate) {
    final UInt64 ceil = estimate.plus(999).dividedBy(1000);
    return ceil.times(UInt64.valueOf(1000).plus(COMMITTEE_WEIGHT_ESTIMATION_ADJUSTMENT_FACTOR));
  }

  /**
   * Implements {@code estimate_committee_weight_between_slots} from the Fast Confirmation spec:
   * estimates the total weight of the committees assigned to the inclusive slot range {@code
   * [startSlot, endSlot]}.
   */
  static UInt64 estimateCommitteeWeightBetweenSlots(
      final Spec spec,
      final UInt64 totalActiveBalance,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    // Sanity check
    if (startSlot.isGreaterThan(endSlot)) {
      return UInt64.ZERO;
    }

    // If an entire epoch is covered by the range, return the total active balance
    if (isFullValidatorSetCovered(spec, startSlot, endSlot)) {
      return totalActiveBalance;
    }

    final int slotsPerEpoch = spec.getSlotsPerEpoch(startSlot);
    final UInt64 startEpoch = spec.computeEpochAtSlot(startSlot);
    final UInt64 endEpoch = spec.computeEpochAtSlot(endSlot);
    final UInt64 committeeWeight = totalActiveBalance.dividedBy(slotsPerEpoch);
    if (startEpoch.equals(endEpoch)) {
      return committeeWeight.times(endSlot.minus(startSlot).plus(1));
    }

    // First, calculate the number of committees in the end epoch
    final UInt64 numSlotsInEndEpoch = computeSlotsSinceEpochStart(spec, endSlot).plus(1);
    // Next, calculate the number of slots remaining in the end epoch
    final UInt64 remainingSlotsInEndEpoch = UInt64.valueOf(slotsPerEpoch).minus(numSlotsInEndEpoch);
    // Then, calculate the number of slots in the start epoch
    final UInt64 numSlotsInStartEpoch =
        UInt64.valueOf(slotsPerEpoch).minus(computeSlotsSinceEpochStart(spec, startSlot));

    final UInt64 startEpochWeight = committeeWeight.times(numSlotsInStartEpoch);
    final UInt64 endEpochWeight = committeeWeight.times(numSlotsInEndEpoch);

    // A range that spans an epoch boundary but does not span any full epoch needs a pro-rata
    // calculation of the start epoch weight.
    final UInt64 startEpochWeightProRated =
        startEpochWeight.dividedBy(slotsPerEpoch).times(remainingSlotsInEndEpoch);

    return adjustCommitteeWeightEstimateToEnsureSafety(
        startEpochWeightProRated.plus(endEpochWeight));
  }

  /**
   * Reconstructs {@code store.unrealized_justified_checkpoint} (the greatest unrealized justified
   * checkpoint) from Teku's per-block checkpoint metadata.
   *
   * <p>Mirrors {@code update_unrealized_checkpoints}: it is initialized to the (realized) justified
   * checkpoint and raised only when a block reports an unrealized justified checkpoint from a
   * strictly higher epoch. Starting from the justified checkpoint matters at/near genesis, where
   * blocks carry a zero unrealized justified checkpoint but the store value is the genesis
   * checkpoint. This is fork-correct: like the spec's store-level value, it rises from any
   * processed block on any fork.
   */
  static Checkpoint getGreatestUnrealizedJustifiedCheckpoint(final ReadOnlyStore store) {
    Checkpoint greatest = store.getJustifiedCheckpoint();
    for (final ProtoNodeData block : store.getForkChoiceStrategy().getBlockData()) {
      final Checkpoint unrealizedJustified =
          block.getCheckpoints().getUnrealizedJustifiedCheckpoint();
      if (unrealizedJustified.getEpoch().isGreaterThan(greatest.getEpoch())) {
        greatest = unrealizedJustified;
      }
    }
    return greatest;
  }

  static FastConfirmationStore updateFastConfirmationVariables(
      final FastConfirmationStore fcrStore,
      final Bytes32 currentSlotHead,
      final Checkpoint greatestUnrealizedJustifiedCheckpoint,
      final boolean currentSlotIsEpochStart,
      final boolean nextSlotIsEpochStart) {
    // Shift the current slot head into the previous slot and record the new current slot head.
    FastConfirmationStore updatedStore =
        fcrStore
            .withPreviousSlotHead(fcrStore.currentSlotHead())
            .withCurrentSlotHead(currentSlotHead);

    if (nextSlotIsEpochStart) {
      updatedStore =
          updatedStore.withPreviousEpochGreatestUnrealizedCheckpoint(
              greatestUnrealizedJustifiedCheckpoint);
    }

    if (currentSlotIsEpochStart) {
      // Rotate the observed justified checkpoints: current becomes previous, and the greatest
      // unrealized checkpoint from the previous epoch becomes the current observed value.
      updatedStore =
          updatedStore
              .withPreviousEpochObservedJustifiedCheckpoint(
                  updatedStore.currentEpochObservedJustifiedCheckpoint())
              .withCurrentEpochObservedJustifiedCheckpoint(
                  updatedStore.previousEpochGreatestUnrealizedCheckpoint());
    }

    return updatedStore;
  }
}

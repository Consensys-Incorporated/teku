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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class ValidatorsUtil {

  protected final SpecConfig specConfig;
  protected final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public ValidatorsUtil(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  public boolean isEligibleForActivation(final UInt64 finalizedEpoch, final Validator validator) {
    return isEligibleForActivation(
        finalizedEpoch,
        validator.getActivationEpoch().longValue(),
        validator.getActivationEligibilityEpoch().longValue());
  }

  /** is_eligible_for_activation from the validator's raw uint64 epoch bits, compared unsigned. */
  public static boolean isEligibleForActivation(
      final UInt64 finalizedEpoch,
      final long activationEpoch,
      final long activationEligibilityEpoch) {
    return Long.compareUnsigned(activationEligibilityEpoch, finalizedEpoch.longValue()) <= 0
        && activationEpoch == SpecConfig.FAR_FUTURE_EPOCH.longValue();
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    return BeaconStateCache.getTransitionCaches(state)
        .getValidatorIndexCache()
        .getValidatorIndex(state, publicKey);
  }

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index``. ``assignment``
   * returned is a tuple of the following form: ``assignment[0]`` is the list of validators in the
   * committee ``assignment[1]`` is the index to which the committee is assigned ``assignment[2]``
   * is the slot at which the committee is assigned Return None if no assignment.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validatorIndex the validator that is calling this function.
   * @return Optional.of(CommitteeAssignment).
   */
  public Optional<CommitteeAssignment> getCommitteeAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    return getCommitteeAssignment(
        state, epoch, validatorIndex, beaconStateAccessors.getCommitteeCountPerSlot(state, epoch));
  }

  public Int2ObjectMap<CommitteeAssignment> getValidatorIndexToCommitteeAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    final Int2ObjectMap<CommitteeAssignment> assignmentMap = new Int2ObjectOpenHashMap<>();

    final int slotsPerEpoch = specConfig.getSlotsPerEpoch();
    final int committeeCountPerSlot =
        beaconStateAccessors.getCommitteeCountPerSlot(state, epoch).intValue();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    for (int slotOffset = 0; slotOffset < slotsPerEpoch; slotOffset++) {
      final UInt64 slot = startSlot.plus(slotOffset);
      for (int i = 0; i < committeeCountPerSlot; i++) {
        final UInt64 committeeIndex = UInt64.valueOf(i);
        final IntList committee =
            beaconStateAccessors.getBeaconCommittee(state, slot, committeeIndex);
        committee.forEach(
            j -> assignmentMap.put(j, new CommitteeAssignment(committee, committeeIndex, slot)));
      }
    }
    return assignmentMap;
  }

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index``. ``assignment``
   * returned is a tuple of the following form: ``assignment[0]`` is the list of validators in the
   * committee ``assignment[1]`` is the index to which the committee is assigned ``assignment[2]``
   * is the slot at which the committee is assigned Return None if no assignment.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validatorIndex the validator that is calling this function.
   * @param committeeCountPerSlot the number of committees for the target epoch
   * @return Optional.of(CommitteeAssignment).
   */
  private Optional<CommitteeAssignment> getCommitteeAssignment(
      final BeaconState state,
      final UInt64 epoch,
      final int validatorIndex,
      final UInt64 committeeCountPerSlot) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(UInt64.ONE);
    checkArgument(
        epoch.compareTo(nextEpoch) <= 0, "get_committee_assignment: Epoch number too high");

    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    final UInt64 endSlotExclusive = startSlot.plus(specConfig.getSlotsPerEpoch());

    for (UInt64 slot = startSlot; slot.isLessThan(endSlotExclusive); slot = slot.plus(UInt64.ONE)) {

      for (UInt64 index = UInt64.ZERO;
          index.compareTo(committeeCountPerSlot) < 0;
          index = index.plus(UInt64.ONE)) {
        final IntList committee = beaconStateAccessors.getBeaconCommittee(state, slot, index);
        if (committee.contains(validatorIndex)) {
          return Optional.of(new CommitteeAssignment(committee, index, slot));
        }
      }
    }
    return Optional.empty();
  }

  public Optional<UInt64> getPtcAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    // NO-OP in Phase0
    return Optional.empty();
  }

  public Int2ObjectMap<UInt64> getValidatorIndexToPtcAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    // NO-OP in Phase0
    return new Int2ObjectOpenHashMap<>();
  }

  public EpochAttestationSchedule getAttestationCommitteesAtEpoch(
      final BeaconState state, final UInt64 epoch, final UInt64 committeeCountPerSlot) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(UInt64.ONE);
    checkArgument(
        epoch.compareTo(nextEpoch) <= 0, "get_committee_assignment: Epoch number too high");
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    final EpochAttestationSchedule.Builder builder = EpochAttestationSchedule.builder();
    for (UInt64 slot = startSlot;
        slot.isLessThan(startSlot.plus(specConfig.getSlotsPerEpoch()));
        slot = slot.plus(UInt64.ONE)) {
      for (UInt64 index = UInt64.ZERO;
          index.isLessThan(committeeCountPerSlot);
          index = index.plus(UInt64.ONE)) {
        builder.add(slot, index, beaconStateAccessors.getBeaconCommittee(state, slot, index));
      }
    }
    return builder.build();
  }

  public boolean isAggregator(final BLSSignature slotSignature, final int modulo) {
    return bytesToUInt64(Hash.sha256(slotSignature.toSSZBytes()).slice(0, 8)).mod(modulo).isZero();
  }

  public int getAggregatorModulo(final int committeeSize) {
    return Math.max(1, committeeSize / ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE);
  }

  public UInt64 getPendingBalanceToWithdraw(final BeaconState state, final int validatorIndex) {
    return BeaconStateElectra.required(state).getPendingPartialWithdrawals().stream()
        .filter(withdrawal -> withdrawal.getValidatorIndex().intValue() == validatorIndex)
        .map(PendingPartialWithdrawal::getAmount)
        .reduce(UInt64::plus)
        .orElse(UInt64.ZERO);
  }
}

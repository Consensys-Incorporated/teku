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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.MAX_VALUE;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.ValidatorFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public abstract class AbstractValidatorStatusFactory implements ValidatorStatusFactory {

  private static final int STATUS_RANGE_SIZE = 1 << 16;
  protected final SpecConfig specConfig;
  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final Predicates predicates;
  protected final BeaconStateAccessors beaconStateAccessors;

  protected AbstractValidatorStatusFactory(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final Predicates predicates,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.predicates = predicates;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  protected abstract void processParticipation(
      List<ValidatorStatus> statuses,
      BeaconState genericState,
      UInt64 previousEpoch,
      UInt64 currentEpoch);

  @Override
  public ValidatorStatuses createValidatorStatuses(final BeaconState state) {
    final SszList<Validator> validators = state.getValidators();

    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 previousEpoch = beaconStateAccessors.getPreviousEpoch(state);

    final List<ValidatorStatus> statuses =
        createInitialValidatorStatuses(validators, currentEpoch, previousEpoch);

    processParticipation(statuses, state, previousEpoch, currentEpoch);

    return new ValidatorStatuses(statuses, createTotalBalances(state, statuses));
  }

  @Override
  public ValidatorStatuses recreateValidatorStatuses(
      final ValidatorStatuses validatorStatuses,
      final List<ValidatorStatus> validatorStatusesToAppend) {
    final List<ValidatorStatus> validatorStatusesList =
        Stream.concat(validatorStatuses.getStatuses().stream(), validatorStatusesToAppend.stream())
            .toList();
    return new ValidatorStatuses(validatorStatusesList, validatorStatuses.getTotalBalances());
  }

  private TotalBalances createTotalBalances(
      final BeaconState state, final List<ValidatorStatus> statuses) {
    final TransitionCaches transitionCaches = BeaconStateCache.getTransitionCaches(state);
    final ProgressiveTotalBalancesUpdates progressiveTotalBalances =
        transitionCaches.getProgressiveTotalBalances();

    return progressiveTotalBalances
        .getTotalBalances(specConfig)
        .orElseGet(() -> createTotalBalances(statuses));
  }

  @VisibleForTesting
  List<ValidatorStatus> createInitialValidatorStatuses(
      final SszList<Validator> validators, final UInt64 currentEpoch, final UInt64 previousEpoch) {
    // Statuses are decoded from the packed validator bytes, without creating Validator views. The
    // index space is split into ranges converted in parallel: each worker walks the immutable
    // backing tree of its own range and writes its own slots of the array.
    final int size = validators.size();
    final ValidatorStatus[] statuses = new ValidatorStatus[size];
    final int ranges = (size + STATUS_RANGE_SIZE - 1) / STATUS_RANGE_SIZE;
    final long previous = previousEpoch.longValue();
    final long current = currentEpoch.longValue();
    final long next = currentEpoch.plus(1).longValue();
    IntStream.range(0, ranges)
        .parallel()
        .forEach(
            range -> {
              final int end = Math.min(size, (range + 1) * STATUS_RANGE_SIZE);
              validators.forEachElementSsz(
                  range * STATUS_RANGE_SIZE,
                  (index, data, offset) -> {
                    if (index >= end) {
                      return false;
                    }
                    statuses[index] =
                        createValidatorStatus(
                            ValidatorFields.slashed(data, offset),
                            ValidatorFields.effectiveBalance(data, offset),
                            ValidatorFields.activationEpoch(data, offset),
                            ValidatorFields.exitEpoch(data, offset),
                            ValidatorFields.withdrawableEpoch(data, offset),
                            previous,
                            current,
                            next);
                    return true;
                  });
            });
    return Collections.unmodifiableList(Arrays.asList(statuses));
  }

  /** Builds a status from the validator's raw uint64 fields and epochs (compared unsigned). */
  private ValidatorStatus createValidatorStatus(
      final boolean slashed,
      final long effectiveBalance,
      final long activationEpoch,
      final long exitEpoch,
      final long withdrawableEpoch,
      final long previousEpoch,
      final long currentEpoch,
      final long nextEpoch) {
    return new ValidatorStatus(
        slashed,
        Long.compareUnsigned(withdrawableEpoch, currentEpoch) <= 0,
        UInt64.fromLongBits(effectiveBalance),
        UInt64.fromLongBits(withdrawableEpoch),
        predicates.isActiveValidator(activationEpoch, exitEpoch, currentEpoch),
        predicates.isActiveValidator(activationEpoch, exitEpoch, previousEpoch),
        predicates.isActiveValidator(activationEpoch, exitEpoch, nextEpoch));
  }

  @Override
  public ValidatorStatus createValidatorStatus(
      final Validator validator, final UInt64 previousEpoch, final UInt64 currentEpoch) {
    return createValidatorStatus(
        validator.isSlashed(),
        validator.getEffectiveBalance().longValue(),
        validator.getActivationEpoch().longValue(),
        validator.getExitEpoch().longValue(),
        validator.getWithdrawableEpoch().longValue(),
        previousEpoch.longValue(),
        currentEpoch.longValue(),
        currentEpoch.plus(1).longValue());
  }

  protected TotalBalances createTotalBalances(final List<ValidatorStatus> statuses) {
    final BalanceAccumulator currentEpochActiveValidators = new BalanceAccumulator();
    final BalanceAccumulator previousEpochActiveValidators = new BalanceAccumulator();
    final BalanceAccumulator currentEpochSourceAttesters = new BalanceAccumulator();
    final BalanceAccumulator currentEpochTargetAttesters = new BalanceAccumulator();
    final BalanceAccumulator currentEpochHeadAttesters = new BalanceAccumulator();
    final BalanceAccumulator previousEpochSourceAttesters = new BalanceAccumulator();
    final BalanceAccumulator previousEpochTargetAttesters = new BalanceAccumulator();
    final BalanceAccumulator previousEpochHeadAttesters = new BalanceAccumulator();

    for (ValidatorStatus status : statuses) {
      final UInt64 balance = status.getCurrentEpochEffectiveBalance();
      if (status.isActiveInCurrentEpoch()) {
        currentEpochActiveValidators.add(balance);
      }
      if (status.isActiveInPreviousEpoch()) {
        previousEpochActiveValidators.add(balance);
      }

      if (status.isSlashed()) {
        continue;
      }
      if (status.isCurrentEpochSourceAttester()) {
        currentEpochSourceAttesters.add(balance);
      }
      if (status.isCurrentEpochTargetAttester()) {
        currentEpochTargetAttesters.add(balance);
      }
      if (status.isCurrentEpochHeadAttester()) {
        currentEpochHeadAttesters.add(balance);
      }

      if (status.isPreviousEpochSourceAttester()) {
        previousEpochSourceAttesters.add(balance);
      }
      if (status.isPreviousEpochTargetAttester()) {
        previousEpochTargetAttesters.add(balance);
      }
      if (status.isPreviousEpochHeadAttester()) {
        previousEpochHeadAttesters.add(balance);
      }
    }
    return new TotalBalances(
        specConfig,
        currentEpochActiveValidators.getValue(),
        previousEpochActiveValidators.getValue(),
        currentEpochSourceAttesters.getValue(),
        currentEpochTargetAttesters.getValue(),
        currentEpochHeadAttesters.getValue(),
        previousEpochSourceAttesters.getValue(),
        previousEpochTargetAttesters.getValue(),
        previousEpochHeadAttesters.getValue());
  }

  protected boolean matchesEpochStartBlock(
      final BeaconState state, final UInt64 currentEpoch, final Bytes32 root) {
    return beaconStateAccessors.getBlockRoot(state, currentEpoch).equals(root);
  }

  private static class BalanceAccumulator {
    private long value;

    public BalanceAccumulator() {
      this.value = 0;
    }

    public void add(final UInt64 valueToAdd) {
      final long longValueToAdd = valueToAdd.longValue();
      if (longValueToAdd != 0
          && Long.compareUnsigned(value, MAX_VALUE.longValue() - longValueToAdd) > 0) {
        throw new ArithmeticException("uint64 overflow");
      }
      value += longValueToAdd;
    }

    public UInt64 getValue() {
      return UInt64.fromLongBits(value);
    }
  }
}

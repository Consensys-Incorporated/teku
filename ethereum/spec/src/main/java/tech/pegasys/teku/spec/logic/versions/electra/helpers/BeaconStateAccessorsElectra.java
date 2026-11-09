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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.crypto.Hash.getSha256Instance;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;
import static tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra.MAX_RANDOM_VALUE;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.BeaconStateAccessorsDeneb;

public class BeaconStateAccessorsElectra extends BeaconStateAccessorsDeneb {

  protected final SpecConfigElectra configElectra;
  protected PredicatesElectra predicatesElectra;

  public BeaconStateAccessorsElectra(
      final SpecConfig config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersElectra miscHelpers) {
    super(SpecConfigDeneb.required(config), predicatesElectra, miscHelpers);
    configElectra = config.toVersionElectra().orElseThrow();
    this.predicatesElectra = predicatesElectra;
  }

  /**
   * get_activation_exit_churn_limit
   *
   * @param state - the state to use to get the churn limit from
   * @return Return the churn limit for the current epoch dedicated to activations and exits.
   */
  public UInt64 getActivationExitChurnLimit(final BeaconStateElectra state) {
    return getBalanceChurnLimit(state).min(configElectra.getMaxPerEpochActivationExitChurnLimit());
  }

  /**
   * get_pending_balance_to_withdraw
   *
   * @param state The state
   * @param validatorIndex The index of the validator
   * @return The sum of the withdrawal amounts for the validator in the partial withdrawal queue.
   */
  public UInt64 getPendingBalanceToWithdraw(
      final BeaconStateElectra state, final int validatorIndex) {
    return state.getPendingPartialWithdrawals().stream()
        .filter(withdrawal -> withdrawal.getValidatorIndex().intValue() == validatorIndex)
        .map(PendingPartialWithdrawal::getAmount)
        .reduce(UInt64.ZERO, UInt64::plus);
  }

  /**
   * get_balance_churn_limit
   *
   * @param state the state to read active balance from
   * @return Return the churn limit for the current epoch.
   */
  public UInt64 getBalanceChurnLimit(final BeaconStateElectra state) {
    final UInt64 churn =
        configElectra
            .getMinPerEpochChurnLimitElectra()
            .max(getTotalActiveBalance(state).dividedBy(configElectra.getChurnLimitQuotient()));
    return churn.minusMinZero(churn.mod(configElectra.getEffectiveBalanceIncrement()));
  }

  /**
   * get_consolidation_churn_limit
   *
   * @param state state to read churn limits from
   */
  public UInt64 getConsolidationChurnLimit(final BeaconStateElectra state) {
    return getBalanceChurnLimit(state).minusMinZero(getActivationExitChurnLimit(state));
  }

  public static BeaconStateAccessorsElectra required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsElectra,
        "Expected %s but it was %s",
        BeaconStateAccessorsElectra.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsElectra) beaconStateAccessors;
  }

  @Override
  public IntList getNextSyncCommitteeIndices(final BeaconState state) {
    return getNextSyncCommitteeIndices(state, configElectra.getMaxEffectiveBalanceElectra());
  }

  @Override
  protected IntList getNextSyncCommitteeIndices(
      final BeaconState state, final UInt64 maxEffectiveBalance) {
    final UInt64 epoch = getCurrentEpoch(state).plus(1);
    final IntList activeValidatorIndices = getActiveValidatorIndices(state, epoch);
    final int activeValidatorCount = activeValidatorIndices.size();
    checkArgument(activeValidatorCount > 0, "Provided state has no active validators");

    final Bytes32 seed = getSeed(state, epoch, Domain.SYNC_COMMITTEE);
    final SszList<Validator> validators = state.getValidators();
    final IntList syncCommitteeIndices = new IntArrayList();
    final int syncCommitteeSize = configElectra.getSyncCommitteeSize();
    final Sha256 sha256 = getSha256Instance();

    int i = 0;
    Bytes randomBytes = null;
    while (syncCommitteeIndices.size() < syncCommitteeSize) {
      if (i % 16 == 0) {
        randomBytes = Bytes.wrap(sha256.digest(seed, uint64ToBytes(Math.floorDiv(i, 16L))));
      }
      final int shuffledIndex =
          miscHelpers.computeShuffledIndex(i % activeValidatorCount, activeValidatorCount, seed);
      final int candidateIndex = activeValidatorIndices.getInt(shuffledIndex);
      final int offset = (i % 16) * 2;
      final UInt64 randomValue = bytesToUInt64(randomBytes.slice(offset, 2));
      final UInt64 effectiveBalance = validators.get(candidateIndex).getEffectiveBalance();
      if (effectiveBalance
          .times(MAX_RANDOM_VALUE)
          .isGreaterThanOrEqualTo(maxEffectiveBalance.times(randomValue))) {
        syncCommitteeIndices.add(candidateIndex);
      }
      i++;
    }

    return syncCommitteeIndices;
  }
}

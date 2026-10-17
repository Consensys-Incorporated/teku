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

package tech.pegasys.teku.api.rewards;

import static tech.pegasys.teku.spec.constants.EthConstants.ETH_TO_GWEI;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_HEAD_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_TARGET_FLAG_INDEX;
import static tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.PARTICIPATION_FLAG_WEIGHTS;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.IdealAttestationReward;
import tech.pegasys.teku.api.migrated.TotalAttestationReward;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;

public class EpochAttestationRewardsCalculator {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecVersion specVersion;
  private final BeaconState state;
  private final EpochProcessor epochProcessor;
  private final ValidatorStatuses validatorStatuses;
  private final List<Integer> validatorIndices;

  public EpochAttestationRewardsCalculator(
      final SpecVersion specVersion, final BeaconState state, final List<String> validatorIds) {
    this.specVersion = specVersion;
    this.state = state;
    this.epochProcessor = specVersion.getEpochProcessor();
    this.validatorStatuses = specVersion.getValidatorStatusFactory().createValidatorStatuses(state);
    this.validatorIndices = mapValidatorIndices(state, validatorIds);
  }

  private List<Integer> mapValidatorIndices(
      final BeaconState state, final List<String> validatorIds) {
    final SszList<Validator> allValidators = state.getValidators();
    return IntStream.range(0, allValidators.size())
        .filter(
            i ->
                validatorIds.isEmpty()
                    || validatorIds.contains(allValidators.get(i).getPublicKey().toHexString())
                    || validatorIds.contains(String.valueOf(i)))
        .filter(i -> validatorStatuses.getStatuses().get(i).isEligibleValidator())
        .distinct()
        .boxed()
        .toList();
  }

  @VisibleForTesting
  List<Integer> getValidatorIndices() {
    return validatorIndices;
  }

  public AttestationRewardsData calculate() {
    try {
      final List<IdealAttestationReward> idealAttestationRewards = idealAttestationRewards();
      final List<TotalAttestationReward> totalAttestationRewards = totalAttestationRewards();
      return new AttestationRewardsData(idealAttestationRewards, totalAttestationRewards);
    } catch (RuntimeException ex) {
      LOG.error("Error calculating detailed rewards and penalties", ex);
      throw ex;
    }
  }

  @VisibleForTesting
  List<IdealAttestationReward> idealAttestationRewards() {
    final List<IdealAttestationReward> idealAttestationRewards =
        IntStream.rangeClosed(0, 32)
            .boxed()
            .map(i -> new IdealAttestationReward(ETH_TO_GWEI.times(i)))
            .toList();

    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();
    final BeaconStateAccessorsAltair beaconStateAccessors =
        BeaconStateAccessorsAltair.required(specVersion.beaconStateAccessors());
    // participation rewards are for the previous epoch (scaled to its slot duration from Heze)
    final UInt64 baseRewardPerIncrement =
        beaconStateAccessors.getBaseRewardPerIncrement(
            state, beaconStateAccessors.getPreviousEpoch(state));

    for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
      final UInt64 weight = PARTICIPATION_FLAG_WEIGHTS.get(flagIndex);
      final UInt64 effectiveBalanceIncrement =
          specVersion.getConfig().getEffectiveBalanceIncrement();
      final UInt64 unslashedParticipatingIncrements =
          getPrevEpochTotalParticipatingBalance(flagIndex).dividedBy(effectiveBalanceIncrement);
      final UInt64 activeIncrements =
          totalBalances.getCurrentEpochActiveValidators().dividedBy(effectiveBalanceIncrement);

      for (int effectiveBalanceEth = 0; effectiveBalanceEth <= 32; effectiveBalanceEth++) {
        final UInt64 baseReward = UInt64.valueOf(effectiveBalanceEth).times(baseRewardPerIncrement);
        final UInt64 rewardNumerator =
            baseReward.times(weight).times(unslashedParticipatingIncrements);
        final UInt64 idealReward =
            rewardNumerator.dividedBy(activeIncrements).dividedBy(WEIGHT_DENOMINATOR);

        final IdealAttestationReward idealAttestationReward =
            idealAttestationRewards.get(effectiveBalanceEth);
        if (!isInactivityLeak()) {
          switch (flagIndex) {
            case TIMELY_SOURCE_FLAG_INDEX ->
                idealAttestationReward.addSource(idealReward.longValue());
            case TIMELY_TARGET_FLAG_INDEX ->
                idealAttestationReward.addTarget(idealReward.longValue());
            case TIMELY_HEAD_FLAG_INDEX -> idealAttestationReward.addHead(idealReward.longValue());
          }
        }
      }
    }

    return idealAttestationRewards;
  }

  private boolean isInactivityLeak() {
    return specVersion.beaconStateAccessors().isInactivityLeak(state);
  }

  private UInt64 getPrevEpochTotalParticipatingBalance(final int flagIndex) {
    final TotalBalances totalBalances = validatorStatuses.getTotalBalances();
    return switch (flagIndex) {
      case TIMELY_HEAD_FLAG_INDEX -> totalBalances.getPreviousEpochHeadAttesters();
      case TIMELY_TARGET_FLAG_INDEX -> totalBalances.getPreviousEpochTargetAttesters();
      case TIMELY_SOURCE_FLAG_INDEX -> totalBalances.getPreviousEpochSourceAttesters();
      default ->
          throw new IllegalArgumentException("Unable to process unknown flag index:" + flagIndex);
    };
  }

  @VisibleForTesting
  List<TotalAttestationReward> totalAttestationRewards() {
    final RewardAndPenaltyDeltas totalRewardAndPenaltyDeltas =
        epochProcessor.getRewardAndPenaltyDeltas(
            state, validatorStatuses, RewardsAndPenaltiesCalculator::getDetailedDeltas);

    return validatorIndices.stream()
        .map(i -> new ImmutablePair<>(i, totalRewardAndPenaltyDeltas.getDelta(i)))
        .map(p -> new TotalAttestationReward(p.left, p.right))
        .toList();
  }
}

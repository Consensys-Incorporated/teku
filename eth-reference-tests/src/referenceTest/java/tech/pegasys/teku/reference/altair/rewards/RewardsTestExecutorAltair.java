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

package tech.pegasys.teku.reference.altair.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.reference.phase0.rewards.ExpectedDeltas;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.RewardsAndPenaltiesCalculatorAltair;

public class RewardsTestExecutorAltair implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> REWARDS_TEST_TYPES =
      ImmutableMap.of(
          "rewards/basic",
          new RewardsTestExecutorAltair(),
          "rewards/leak",
          new RewardsTestExecutorAltair(),
          "rewards/random",
          new RewardsTestExecutorAltair(),
          "rewards/inactivity_scores",
          new RewardsTestExecutorAltair());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz_snappy");

    final ValidatorStatusFactory statusFactory =
        testDefinition.getSpec().getGenesisSpec().getValidatorStatusFactory();
    final ValidatorStatuses validatorStatuses = statusFactory.createValidatorStatuses(preState);

    final SpecVersion spec = testDefinition.getSpec().getGenesisSpec();
    final RewardsAndPenaltiesCalculatorAltair calculator =
        createRewardsAndPenaltiesCalculator(preState, validatorStatuses, spec);
    runTest(testDefinition, calculator, validatorStatuses);
  }

  protected RewardsAndPenaltiesCalculatorAltair createRewardsAndPenaltiesCalculator(
      final BeaconState preState,
      final ValidatorStatuses validatorStatuses,
      final SpecVersion spec) {
    return new RewardsAndPenaltiesCalculatorAltair(
        SpecConfigAltair.required(spec.getConfig()),
        BeaconStateAltair.required(preState),
        validatorStatuses,
        (MiscHelpersAltair) spec.miscHelpers(),
        (BeaconStateAccessorsAltair) spec.beaconStateAccessors());
  }

  private void runTest(
      final TestDefinition testDefinition,
      final RewardsAndPenaltiesCalculatorAltair calculator,
      final ValidatorStatuses validatorStatuses) {
    assertDeltas(
        testDefinition,
        "head_deltas.ssz_snappy",
        applyFlagIndexDeltas(
            calculator, validatorStatuses, ParticipationFlags.TIMELY_HEAD_FLAG_INDEX));
    assertDeltas(
        testDefinition,
        "inactivity_penalty_deltas.ssz_snappy",
        apply(validatorStatuses, calculator::processInactivityPenaltyDeltas));
    assertDeltas(
        testDefinition,
        "source_deltas.ssz_snappy",
        applyFlagIndexDeltas(
            calculator, validatorStatuses, ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX));
    assertDeltas(
        testDefinition,
        "target_deltas.ssz_snappy",
        applyFlagIndexDeltas(
            calculator, validatorStatuses, ParticipationFlags.TIMELY_TARGET_FLAG_INDEX));
  }

  private Supplier<RewardAndPenaltyDeltas> applyFlagIndexDeltas(
      final RewardsAndPenaltiesCalculatorAltair calculator,
      final ValidatorStatuses validatorStatuses,
      final int flagIndex) {
    return apply(validatorStatuses, deltas -> calculator.processFlagIndexDeltas(deltas, flagIndex));
  }

  private Supplier<RewardAndPenaltyDeltas> apply(
      final ValidatorStatuses validatorStatuses, final Consumer<RewardAndPenaltyDeltas> step) {
    return () -> {
      final RewardAndPenaltyDeltas deltas =
          RewardAndPenaltyDeltas.aggregated(validatorStatuses.getValidatorCount());
      step.accept(deltas);
      return deltas;
    };
  }

  private void assertDeltas(
      final TestDefinition testDefinition,
      final String expectedResultsFileName,
      final Supplier<RewardAndPenaltyDeltas> function) {
    final RewardAndPenaltyDeltas expectedDeltas =
        loadSsz(testDefinition, expectedResultsFileName, ExpectedDeltas.SSZ_SCHEMA).getDeltas();
    final RewardAndPenaltyDeltas actualDeltas = function.get();
    assertThat(actualDeltas)
        .describedAs(expectedResultsFileName)
        .usingRecursiveComparison()
        .isEqualTo(expectedDeltas);
  }
}

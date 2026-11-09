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

package tech.pegasys.teku.spec.logic.versions.electra.withdrawals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers.ExpectedWithdrawals;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WithdrawalsHelpersElectraTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void getsExpectedWithdrawals() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 electraMaxBalance = specConfigElectra.getMaxEffectiveBalance();
    final long excessBalance = 1024000L;
    final long partialWithdrawalBalance = 10241024L;

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeEth1Validator(UInt64.THIRTY_TWO_ETH.plus(excessBalance))
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(1, electraMaxBalance.plus(partialWithdrawalBalance))
                .build());

    final WithdrawalsHelpers withdrawalsHelpers = getWithdrawalsHelpers(preState);

    final ExpectedWithdrawals expectedWithdrawals =
        withdrawalsHelpers.getExpectedWithdrawals(preState);

    assertThat(expectedWithdrawals.withdrawals().get(0).getAmount())
        .isEqualTo(UInt64.valueOf(partialWithdrawalBalance));
    assertThat(expectedWithdrawals.withdrawals().get(1).getAmount())
        .isEqualTo(UInt64.valueOf(excessBalance));
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(1);
    final MutableBeaconStateElectra mutableBeaconStateElectra =
        MutableBeaconStateElectra.required(preState.createWritableCopy());

    withdrawalsHelpers.processWithdrawals(mutableBeaconStateElectra);
    assertThat(mutableBeaconStateElectra.getPendingPartialWithdrawals().size()).isEqualTo(0);

    assertThat(mutableBeaconStateElectra.getNextWithdrawalIndex()).isEqualTo(UInt64.valueOf(2));
    assertThat(mutableBeaconStateElectra.getValidators().size()).isEqualTo(2);
  }

  @Test
  void pendingPartialWithdrawals_SingleExceedingMinimumBalance() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 minActivationBalance = specConfigElectra.getMaxEffectiveBalance();
    final UInt64 partialWithdrawalBalance = UInt64.valueOf(10_000L);
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(minActivationBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(0, partialWithdrawalBalance.times(2))
                .build());

    final ExpectedWithdrawals expectedWithdrawals =
        getWithdrawalsHelpers(preState).getExpectedWithdrawals(preState);
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(1);
    // although the withdrawal was for 2x the available, we could only satisfy half of it
    assertThat(expectedWithdrawals.withdrawals().getFirst().getAmount())
        .isEqualTo(partialWithdrawalBalance);
  }

  @Test
  void pendingPartialWithdrawals_MultipleExceedingMinimumBalance() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 minActivationBalance = specConfigElectra.getMaxEffectiveBalance();
    final UInt64 partialWithdrawalBalance = UInt64.valueOf(10_000L);
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(minActivationBalance.plus(15_000))
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                .build());

    final ExpectedWithdrawals expectedWithdrawals =
        getWithdrawalsHelpers(preState).getExpectedWithdrawals(preState);
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(2);
    // the first withdrawal could be fully satisfied
    assertThat(expectedWithdrawals.withdrawals().get(0).getAmount())
        .isEqualTo(partialWithdrawalBalance);
    // the second withdrawal could only be partially satisfied
    assertThat(expectedWithdrawals.withdrawals().get(1).getAmount())
        .isEqualTo(UInt64.valueOf(5_000L));
  }

  @Test
  void pendingPartialWithdrawals_MultipleExceedingMinimumBalanceOneSkipped() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 minActivationBalance = specConfigElectra.getMaxEffectiveBalance();
    final UInt64 partialWithdrawalBalance = UInt64.valueOf(10_000L);
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(minActivationBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                // this second pending partial will end up ignored because of insufficient balance
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                .build());

    final ExpectedWithdrawals expectedWithdrawals =
        getWithdrawalsHelpers(preState).getExpectedWithdrawals(preState);
    // 2 partials came out of the state queue
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(2);
    // only 1 withdrawal added because we had insufficient balance to allow the second
    assertThat(expectedWithdrawals.withdrawals().size()).isEqualTo(1);
    // the first withdrawal was allowed at the requested amount
    assertThat(expectedWithdrawals.withdrawals().get(0).getAmount())
        .isEqualTo(UInt64.valueOf(10_000L));
  }

  @Test
  void pendingPartialWithdrawals() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 electraMaxBalance = specConfigElectra.getMaxEffectiveBalance();
    final long partialWithdrawalBalance = 10241024L;

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance))
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance + 1))
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance + 2))
                .pendingPartialWithdrawal(0, electraMaxBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(
                    1, electraMaxBalance.plus(partialWithdrawalBalance).plus(1))
                .pendingPartialWithdrawal(
                    2, electraMaxBalance.plus(partialWithdrawalBalance).plus(2))
                .build());

    final WithdrawalsHelpers withdrawalsHelpers = getWithdrawalsHelpers(preState);

    final ExpectedWithdrawals expectedWithdrawals =
        withdrawalsHelpers.getExpectedWithdrawals(preState);
    final MutableBeaconStateElectra mutableBeaconStateElectra =
        MutableBeaconStateElectra.required(preState.createWritableCopy());
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(2);

    withdrawalsHelpers.processWithdrawals(mutableBeaconStateElectra);
    assertThat(mutableBeaconStateElectra.getPendingPartialWithdrawals().size()).isEqualTo(1);
    assertThat(mutableBeaconStateElectra.getNextWithdrawalIndex()).isEqualTo(UInt64.valueOf(2));
    assertThat(mutableBeaconStateElectra.getValidators().size()).isEqualTo(3);
  }

  @Test
  void pendingPartialCountsSkippedWithdrawals() {
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 electraMaxBalance = specConfigElectra.getMaxEffectiveBalance();
    final long partialWithdrawalBalance = 10241024L;

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance))
                // the two validators below are skipped because they are queued for exit
                .activeConsolidatingValidatorQueuedForExit(
                    electraMaxBalance.plus(partialWithdrawalBalance + 1))
                .activeConsolidatingValidatorQueuedForExit(
                    electraMaxBalance.plus(partialWithdrawalBalance + 2))
                .pendingPartialWithdrawal(0, electraMaxBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(
                    1, electraMaxBalance.plus(partialWithdrawalBalance).plus(1))
                .pendingPartialWithdrawal(
                    2, electraMaxBalance.plus(partialWithdrawalBalance).plus(2))
                .build());

    final ExpectedWithdrawals expectedWithdrawals =
        getWithdrawalsHelpers(preState).getExpectedWithdrawals(preState);
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(3);
  }

  private WithdrawalsHelpers getWithdrawalsHelpers(final BeaconState state) {
    return spec.atSlot(state.getSlot()).getWithdrawalsHelpers().orElseThrow();
  }
}

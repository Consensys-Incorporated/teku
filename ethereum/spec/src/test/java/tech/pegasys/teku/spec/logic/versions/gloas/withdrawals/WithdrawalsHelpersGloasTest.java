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

package tech.pegasys.teku.spec.logic.versions.gloas.withdrawals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers.ExpectedWithdrawals;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WithdrawalsHelpersGloasTest {

  private static final UInt64 ONE_ETH = UInt64.valueOf(1_000_000_000L);

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());

  @Test
  void buildersSweep_shouldWithdrawBalanceLeftAfterPendingWithdrawalInSamePayload() {
    final UInt64 balance = ONE_ETH.times(5);
    final UInt64 pendingAmount = ONE_ETH;
    final BeaconStateGloas state = stateWithSweepableBuilder(balance, pendingAmount);

    final ExpectedWithdrawals expectedWithdrawals =
        getWithdrawalsHelpers(state).getExpectedWithdrawals(state);

    assertThat(builderWithdrawalAmounts(expectedWithdrawals.withdrawals()))
        .containsExactly(pendingAmount, balance.minus(pendingAmount));
    assertThat(expectedWithdrawals.processedBuilderWithdrawalsCount()).isEqualTo(1);
    assertThat(expectedWithdrawals.processedBuildersSweepCount()).isEqualTo(1);
  }

  @Test
  void buildersSweep_shouldSkipBuilderWhenPendingWithdrawalConsumesWholeBalance() {
    final UInt64 balance = ONE_ETH.times(5);
    final BeaconStateGloas state = stateWithSweepableBuilder(balance, balance);

    final ExpectedWithdrawals expectedWithdrawals =
        getWithdrawalsHelpers(state).getExpectedWithdrawals(state);

    assertThat(builderWithdrawalAmounts(expectedWithdrawals.withdrawals()))
        .containsExactly(balance);
    assertThat(expectedWithdrawals.processedBuilderWithdrawalsCount()).isEqualTo(1);
    // the builder is still visited by the sweep, but nothing is withdrawn for it
    assertThat(expectedWithdrawals.processedBuildersSweepCount()).isEqualTo(1);
  }

  /**
   * A state with a single builder that is eligible for the sweep (withdrawable epoch is the current
   * epoch) and has one pending withdrawal queued ahead of the sweep.
   */
  private BeaconStateGloas stateWithSweepableBuilder(
      final UInt64 balance, final UInt64 pendingAmount) {
    final UInt64 slot = UInt64.valueOf(spec.getGenesisSpecConfig().getSlotsPerEpoch() * 2L);
    final UInt64 builderIndex = UInt64.ZERO;
    final Builder builder =
        dataStructureUtil
            .builderBuilder()
            .balance(balance)
            .withdrawableEpoch(spec.computeEpochAtSlot(slot))
            .build();
    return BeaconStateGloas.required(
        dataStructureUtil
            .randomBeaconState(slot)
            .updated(
                mutable -> {
                  final MutableBeaconStateGloas state = MutableBeaconStateGloas.required(mutable);
                  state.getBuilders().setAll(List.of(builder));
                  state.setNextWithdrawalBuilderIndex(builderIndex);
                  state
                      .getBuilderPendingWithdrawals()
                      .setAll(
                          List.of(
                              schemaDefinitions
                                  .getBuilderPendingWithdrawalSchema()
                                  .create(
                                      dataStructureUtil.randomEth1Address(),
                                      pendingAmount,
                                      builderIndex)));
                  // keep the payload free for the builder withdrawals
                  state.getPendingPartialWithdrawals().clear();
                }));
  }

  private List<UInt64> builderWithdrawalAmounts(final List<Withdrawal> withdrawals) {
    final UInt64 validatorIndex =
        miscHelpersGloas().convertBuilderIndexToValidatorIndex(UInt64.ZERO);
    return withdrawals.stream()
        .filter(withdrawal -> withdrawal.getValidatorIndex().equals(validatorIndex))
        .map(Withdrawal::getAmount)
        .toList();
  }

  private MiscHelpersGloas miscHelpersGloas() {
    return MiscHelpersGloas.required(spec.getGenesisSpec().miscHelpers());
  }

  private WithdrawalsHelpers getWithdrawalsHelpers(final BeaconStateGloas state) {
    return spec.atSlot(state.getSlot()).getWithdrawalsHelpers().orElseThrow();
  }
}

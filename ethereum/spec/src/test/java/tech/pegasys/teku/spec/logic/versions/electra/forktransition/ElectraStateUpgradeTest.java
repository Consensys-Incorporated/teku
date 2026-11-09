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

package tech.pegasys.teku.spec.logic.versions.electra.forktransition;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ElectraStateUpgradeTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final PredicatesElectra predicatesElectra =
      new PredicatesElectra(spec.getGenesisSpecConfig());
  final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersElectra miscHelpersElectra =
      new MiscHelpersElectra(
          spec.getGenesisSpecConfig().toVersionElectra().orElseThrow(),
          predicatesElectra,
          schemaDefinitionsElectra);
  final BeaconStateAccessorsElectra stateAccessorsElectra =
      new BeaconStateAccessorsElectra(
          spec.getGenesisSpecConfig(), predicatesElectra, miscHelpersElectra);
  final BeaconStateMutatorsElectra stateMutatorsElectra =
      new BeaconStateMutatorsElectra(
          spec.getGenesisSpecConfig(),
          miscHelpersElectra,
          stateAccessorsElectra,
          schemaDefinitionsElectra);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void canUpgradeFromDeneb() {
    final UInt64 slot = UInt64.valueOf(80_000L);
    final BeaconStateDeneb pre =
        BeaconStateDeneb.required(
            dataStructureUtil.stateBuilder(SpecMilestone.DENEB, 0, 0).slot(slot).build());

    final ElectraStateUpgrade upgrade =
        new ElectraStateUpgrade(
            SpecConfigElectra.required(spec.getGenesisSpecConfig()),
            schemaDefinitionsElectra,
            stateAccessorsElectra,
            stateMutatorsElectra);

    final BeaconStateElectra post = upgrade.upgrade(pre);

    assertThat(post.getDepositBalanceToConsume()).isEqualTo(UInt64.ZERO);
    // min churn - churn % balance_increment
    // = (64 *10^9) - (64 *10^9) MOD 10^9
    // = (64 *10^9) - 0
    assertThat(post.getExitBalanceToConsume()).isEqualTo(UInt64.valueOf(64_000_000_000L));
    assertThat(post.getEarliestExitEpoch())
        .isEqualTo(
            miscHelpersElectra.computeActivationExitEpoch(
                spec.computeEpochAtSlot(slot).increment()));
    assertThat(post.getConsolidationBalanceToConsume()).isEqualTo(UInt64.ZERO);
    // 80_000/8 (slots -> epochs) + max_seed_lookahead + 1
    assertThat(post.getEarliestConsolidationEpoch()).isEqualTo(UInt64.valueOf(10005));
    assertThat(post.getPendingDeposits()).isEmpty();
    assertThat(post.getPendingConsolidations()).isEmpty();
    assertThat(post.getPendingPartialWithdrawals()).isEmpty();
  }

  @Test
  public void shouldAddNonActiveValidatorsToPendingBalanceDeposits() {
    final UInt64 maxEffectiveBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();

    // Not-active validator 1
    final Validator validator1 =
        dataStructureUtil
            .validatorBuilder()
            .activationEpoch(FAR_FUTURE_EPOCH)
            .activationEligibilityEpoch(UInt64.valueOf(1))
            .build();
    // Not-active validator 2
    final Validator validator2 =
        dataStructureUtil
            .validatorBuilder()
            .activationEpoch(FAR_FUTURE_EPOCH)
            .activationEligibilityEpoch(UInt64.valueOf(10))
            .build();
    // Not-active validator 3, with activation eligibility epoch lower than validator 2
    final Validator validator3 =
        dataStructureUtil
            .validatorBuilder()
            .activationEpoch(FAR_FUTURE_EPOCH)
            .activationEligibilityEpoch(UInt64.valueOf(5))
            .build();

    final BeaconStateDeneb preState =
        BeaconStateDeneb.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.DENEB, 3, 0)
                .validators(validator1, validator2, validator3)
                // All validators have their balance = maxEffectiveBalance
                .balances(maxEffectiveBalance, maxEffectiveBalance, maxEffectiveBalance)
                .build());

    final ElectraStateUpgrade upgrade =
        new ElectraStateUpgrade(
            SpecConfigElectra.required(spec.getGenesisSpecConfig()),
            schemaDefinitionsElectra,
            stateAccessorsElectra,
            stateMutatorsElectra);

    final BeaconStateElectra postState = upgrade.upgrade(preState);

    final SszList<PendingDeposit> pendingDeposits = postState.getPendingDeposits();
    assertThat(pendingDeposits.size()).isEqualTo(3);

    assertPendingDeposit(
        pendingDeposits.get(0), postState.getValidators().get(0), maxEffectiveBalance);
    // During Electra fork upgrade, pending balance deposits must be ordered by activation
    // eligibility epoch.
    // Because Validator3 (index = 2) activation eligibility epoch is lower than validator2,
    // Validator3's pending balance deposit must come before Validator2 (index = 1)
    assertPendingDeposit(
        pendingDeposits.get(1), postState.getValidators().get(2), maxEffectiveBalance);
    assertPendingDeposit(
        pendingDeposits.get(2), postState.getValidators().get(1), maxEffectiveBalance);
  }

  @Test
  public void shouldAddValidatorsWithCompoundingCredentialsExcessBalanceToPendingBalanceDeposits() {
    final UInt64 maxEffectiveBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();
    final UInt64 excessBalance = UInt64.valueOf(1_000);

    // Compounding validator with excess balance
    final Validator validator1 =
        dataStructureUtil
            .validatorBuilder()
            .activationEpoch(UInt64.ZERO)
            .withRandomCompoundingWithdrawalCredentials()
            .build();

    // Another compounding validator with excess balance
    final Validator validator2 =
        dataStructureUtil
            .validatorBuilder()
            .activationEpoch(UInt64.ZERO)
            .withRandomCompoundingWithdrawalCredentials()
            .build();

    final BeaconStateDeneb preState =
        BeaconStateDeneb.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.DENEB, 4, 0)
                .slot(UInt64.valueOf(100_000))
                .validators(validator1, validator2)
                // All validators have their balance = maxEffectiveBalance
                .balances(
                    maxEffectiveBalance.plus(excessBalance),
                    maxEffectiveBalance.plus(excessBalance))
                .build());

    final ElectraStateUpgrade upgrade =
        new ElectraStateUpgrade(
            SpecConfigElectra.required(spec.getGenesisSpecConfig()),
            schemaDefinitionsElectra,
            stateAccessorsElectra,
            stateMutatorsElectra);

    final BeaconStateElectra postState = upgrade.upgrade(preState);

    final SszList<PendingDeposit> pendingDeposits = postState.getPendingDeposits();
    assertThat(pendingDeposits.size()).isEqualTo(2);
    // Compounding validator will have a pending balance deposit only of the excess, in their index
    // order
    assertPendingDeposit(pendingDeposits.get(0), postState.getValidators().get(0), excessBalance);
    assertPendingDeposit(pendingDeposits.get(1), postState.getValidators().get(1), excessBalance);
  }

  private void assertPendingDeposit(
      final PendingDeposit pendingDeposit, final Validator validator, final UInt64 expectedAmount) {
    assertThat(pendingDeposit.getPublicKey()).isEqualTo(validator.getPublicKey());
    assertThat(pendingDeposit.getAmount()).isEqualTo(expectedAmount);
  }
}

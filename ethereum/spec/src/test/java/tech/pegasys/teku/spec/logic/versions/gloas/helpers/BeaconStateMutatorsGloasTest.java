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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateMutatorsGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigGloas configGloas = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas =
      BeaconStateAccessorsGloas.required(spec.getGenesisSpec().beaconStateAccessors());
  private final SchemaDefinitionsGloas schemaDefinitionsGloas =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());

  private BeaconStateMutatorsGloas stateMutatorsGloas;

  @BeforeEach
  public void setUp() {
    stateMutatorsGloas =
        BeaconStateMutatorsGloas.required(spec.getGenesisSpec().beaconStateMutators());
    // Sanity: the mutator wired by the spec must be the Gloas variant we are testing.
    assertThat(stateMutatorsGloas).isNotNull();
    assertThat(stateMutatorsGloas).isInstanceOf(BeaconStateMutators.class);
    assertThat(schemaDefinitionsGloas).isNotNull();
  }

  @Test
  public void computeExitEpochAndUpdateChurn_shouldUseUncappedExitChurnAboveActivationCap() {
    // 65 validators × 32 ETH → exit churn limit = 130 ETH (uncapped), well above the activation
    // cap of 128 ETH. A 32 ETH exit must consume 32 ETH from the 130 ETH-per-epoch budget without
    // pushing the earliest exit epoch beyond the freshly-computed activation-exit epoch.
    final BeaconStateElectra preState = activeStateWithValidators(65);
    final UInt64 perEpochExitChurn = beaconStateAccessorsGloas.getExitChurnLimit(preState);
    assertThat(perEpochExitChurn)
        .isGreaterThan(configGloas.getMaxPerEpochActivationChurnLimitGloas());

    final UInt64 expectedEarliestExitEpoch = computedActivationExitEpoch(preState);
    final UInt64 exitBalance = UInt64.THIRTY_TWO_ETH;

    final BeaconStateElectra postState =
        preState.updatedElectra(
            state -> stateMutatorsGloas.computeExitEpochAndUpdateChurn(state, exitBalance));

    assertThat(postState.getEarliestExitEpoch()).isEqualTo(expectedEarliestExitEpoch);
    assertThat(postState.getExitBalanceToConsume())
        .isEqualTo(perEpochExitChurn.minusMinZero(exitBalance));
  }

  @Test
  public void computeExitEpochAndUpdateChurn_shouldBumpEarliestExitEpochWhenBalanceExceedsBudget() {
    // 65 validators × 32 ETH → exit churn = 130 ETH/epoch. Exiting just under 2× the budget must
    // advance earliestExitEpoch by exactly 1 above the freshly-computed activation-exit epoch.
    final BeaconStateElectra preState = activeStateWithValidators(65);
    final UInt64 perEpochExitChurn = beaconStateAccessorsGloas.getExitChurnLimit(preState);
    final UInt64 expectedEarliestExitEpoch = computedActivationExitEpoch(preState);
    final UInt64 exitBalance = perEpochExitChurn.times(UInt64.valueOf(2)).minusMinZero(UInt64.ONE);

    final BeaconStateElectra postState =
        preState.updatedElectra(
            state -> stateMutatorsGloas.computeExitEpochAndUpdateChurn(state, exitBalance));

    assertThat(postState.getEarliestExitEpoch()).isEqualTo(expectedEarliestExitEpoch.plus(1));
  }

  @Test
  public void slashValidator_shouldClearEveryPendingPaymentProposedBySlashedValidator() {
    final int slotsPerEpoch = configGloas.getSlotsPerEpoch();
    final int slashedIndex = 3;
    final UInt64 otherProposer = UInt64.valueOf(7);
    // Payments for the slashed proposer in both the previous and current epoch halves, plus one
    // from another proposer in each half that must survive.
    final int previousEpochPayment = 1;
    final int currentEpochPayment = slotsPerEpoch + 2;
    final int otherPreviousEpochPayment = 4;
    final int otherCurrentEpochPayment = slotsPerEpoch + 5;
    final BuilderPendingPayment otherPayment = paymentWithProposer(otherProposer);

    final BeaconState preState =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(2L * slotsPerEpoch + 1))
            .updated(
                mutable -> {
                  final MutableBeaconStateGloas state = MutableBeaconStateGloas.required(mutable);
                  final BuilderPendingPayment slashedPayment =
                      paymentWithProposer(UInt64.valueOf(slashedIndex));
                  state.getBuilderPendingPayments().set(previousEpochPayment, slashedPayment);
                  state.getBuilderPendingPayments().set(currentEpochPayment, slashedPayment);
                  state.getBuilderPendingPayments().set(otherPreviousEpochPayment, otherPayment);
                  state.getBuilderPendingPayments().set(otherCurrentEpochPayment, otherPayment);
                });

    final BeaconStateGloas postState = slash(preState, slashedIndex);

    assertThat(postState.getValidators().get(slashedIndex).isSlashed()).isTrue();
    final BuilderPendingPayment emptyPayment =
        schemaDefinitionsGloas.getBuilderPendingPaymentSchema().getDefault();
    assertThat(postState.getBuilderPendingPayments().get(previousEpochPayment))
        .isEqualTo(emptyPayment);
    assertThat(postState.getBuilderPendingPayments().get(currentEpochPayment))
        .isEqualTo(emptyPayment);
    assertThat(postState.getBuilderPendingPayments().get(otherPreviousEpochPayment))
        .isEqualTo(otherPayment);
    assertThat(postState.getBuilderPendingPayments().get(otherCurrentEpochPayment))
        .isEqualTo(otherPayment);
  }

  @Test
  public void slashValidator_shouldKeepPaymentsWhenSlashedValidatorProposedNone() {
    final int slashedIndex = 3;
    final BuilderPendingPayment otherPayment = paymentWithProposer(UInt64.valueOf(7));
    final BeaconState preState =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(2L * configGloas.getSlotsPerEpoch() + 1))
            .updated(
                mutable ->
                    MutableBeaconStateGloas.required(mutable)
                        .getBuilderPendingPayments()
                        .set(0, otherPayment));
    final BeaconStateGloas preStateGloas = BeaconStateGloas.required(preState);

    final BeaconStateGloas postState = slash(preState, slashedIndex);

    assertThat(postState.getValidators().get(slashedIndex).isSlashed()).isTrue();
    assertThat(postState.getBuilderPendingPayments())
        .isEqualTo(preStateGloas.getBuilderPendingPayments());
  }

  private BeaconStateGloas slash(final BeaconState preState, final int slashedIndex) {
    return BeaconStateGloas.required(
        preState.updated(
            state ->
                stateMutatorsGloas.slashValidator(
                    state,
                    slashedIndex,
                    stateMutatorsGloas.createValidatorExitContextSupplier(state))));
  }

  private BuilderPendingPayment paymentWithProposer(final UInt64 proposerIndex) {
    return schemaDefinitionsGloas
        .getBuilderPendingPaymentSchema()
        .create(
            UInt64.valueOf(1000),
            dataStructureUtil.randomBuilderPendingWithdrawal(),
            proposerIndex);
  }

  private UInt64 computedActivationExitEpoch(final BeaconStateElectra state) {
    return spec.getGenesisSpec()
        .miscHelpers()
        .computeActivationExitEpoch(beaconStateAccessorsGloas.getCurrentEpoch(state));
  }

  private BeaconStateElectra activeStateWithValidators(final int validatorCount) {
    final BeaconStateTestBuilder builder = new BeaconStateTestBuilder(dataStructureUtil).slot(0);
    IntStream.range(0, validatorCount)
        .forEach(__ -> builder.activeValidator(UInt64.THIRTY_TWO_ETH));
    return BeaconStateElectra.required(builder.build());
  }
}

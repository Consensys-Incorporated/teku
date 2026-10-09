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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableVector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BeaconStateMutatorsGloas extends BeaconStateMutatorsElectra {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigGloas specConfigGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public static BeaconStateMutatorsGloas required(final BeaconStateMutators beaconStateMutators) {
    checkArgument(
        beaconStateMutators instanceof BeaconStateMutatorsGloas,
        "Expected %s but it was %s",
        BeaconStateMutatorsGloas.class,
        beaconStateMutators.getClass());
    return (BeaconStateMutatorsGloas) beaconStateMutators;
  }

  public BeaconStateMutatorsGloas(
      final SpecConfigGloas specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final SchemaDefinitionsGloas schemaDefinitions) {
    super(specConfig, miscHelpers, beaconStateAccessors, schemaDefinitions);
    this.specConfigGloas = specConfig;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  /**
   * compute_exit_epoch_and_update_churn
   *
   * <p>EIP-8061 (Gloas): exits use the uncapped {@code get_exit_churn_limit} instead of the
   * activation/exit churn shared in Electra.
   */
  @Override
  public UInt64 computeExitEpochAndUpdateChurn(
      final MutableBeaconStateElectra state, final UInt64 exitBalance) {
    final UInt64 earliestExitEpoch =
        miscHelpers
            .computeActivationExitEpoch(beaconStateAccessorsGloas.getCurrentEpoch(state))
            .max(state.getEarliestExitEpoch());
    final UInt64 perEpochChurn = beaconStateAccessorsGloas.getExitChurnLimit(state);
    final UInt64 exitBalanceToConsume =
        state.getEarliestExitEpoch().isLessThan(earliestExitEpoch)
            ? perEpochChurn
            : state.getExitBalanceToConsume();

    if (exitBalance.isGreaterThan(exitBalanceToConsume)) {
      final UInt64 balanceToProcess = exitBalance.minusMinZero(exitBalanceToConsume);
      final UInt64 additionalEpochs =
          balanceToProcess.minusMinZero(1).dividedBy(perEpochChurn).increment();
      state.setExitBalanceToConsume(
          exitBalanceToConsume
              .plus(additionalEpochs.times(perEpochChurn))
              .minusMinZero(exitBalance));
      state.setEarliestExitEpoch(earliestExitEpoch.plus(additionalEpochs));
    } else {
      state.setExitBalanceToConsume(exitBalanceToConsume.minusMinZero(exitBalance));
      state.setEarliestExitEpoch(earliestExitEpoch);
    }

    return state.getEarliestExitEpoch();
  }

  /**
   * slash_validator
   *
   * <p>Gloas also removes the pending builder payments for blocks proposed by the slashed
   * validator.
   */
  @Override
  public void slashValidator(
      final MutableBeaconState state,
      final int slashedIndex,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    super.slashValidator(state, slashedIndex, validatorExitContextSupplier);

    final SszMutableVector<BuilderPendingPayment> builderPendingPayments =
        MutableBeaconStateGloas.required(state).getBuilderPendingPayments();
    final UInt64 slashedValidatorIndex = UInt64.valueOf(slashedIndex);
    for (int paymentIndex = 0; paymentIndex < builderPendingPayments.size(); paymentIndex++) {
      final BuilderPendingPayment payment = builderPendingPayments.get(paymentIndex);
      if (payment.getProposerIndex().equals(slashedValidatorIndex)) {
        builderPendingPayments.set(paymentIndex, payment.getSchema().getDefault());
      }
    }
  }

  /**
   * initiate_builder_exit
   *
   * <p>Initiate the exit of the builder with index ``index``.
   */
  public void initiateBuilderExit(final MutableBeaconState state, final UInt64 builderIndex) {
    final SszMutableList<Builder> builders = MutableBeaconStateGloas.required(state).getBuilders();
    final Builder builder = builders.get(builderIndex.intValue());
    // Set builder exit epoch
    final UInt64 exitEpoch =
        beaconStateAccessorsGloas
            .getCurrentEpoch(state)
            .plus(specConfigGloas.getMinBuilderWithdrawabilityDelay());
    builders.set(builderIndex.intValue(), builder.copyWithNewWithdrawableEpoch(exitEpoch));
  }

  /** settle_builder_payment */
  public void settleBuilderPayment(final MutableBeaconState state, final UInt64 paymentIndex) {
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
    checkArgument(
        paymentIndex.isLessThan(stateGloas.getBuilderPendingPayments().size()),
        "Payment index out of bounds");
    final BuilderPendingPayment payment =
        stateGloas.getBuilderPendingPayments().get(paymentIndex.intValue());
    if (payment.getWithdrawal().getAmount().isGreaterThan(UInt64.ZERO)) {
      stateGloas.getBuilderPendingWithdrawals().append(payment.getWithdrawal());
    }
    stateGloas
        .getBuilderPendingPayments()
        .set(paymentIndex.intValue(), payment.getSchema().getDefault());
  }

  public void addBuilderToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final int version,
      final Eth1Address executionAddress,
      final UInt64 amount,
      final UInt64 slot) {
    final UInt64 index = beaconStateAccessorsGloas.getIndexForNewBuilder(state);
    final Builder builder =
        new Builder(
            pubkey,
            version,
            executionAddress,
            amount,
            miscHelpers.computeEpochAtSlot(slot),
            FAR_FUTURE_EPOCH);
    final SszMutableList<Builder> builders = MutableBeaconStateGloas.required(state).getBuilders();
    if (index.isGreaterThanOrEqualTo(builders.size())) {
      LOG.debug("Adding new builder with index {} to state", builders.size());
      builders.append(builder);
    } else {
      // The index is reassigned to a new builder, so updating the caches
      final TransitionCaches caches = BeaconStateCache.getTransitionCaches(state);
      caches.getBuildersPubKeys().invalidateWithNewValue(index, pubkey);
      caches.getBuilderIndexCache().invalidateWithNewValue(pubkey, index.intValue());
      // Remove the pubkey mapping for the old builder
      caches.getBuilderIndexCache().invalidate(builders.get(index.intValue()).getPublicKey());
      LOG.debug(
          "Adding new builder to an existing index {} (builder has exited) in the state",
          index.intValue());
      builders.set(index.intValue(), builder);
    }
  }
}

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

package tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszMutableVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.statetransition.epoch.EpochProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class EpochProcessorGloas extends EpochProcessorFulu {

  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;

  public EpochProcessorGloas(
      final SpecConfigGloas specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitionsGloas schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
    this.beaconStateAccessorsGloas = beaconStateAccessors;
    this.schemaDefinitionsGloas = schemaDefinitions;
  }

  /**
   * EIP-8061: deposits consume the activation-only churn budget; consolidation churn is now tracked
   * independently via {@code CONSOLIDATION_CHURN_LIMIT_QUOTIENT}.
   */
  @Override
  protected UInt64 getPendingDepositsChurnLimit(final MutableBeaconStateElectra state) {
    return beaconStateAccessorsGloas.getActivationChurnLimit(state);
  }

  /**
   * process_builder_pending_payments
   *
   * <p>Processes the builder pending payments from the previous epoch.
   */
  @Override
  public void processBuilderPendingPayments(final MutableBeaconState state) {
    final UInt64 quorum = beaconStateAccessorsGloas.getBuilderPaymentQuorumThreshold(state);
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
    final SszMutableVector<BuilderPendingPayment> builderPendingPayments =
        stateGloas.getBuilderPendingPayments();
    IntStream.range(0, specConfig.getSlotsPerEpoch())
        .forEach(
            i -> {
              final BuilderPendingPayment payment = builderPendingPayments.get(i);
              if (payment.getWeight().isGreaterThanOrEqualTo(quorum)) {
                stateGloas.getBuilderPendingWithdrawals().append(payment.getWithdrawal());
              }
            });
    final List<BuilderPendingPayment> oldPayments =
        new ArrayList<>(
            builderPendingPayments
                .asList()
                .subList(specConfig.getSlotsPerEpoch(), builderPendingPayments.size()));
    final List<BuilderPendingPayment> newPayments =
        Collections.nCopies(
            specConfig.getSlotsPerEpoch(),
            builderPendingPayments.getSchema().getElementSchema().getDefault());
    builderPendingPayments.setAll(Iterables.concat(oldPayments, newPayments));
  }

  /**
   * process_ptc_window
   *
   * <p>Update the cached PTC window.
   */
  @Override
  public void processPtcWindow(final MutableBeaconState state) {
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
    // Shift all epochs forward by one
    final List<SszUInt64Vector> ptcToShiftOut =
        stateGloas
            .getPtcWindow()
            .asList()
            .subList(specConfig.getSlotsPerEpoch(), stateGloas.getPtcWindow().size());
    // Fill in the last epoch
    final UInt64 nextEpoch =
        beaconStateAccessors.getCurrentEpoch(state).plus(specConfig.getMinSeedLookahead()).plus(1);
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(nextEpoch);
    final List<SszUInt64Vector> lastEpochPtcWindow =
        UInt64.range(startSlot, startSlot.plus(specConfig.getSlotsPerEpoch()))
            .map(slot -> beaconStateAccessorsGloas.computePtc(state, slot))
            .toList();
    final List<SszUInt64Vector> ptcWindow = new ArrayList<>(ptcToShiftOut);
    ptcWindow.addAll(lastEpochPtcWindow);

    stateGloas.setPtcWindow(
        schemaDefinitionsGloas.getPtcWindowSchema().createFromElements(ptcWindow));
  }
}

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

package tech.pegasys.teku.spec.logic.versions.pq.statetransition.epoch;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.MutableBeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/**
 * PQ epoch processing: no sync-committee updates; the aggregation-committee window and the
 * preregistration sweep follow the PTC window; pending deposits are matched by XMSS key and legacy
 * deposits create already-exited validators without consuming churn.
 */
public class EpochProcessorPq extends EpochProcessorGloas {

  private final SpecConfigPq specConfigPq;
  private final PredicatesPq predicatesPq;
  private final MiscHelpersPq miscHelpersPq;
  private final BeaconStateAccessorsPq beaconStateAccessorsPq;
  private final BeaconStateMutatorsPq beaconStateMutatorsPq;
  private final SchemaDefinitionsPq schemaDefinitionsPq;

  public EpochProcessorPq(
      final SpecConfigPq specConfig,
      final PredicatesPq predicates,
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final BeaconStateMutatorsPq beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitionsPq schemaDefinitions,
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
    this.specConfigPq = specConfig;
    this.predicatesPq = predicates;
    this.miscHelpersPq = miscHelpers;
    this.beaconStateAccessorsPq = beaconStateAccessors;
    this.beaconStateMutatorsPq = beaconStateMutators;
    this.schemaDefinitionsPq = schemaDefinitions;
  }

  /** PQ has no sync committees. */
  @Override
  public void processSyncCommitteeUpdates(final MutableBeaconState state) {}

  /**
   * process_epoch, modified in PQ: process_aggregation_committee_window and
   * process_preregistration_expiry run after process_ptc_window, the last Gloas step.
   */
  @Override
  public void processPtcWindow(final MutableBeaconState state) {
    super.processPtcWindow(state);
    processAggregationCommitteeWindow(state);
    processPreregistrationExpiry(state);
  }

  /** process_aggregation_committee_window: shift by an epoch and fill the last epoch. */
  public void processAggregationCommitteeWindow(final MutableBeaconState state) {
    final MutableBeaconStatePq statePq = MutableBeaconStatePq.required(state);
    final int slotsPerEpoch = specConfigPq.getSlotsPerEpoch();
    final List<SszList<SszList<SszUInt64>>> window =
        new ArrayList<>(statePq.getAggregationCommitteeWindow().asList());
    final List<SszList<SszList<SszUInt64>>> shifted =
        new ArrayList<>(window.subList(slotsPerEpoch, window.size()));
    final UInt64 nextEpoch =
        beaconStateAccessorsPq
            .getCurrentEpoch(state)
            .plus(specConfigPq.getMinSeedLookahead())
            .plus(1);
    final UInt64 startSlot = miscHelpersPq.computeStartSlotAtEpoch(nextEpoch);
    final SszVectorSchema<SszList<SszList<SszUInt64>>, ?> windowSchema =
        schemaDefinitionsPq.getPqBeaconStateSchema().getAggregationCommitteeWindowSchema();
    for (int i = 0; i < slotsPerEpoch; i++) {
      shifted.add(
          toAggregationCommittees(
              windowSchema,
              beaconStateAccessorsPq.computeAggregationCommittees(state, startSlot.plus(i))));
    }
    statePq.setAggregationCommitteeWindow(windowSchema.createFromElements(shifted));
  }

  /**
   * process_preregistration_expiry: garbage collection only, since bindings are enforced through
   * the activity checks.
   */
  public void processPreregistrationExpiry(final MutableBeaconState state) {
    final MutableBeaconStatePq statePq = MutableBeaconStatePq.required(state);
    statePq.setValidatorPreregistrations(
        statePq
            .getValidatorPreregistrations()
            .getSchema()
            .createFromElements(
                statePq.getValidatorPreregistrations().stream()
                    .filter(record -> predicatesPq.isActiveValidatorPreregistration(state, record))
                    .toList()));
    statePq.setBuilderPreregistrations(
        statePq
            .getBuilderPreregistrations()
            .getSchema()
            .createFromElements(
                statePq.getBuilderPreregistrations().stream()
                    .filter(record -> predicatesPq.isActiveBuilderPreregistration(state, record))
                    .toList()));
  }

  @Override
  protected Optional<Integer> getPendingDepositValidatorIndex(
      final BeaconState state, final PendingDeposit deposit) {
    return beaconStateAccessorsPq.getValidatorIndexByXmssKey(
        state, miscHelpersPq.computeDepositPubkey(deposit.getPubkeyBytes()));
  }

  /** A legacy deposit's balance never becomes active, so it consumes no churn. */
  @Override
  protected boolean appliesWithoutChurn(final PendingDeposit deposit) {
    return predicatesPq.isLegacyPubkey(deposit.getPubkeyBytes());
  }

  /**
   * apply_pending_deposit, modified in PQ: deposits carry no signature, so a new validator is not
   * verified; a legacy deposit's validator is exited at once and is withdrawable after
   * MIN_VALIDATOR_WITHDRAWABILITY_DELAY.
   */
  @Override
  public void applyPendingDeposits(final MutableBeaconState state, final PendingDeposit deposit) {
    final Bytes32 pubkey = miscHelpersPq.computeDepositPubkey(deposit.getPubkeyBytes());
    final Optional<Integer> existing =
        beaconStateAccessorsPq.getValidatorIndexByXmssKey(state, pubkey);
    if (existing.isPresent()) {
      beaconStateMutatorsPq.increaseBalance(state, existing.get(), deposit.getAmount());
      return;
    }
    final int index = state.getValidators().size();
    beaconStateMutatorsPq.addValidatorToRegistry(
        state,
        pubkey,
        miscHelpersPq.computeDepositVrfRoot(deposit),
        deposit.getWithdrawalCredentials(),
        deposit.getAmount());
    if (predicatesPq.isLegacyPubkey(deposit.getPubkeyBytes())) {
      final UInt64 epoch = beaconStateAccessorsPq.getCurrentEpoch(state);
      state
          .getValidators()
          .update(
              index,
              validator ->
                  validator
                      .withExitEpoch(epoch)
                      .withWithdrawableEpoch(
                          epoch.plus(specConfigPq.getMinValidatorWithdrawabilityDelay())));
    }
  }

  @SuppressWarnings("unchecked")
  private static SszList<SszList<SszUInt64>> toAggregationCommittees(
      final SszVectorSchema<SszList<SszList<SszUInt64>>, ?> windowSchema,
      final List<IntList> committees) {
    final SszListSchema<SszList<SszUInt64>, ?> committeesSchema =
        (SszListSchema<SszList<SszUInt64>, ?>) windowSchema.getElementSchema();
    final SszListSchema<SszUInt64, ?> committeeSchema =
        (SszListSchema<SszUInt64, ?>) committeesSchema.getElementSchema();
    return committeesSchema.createFromElements(
        committees.stream()
            .map(
                committee ->
                    (SszList<SszUInt64>)
                        committeeSchema.createFromElements(
                            committee
                                .intStream()
                                .mapToObj(index -> SszUInt64.of(UInt64.valueOf(index)))
                                .toList()))
            .toList());
  }
}

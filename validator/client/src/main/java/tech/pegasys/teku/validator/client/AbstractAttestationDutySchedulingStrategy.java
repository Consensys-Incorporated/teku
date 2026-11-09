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

package tech.pegasys.teku.validator.client;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

abstract class AbstractAttestationDutySchedulingStrategy
    implements AttestationDutySchedulingStrategy {

  private static final Logger LOG = LogManager.getLogger();

  protected final Spec spec;
  private final ForkProvider forkProvider;
  private final Function<
          Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
      scheduledDutiesFactory;
  protected final OwnedValidators validators;
  protected final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions;

  AbstractAttestationDutySchedulingStrategy(
      final Spec spec,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions) {
    this.spec = spec;
    this.forkProvider = forkProvider;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.validators = validators;
    this.beaconCommitteeSubscriptions = beaconCommitteeSubscriptions;
  }

  protected SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> getScheduledDuties(
      final AttesterDuties duties) {
    return scheduledDutiesFactory.apply(duties.getDependentRoot());
  }

  protected SafeFuture<Void> scheduleDuties(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final List<AttesterDuty> duties,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregations) {
    return SafeFuture.allOf(
        duties.stream()
            .map(duty -> scheduleDuty(scheduledDuties, duty, dvtAttestationAggregations))
            .toArray(SafeFuture[]::new));
  }

  protected SafeFuture<Void> scheduleDuty(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final AttesterDuty duty,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregations) {
    final Optional<Validator> maybeValidator = validators.getValidator(duty.getPublicKey());
    if (maybeValidator.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final Validator validator = maybeValidator.get();
    final int aggregatorModulo =
        spec.atSlot(duty.getSlot())
            .getValidatorsUtil()
            .getAggregatorModulo(duty.getCommitteeLength());

    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        scheduleAttestationProduction(
            scheduledDuties,
            duty.getCommitteeIndex(),
            duty.getValidatorCommitteeIndex(),
            duty.getCommitteeLength(),
            duty.getValidatorIndex(),
            validator,
            duty.getSlot());

    return scheduleAggregation(
        scheduledDuties,
        duty.getCommitteeIndex(),
        duty.getCommitteesAtSlot(),
        duty.getValidatorIndex(),
        validator,
        duty.getSlot(),
        aggregatorModulo,
        unsignedAttestationFuture,
        dvtAttestationAggregations);
  }

  protected SafeFuture<Optional<AttestationData>> scheduleAttestationProduction(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int attestationCommitteeSize,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot) {
    return scheduledDuties.scheduleProduction(
        slot,
        validator,
        duty ->
            duty.addValidator(
                validator,
                attestationCommitteeIndex,
                attestationCommitteePosition,
                validatorIndex,
                attestationCommitteeSize));
  }

  protected SafeFuture<Void> scheduleAggregation(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final int attestationCommitteeIndex,
      final int committeesAtSlot,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot,
      final int aggregatorModulo,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture,
      final Optional<DvtAttestationAggregations> dvtAttestationAggregations) {
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> validator.getSigner().signAggregationSlot(slot, forkInfo))
        .thenCompose(
            slotSignature ->
                dvtAttestationAggregations
                    .map(
                        dvt ->
                            dvt.getCombinedSelectionProofFuture(
                                validatorIndex, slot, slotSignature))
                    .orElse(SafeFuture.completedFuture(slotSignature)))
        .thenAccept(
            slotSignature -> {
              final SpecVersion specVersion = spec.atSlot(slot);
              final boolean isAggregator =
                  specVersion.getValidatorsUtil().isAggregator(slotSignature, aggregatorModulo);
              beaconCommitteeSubscriptions.subscribeToBeaconCommittee(
                  new CommitteeSubscriptionRequest(
                      validatorIndex,
                      attestationCommitteeIndex,
                      UInt64.valueOf(committeesAtSlot),
                      slot,
                      isAggregator));
              if (isAggregator) {
                scheduledDuties.scheduleAggregation(
                    slot,
                    validator,
                    duty ->
                        duty.addValidator(
                            validator,
                            validatorIndex,
                            slotSignature,
                            attestationCommitteeIndex,
                            unsignedAttestationFuture));
              }
            })
        .exceptionally(
            error -> {
              if (Throwables.getRootCause(error) instanceof CancellationException) {
                LOG.debug("DVT attestation aggregation cancelled for slot {}", slot);
              } else {
                LOG.error("Failed to schedule aggregation duties", error);
              }
              return null;
            });
  }
}

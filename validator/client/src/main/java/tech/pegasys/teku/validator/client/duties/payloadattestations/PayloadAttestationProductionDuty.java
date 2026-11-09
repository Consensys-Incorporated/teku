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

package tech.pegasys.teku.validator.client.duties.payloadattestations;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.CREATE_TOTAL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.SendingStrategy;

public class PayloadAttestationProductionDuty implements Duty {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final SendingStrategy<PayloadAttestationMessage> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  private final List<ValidatorWithIndex> validators = new ArrayList<>();

  public PayloadAttestationProductionDuty(
      final Spec spec,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final SendingStrategy<PayloadAttestationMessage> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.spec = spec;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  @Override
  public DutyType getType() {
    return DutyType.PAYLOAD_ATTESTATION_PRODUCTION;
  }

  public void addValidator(final Validator validator, final UInt64 index) {
    validators.add(new ValidatorWithIndex(validator, index));
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating payload attestations at slot {}", slot);

    if (validators.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }

    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                sendingStrategy.send(produceAllPayloadAttestations(slot, forkInfo, validators)));
  }

  private Stream<SafeFuture<ProductionResult<PayloadAttestationMessage>>>
      produceAllPayloadAttestations(
          final UInt64 slot,
          final ForkInfo forkInfo,
          final List<ValidatorWithIndex> validatorsWithIndex) {

    final SafeFuture<Optional<PayloadAttestationData>> unsignedPayloadAttestationDataFuture =
        validatorDutyMetrics.record(
            () -> validatorApiChannel.createPayloadAttestationData(slot), this, CREATE_TOTAL);

    return validatorsWithIndex.stream()
        .map(
            validatorWithIndex ->
                signPayloadAttestationForValidator(
                    slot, forkInfo, validatorWithIndex, unsignedPayloadAttestationDataFuture));
  }

  private SafeFuture<ProductionResult<PayloadAttestationMessage>>
      signPayloadAttestationForValidator(
          final UInt64 slot,
          final ForkInfo forkInfo,
          final ValidatorWithIndex validatorWithIndex,
          final SafeFuture<Optional<PayloadAttestationData>> unsignedPayloadAttestationDataFuture) {
    return unsignedPayloadAttestationDataFuture
        .thenCompose(
            maybeUnsignedPayloadAttestationData ->
                maybeUnsignedPayloadAttestationData
                    .map(
                        payloadAttestationData -> {
                          validatePayloadAttestationData(slot, payloadAttestationData);
                          return validatorDutyMetrics.record(
                              () ->
                                  signPayloadAttestationForValidator(
                                      forkInfo, payloadAttestationData, validatorWithIndex),
                              this,
                              ValidatorDutyMetricsSteps.SIGN);
                        })
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                ProductionResult.noop(
                                    validatorWithIndex.validator.getPublicKey()))))
        .exceptionally(
            error -> ProductionResult.failure(validatorWithIndex.validator.getPublicKey(), error));
  }

  private static void validatePayloadAttestationData(
      final UInt64 slot, final PayloadAttestationData payloadAttestationData) {
    checkArgument(
        payloadAttestationData.getSlot().equals(slot),
        "Unsigned payload attestation slot (%s) does not match expected slot %s",
        payloadAttestationData.getSlot(),
        slot);
  }

  private SafeFuture<ProductionResult<PayloadAttestationMessage>>
      signPayloadAttestationForValidator(
          final ForkInfo forkInfo,
          final PayloadAttestationData payloadAttestationData,
          final ValidatorWithIndex validatorWithIndex) {
    return validatorWithIndex
        .validator
        .getSigner()
        .signPayloadAttestationData(payloadAttestationData, forkInfo)
        .thenApply(
            signature ->
                createSignedPayloadAttestation(
                    payloadAttestationData, validatorWithIndex, signature))
        .thenApply(
            attestation ->
                ProductionResult.success(
                    validatorWithIndex.validator.getPublicKey(),
                    payloadAttestationData.getBeaconBlockRoot(),
                    attestation));
  }

  private PayloadAttestationMessage createSignedPayloadAttestation(
      final PayloadAttestationData data,
      final ValidatorWithIndex validatorWithIndex,
      final BLSSignature signature) {
    final PayloadAttestationMessageSchema payloadAttestationMessageSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(data.getSlot()).getSchemaDefinitions())
            .getPayloadAttestationMessageSchema();

    return payloadAttestationMessageSchema.create(validatorWithIndex.index, data, signature);
  }

  private record ValidatorWithIndex(Validator validator, UInt64 index) {}
}

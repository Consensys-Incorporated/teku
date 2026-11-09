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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;

@TestSpecContext(milestone = {GLOAS})
class PayloadAttestationProductionDutyTest {

  private static final String TYPE = "payload_attestation";
  private static final UInt64 SLOT = UInt64.valueOf(42);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ForkInfo fork;

  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final ValidatorDutyMetrics validatorDutyMetrics =
      spy(ValidatorDutyMetrics.create(new StubMetricsSystem()));

  private PayloadAttestationProductionDuty duty;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    fork = dataStructureUtil.randomForkInfo();

    duty =
        new PayloadAttestationProductionDuty(
            spec,
            SLOT,
            forkProvider,
            validatorApiChannel,
            new BatchAttestationSendingStrategy<>(
                validatorApiChannel::sendPayloadAttestationMessages),
            validatorDutyMetrics);

    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendPayloadAttestationMessages(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
  }

  @TestTemplate
  public void shouldNotProduceAnyPayloadAttestationsWhenNoValidatorsAdded() {
    performAndReportDuty();

    verifyNoInteractions(validatorApiChannel, validatorLogger, validatorDutyMetrics);
  }

  @TestTemplate
  void shouldProducePayloadAttestationMessage() {
    final Validator validator = createValidator();
    final UInt64 validatorIndex = UInt64.valueOf(23);
    final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData(SLOT);
    final BLSSignature signature = dataStructureUtil.randomSignature();

    duty.addValidator(validator, validatorIndex);

    when(validatorApiChannel.createPayloadAttestationData(SLOT))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data)));
    when(validator.getSigner().signPayloadAttestationData(data, fork))
        .thenReturn(SafeFuture.completedFuture(signature));

    performAndReportDuty();

    verify(validatorApiChannel)
        .sendPayloadAttestationMessages(
            List.of(createExpectedMessage(data, validatorIndex, signature)));
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(data.getBeaconBlockRoot()), Optional.empty());
  }

  @TestTemplate
  void shouldTreatEmptyPayloadAttestationDataFutureAsNoop() {
    final Validator validator = createValidator();
    final UInt64 validatorIndex = UInt64.valueOf(23);

    duty.addValidator(validator, validatorIndex);

    when(validatorApiChannel.createPayloadAttestationData(SLOT))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    performAndReportDuty();

    verify(validatorApiChannel).createPayloadAttestationData(SLOT);
    verifyNoMoreInteractions(validatorApiChannel);
    verifyNoInteractions(validator.getSigner());
    verifyNoInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldFailWhenPayloadAttestationDataFutureIsExceptional() {
    final Validator validator = createValidator();
    final UInt64 validatorIndex = UInt64.valueOf(23);
    final RuntimeException failure = new RuntimeException("Unable to create payload attestation");

    duty.addValidator(validator, validatorIndex);

    when(validatorApiChannel.createPayloadAttestationData(SLOT)).thenReturn(failedFuture(failure));

    performAndReportDuty();

    verify(validatorApiChannel).createPayloadAttestationData(SLOT);
    verifyNoMoreInteractions(validatorApiChannel);
    verifyNoInteractions(validator.getSigner());
    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator.getPublicKey().toAbbreviatedString()), failure);
    verifyNoMoreInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldProduceOnePayloadAttestationMessageForEachValidator() {
    final UInt64 validatorIndex1 = UInt64.valueOf(11);
    final UInt64 validatorIndex2 = UInt64.valueOf(22);
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData(SLOT);
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();

    duty.addValidator(validator1, validatorIndex1);
    duty.addValidator(validator2, validatorIndex2);

    when(validatorApiChannel.createPayloadAttestationData(SLOT))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data)));
    when(validator1.getSigner().signPayloadAttestationData(data, fork))
        .thenReturn(SafeFuture.completedFuture(signature1));
    when(validator2.getSigner().signPayloadAttestationData(data, fork))
        .thenReturn(SafeFuture.completedFuture(signature2));

    performAndReportDuty();

    verify(validatorApiChannel)
        .sendPayloadAttestationMessages(
            List.of(
                createExpectedMessage(data, validatorIndex1, signature1),
                createExpectedMessage(data, validatorIndex2, signature2)));
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 2, Set.of(data.getBeaconBlockRoot()), Optional.empty());
  }

  @TestTemplate
  void shouldReportPartialFailureWhenBeaconNodeRejectsSomePayloadAttestationMessages() {
    final UInt64 validatorIndex1 = UInt64.valueOf(11);
    final UInt64 validatorIndex2 = UInt64.valueOf(22);
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData(SLOT);
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();

    duty.addValidator(validator1, validatorIndex1);
    duty.addValidator(validator2, validatorIndex2);

    when(validatorApiChannel.createPayloadAttestationData(SLOT))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data)));
    when(validator1.getSigner().signPayloadAttestationData(data, fork))
        .thenReturn(SafeFuture.completedFuture(signature1));
    when(validator2.getSigner().signPayloadAttestationData(data, fork))
        .thenReturn(SafeFuture.completedFuture(signature2));

    when(validatorApiChannel.sendPayloadAttestationMessages(any()))
        .thenReturn(
            SafeFuture.completedFuture(List.of(new SubmitDataError(UInt64.ZERO, "API Rejected"))));

    performAndReportDuty();

    verify(validatorApiChannel)
        .sendPayloadAttestationMessages(
            List.of(
                createExpectedMessage(data, validatorIndex1, signature1),
                createExpectedMessage(data, validatorIndex2, signature2)));
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(data.getBeaconBlockRoot()), Optional.empty());
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator1.getPublicKey().toAbbreviatedString())),
            argThat(error -> error.getMessage().equals("API Rejected")));
  }

  private void performAndReportDuty() {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    safeJoin(result).report(TYPE, SLOT, validatorLogger);
  }

  private Validator createValidator() {
    final Signer signer = mock(Signer.class);
    return new Validator(
        dataStructureUtil.randomPublicKey(), signer, new FileBackedGraffitiProvider());
  }

  private PayloadAttestationMessage createExpectedMessage(
      final PayloadAttestationData data,
      final UInt64 validatorIndex,
      final BLSSignature signature) {
    return SchemaDefinitionsGloas.required(spec.atSlot(data.getSlot()).getSchemaDefinitions())
        .getPayloadAttestationMessageSchema()
        .create(validatorIndex, data, signature);
  }
}

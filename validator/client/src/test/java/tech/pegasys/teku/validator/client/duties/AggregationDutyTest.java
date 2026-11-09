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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AggregatorsGroupedByCommittee;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;
import tech.pegasys.teku.validator.client.duties.attestations.UngroupedAggregators;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
class AggregationDutyTest {

  private static final String TYPE = "aggregate";
  private static final UInt64 SLOT = UInt64.valueOf(2832);

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final Signer signer1 = mock(Signer.class);
  private final Signer signer2 = mock(Signer.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final ValidatorDutyMetrics validatorDutyMetrics =
      spy(ValidatorDutyMetrics.create(new StubMetricsSystem()));

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private AggregateAndProofSchema aggregateAndProofSchema;
  private SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private ForkInfo forkInfo;
  private Validator validator1;
  private Validator validator2;

  private AggregationDuty duty;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    aggregateAndProofSchema = spec.getGenesisSchemaDefinitions().getAggregateAndProofSchema();
    signedAggregateAndProofSchema =
        spec.getGenesisSchemaDefinitions().getSignedAggregateAndProofSchema();
    forkInfo = dataStructureUtil.randomForkInfo();

    validator1 =
        new Validator(
            dataStructureUtil.randomPublicKey(), signer1, new FileBackedGraffitiProvider());
    validator2 =
        new Validator(
            dataStructureUtil.randomPublicKey(), signer2, new FileBackedGraffitiProvider());

    duty =
        new AggregationDuty(
            spec,
            SLOT,
            validatorApiChannel,
            new AggregatorsGroupedByCommittee(),
            forkProvider,
            validatorLogger,
            new BatchAttestationSendingStrategy<>(validatorApiChannel::sendAggregateAndProofs),
            validatorDutyMetrics);

    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.completedFuture(forkInfo));
    when(validatorApiChannel.sendAggregateAndProofs(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
  }

  @TestTemplate
  public void shouldBeCompleteWhenNoValidatorsAdded() {
    performAndReportDuty();
    verifyNoInteractions(validatorApiChannel, validatorLogger);
  }

  @TestTemplate
  public void shouldProduceAggregateAndProof() {
    final int validatorIndex = 1;
    final int attestationCommitteeIndex = 2;
    final BLSSignature proof = dataStructureUtil.randomSignature();
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validatorIndex,
        proof,
        attestationCommitteeIndex,
        completedFuture(Optional.of(attestationData)));

    when(validatorApiChannel.createAggregate(
            SLOT,
            attestationData.hashTreeRoot(),
            Optional.of(UInt64.valueOf(attestationCommitteeIndex))))
        .thenReturn(completedFuture(Optional.of(aggregate)));

    final AggregateAndProof expectedAggregateAndProof =
        aggregateAndProofSchema.create(UInt64.valueOf(validatorIndex), aggregate, proof);
    final BLSSignature aggregateSignature = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(expectedAggregateAndProof, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature));

    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendAggregateAndProofs(
            List.of(
                signedAggregateAndProofSchema.create(
                    expectedAggregateAndProof, aggregateSignature)));

    verify(validatorDutyMetrics)
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics)
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
  }

  @SuppressWarnings("unchecked")
  @TestTemplate
  public void shouldProduceAggregateAndProofForMultipleCommittees() {
    final int validator1Index = 1;
    final int validator1CommitteeIndex = 2;
    final BLSSignature validator1Proof = dataStructureUtil.randomSignature();

    final int validator2Index = 6;
    final int validator2CommitteeIndex = 0;
    final BLSSignature validator2Proof = dataStructureUtil.randomSignature();

    final AttestationData committee1AttestationData = dataStructureUtil.randomAttestationData();
    final AttestationData committee2AttestationData = dataStructureUtil.randomAttestationData();
    final Attestation committee1Aggregate = dataStructureUtil.randomAttestation();
    final Attestation committee2Aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        validator1CommitteeIndex,
        completedFuture(Optional.of(committee1AttestationData)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        validator2CommitteeIndex,
        completedFuture(Optional.of(committee2AttestationData)));

    when(validatorApiChannel.createAggregate(
            SLOT,
            committee1AttestationData.hashTreeRoot(),
            Optional.of(UInt64.valueOf(validator1CommitteeIndex))))
        .thenReturn(completedFuture(Optional.of(committee1Aggregate)));
    when(validatorApiChannel.createAggregate(
            SLOT,
            committee2AttestationData.hashTreeRoot(),
            Optional.of(UInt64.valueOf(validator2CommitteeIndex))))
        .thenReturn(completedFuture(Optional.of(committee2Aggregate)));

    final AggregateAndProof aggregateAndProof1 =
        aggregateAndProofSchema.create(
            UInt64.valueOf(validator1Index), committee1Aggregate, validator1Proof);
    final AggregateAndProof aggregateAndProof2 =
        aggregateAndProofSchema.create(
            UInt64.valueOf(validator2Index), committee2Aggregate, validator2Proof);
    final BLSSignature aggregateSignature1 = dataStructureUtil.randomSignature();
    final BLSSignature aggregateSignature2 = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(aggregateAndProof1, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature1));
    when(signer2.signAggregateAndProof(aggregateAndProof2, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature2));

    assertThat(duty.performDuty()).isCompleted();

    ArgumentCaptor<List<SignedAggregateAndProof>> argumentCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(validatorApiChannel).sendAggregateAndProofs(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue())
        .containsExactlyInAnyOrder(
            signedAggregateAndProofSchema.create(aggregateAndProof1, aggregateSignature1),
            signedAggregateAndProofSchema.create(aggregateAndProof2, aggregateSignature2));

    // Metrics were recorded for the aggregation for each committee
    verify(validatorDutyMetrics, times(2))
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics, times(2))
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
  }

  @TestTemplate
  public void shouldProduceSingleAggregateAndProofWhenMultipleValidatorsAggregateSameCommittee() {
    final int committeeIndex = 2;

    final int validator1Index = 1;
    final BLSSignature validator1Proof = dataStructureUtil.randomSignature();

    final int validator2Index = 6;
    final BLSSignature validator2Proof = dataStructureUtil.randomSignature();

    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        committeeIndex,
        completedFuture(Optional.of(attestationData)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        committeeIndex,
        completedFuture(Optional.of(attestationData)));

    when(validatorApiChannel.createAggregate(
            SLOT, attestationData.hashTreeRoot(), Optional.of(UInt64.valueOf(committeeIndex))))
        .thenReturn(completedFuture(Optional.of(aggregate)));
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final AggregateAndProof aggregateAndProof =
        aggregateAndProofSchema.create(UInt64.valueOf(validator1Index), aggregate, validator1Proof);
    final BLSSignature aggregateSignature1 = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(aggregateAndProof, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature1));

    performAndReportDuty();

    verify(validatorApiChannel)
        .sendAggregateAndProofs(
            List.of(signedAggregateAndProofSchema.create(aggregateAndProof, aggregateSignature1)));
    // Only one proof should be sent.
    verify(validatorApiChannel, times(1)).sendAggregateAndProofs(anyList());
    verify(validatorLogger)
        .dutyCompleted(
            TYPE, SLOT, 1, Set.of(aggregate.getData().getBeaconBlockRoot()), Optional.empty());
    verifyNoMoreInteractions(validatorLogger);

    // Only one aggregation was created, so we only capture the time for a single operation
    verify(validatorDutyMetrics)
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics)
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
  }

  @TestTemplate
  public void shouldProduceAllAggregatesForSameCommitteeUsingUngroupedAggregators() {
    duty =
        new AggregationDuty(
            spec,
            SLOT,
            validatorApiChannel,
            new UngroupedAggregators(),
            forkProvider,
            validatorLogger,
            new BatchAttestationSendingStrategy<>(validatorApiChannel::sendAggregateAndProofs),
            validatorDutyMetrics);

    final int committeeIndex = 2;

    final int validator1Index = 1;
    final BLSSignature validator1Proof = dataStructureUtil.randomSignature();

    final int validator2Index = 6;
    final BLSSignature validator2Proof = dataStructureUtil.randomSignature();

    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation aggregate = dataStructureUtil.randomAttestation();
    duty.addValidator(
        validator1,
        validator1Index,
        validator1Proof,
        committeeIndex,
        completedFuture(Optional.of(attestationData)));
    duty.addValidator(
        validator2,
        validator2Index,
        validator2Proof,
        committeeIndex,
        completedFuture(Optional.of(attestationData)));

    when(validatorApiChannel.createAggregate(
            SLOT, attestationData.hashTreeRoot(), Optional.of(UInt64.valueOf(committeeIndex))))
        .thenReturn(completedFuture(Optional.of(aggregate)));
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final AggregateAndProof aggregateAndProof1 =
        aggregateAndProofSchema.create(UInt64.valueOf(validator1Index), aggregate, validator1Proof);
    final AggregateAndProof aggregateAndProof2 =
        aggregateAndProofSchema.create(UInt64.valueOf(validator2Index), aggregate, validator2Proof);
    final BLSSignature aggregateSignature1 = dataStructureUtil.randomSignature();
    final BLSSignature aggregateSignature2 = dataStructureUtil.randomSignature();
    when(signer1.signAggregateAndProof(aggregateAndProof1, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature1));
    when(signer2.signAggregateAndProof(aggregateAndProof2, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregateSignature2));

    performAndReportDuty();

    // Two proofs should be sent. Both proofs are sent together so there is only one call to
    // sendAggregateAndProofs.
    verify(validatorApiChannel)
        .sendAggregateAndProofs(
            List.of(
                signedAggregateAndProofSchema.create(aggregateAndProof1, aggregateSignature1),
                signedAggregateAndProofSchema.create(aggregateAndProof2, aggregateSignature2)));
    // Duty is completed with two aggregates (for the same slot/committee). So we check success
    // count is 2
    verify(validatorLogger)
        .dutyCompleted(
            TYPE, SLOT, 2, Set.of(aggregate.getData().getBeaconBlockRoot()), Optional.empty());
    verifyNoMoreInteractions(validatorLogger);

    // Two aggregation were created, so we capture the time for both individually
    verify(validatorDutyMetrics, times(2))
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics, times(2))
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
  }

  @TestTemplate
  public void shouldFailWhenAttestationDataNotCreated() {
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), 2, completedFuture(Optional.empty()));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator1.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);

    verifyNoInteractions(validatorDutyMetrics);
  }

  @TestTemplate
  public void shouldFailWhenAttestationDataCompletesExceptionally() {
    final RuntimeException exception = new RuntimeException("Doh!");
    duty.addValidator(
        validator1, 1, dataStructureUtil.randomSignature(), 2, failedFuture(exception));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator1.getPublicKey().toAbbreviatedString()), exception);
    verifyNoMoreInteractions(validatorLogger);

    verifyNoInteractions(validatorDutyMetrics);
  }

  @TestTemplate
  public void shouldReportWhenAggregateNotCreated() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    duty.addValidator(
        validator1,
        1,
        dataStructureUtil.randomSignature(),
        2,
        completedFuture(Optional.of(attestationData)));
    when(validatorApiChannel.createAggregate(
            SLOT, attestationData.hashTreeRoot(), Optional.of(UInt64.valueOf(2))))
        .thenReturn(completedFuture(Optional.empty()));

    assertThat(duty.performDuty()).isCompleted();
    verify(validatorApiChannel, never()).sendAggregateAndProofs(anyList());
    verify(validatorLogger).aggregationSkipped(SLOT, UInt64.valueOf(2));
    verifyNoMoreInteractions(validatorLogger);

    // Even when we fail creating the aggregation, we capture the time taken
    verify(validatorDutyMetrics)
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    // Because it failed creating the aggregation, we won't have a sign step time
    verifyNoMoreInteractions(validatorDutyMetrics);
  }

  @TestTemplate
  public void shouldFailWhenAggregateFails() {
    final Exception exception = new RuntimeException("Whoops");
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    duty.addValidator(
        validator1,
        1,
        dataStructureUtil.randomSignature(),
        2,
        completedFuture(Optional.of(attestationData)));
    when(validatorApiChannel.createAggregate(
            SLOT, attestationData.hashTreeRoot(), Optional.of(UInt64.valueOf(2))))
        .thenReturn(failedFuture(exception));

    performAndReportDuty();
    verify(validatorApiChannel, never()).sendAggregateAndProofs(anyList());
    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator1.getPublicKey().toAbbreviatedString()), exception);
    verifyNoMoreInteractions(validatorLogger);

    // Even when we fail creating the aggregation, we capture the time taken
    verify(validatorDutyMetrics)
        .record(any(), any(AggregationDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    // Because it failed creating the aggregation, we won't have a sign step time
    verifyNoMoreInteractions(validatorDutyMetrics);
  }

  private void performAndReportDuty() {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    safeJoin(result).report(TYPE, SLOT, validatorLogger);
  }
}

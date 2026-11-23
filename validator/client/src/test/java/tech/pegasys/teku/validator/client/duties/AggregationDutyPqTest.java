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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AggregatorsGroupedByCommittee;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;
import tech.pegasys.teku.xmss.XmssSignature;

/** From PQ, an aggregator signs the beacon node's aggregate and proof with leanXMSS. */
class AggregationDutyPqTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final PqAttestationSchemas schemas =
      SchemaDefinitionsPq.required(spec.getGenesisSchemaDefinitions()).getPqAttestationSchemas();
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final XmssSigner xmssSigner = mock(XmssSigner.class);
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          mock(Signer.class),
          Optional::empty,
          false,
          Optional.of(xmssSigner));

  private AggregationDuty duty;

  @BeforeEach
  void setUp() {
    duty =
        new AggregationDuty(
            spec,
            SLOT,
            validatorApiChannel,
            new AggregatorsGroupedByCommittee(),
            forkProvider,
            validatorLogger,
            new BatchAttestationSendingStrategy<>(validatorApiChannel::sendAggregateAndProofs),
            ValidatorDutyMetrics.create(new StubMetricsSystem()));
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendPqAggregateAndProofs(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
  }

  @Test
  void shouldSignAggregateWithProofWithXmssAndPublishIt() {
    final AttestationPq aggregate = (AttestationPq) dataStructureUtil.randomAttestation();
    final AttestationData attestationData = aggregate.getData();
    final Bytes proof = dataStructureUtil.randomBytes(64);
    final XmssSignature signature =
        XmssSignature.fromBytes(dataStructureUtil.randomXmssSignature().getBytes());
    when(validatorApiChannel.createPqAggregate(
            SLOT, attestationData.hashTreeRoot(), UInt64.valueOf(2)))
        .thenReturn(
            completedFuture(
                Optional.of(schemas.getAttestationAndProofSchema().create(aggregate, proof))));
    final AggregateAndProofPq aggregateAndProof =
        schemas.getAggregateAndProofSchema().create(UInt64.valueOf(7), aggregate, proof);
    when(xmssSigner.signAggregateAndProof(aggregateAndProof, fork))
        .thenReturn(completedFuture(signature));

    duty.addValidator(
        validator, 7, BLSSignature.empty(), 2, completedFuture(Optional.of(attestationData)));
    assertThat(duty.performDuty()).isCompleted();

    verify(validatorApiChannel)
        .sendPqAggregateAndProofs(
            List.of(
                schemas
                    .getSignedAggregateAndProofSchema()
                    .create(aggregateAndProof, signature.toBytes())));
  }

  @Test
  void shouldPublishEachAggregateOnceSigned() {
    final XmssSigner otherSigner = mock(XmssSigner.class);
    final Validator otherValidator =
        new Validator(
            dataStructureUtil.randomPublicKey(),
            mock(Signer.class),
            Optional::empty,
            false,
            Optional.of(otherSigner));
    final SafeFuture<XmssSignature> signing = new SafeFuture<>();
    final SafeFuture<XmssSignature> otherSigning = new SafeFuture<>();
    final SignedAggregateAndProofPq signed = prepareAggregate(xmssSigner, 7, 2, signing);
    final SignedAggregateAndProofPq otherSigned = prepareAggregate(otherSigner, 8, 3, otherSigning);
    duty.addValidator(
        validator,
        7,
        BLSSignature.empty(),
        2,
        completedFuture(Optional.of(signed.getMessage().getAggregate().getData())));
    duty.addValidator(
        otherValidator,
        8,
        BLSSignature.empty(),
        3,
        completedFuture(Optional.of(otherSigned.getMessage().getAggregate().getData())));

    final SafeFuture<DutyResult> result = duty.performDuty();
    otherSigning.complete(XmssSignature.fromBytes(otherSigned.getSignature()));

    // The second is published without waiting for the first
    verify(validatorApiChannel).sendPqAggregateAndProofs(List.of(otherSigned));
    verify(validatorApiChannel, never()).sendPqAggregateAndProofs(List.of(signed));
    assertThat(result).isNotDone();

    signing.complete(XmssSignature.fromBytes(signed.getSignature()));
    verify(validatorApiChannel).sendPqAggregateAndProofs(List.of(signed));
    assertThat(result).isCompleted();
  }

  /** Mocks the beacon node's aggregate for the committee, and its signing with {@code signer}. */
  private SignedAggregateAndProofPq prepareAggregate(
      final XmssSigner signer,
      final int aggregatorIndex,
      final int committeeIndex,
      final SafeFuture<XmssSignature> signing) {
    final AttestationPq aggregate = (AttestationPq) dataStructureUtil.randomAttestation();
    final Bytes proof = dataStructureUtil.randomBytes(64);
    when(validatorApiChannel.createPqAggregate(
            SLOT, aggregate.getData().hashTreeRoot(), UInt64.valueOf(committeeIndex)))
        .thenReturn(
            completedFuture(
                Optional.of(schemas.getAttestationAndProofSchema().create(aggregate, proof))));
    final AggregateAndProofPq aggregateAndProof =
        schemas
            .getAggregateAndProofSchema()
            .create(UInt64.valueOf(aggregatorIndex), aggregate, proof);
    when(signer.signAggregateAndProof(aggregateAndProof, fork)).thenReturn(signing);
    return schemas
        .getSignedAggregateAndProofSchema()
        .create(aggregateAndProof, dataStructureUtil.randomXmssSignature().getBytes());
  }
}

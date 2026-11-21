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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;
import tech.pegasys.teku.xmss.XmssSignature;

/** From PQ, a PTC member signs its payload attestation with leanXMSS. */
class PayloadAttestationProductionDutyPqTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final XmssSigner xmssSigner = mock(XmssSigner.class);

  private PayloadAttestationProductionDuty duty;

  @BeforeEach
  void setUp() {
    duty =
        new PayloadAttestationProductionDuty(
            spec,
            SLOT,
            forkProvider,
            validatorApiChannel,
            new BatchAttestationSendingStrategy<>(
                validatorApiChannel::sendPayloadAttestationMessages),
            ValidatorDutyMetrics.create(new StubMetricsSystem()));
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendPqPayloadAttestationMessages(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
  }

  @Test
  void shouldSignWithXmssAndSendPqMessages() {
    final UInt64 validatorIndex = UInt64.valueOf(23);
    final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData(SLOT);
    final XmssSignature signature =
        XmssSignature.fromBytes(dataStructureUtil.randomXmssSignature().getBytes());
    when(validatorApiChannel.createPayloadAttestationData(SLOT))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data)));
    when(xmssSigner.signPayloadAttestationData(data, fork))
        .thenReturn(SafeFuture.completedFuture(signature));
    duty.addValidator(validator(Optional.of(xmssSigner)), validatorIndex);

    final SafeFuture<DutyResult> result = duty.performDuty();

    assertThat(result).isCompleted();
    final PayloadAttestationMessagePq expected =
        SchemaDefinitionsPq.required(spec.atSlot(SLOT).getSchemaDefinitions())
            .getPqAttestationSchemas()
            .getPayloadAttestationMessageSchema()
            .create(validatorIndex, data, signature.toBytes());
    verify(validatorApiChannel).sendPqPayloadAttestationMessages(List.of(expected));
    verify(validatorApiChannel, never()).sendPayloadAttestationMessages(any());
    verifyNoInteractions(signer);
  }

  @Test
  void shouldNotSendWithoutPqKeys() {
    final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData(SLOT);
    when(validatorApiChannel.createPayloadAttestationData(SLOT))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data)));
    duty.addValidator(validator(Optional.empty()), UInt64.valueOf(23));

    final SafeFuture<DutyResult> result = duty.performDuty();

    assertThat(result).isCompleted();
    verify(validatorApiChannel, never()).sendPqPayloadAttestationMessages(any());
    verifyNoInteractions(signer);
  }

  private Validator validator(final Optional<XmssSigner> maybeXmssSigner) {
    return new Validator(
        dataStructureUtil.randomPublicKey(), signer, Optional::empty, false, maybeXmssSigner);
  }
}

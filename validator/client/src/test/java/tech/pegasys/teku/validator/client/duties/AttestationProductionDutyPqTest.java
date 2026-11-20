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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

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
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;
import tech.pegasys.teku.xmss.XmssSignature;

/** From PQ, validators attest with single attestations signed with leanXMSS. */
class AttestationProductionDutyPqTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final XmssSigner xmssSigner = mock(XmssSigner.class);

  private AttestationProductionDuty duty;

  @BeforeEach
  void setUp() {
    duty =
        new AttestationProductionDuty(
            spec,
            SLOT,
            forkProvider,
            validatorApiChannel,
            new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedAttestations),
            ValidatorDutyMetrics.create(new StubMetricsSystem()));
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendSignedAttestations(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
  }

  @Test
  void shouldSignSingleAttestationWithXmss() {
    final Validator validator =
        new Validator(
            dataStructureUtil.randomPublicKey(),
            mock(Signer.class),
            Optional::empty,
            false,
            Optional.of(xmssSigner));
    final AttestationData attestationData = attestationData();
    final XmssSignature signature =
        XmssSignature.fromBytes(dataStructureUtil.randomXmssSignature().getBytes());
    when(validatorApiChannel.createAttestationData(SLOT, 0))
        .thenReturn(completedFuture(Optional.of(attestationData)));
    when(xmssSigner.signAttestationData(attestationData, fork))
        .thenReturn(completedFuture(signature));

    final SafeFuture<Optional<AttestationData>> attestationFuture =
        duty.addValidator(validator, 3, 5, 10, 11);
    assertThat(duty.performDuty()).isCompleted();
    assertThat(attestationFuture).isCompletedWithValue(Optional.of(attestationData));

    verify(validatorApiChannel)
        .sendSignedAttestations(
            List.of(
                new SingleAttestationPq(
                    UInt64.valueOf(3), UInt64.valueOf(10), attestationData, signature.toBytes())));
  }

  @Test
  void shouldFailForValidatorWithoutPqKeys() {
    final Validator validator =
        new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
    when(validatorApiChannel.createAttestationData(SLOT, 0))
        .thenReturn(completedFuture(Optional.of(attestationData())));

    final SafeFuture<Optional<AttestationData>> attestationFuture =
        duty.addValidator(validator, 3, 5, 10, 11);
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(attestationFuture).isCompleted();

    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getSuccessCount()).isZero();
  }

  private AttestationData attestationData() {
    final UInt64 epoch = spec.computeEpochAtSlot(SLOT);
    return new AttestationData(
        SLOT,
        UInt64.ZERO,
        dataStructureUtil.randomBytes32(),
        new Checkpoint(epoch.minusMinZero(1), dataStructureUtil.randomBytes32()),
        new Checkpoint(epoch, dataStructureUtil.randomBytes32()));
  }
}

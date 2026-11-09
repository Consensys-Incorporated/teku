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

package tech.pegasys.teku.validator.client.duties.attestations;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

class BatchAttestationSendingStrategyTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final BatchAttestationSendingStrategy<Attestation> strategy =
      new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedAttestations);

  @BeforeEach
  void setUp() {
    when(validatorApiChannel.sendSignedAttestations(anyList()))
        .thenReturn(SafeFuture.completedFuture(emptyList()));
  }

  @Test
  void shouldSendAllAttestationsInASingleBatch() {
    final SafeFuture<ProductionResult<Attestation>> future1 = new SafeFuture<>();
    final SafeFuture<ProductionResult<Attestation>> future2 = new SafeFuture<>();
    final SafeFuture<ProductionResult<Attestation>> future3 = new SafeFuture<>();

    final Attestation attestation1 = dataStructureUtil.randomAttestation();
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    final Attestation attestation3 = dataStructureUtil.randomAttestation();

    final SafeFuture<DutyResult> result = strategy.send(Stream.of(future1, future2, future3));

    assertThat(result).isNotDone();

    future1.complete(
        ProductionResult.success(
            dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), attestation1));
    assertThat(result).isNotDone();

    future3.complete(
        ProductionResult.success(
            dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), attestation3));
    assertThat(result).isNotDone();

    future2.complete(
        ProductionResult.success(
            dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), attestation2));
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getSuccessCount()).isEqualTo(3);
    verify(validatorApiChannel)
        .sendSignedAttestations(List.of(attestation1, attestation2, attestation3));
  }
}

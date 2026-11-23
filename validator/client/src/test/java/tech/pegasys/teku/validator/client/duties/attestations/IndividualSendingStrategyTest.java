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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

class IndividualSendingStrategyTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private final IndividualSendingStrategy<Attestation> strategy =
      new IndividualSendingStrategy<>(validatorApiChannel::sendSignedAttestations);

  @BeforeEach
  void setUp() {
    when(validatorApiChannel.sendSignedAttestations(anyList()))
        .thenReturn(SafeFuture.completedFuture(emptyList()));
  }

  @Test
  void shouldSendEachAttestationAsSoonAsItIsProduced() {
    final SafeFuture<ProductionResult<Attestation>> future1 = new SafeFuture<>();
    final SafeFuture<ProductionResult<Attestation>> future2 = new SafeFuture<>();
    final Attestation attestation1 = dataStructureUtil.randomAttestation();
    final Attestation attestation2 = dataStructureUtil.randomAttestation();

    final SafeFuture<DutyResult> result = strategy.send(Stream.of(future1, future2));

    future2.complete(success(attestation2));
    verify(validatorApiChannel).sendSignedAttestations(List.of(attestation2));
    verify(validatorApiChannel, never()).sendSignedAttestations(List.of(attestation1));
    assertThat(result).isNotDone();

    future1.complete(success(attestation1));
    verify(validatorApiChannel).sendSignedAttestations(List.of(attestation1));
    verifyNoMoreInteractions(validatorApiChannel);
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getSuccessCount()).isEqualTo(2);
  }

  @Test
  void shouldCombineErrorsFromTheBeaconNodeAndFailuresToProduce() {
    final Attestation rejected = dataStructureUtil.randomAttestation();
    final Attestation accepted = dataStructureUtil.randomAttestation();
    when(validatorApiChannel.sendSignedAttestations(List.of(rejected)))
        .thenReturn(
            SafeFuture.completedFuture(List.of(new SubmitDataError(UInt64.ZERO, "invalid"))));

    final SafeFuture<DutyResult> result =
        strategy.send(
            Stream.of(
                SafeFuture.completedFuture(success(rejected)),
                SafeFuture.completedFuture(success(accepted)),
                SafeFuture.completedFuture(
                    ProductionResult.failure(
                        dataStructureUtil.randomPublicKey(), new IllegalStateException("no")))));

    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getSuccessCount()).isEqualTo(1);
    assertThat(safeJoin(result).getFailureCount()).isEqualTo(2);
    // A message that failed to be produced is not sent
    verify(validatorApiChannel).sendSignedAttestations(List.of(rejected));
    verify(validatorApiChannel).sendSignedAttestations(List.of(accepted));
    verifyNoMoreInteractions(validatorApiChannel);
  }

  private ProductionResult<Attestation> success(final Attestation attestation) {
    return ProductionResult.success(
        dataStructureUtil.randomPublicKey(), dataStructureUtil.randomBytes32(), attestation);
  }
}

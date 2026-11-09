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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.executionlayer.PayloadStatus.VALID;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoicePayloadExecutorGloasTest {

  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SafeFuture<PayloadStatus> executionResult = new SafeFuture<>();
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
  private final SignedExecutionPayloadEnvelope signedEnvelope =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(0);
  private final NewPayloadRequest payloadRequest = new NewPayloadRequest(payload);

  private final ForkChoicePayloadExecutorGloas payloadExecutor =
      new ForkChoicePayloadExecutorGloas(signedEnvelope, executionLayer);

  @BeforeEach
  void setUp() {
    when(executionLayer.engineNewPayload(any(), any())).thenReturn(executionResult);
  }

  @Test
  void optimisticallyExecute_shouldSendToExecutionEngineAndReturnTrue() {
    final boolean result = payloadExecutor.optimisticallyExecute(Optional.empty(), payloadRequest);
    verify(executionLayer).engineNewPayload(payloadRequest, UInt64.ZERO);
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldReturnFailedExecutionWhenELOfflineAtExecution() {
    when(executionLayer.engineNewPayload(payloadRequest, UInt64.ZERO))
        .thenReturn(SafeFuture.failedFuture(new Error()));
    final boolean execution =
        payloadExecutor.optimisticallyExecute(Optional.empty(), payloadRequest);

    verify(executionLayer).engineNewPayload(payloadRequest, UInt64.ZERO);
    assertThat(execution).isTrue();
    assertThat(payloadExecutor.getExecutionResult())
        .isCompletedWithValueMatching(PayloadStatus::hasFailedExecution);
  }

  @Test
  void shouldReturnExecutionResultWhenExecuted() {
    payloadExecutor.optimisticallyExecute(Optional.empty(), payloadRequest);

    final SafeFuture<PayloadStatus> result = payloadExecutor.getExecutionResult();
    assertThat(result).isNotCompleted();

    this.executionResult.complete(VALID);

    assertThat(result).isCompletedWithValue(VALID);
  }
}

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

package tech.pegasys.teku.ethereum.executionclient.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineNewPayloadV1Test {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineNewPayloadV1 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineNewPayloadV1(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_newPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(1);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_newPayloadV1");
  }

  @Test
  public void executionPayloadParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.newPayloadV1(any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertThat(jsonRpcMethod.execute(params))
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(PayloadStatus::hasFailedExecution);
  }

  @Test
  public void shouldCallNewPayloadV1WithExecutionPayloadV1() {
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV1 executionPayloadV1 =
        ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload);

    when(executionEngineClient.newPayloadV1(executionPayloadV1))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).newPayloadV1(eq(executionPayloadV1));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<PayloadStatusV1>> dummySuccessfulResponse() {
    return SafeFuture.completedFuture(
        Response.fromPayloadReceivedAsJson(
            new PayloadStatusV1(
                ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
  }

  private SafeFuture<Response<PayloadStatusV1>> dummyFailedResponse(final String errorMsg) {
    return SafeFuture.completedFuture(Response.fromErrorMessage(errorMsg));
  }
}

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
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineForkChoiceUpdatedV3Test {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineForkChoiceUpdatedV3 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineForkChoiceUpdatedV3(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_forkchoiceUpdated");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(3);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_forkchoiceUpdatedV3");
  }

  @Test
  public void forkChoiceStateParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void payloadBuildingAttributesParamIsOptional() {
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);

    when(executionEngineClient.forkChoiceUpdatedV3(any(), eq(Optional.empty())))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(forkChoiceState).build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).forkChoiceUpdatedV3(any(), eq(Optional.empty()));
  }

  @Test
  public void shouldReturnFailedFutureWithMessageWhenEngineClientRequestFails() {
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.forkChoiceUpdatedV3(any(), any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(forkChoiceState).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldCallForkChoiceUpdateV3WithPayloadAttributesV3WhenInCapella() {
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        dataStructureUtil.randomPayloadBuildingAttributes(false);
    final ForkChoiceStateV1 forkChoiceStateV1 =
        ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState);
    final Optional<PayloadAttributesV3> payloadAttributesV3 =
        PayloadAttributesV3.fromInternalPayloadBuildingAttributesV3(
            Optional.of(payloadBuildingAttributes));

    jsonRpcMethod = new EngineForkChoiceUpdatedV3(executionEngineClient);

    when(executionEngineClient.forkChoiceUpdatedV3(forkChoiceStateV1, payloadAttributesV3))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(forkChoiceState)
            .add(payloadBuildingAttributes)
            .build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).forkChoiceUpdatedV3(forkChoiceStateV1, payloadAttributesV3);
  }

  private SafeFuture<Response<ForkChoiceUpdatedResult>> dummySuccessfulResponse() {
    return SafeFuture.completedFuture(
        Response.fromPayloadReceivedAsJson(
            new ForkChoiceUpdatedResult(
                new PayloadStatusV1(
                    ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), ""),
                dataStructureUtil.randomBytes8())));
  }

  private SafeFuture<Response<ForkChoiceUpdatedResult>> dummyFailedResponse(
      final String errorMessage) {
    return SafeFuture.completedFuture(Response.fromErrorMessage(errorMessage));
  }
}

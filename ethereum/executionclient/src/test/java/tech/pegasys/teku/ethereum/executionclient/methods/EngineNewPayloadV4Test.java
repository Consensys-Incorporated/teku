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

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineNewPayloadV4Test {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineNewPayloadV4 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineNewPayloadV4(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_newPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(4);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_newPayloadV4");
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
  public void blobVersionedHashesParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(dataStructureUtil.randomExecutionPayload()).build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 1");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void parentBeaconBlockRootParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(dataStructureUtil.randomExecutionPayload())
            .add(dataStructureUtil.randomVersionedHashes(3))
            .build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 2");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void executionRequestHashParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(dataStructureUtil.randomExecutionPayload())
            .add(dataStructureUtil.randomVersionedHashes(3))
            .add(dataStructureUtil.randomBytes32())
            .build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 3");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final List<VersionedHash> blobVersionedHashes = dataStructureUtil.randomVersionedHashes(3);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final List<Bytes> executionRequests = dataStructureUtil.randomEncodedExecutionRequests();
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.newPayloadV4(any(), any(), any(), any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(executionPayload)
            .add(blobVersionedHashes)
            .add(parentBeaconBlockRoot)
            .add(executionRequests)
            .build();

    assertThat(jsonRpcMethod.execute(params))
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(PayloadStatus::hasFailedExecution);
  }

  @Test
  public void shouldCallNewPayloadV4WithExecutionPayloadV3AndCorrectParameters() {
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final List<VersionedHash> blobVersionedHashes = dataStructureUtil.randomVersionedHashes(4);
    final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
    final List<Bytes> executionRequests = dataStructureUtil.randomEncodedExecutionRequests();

    final ExecutionPayloadV3 executionPayloadV3 =
        ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload);
    assertThat(executionPayloadV3).isExactlyInstanceOf(ExecutionPayloadV3.class);

    jsonRpcMethod = new EngineNewPayloadV4(executionEngineClient);

    when(executionEngineClient.newPayloadV4(
            eq(executionPayloadV3),
            eq(blobVersionedHashes),
            eq(parentBeaconBlockRoot),
            eq(executionRequests)))
        .thenReturn(dummySuccessfulResponse());

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(executionPayload)
            .add(blobVersionedHashes)
            .add(parentBeaconBlockRoot)
            .add(executionRequests)
            .build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient)
        .newPayloadV4(
            eq(executionPayloadV3),
            eq(blobVersionedHashes),
            eq(parentBeaconBlockRoot),
            eq(executionRequests));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<PayloadStatusV1>> dummySuccessfulResponse() {
    return SafeFuture.completedFuture(
        Response.fromPayloadReceivedAsJson(
            new PayloadStatusV1(
                ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
  }

  private SafeFuture<Response<PayloadStatusV1>> dummyFailedResponse(final String errorMessage) {
    return SafeFuture.completedFuture(Response.fromErrorMessage(errorMessage));
  }
}

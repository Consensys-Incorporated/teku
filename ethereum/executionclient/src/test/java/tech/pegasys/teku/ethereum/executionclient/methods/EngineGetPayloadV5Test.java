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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineGetPayloadV5Test {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EngineGetPayloadV5 jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EngineGetPayloadV5(executionEngineClient, spec);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_getPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(5);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_getPayloadV5");
  }

  @Test
  public void executionPayloadContextParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void slotParamIsRequired() {
    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 1");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.getPayloadV5(any()))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldCallGetPayloadV5AndParseResponseSuccessfully() {
    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final UInt256 blockValue = UInt256.MAX_VALUE;
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    final ExecutionPayload executionPayloadFulu = dataStructureUtil.randomExecutionPayload();
    final ExecutionRequests executionRequests = dataStructureUtil.randomExecutionRequests();
    final List<Bytes> encodedExecutionRequests =
        spec.getExecutionRequestsDataCodec(UInt64.ZERO).encode(executionRequests);
    assertThat(executionPayloadFulu).isInstanceOf(ExecutionPayloadDeneb.class);

    when(executionEngineClient.getPayloadV5(eq(executionPayloadContext.getPayloadId())))
        .thenReturn(
            dummySuccessfulResponse(
                executionPayloadFulu, blockValue, blobsBundle, encodedExecutionRequests));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(UInt64.ZERO).build();

    jsonRpcMethod = new EngineGetPayloadV5(executionEngineClient, spec);

    final GetPayloadResponse expectedGetPayloadResponse =
        new GetPayloadResponse(
            executionPayloadFulu, blockValue, blobsBundle, false, executionRequests);
    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(expectedGetPayloadResponse);

    verify(executionEngineClient).getPayloadV5(eq(executionPayloadContext.getPayloadId()));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Response<GetPayloadV5Response>> dummySuccessfulResponse(
      final ExecutionPayload executionPayload,
      final UInt256 blockValue,
      final BlobsBundle blobsBundle,
      final List<Bytes> encodedExecutionRequests) {
    return SafeFuture.completedFuture(
        Response.fromPayloadReceivedAsJson(
            new GetPayloadV5Response(
                ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload),
                blockValue,
                BlobsBundleV2.fromInternalBlobsBundle(blobsBundle),
                false,
                encodedExecutionRequests)));
  }

  private SafeFuture<Response<GetPayloadV5Response>> dummyFailedResponse(
      final String errorMessage) {
    return SafeFuture.completedFuture(Response.fromErrorMessage(errorMessage));
  }
}

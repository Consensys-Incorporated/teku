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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class EngineNewPayloadV4 extends AbstractEngineJsonRpcMethod<PayloadStatus> {

  private static final Logger LOG = LogManager.getLogger();

  public EngineNewPayloadV4(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_NEW_PAYLOAD.getName();
  }

  @Override
  public int getVersion() {
    return 4;
  }

  @Override
  public SafeFuture<PayloadStatus> execute(final JsonRpcRequestParams params) {
    final ExecutionPayload executionPayload =
        params.getRequiredParameter(0, ExecutionPayload.class);
    final List<VersionedHash> blobVersionedHashes =
        params.getRequiredListParameter(1, VersionedHash.class);
    final Bytes32 parentBeaconBlockRoot = params.getRequiredParameter(2, Bytes32.class);
    final List<Bytes> executionRequests = params.getRequiredListParameter(3, Bytes.class);

    LOG.trace(
        "Calling {}(executionPayload={}, blobVersionedHashes={}, parentBeaconBlockRoot={}, executionRequests={})",
        getVersionedName(),
        executionPayload,
        blobVersionedHashes,
        parentBeaconBlockRoot,
        executionRequests);

    final ExecutionPayloadV3 executionPayloadV3 =
        ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload);
    return executionEngineClient
        .newPayloadV4(
            executionPayloadV3, blobVersionedHashes, parentBeaconBlockRoot, executionRequests)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace(
                    "Response {}(executionPayload={}) -> {}",
                    getVersionedName(),
                    executionPayload,
                    payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }
}

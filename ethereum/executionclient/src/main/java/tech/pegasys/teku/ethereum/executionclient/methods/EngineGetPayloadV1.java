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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class EngineGetPayloadV1 extends AbstractEngineJsonRpcMethod<GetPayloadResponse> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;

  public EngineGetPayloadV1(final ExecutionEngineClient executionEngineClient, final Spec spec) {
    super(executionEngineClient);
    this.spec = spec;
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_GET_PAYLOAD.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<GetPayloadResponse> execute(final JsonRpcRequestParams params) {
    final ExecutionPayloadContext executionPayloadContext =
        params.getRequiredParameter(0, ExecutionPayloadContext.class);
    final UInt64 slot = params.getRequiredParameter(1, UInt64.class);

    LOG.trace(
        "Calling {}(payloadId={}, slot={})",
        getVersionedName(),
        executionPayloadContext.getPayloadId(),
        slot);

    return executionEngineClient
        .getPayloadV1(executionPayloadContext.getPayloadId())
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            payload -> {
              final ExecutionPayloadSchema<?> payloadSchema =
                  SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                      .getExecutionPayloadSchema();
              return new GetPayloadResponse(payload.asInternalExecutionPayload(payloadSchema));
            })
        .thenPeek(
            getPayloadResponse ->
                LOG.trace(
                    "Response {}(payloadId={}, slot={}) -> {}",
                    getVersionedName(),
                    executionPayloadContext.getPayloadId(),
                    slot,
                    getPayloadResponse));
  }
}

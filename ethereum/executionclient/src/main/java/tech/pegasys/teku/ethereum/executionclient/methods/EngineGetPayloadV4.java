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
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV4Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class EngineGetPayloadV4 extends AbstractEngineJsonRpcMethod<GetPayloadResponse> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;

  public EngineGetPayloadV4(final ExecutionEngineClient executionEngineClient, final Spec spec) {
    super(executionEngineClient);
    this.spec = spec;
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_GET_PAYLOAD.getName();
  }

  @Override
  public int getVersion() {
    return 4;
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
        .getPayloadV4(executionPayloadContext.getPayloadId())
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            response -> {
              final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
              final ExecutionPayloadSchema<?> payloadSchema =
                  SchemaDefinitionsBellatrix.required(schemaDefinitions)
                      .getExecutionPayloadSchema();
              final ExecutionPayload executionPayload =
                  response.executionPayload.asInternalExecutionPayload(payloadSchema);
              final BlobsBundle blobsBundle = getBlobsBundle(response, schemaDefinitions);
              final ExecutionRequests executionRequests =
                  spec.getExecutionRequestsDataCodec(slot).decode(response.executionRequests);
              return new GetPayloadResponse(
                  executionPayload,
                  response.blockValue,
                  blobsBundle,
                  response.shouldOverrideBuilder,
                  executionRequests);
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

  private BlobsBundle getBlobsBundle(
      final GetPayloadV4Response response, final SchemaDefinitions schemaDefinitions) {
    final BlobSchema blobSchema =
        SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobSchema();
    return response.blobsBundle.asInternalBlobsBundle(blobSchema);
  }
}

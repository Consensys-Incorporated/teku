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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadBodyV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class EngineGetPayloadBodiesByHashV2
    extends AbstractEngineJsonRpcMethod<List<ExecutionPayloadBodyV2>> {

  private static final Logger LOG = LogManager.getLogger();

  public EngineGetPayloadBodiesByHashV2(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_GET_PAYLOAD_BODIES_BY_HASH.getName();
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @Override
  public SafeFuture<List<ExecutionPayloadBodyV2>> execute(final JsonRpcRequestParams params) {
    final List<Bytes32> blockHashes = params.getRequiredListParameter(0, Bytes32.class);

    LOG.trace("Calling {}(blockHashes={})", getVersionedName(), blockHashes);

    return executionEngineClient
        .getPayloadBodiesByHashV2(blockHashes)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenPeek(
            response ->
                LOG.trace(
                    "Response {}(blockHashes={}) -> {} bodies",
                    getVersionedName(),
                    blockHashes,
                    response.size()));
  }
}

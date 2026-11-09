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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;

public abstract class GetChainHeads extends RestApiEndpoint {

  private final ChainDataProvider chainDataProvider;

  public GetChainHeads(final ChainDataProvider chainDataProvider, final EndpointMetadata metadata) {
    super(metadata);
    this.chainDataProvider = chainDataProvider;
  }

  protected static SerializableTypeDefinition<List<ProtoNodeData>> responseType(
      final SerializableTypeDefinition<ProtoNodeData> chainHeadTypeDefinition) {
    return SerializableTypeDefinition.<List<ProtoNodeData>>object()
        .withField(
            "data", SerializableTypeDefinition.listOf(chainHeadTypeDefinition), Function.identity())
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondOk(chainDataProvider.getChainHeads(), CacheLength.NO_CACHE);
  }
}

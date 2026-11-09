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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

public class GetEth1DataCache extends RestApiEndpoint {

  public static final String ROUTE = "/teku/v1/beacon/pool/eth1cache";

  private static final SerializableTypeDefinition<List<Eth1Data>> ETH1DATA_CACHE_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Eth1Data>>object()
          .name("GetEth1DataCacheResponse")
          .withField(
              "data",
              SerializableTypeDefinition.listOf(Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition()),
              Function.identity())
          .build();

  private final Eth1DataProvider eth1DataProvider;

  public GetEth1DataCache(final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getTekuV1BeaconPoolEth1cache")
            .summary("Get cached eth1 blocks")
            .description(
                "Get all of the eth1 blocks currently cached by the beacon node, that could be considered for inclusion during block production.")
            .tags(TAG_TEKU)
            .response(SC_OK, "OK", ETH1DATA_CACHE_RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.eth1DataProvider = eth1DataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    Collection<Eth1Data> eth1CachedBlocks = this.eth1DataProvider.getEth1CachedBlocks();
    if (eth1CachedBlocks.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Eth1 blocks cache is empty");
    } else {
      request.respondOk(eth1CachedBlocks);
    }
  }
}

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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;

public class GetProposerSlashings extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/proposer_slashings";
  private final NodeDataProvider nodeDataProvider;

  private static final SerializableTypeDefinition<List<ProposerSlashing>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<ProposerSlashing>>object()
          .name("GetPoolProposerSlashingsResponse")
          .withField(
              "data",
              listOf(ProposerSlashing.SSZ_SCHEMA.getJsonTypeDefinition()),
              Function.identity())
          .build();

  public GetProposerSlashings(final DataProvider dataProvider) {
    this(dataProvider.getNodeDataProvider());
  }

  GetProposerSlashings(final NodeDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPoolProposerSlashings")
            .summary("Get ProposerSlashings from operations pool")
            .description(
                "Retrieves proposer slashings known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.nodeDataProvider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<ProposerSlashing> proposerSlashings = nodeDataProvider.getProposerSlashings();
    request.respondOk(proposerSlashings);
  }
}

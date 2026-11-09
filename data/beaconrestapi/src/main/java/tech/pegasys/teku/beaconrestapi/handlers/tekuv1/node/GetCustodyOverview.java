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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetCustodyOverview extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/node/custody_overview";

  private final NodeDataProvider provider;

  private static final SerializableTypeDefinition<CustodyOverview> CUSTODY_OVERVIEW_TYPE =
      SerializableTypeDefinition.object(CustodyOverview.class)
          .withField("custody_columns", listOf(UINT64_TYPE), CustodyOverview::columns)
          .build();

  private static final SerializableTypeDefinition<CustodyOverview> RESPONSE_TYPE =
      SerializableTypeDefinition.<CustodyOverview>object()
          .name("GetCustodyOverviewResponse")
          .withField("data", CUSTODY_OVERVIEW_TYPE, Function.identity())
          .build();

  public GetCustodyOverview(final DataProvider provider) {
    this(provider.getNodeDataProvider());
  }

  GetCustodyOverview(final NodeDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getCustodyOverview")
            .summary("Get Custody Overview")
            .description("Retrieves current custody overview.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(
        new CustodyOverview(provider.getCustodyColumnIndices().stream().sorted().toList()));
  }

  record CustodyOverview(List<UInt64> columns) {}
}

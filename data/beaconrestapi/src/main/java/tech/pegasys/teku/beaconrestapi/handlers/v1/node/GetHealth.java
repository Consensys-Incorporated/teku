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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SYNCING_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PARTIAL_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetHealth extends RestApiEndpoint {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/node/health";
  private final SyncDataProvider syncProvider;
  private final ChainDataProvider chainDataProvider;

  public GetHealth(final DataProvider provider) {
    this(provider.getSyncDataProvider(), provider.getChainDataProvider());
  }

  GetHealth(final SyncDataProvider syncProvider, final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getHealth")
            .summary("Get health check")
            .description(
                "Returns node health status in http status codes. Useful for load balancers.")
            .queryParam(SYNCING_PARAMETER)
            .tags(TAG_NODE)
            .response(SC_OK, "Node is ready")
            .response(SC_PARTIAL_CONTENT, "Node is syncing but can serve incomplete data")
            .response(SC_SERVICE_UNAVAILABLE, "Node not initialized or having issues")
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withBadRequestResponse(Optional.of("Invalid syncing status code"))
            .build());
    this.syncProvider = syncProvider;
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    if (!chainDataProvider.isStoreAvailable() || syncProvider.getRejectedExecutionCount() > 0) {
      request.respondWithCode(SC_SERVICE_UNAVAILABLE);
    } else if (syncProvider.isSyncing()) {
      request.respondWithUndocumentedCode(getResponseCodeFromQueryParams(request));
    } else {
      request.respondWithCode(SC_OK);
    }
  }

  private int getResponseCodeFromQueryParams(final RestApiRequest request) {
    try {
      final int responseCode =
          request.getOptionalQueryParameter(SYNCING_PARAMETER).orElse(SC_PARTIAL_CONTENT);
      if (responseCode < 100 || responseCode > 599) {
        throw new BadRequestException("Invalid syncing status code");
      } else {
        return responseCode;
      }
    } catch (IllegalArgumentException ex) {
      LOG.trace("Illegal parameter in GetHealth", ex);
    }
    return SC_PARTIAL_CONTENT;
  }
}

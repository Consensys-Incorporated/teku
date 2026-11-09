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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class Liveness extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/admin/liveness";

  private final SyncDataProvider syncProvider;

  private static final ParameterMetadata<Boolean> FAIL_ON_REJECTED_COUNT =
      new ParameterMetadata<>("failOnRejectedCount", CoreTypes.BOOLEAN_TYPE);

  public Liveness(final DataProvider provider) {
    this(provider.getSyncDataProvider());
  }

  Liveness(final SyncDataProvider syncProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("GetLiveness")
            .summary("Get node liveness")
            .description("Returns 200 if the node is up even if it is syncing.")
            .queryParam(FAIL_ON_REJECTED_COUNT)
            .tags(TAG_TEKU)
            .response(SC_OK, "Node is ready")
            .response(
                SC_SERVICE_UNAVAILABLE,
                "Node is having issues that it may not recover from. Only occurs if failOnRejectedCount is set")
            .build());
    this.syncProvider = syncProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    if (request.getOptionalQueryParameter(FAIL_ON_REJECTED_COUNT).orElse(false)
        && syncProvider.getRejectedExecutionCount() > 0) {
      request.respondWithCode(SC_SERVICE_UNAVAILABLE, CacheLength.NO_CACHE);
      return;
    }
    request.respondWithCode(SC_OK, CacheLength.NO_CACHE);
  }
}

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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class GetVersion extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/node/version";

  private static final SerializableTypeDefinition<String> DATA_TYPE =
      SerializableTypeDefinition.object(String.class)
          .withField("version", STRING_TYPE, Function.identity())
          .build();

  private static final SerializableTypeDefinition<String> RESPONSE_TYPE =
      SerializableTypeDefinition.object(String.class)
          .name("GetVersionResponse")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  public GetVersion() {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodeVersion")
            .summary("Get version string of the running beacon node.")
            .description(
                "similar to [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3).")
            .tags(TAG_NODE)
            .deprecated(true)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(VersionProvider.VERSION);
  }
}

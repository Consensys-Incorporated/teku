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

package tech.pegasys.teku.beaconrestapi.handlers.v2.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class GetVersionV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/node/version";

  private static final ClientVersion TEKU_CLIENT_VERSION =
      new ClientVersion(
          ClientVersion.TEKU_CLIENT_CODE,
          VersionProvider.CLIENT_IDENTITY,
          VersionProvider.IMPLEMENTATION_VERSION,
          VersionProvider.COMMIT_HASH
              .map(commitHash -> Bytes4.fromHexString(commitHash.substring(0, 8)))
              .orElse(Bytes4.ZERO));

  private static final SerializableTypeDefinition<ClientVersion> CLIENT_VERSION_TYPE =
      SerializableTypeDefinition.object(ClientVersion.class)
          .withField("code", STRING_TYPE, ClientVersion::code)
          .withField("name", STRING_TYPE, ClientVersion::name)
          .withField("version", STRING_TYPE, ClientVersion::version)
          .withField("commit", BYTES4_TYPE, ClientVersion::commit)
          .build();

  private static final SerializableTypeDefinition<VersionDataV2> DATA_TYPE =
      SerializableTypeDefinition.object(VersionDataV2.class)
          .withField("beacon_node", CLIENT_VERSION_TYPE, VersionDataV2::beaconNode)
          .withOptionalField(
              "execution_client", CLIENT_VERSION_TYPE, VersionDataV2::executionClient)
          .build();

  private static final SerializableTypeDefinition<VersionDataV2> RESPONSE_TYPE =
      SerializableTypeDefinition.object(VersionDataV2.class)
          .name("GetVersionV2Response")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  private final ExecutionClientDataProvider executionClientDataProvider;

  public GetVersionV2(final DataProvider dataProvider) {
    this(dataProvider.getExecutionClientDataProvider());
  }

  public GetVersionV2(final ExecutionClientDataProvider executionClientDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodeVersionV2")
            .summary("Get version information for the beacon node and execution client.")
            .description(
                "Retrieves structured information about the version of the beacon node and its attached execution client in the same format as used on the Engine API. Version information about the execution client may not be available at all times and is therefore optional. If the beacon node receives multiple values from `engine_getClientVersionV1`, the first value should be returned on this endpoint.")
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.executionClientDataProvider = executionClientDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<ClientVersion> executionClient =
        executionClientDataProvider.getExecutionClientVersion();
    request.respondOk(new VersionDataV2(TEKU_CLIENT_VERSION, executionClient));
  }

  public record VersionDataV2(ClientVersion beaconNode, Optional<ClientVersion> executionClient) {}
}

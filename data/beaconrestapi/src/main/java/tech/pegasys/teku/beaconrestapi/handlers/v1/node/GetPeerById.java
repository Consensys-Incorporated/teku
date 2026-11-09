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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PEER_ID_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers.PEER_DATA_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.peer.Eth2PeerWithEnr;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetPeerById extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/node/peers/{peer_id}";

  private static final SerializableTypeDefinition<Eth2PeerWithEnr> PEERS_BY_ID_RESPONSE_TYPE =
      SerializableTypeDefinition.object(Eth2PeerWithEnr.class)
          .name("GetPeerResponse")
          .withField("data", PEER_DATA_TYPE, Function.identity())
          .build();

  private final NetworkDataProvider network;

  public GetPeerById(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetPeerById(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPeer")
            .summary("Get node peer")
            .description("Retrieves data about the given peer.")
            .pathParam(PEER_ID_PARAMETER)
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", PEERS_BY_ID_RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.network = network;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<Eth2PeerWithEnr> peer =
        network.getEth2PeerById(request.getPathParameter(PEER_ID_PARAMETER));
    if (peer.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Peer not found");
    } else {
      request.respondOk(peer.get());
    }
  }
}

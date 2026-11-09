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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_DOUBLE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class GetPeersScore extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/nodes/peer_scores";
  private final NetworkDataProvider network;

  private static final SerializableTypeDefinition<Eth2Peer> PEER_TYPE =
      SerializableTypeDefinition.object(Eth2Peer.class)
          .withField("peer_id", STRING_TYPE, eth2Peer -> eth2Peer.getId().toBase58())
          .withField("gossip_score", RAW_DOUBLE_TYPE, Peer::getGossipScore)
          .build();

  private static final SerializableTypeDefinition<List<Eth2Peer>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Eth2Peer>>object()
          .name("GetPeerScoresResponse")
          .withField("data", listOf(PEER_TYPE), Function.identity())
          .build();

  public GetPeersScore(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetPeersScore(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPeersScore")
            .summary("Get peer scores")
            .description("Retrieves data about the node's network peers.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.network = network;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(network.getPeerScores());
  }
}

/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_DOUBLE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.string;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength.NO_CACHE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.peer.Eth2PeerWithEnr;
import tech.pegasys.teku.api.provider.Direction;
import tech.pegasys.teku.api.provider.State;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetPeers extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/node/peers";

  private static final DeserializableTypeDefinition<State> STATE_TYPE =
      DeserializableTypeDefinition.enumOf(State.class);

  private static final DeserializableTypeDefinition<Direction> DIRECTION_TYPE =
      DeserializableTypeDefinition.enumOf(Direction.class);

  static final SerializableTypeDefinition<Eth2PeerWithEnr> PEER_DATA_TYPE =
      SerializableTypeDefinition.object(Eth2PeerWithEnr.class)
          .name("Peer")
          .withField(
              "peer_id",
              string(
                  "Cryptographic hash of a peer’s public key. "
                      + "'[Read more](https://docs.libp2p.io/concepts/peer-id/)",
                  "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"),
              eth2Peer -> eth2Peer.peer().getId().toBase58())
          .withOptionalField(
              "enr",
              string(
                  "Ethereum node record. " + "[Read more](https://eips.ethereum.org/EIPS/eip-778)",
                  "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrk"
                      + "Tfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYp"
                      + "Ma2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8"),
              Eth2PeerWithEnr::enr)
          .withField(
              "last_seen_p2p_address",
              string(
                  "Multiaddr used in last peer connection. "
                      + "[Read more](https://libp2p.io/docs/addressing/)",
                  "/ip4/7.7.7.7/tcp/4242/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"),
              eth2Peer -> eth2Peer.peer().getAddress().toExternalForm())
          .withField(
              "state",
              STATE_TYPE,
              eth2Peer -> eth2Peer.peer().isConnected() ? State.connected : State.disconnected)
          .withField(
              "direction",
              DIRECTION_TYPE,
              eth2Peer ->
                  eth2Peer.peer().connectionInitiatedLocally()
                      ? Direction.outbound
                      : Direction.inbound)
          .withOptionalField(
              "agent_version",
              string(
                  "The peer's libp2p agent version string.",
                  "Lighthouse/v8.1.3-66919c2/aarch64-linux"),
              eth2Peer -> eth2Peer.peer().getAgentVersion())
          .withOptionalField(
              "score",
              RAW_DOUBLE_TYPE.withDescription("Client-native peer score."),
              eth2Peer -> Optional.ofNullable(eth2Peer.peer().getGossipScore()))
          .build();

  private static final SerializableTypeDefinition<Integer> PEERS_META_TYPE =
      SerializableTypeDefinition.object(Integer.class)
          .name("Meta")
          .withField(
              "count",
              RAW_INTEGER_TYPE.withDescription("Total number of items"),
              Function.identity())
          .build();

  private static final SerializableTypeDefinition<PeersData> PEERS_RESPONSE_TYPE =
      SerializableTypeDefinition.<PeersData>object()
          .name("GetPeersResponse")
          .withField("data", listOf(PEER_DATA_TYPE), PeersData::getPeers)
          .withField("meta", PEERS_META_TYPE, PeersData::getCount)
          .build();

  private final NetworkDataProvider network;

  public GetPeers(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetPeers(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPeers")
            .summary("Get node network peers")
            .description("Retrieves data about the node's network peers.")
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", PEERS_RESPONSE_TYPE)
            .build());

    this.network = network;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondOk(new PeersData(network.getEth2PeersWithEnr()), NO_CACHE);
  }

  static class PeersData {
    private final List<Eth2PeerWithEnr> peers;
    private final Integer count;

    PeersData(final List<Eth2PeerWithEnr> peers) {
      this.peers = peers;
      this.count = peers.size();
    }

    public List<Eth2PeerWithEnr> getPeers() {
      return peers;
    }

    public Integer getCount() {
      return count;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final PeersData peersData = (PeersData) o;
      return Objects.equals(peers, peersData.peers) && Objects.equals(count, peersData.count);
    }

    @Override
    public int hashCode() {
      return Objects.hash(peers, count);
    }
  }
}

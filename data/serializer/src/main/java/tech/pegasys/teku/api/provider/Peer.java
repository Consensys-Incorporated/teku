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

package tech.pegasys.teku.api.provider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Peer {

  @JsonProperty("peer_id")
  @Schema(
      type = "string",
      description =
          "Cryptographic hash of a peer’s public key. "
              + "'[Read more](https://docs.libp2p.io/concepts/peer-id/)",
      example = "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N")
  public final String peerId;

  @JsonProperty("enr")
  @Schema(
      type = "string",
      nullable = true,
      description =
          "Ethereum node record. Not currently populated. "
              + "[Read more](https://eips.ethereum.org/EIPS/eip-778)",
      example =
          "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrk"
              + "Tfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYp"
              + "Ma2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8")
  public final String enr;

  @JsonProperty("last_seen_p2p_address")
  @Schema(
      type = "string",
      example = "/ip4/7.7.7.7/tcp/4242/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N",
      description =
          "Multiaddr used in last peer connection. "
              + "[Read more](https://libp2p.io/docs/addressing/)")
  public final String address;

  @JsonProperty("state")
  public final State state;

  @JsonProperty("direction")
  public final Direction direction;

  @JsonCreator
  public Peer(
      @JsonProperty("peer_id") final String peerId,
      @JsonProperty("enr") final String enr,
      @JsonProperty("last_seen_p2p_address") final String address,
      @JsonProperty("state") final State state,
      @JsonProperty("direction") final Direction direction) {
    this.peerId = peerId;
    this.enr = enr;
    this.address = address;
    this.state = state;
    this.direction = direction;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Peer peer = (Peer) o;
    return Objects.equals(peerId, peer.peerId)
        && Objects.equals(enr, peer.enr)
        && Objects.equals(address, peer.address)
        && state == peer.state
        && direction == peer.direction;
  }

  @Override
  public int hashCode() {
    return Objects.hash(peerId, enr, address, state, direction);
  }
}

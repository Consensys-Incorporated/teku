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

package tech.pegasys.teku.networking.p2p.discovery;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;

public class DiscoveryPeer {
  private final Bytes publicKey;
  private final Bytes nodeId;
  private final Optional<InetSocketAddress> tcpAddress;
  private final Optional<InetSocketAddress> quicAddress;
  private final Optional<EnrForkId> enrForkId;
  private final SszBitvector persistentAttestationSubnets;
  private final SszBitvector syncCommitteeSubnets;
  private final Optional<Integer> dasCustodySubnetCount;
  private final Optional<Bytes4> nextForkDigest;

  public DiscoveryPeer(
      final Bytes publicKey,
      final Bytes nodeId,
      final Optional<InetSocketAddress> tcpAddress,
      final Optional<InetSocketAddress> quicAddress,
      final Optional<EnrForkId> enrForkId,
      final SszBitvector persistentAttestationSubnets,
      final SszBitvector syncCommitteeSubnets,
      final Optional<Integer> dasCustodySubnetCount,
      final Optional<Bytes4> nextForkDigest) {
    this.publicKey = publicKey;
    this.nodeId = nodeId;
    this.tcpAddress = tcpAddress;
    this.quicAddress = quicAddress;
    this.enrForkId = enrForkId;
    this.persistentAttestationSubnets = persistentAttestationSubnets;
    this.syncCommitteeSubnets = syncCommitteeSubnets;
    this.dasCustodySubnetCount = dasCustodySubnetCount;
    this.nextForkDigest = nextForkDigest;
  }

  public Bytes getPublicKey() {
    return publicKey;
  }

  public Bytes getNodeId() {
    return nodeId;
  }

  public Optional<InetSocketAddress> getTcpAddress() {
    return tcpAddress;
  }

  public Optional<InetSocketAddress> getQuicAddress() {
    return quicAddress;
  }

  public Optional<EnrForkId> getEnrForkId() {
    return enrForkId;
  }

  public SszBitvector getPersistentAttestationSubnets() {
    return persistentAttestationSubnets;
  }

  public SszBitvector getSyncCommitteeSubnets() {
    return syncCommitteeSubnets;
  }

  public Optional<Integer> getDasCustodySubnetCount() {
    return dasCustodySubnetCount;
  }

  public Optional<Bytes4> getNextForkDigest() {
    return nextForkDigest;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DiscoveryPeer)) {
      return false;
    }
    DiscoveryPeer that = (DiscoveryPeer) o;
    return Objects.equal(getPublicKey(), that.getPublicKey())
        && Objects.equal(getTcpAddress(), that.getTcpAddress())
        && Objects.equal(getQuicAddress(), that.getQuicAddress())
        && Objects.equal(getEnrForkId(), that.getEnrForkId())
        && Objects.equal(getPersistentAttestationSubnets(), that.getPersistentAttestationSubnets())
        && Objects.equal(getSyncCommitteeSubnets(), that.getSyncCommitteeSubnets())
        && Objects.equal(getDasCustodySubnetCount(), that.getDasCustodySubnetCount())
        && Objects.equal(getNextForkDigest(), that.getNextForkDigest());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getPublicKey(),
        getTcpAddress(),
        getQuicAddress(),
        getEnrForkId(),
        getPersistentAttestationSubnets(),
        getSyncCommitteeSubnets(),
        getDasCustodySubnetCount(),
        getNextForkDigest());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("tcpAddress", tcpAddress)
        .add("quicAddress", quicAddress)
        .add("enrForkId", enrForkId)
        .add("persistentSubnets", persistentAttestationSubnets)
        .add("syncCommitteeSubnets", syncCommitteeSubnets)
        .add("dasCustodySubnetCount", dasCustodySubnetCount)
        .add("nextForkDigest", nextForkDigest)
        .toString();
  }
}

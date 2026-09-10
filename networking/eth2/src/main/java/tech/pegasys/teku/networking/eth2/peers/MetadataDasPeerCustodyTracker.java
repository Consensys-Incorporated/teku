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

package tech.pegasys.teku.networking.eth2.peers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DasPeerCustodyCountSupplier;

public class MetadataDasPeerCustodyTracker
    implements DasPeerCustodyCountSupplier, PeerConnectedSubscriber<Eth2Peer> {

  private final int maximumCustodyGroupCount;
  private final Map<UInt256, Integer> connectedPeerCustodyGroupCount = new ConcurrentHashMap<>();

  public MetadataDasPeerCustodyTracker(final int maximumCustodyGroupCount) {
    this.maximumCustodyGroupCount = maximumCustodyGroupCount;
  }

  @Override
  public void onConnected(final Eth2Peer peer) {
    peer.subscribeDisconnect((__, ___) -> peerDisconnected(peer));
    peer.subscribeMetadataUpdates(this::onPeerMetadataUpdate);
  }

  private void peerDisconnected(final Eth2Peer peer) {
    peer.getDiscoveryNodeId().ifPresent(connectedPeerCustodyGroupCount::remove);
  }

  private void onPeerMetadataUpdate(final Eth2Peer peer, final MetadataMessage metadata) {
    if (peer.getDiscoveryNodeId().isEmpty()) {
      return;
    }

    final UInt256 nodeId = peer.getDiscoveryNodeId().get();
    metadata
        .getOptionalCustodyGroupCount()
        .filter(cgc -> cgc.isLessThanOrEqualTo(maximumCustodyGroupCount))
        .ifPresent(cgc -> connectedPeerCustodyGroupCount.put(nodeId, cgc.intValue()));
  }

  @Override
  public int getCustodyGroupCountForPeer(final UInt256 nodeId) {
    return connectedPeerCustodyGroupCount.getOrDefault(nodeId, 0);
  }
}

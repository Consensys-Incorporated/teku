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

package tech.pegasys.teku.networking.eth2.peers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStreamPublisher;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRangeReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRootReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnPeerManager;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnReqResp;

public class DataColumnPeerManagerImpl
    implements DataColumnPeerManager,
        PeerConnectedSubscriber<Eth2Peer>,
        BatchDataColumnsByRootReqResp,
        BatchDataColumnsByRangeReqResp {

  private final Subscribers<PeerListener> listeners = Subscribers.create(true);
  private final Map<UInt256, Eth2Peer> connectedPeers = new ConcurrentHashMap<>();

  @Override
  public void onConnected(final Eth2Peer peer) {
    peerConnected(peer);
  }

  private void peerConnected(final Eth2Peer peer) {
    // Peers without a derivable discovery node id (e.g. non-secp256k1 identities) cannot be mapped
    // to DAS custody columns, so they are not registered as data column sidecar providers.
    if (peer.getDiscoveryNodeId().isEmpty()) {
      return;
    }

    final UInt256 nodeId = peer.getDiscoveryNodeId().get();
    connectedPeers.put(nodeId, peer);
    listeners.forEach(
        l -> l.peerConnected(nodeId, () -> peer.getStatus().getEarliestAvailableSlot()));
    peer.subscribeDisconnect((__, ___) -> peerDisconnected(peer));
  }

  private void peerDisconnected(final Eth2Peer peer) {
    if (peer.getDiscoveryNodeId().isEmpty()) {
      return;
    }

    final UInt256 nodeId = peer.getDiscoveryNodeId().get();
    listeners.forEach(l -> l.peerDisconnected(nodeId));
    connectedPeers.remove(nodeId);
  }

  @Override
  public void addPeerListener(final PeerListener listener) {
    listeners.subscribe(listener);
  }

  @Override
  public AsyncStream<DataColumnSidecar> requestDataColumnSidecarsByRoot(
      final UInt256 nodeId, final List<DataColumnsByRootIdentifier> byRootIdentifiers) {
    final Eth2Peer eth2Peer = connectedPeers.get(nodeId);
    final AsyncStreamPublisher<DataColumnSidecar> ret =
        AsyncStream.createPublisher(Integer.MAX_VALUE);
    if (eth2Peer == null) {
      ret.onError(new DataColumnReqResp.DasPeerDisconnectedException());
    } else {
      eth2Peer
          .requestDataColumnSidecarsByRoot(byRootIdentifiers, ret::onNext)
          .finish(__ -> ret.onComplete(), ret::onError);
    }
    return ret;
  }

  @Override
  public AsyncStream<DataColumnSidecar> requestDataColumnSidecarsByRange(
      final UInt256 nodeId,
      final UInt64 startSlot,
      final int slotCount,
      final List<UInt64> columnIndices) {
    final Eth2Peer eth2Peer = connectedPeers.get(nodeId);
    final AsyncStreamPublisher<DataColumnSidecar> ret =
        AsyncStream.createPublisher(Integer.MAX_VALUE);
    if (eth2Peer == null) {
      ret.onError(new DataColumnReqResp.DasPeerDisconnectedException());
    } else {
      eth2Peer
          .requestDataColumnSidecarsByRange(
              startSlot, UInt64.valueOf(slotCount), columnIndices, ret::onNext)
          .finish(__ -> ret.onComplete(), ret::onError);
    }
    return ret;
  }

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    final Eth2Peer eth2Peer = connectedPeers.get(nodeId);
    if (eth2Peer == null) {
      return 0;
    } else {
      return (int) eth2Peer.getAvailableDataColumnSidecarsRequestCount();
    }
  }
}

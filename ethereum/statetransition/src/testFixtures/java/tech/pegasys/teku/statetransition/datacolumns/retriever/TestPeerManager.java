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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class TestPeerManager implements DataColumnPeerManager, DataColumnReqResp {
  private final DataColumnPeerManagerStub dataColumnPeerManagerStub =
      new DataColumnPeerManagerStub();

  private final Map<UInt256, TestPeer> connectedPeers = new HashMap<>();

  public void connectPeer(final TestPeer peer) {
    dataColumnPeerManagerStub.addPeer(peer);
    connectedPeers.put(peer.getNodeId(), peer);
  }

  /**
   * Drops the peer the way the real network layer does: its in-flight requests fail with {@link
   * DasPeerDisconnectedException} and listeners see it leave the custody peer set.
   */
  public void disconnectPeer(final TestPeer peer) {
    connectedPeers.remove(peer.getNodeId());
    peer.onDisconnect();
    dataColumnPeerManagerStub.removePeer(peer);
  }

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      final UInt256 nodeId, final DataColumnSlotAndIdentifier columnIdentifier) {
    final TestPeer peer = connectedPeers.get(nodeId);
    if (peer == null) {
      return SafeFuture.failedFuture(new DasPeerDisconnectedException());
    } else {
      return peer.requestSidecar(columnIdentifier.toDataColumnIdentifier());
    }
  }

  @Override
  public void flush() {}

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    final TestPeer peer = connectedPeers.get(nodeId);
    if (peer == null) {
      return 0;
    } else {
      return peer.getAvailableRequestCount();
    }
  }

  @Override
  public void addPeerListener(final PeerListener listener) {
    dataColumnPeerManagerStub.addPeerListener(listener);
  }
}

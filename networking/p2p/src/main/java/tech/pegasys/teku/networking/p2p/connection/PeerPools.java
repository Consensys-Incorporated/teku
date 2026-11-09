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

package tech.pegasys.teku.networking.p2p.connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class PeerPools {

  private static final PeerConnectionType DEFAULT_POOL = PeerConnectionType.SCORE_BASED;
  private final Map<NodeId, PeerConnectionType> knownSources = new ConcurrentHashMap<>();

  public void addPeerToPool(final NodeId nodeId, final PeerConnectionType peerPool) {
    if (peerPool == DEFAULT_POOL) {
      // No need to record the peer if it's in the default pool anyway.
      forgetPeer(nodeId);
    } else {
      knownSources.put(nodeId, peerPool);
    }
  }

  public void forgetPeer(final NodeId nodeId) {
    knownSources.remove(nodeId);
  }

  public PeerConnectionType getPeerConnectionType(final NodeId nodeId) {
    return knownSources.getOrDefault(nodeId, DEFAULT_POOL);
  }
}

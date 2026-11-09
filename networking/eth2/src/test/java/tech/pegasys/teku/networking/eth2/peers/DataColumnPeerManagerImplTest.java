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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnPeerManager;

class DataColumnPeerManagerImplTest {

  private final DataColumnPeerManagerImpl manager = new DataColumnPeerManagerImpl();
  private final DataColumnPeerManager.PeerListener listener =
      mock(DataColumnPeerManager.PeerListener.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @Test
  void onConnected_withDiscoveryNodeId_registersAndNotifiesListener() {
    final UInt256 nodeId = UInt256.valueOf(1234);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(nodeId));
    manager.addPeerListener(listener);

    manager.onConnected(peer);

    verify(listener).peerConnected(eq(nodeId), any());
    verify(peer).subscribeDisconnect(any());
  }

  @Test
  void onConnected_withoutDiscoveryNodeId_isIgnoredAndDoesNotThrow() {
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.empty());
    manager.addPeerListener(listener);

    manager.onConnected(peer);

    verify(listener, never()).peerConnected(any(), any());
    verify(peer, never()).subscribeDisconnect(any());
  }

  @Test
  void onDisconnected_removesPeerAndNotifiesListener() {
    final UInt256 nodeId = UInt256.valueOf(1234);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(nodeId));
    manager.addPeerListener(listener);
    manager.onConnected(peer);

    final ArgumentCaptor<PeerDisconnectedSubscriber> disconnectSubscriber =
        ArgumentCaptor.forClass(PeerDisconnectedSubscriber.class);
    verify(peer).subscribeDisconnect(disconnectSubscriber.capture());
    disconnectSubscriber.getValue().onDisconnected(Optional.empty(), false);

    verify(listener).peerDisconnected(nodeId);
  }
}

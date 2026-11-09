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

package tech.pegasys.teku.beacon.sync.fetch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class AbstractFetchTaskTest {

  protected final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());
  protected final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  protected final List<Eth2Peer> peers = new ArrayList<>();

  @BeforeEach
  public void setup() {
    when(eth2P2PNetwork.streamPeers()).thenAnswer((invocation) -> peers.stream());
  }

  protected Eth2Peer registerNewPeer(final int id) {
    final Eth2Peer peer = createNewPeer(id);
    peers.add(peer);
    return peer;
  }

  protected Eth2Peer createNewPeer(final int id) {
    final Eth2Peer peer = mock(Eth2Peer.class);
    when(peer.getOutstandingRequests()).thenReturn(0);
    when(peer.getId()).thenReturn(new MockNodeId(id));
    return peer;
  }
}

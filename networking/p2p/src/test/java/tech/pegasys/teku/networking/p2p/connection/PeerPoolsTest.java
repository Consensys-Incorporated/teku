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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;

class PeerPoolsTest {
  private final PeerPools pools = new PeerPools();

  @Test
  void shouldAddPeerToPool() {
    pools.addPeerToPool(new MockNodeId(1), PeerConnectionType.RANDOMLY_SELECTED);
    pools.addPeerToPool(new MockNodeId(2), PeerConnectionType.STATIC);
    pools.addPeerToPool(new MockNodeId(3), PeerConnectionType.SCORE_BASED);
    assertThat(pools.getPeerConnectionType(new MockNodeId(1)))
        .isEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
    assertThat(pools.getPeerConnectionType(new MockNodeId(2))).isEqualTo(PeerConnectionType.STATIC);
    assertThat(pools.getPeerConnectionType(new MockNodeId(3)))
        .isEqualTo(PeerConnectionType.SCORE_BASED);
  }

  @Test
  void shouldDefaultToScoreBasedPool() {
    assertThat(pools.getPeerConnectionType(new MockNodeId(1)))
        .isEqualTo(PeerConnectionType.SCORE_BASED);
  }

  @Test
  void shouldReturnToDefaultPoolWhenPeerForgotten() {
    final MockNodeId nodeId = new MockNodeId(1);
    pools.addPeerToPool(nodeId, PeerConnectionType.RANDOMLY_SELECTED);
    pools.forgetPeer(nodeId);
    assertThat(pools.getPeerConnectionType(nodeId)).isEqualTo(PeerConnectionType.SCORE_BASED);
  }
}

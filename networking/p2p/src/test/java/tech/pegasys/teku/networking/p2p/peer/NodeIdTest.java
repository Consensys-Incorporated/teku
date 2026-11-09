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

package tech.pegasys.teku.networking.p2p.peer;

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.PeerId;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;

class NodeIdTest {

  public static final String ID =
      "075a94fea63205f0893570157ca45487bc47fdcb2714ebe08a6e942cb86005e6";

  @Test
  public void shouldBeEqualToNodeIdsOfDifferentTypes() {
    final PeerId peerId = PeerId.fromHex(ID);
    final NodeId libP2PNodeId = new LibP2PNodeId(peerId);
    final MockNodeId mockNodeId = new MockNodeId(Bytes.fromHexString(ID));
    assertThat(mockNodeId).isEqualTo(libP2PNodeId);
    assertThat(libP2PNodeId).isEqualTo(mockNodeId);
    assertThat(mockNodeId.hashCode()).isEqualTo(libP2PNodeId.hashCode());
  }

  @Test
  public void shouldBeDifferentWhenBytesAreDifferent() {
    final LibP2PNodeId libP2PNodeId = new LibP2PNodeId(PeerId.fromHex(ID));
    final MockNodeId mockNodeId = new MockNodeId(1);
    assertThat(mockNodeId).isNotEqualTo(libP2PNodeId);
    assertThat(libP2PNodeId).isNotEqualTo(mockNodeId);
    assertThat(mockNodeId.hashCode()).isNotEqualTo(libP2PNodeId.hashCode());
  }
}

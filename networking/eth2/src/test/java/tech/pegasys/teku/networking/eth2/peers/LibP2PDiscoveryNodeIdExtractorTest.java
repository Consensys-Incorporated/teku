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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.libp2p.core.crypto.PubKey;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPeer;
import tech.pegasys.teku.networking.p2p.peer.DelegatingPeer;

public class LibP2PDiscoveryNodeIdExtractorTest {
  public final LibP2PDiscoveryNodeIdExtractor nodeIdExtractor =
      new LibP2PDiscoveryNodeIdExtractor();

  @Test
  public void shouldReturnEmptyWhenNonLibP2PPeer() {
    final DefaultEth2Peer nonLibP2PPeer1 = mock(DefaultEth2Peer.class);
    final DelegatingPeer nonLibP2PPeer2 = mock(DelegatingPeer.class);
    assertThat(nodeIdExtractor.calculateDiscoveryNodeId(nonLibP2PPeer1)).isEmpty();
    assertThat(nodeIdExtractor.calculateDiscoveryNodeId(nonLibP2PPeer2)).isEmpty();
  }

  @Test
  public void shouldCalculateNodeIdForLibP2PPeer() {
    final LibP2PPeer libP2PPeer = mock(LibP2PPeer.class);
    final PubKey pubKey = mock(PubKey.class);
    when(pubKey.raw())
        .thenReturn(
            Bytes.fromHexString(
                    "02197B9014C6C0500CF168BD1F17A3B4A1307251849A5ECEEE0B5EBC76A7EBDB37")
                .toArray());
    when(libP2PPeer.getPubKey()).thenReturn(pubKey);
    assertThat(nodeIdExtractor.calculateDiscoveryNodeId(libP2PPeer))
        .contains(
            UInt256.fromHexString(
                "b103ab4e5b2e6d94021547598485baaf1faa0ce5162e1131fb030190cb47269e"));
  }
}

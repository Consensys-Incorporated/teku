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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

class MetadataDasPeerCustodyTrackerTest {

  private static final int NUMBER_OF_CUSTODY_GROUPS = 128;

  private final MetadataDasPeerCustodyTracker tracker =
      new MetadataDasPeerCustodyTracker(NUMBER_OF_CUSTODY_GROUPS);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final MetadataMessage metadata = mock(MetadataMessage.class);

  @Test
  void metadataUpdate_withDiscoveryNodeId_tracksCustodyGroupCount() {
    final UInt256 nodeId = UInt256.valueOf(99);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(nodeId));
    when(metadata.getOptionalCustodyGroupCount()).thenReturn(Optional.of(UInt64.valueOf(7)));

    metadataUpdateSubscriber().onPeerMetadataUpdate(peer, metadata);

    assertThat(tracker.getCustodyGroupCountForPeer(nodeId)).isEqualTo(7);
  }

  @Test
  void metadataUpdate_withCustodyGroupCountExceedingMax_isIgnored() {
    final UInt256 nodeId = UInt256.valueOf(99);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(nodeId));
    when(metadata.getOptionalCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(NUMBER_OF_CUSTODY_GROUPS + 1)));

    metadataUpdateSubscriber().onPeerMetadataUpdate(peer, metadata);

    assertThat(tracker.getCustodyGroupCountForPeer(nodeId)).isZero();
  }

  @Test
  void metadataUpdate_withoutDiscoveryNodeId_isIgnoredAndDoesNotThrow() {
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.empty());

    metadataUpdateSubscriber().onPeerMetadataUpdate(peer, metadata);

    assertThat(tracker.getCustodyGroupCountForPeer(UInt256.ZERO)).isZero();
  }

  private Eth2Peer.PeerMetadataUpdateSubscriber metadataUpdateSubscriber() {
    tracker.onConnected(peer);
    final ArgumentCaptor<Eth2Peer.PeerMetadataUpdateSubscriber> subscriber =
        ArgumentCaptor.forClass(Eth2Peer.PeerMetadataUpdateSubscriber.class);
    verify(peer).subscribeMetadataUpdates(subscriber.capture());
    return subscriber.getValue();
  }
}

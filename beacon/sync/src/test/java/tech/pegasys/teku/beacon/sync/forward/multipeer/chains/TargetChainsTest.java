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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class TargetChainsTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final TargetChains targetChains =
      new TargetChains(mock(SettableLabelledGauge.class), "chains");
  private final SyncSource peer1 = mock(SyncSource.class);
  private final SyncSource peer2 = mock(SyncSource.class);

  @Test
  void onPeerStatus_shouldAddNewChainToTrack() {
    final SlotAndBlockRoot chainHead = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead);

    assertTargetChains(chainWith(chainHead, peer1));
  }

  @Test
  void onPeerStatus_shouldAddPeerToExistingChain() {
    final SlotAndBlockRoot chainHead = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead);
    targetChains.onPeerStatusUpdated(peer2, chainHead);

    assertTargetChains(chainWith(chainHead, peer1, peer2));
  }

  @Test
  void onPeerStatus_shouldAddPeerToDifferentChain() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot chainHead2 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead2);

    assertTargetChains(chainWith(chainHead1, peer1), chainWith(chainHead2, peer2));
  }

  @Test
  void onPeerStatus_shouldMovePeerToDifferentChainWhenStatusChanges() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot chainHead2 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead1);

    // Then peer 1 changes
    targetChains.onPeerStatusUpdated(peer1, chainHead2);

    assertTargetChains(chainWith(chainHead1, peer2), chainWith(chainHead2, peer1));
  }

  @Test
  void onPeerStatus_shouldRemoveChainWhenLastPeerChangesToADifferentChain() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot chainHead2 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer1, chainHead2);

    assertTargetChains(chainWith(chainHead2, peer1));
  }

  @Test
  void onPeerDisconnected_shouldRemovePeerFromChainWhenDisconnected() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead1);

    // Then peer 1 disconnects
    targetChains.onPeerDisconnected(peer1);

    assertTargetChains(chainWith(chainHead1, peer2));
  }

  @Test
  void onPeerStatus_shouldRemoveChainWhenLastPeerDisconnects() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);

    targetChains.onPeerDisconnected(peer1);

    assertTargetChains();
  }

  @Test
  void shouldOrderChainsByPeerCountThenSlot() {
    final SyncSource peer3 = mock(SyncSource.class);
    final SyncSource peer4 = mock(SyncSource.class);
    final SlotAndBlockRoot chainHead1 =
        new SlotAndBlockRoot(UInt64.valueOf(100), dataStructureUtil.randomBytes32());
    final SlotAndBlockRoot chainHead2 =
        new SlotAndBlockRoot(UInt64.valueOf(100), dataStructureUtil.randomBytes32());
    final SlotAndBlockRoot chainHead3 =
        new SlotAndBlockRoot(UInt64.valueOf(102), dataStructureUtil.randomBytes32());

    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead1);
    targetChains.onPeerStatusUpdated(peer3, chainHead2);
    targetChains.onPeerStatusUpdated(peer4, chainHead3);

    assertThat(targetChains.streamChains())
        .containsExactly(
            chainWith(chainHead1, peer1, peer2),
            chainWith(chainHead3, peer4),
            chainWith(chainHead2, peer3));
  }

  private void assertTargetChains(final TargetChain... expected) {
    assertThat(targetChains.streamChains()).containsExactlyInAnyOrder(expected);
  }
}

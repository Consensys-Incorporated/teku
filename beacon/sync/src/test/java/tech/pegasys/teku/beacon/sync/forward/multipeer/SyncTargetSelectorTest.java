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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.SyncTargetSelector.MIN_PENDING_BLOCKS;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChains;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncTargetSelectorTest {

  private static final int SYNC_THRESHOLD = 40;
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private UInt64 suitableSyncTargetSlot;

  @SuppressWarnings("unchecked")
  private final PendingPool<SignedBeaconBlock> pendingBlocks = mock(PendingPool.class);

  private final TargetChains finalizedChains =
      new TargetChains(mock(SettableLabelledGauge.class), "finalized");
  private final TargetChains nonfinalizedChains =
      new TargetChains(mock(SettableLabelledGauge.class), "nonfinalized");

  private final SyncTargetSelector selector =
      new SyncTargetSelector(
          recentChainData, pendingBlocks, finalizedChains, nonfinalizedChains, SYNC_THRESHOLD);

  @BeforeEach
  void setUp() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.valueOf(50));
    when(recentChainData.getHeadSlot()).thenReturn(UInt64.valueOf(500));
    suitableSyncTargetSlot = recentChainData.getHeadSlot().plus(SYNC_THRESHOLD + 1);
  }

  @Test
  void finalized_shouldHaveNoSyncTargetWhenNoChainsAvailable() {
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void finalized_shouldNotSelectFinalizedChainWhenHeadBlockAlreadyImported() {
    final SlotAndBlockRoot finalizedChainHead = addPeerWithFinalizedSlot(suitableSyncTargetSlot);
    when(recentChainData.containsBlock(finalizedChainHead.getBlockRoot())).thenReturn(true);
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void finalized_shouldSelectFinalizedChainWithSlotSufficientlyAboveLocalChainHead() {
    final SlotAndBlockRoot finalizedChainHead = addPeerWithFinalizedSlot(suitableSyncTargetSlot);
    assertThat(selector.selectSyncTarget(Optional.empty()))
        .contains(finalizedTarget(finalizedChainHead));
  }

  @Test
  void finalized_shouldNotSelectFinalizedChainWhenSlotNotSufficientlyAboveLocalChainHead() {
    addPeerWithFinalizedSlot(suitableSyncTargetSlot.minus(1));
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void finalized_shouldNotSelectFinalizedChainWhenSlotEqualToLocalChainHead() {
    addPeerWithFinalizedSlot(recentChainData.getHeadSlot());
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void finalized_shouldNotSelectFinalizedChainWhenSlotBelowLocalChainHead() {
    addPeerWithFinalizedSlot(recentChainData.getHeadSlot().minus(1));
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void finalized_shouldSelectFinalizedChainWithinSyncThresholdIfAlreadySyncing() {
    final SlotAndBlockRoot chainHead =
        addPeerWithFinalizedSlot(recentChainData.getHeadSlot().plus(1));
    assertThat(
            selector.selectSyncTarget(
                Optional.of(
                    SyncTarget.finalizedTarget(
                        new TargetChain(dataStructureUtil.randomSlotAndBlockRoot())))))
        .contains(finalizedTarget(chainHead));
  }

  @Test
  void finalized_shouldNotSwitchFinalizedChainsWhenEqualNumberOfPeersToExistingTarget() {
    final SlotAndBlockRoot currentChain = addPeerWithFinalizedSlot(suitableSyncTargetSlot);
    addPeerWithFinalizedSlot(recentChainData.getHeadSlot().plus(SYNC_THRESHOLD + 1));

    final SyncTarget currentSyncTarget = finalizedTarget(currentChain);
    assertThat(selector.selectSyncTarget(Optional.of(currentSyncTarget)))
        .contains(currentSyncTarget);
  }

  @Test
  void finalized_shouldSwitchToBetterFinalizedChainWhenAvailable() {
    final SlotAndBlockRoot currentChain = addPeerWithFinalizedSlot(suitableSyncTargetSlot);
    final SlotAndBlockRoot betterChain = addPeerWithFinalizedSlot(suitableSyncTargetSlot);
    addPeerToFinalizedChain(betterChain);

    // Switch to better chain because it has more peers
    assertThat(selector.selectSyncTarget(Optional.of(finalizedTarget(currentChain))))
        .contains(finalizedTarget(betterChain));
  }

  @Test
  void finalized_shouldSwitchBackToFinalizedSyncWhenSuitablyBetterFinalizedChainBecomesAvailable() {
    final SlotAndBlockRoot nonfinalizedChain =
        addPeerWithNonfinalizedSlot(suitableSyncTargetSlot.plus(10));
    addPeerToNonfinalizedChain(nonfinalizedChain);
    final SlotAndBlockRoot finalizedChain = addPeerWithFinalizedSlot(suitableSyncTargetSlot);

    // Should switch to the finalized chain even though it has fewer peers
    assertThat(selector.selectSyncTarget(Optional.of(nonfinalizedTarget(nonfinalizedChain))))
        .contains(finalizedTarget(finalizedChain));
  }

  @Test
  void nonfinalized_shouldNotSwitchToNonfinalizedChainWhenFinalizedChainStillSyncing() {
    final SlotAndBlockRoot finalizedHead = addPeerWithFinalizedSlot(suitableSyncTargetSlot);
    addPeerWithNonfinalizedSlot(suitableSyncTargetSlot.plus(10));

    assertThat(selector.selectSyncTarget(Optional.of(finalizedTarget(finalizedHead))))
        .contains(finalizedTarget(finalizedHead));
  }

  @Test
  void nonfinalized_shouldSelectNonFinalizedChainWhenNoSuitableFinalizedChain() {
    // Finalized chain that isn't suitable
    addPeerWithFinalizedSlot(suitableSyncTargetSlot.minus(1));

    final SlotAndBlockRoot expectedChain = addPeerWithNonfinalizedSlot(suitableSyncTargetSlot);
    assertThat(selector.selectSyncTarget(Optional.empty()))
        .contains(nonfinalizedTarget(expectedChain));
  }

  @Test
  void nonfinalized_shouldNotSelectNonfinalizedChainWhenSlotNotSufficientlyAboveLocalChainHead() {
    addPeerWithNonfinalizedSlot(suitableSyncTargetSlot.minus(1));
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSelectNonfinalizedChainWhenSlotEqualToLocalChainHead() {
    addPeerWithNonfinalizedSlot(recentChainData.getHeadSlot());
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSelectNonfinalizedChainWhenSlotBelowLocalChainHead() {
    addPeerWithNonfinalizedSlot(recentChainData.getHeadSlot().minus(1));
    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  @Test
  void nonfinalized_shouldSelectNonfinalizedChainWithinSyncThresholdIfAlreadySyncing() {
    final SlotAndBlockRoot chainHead =
        addPeerWithNonfinalizedSlot(recentChainData.getHeadSlot().plus(1));
    assertThat(
            selector.selectSyncTarget(
                Optional.of(
                    SyncTarget.nonfinalizedTarget(
                        new TargetChain(dataStructureUtil.randomSlotAndBlockRoot())))))
        .contains(nonfinalizedTarget(chainHead));
  }

  @Test
  void nonfinalized_shouldSwitchBackToNonfinalizedSyncWhenSuitableCandidateBecomesAvailable() {
    final SlotAndBlockRoot speculativeChain =
        addPeerWithNonfinalizedSlot(recentChainData.getHeadSlot());
    final SlotAndBlockRoot suitableChain = addPeerWithNonfinalizedSlot(suitableSyncTargetSlot);

    assertThat(selector.selectSyncTarget(Optional.of(speculativeTarget(speculativeChain))))
        .contains(nonfinalizedTarget(suitableChain));
  }

  @Test
  void nonfinalized_shouldNotSwitchBackToNonfinalizedSyncWhenChainsAreNotFarEnoughAhead() {
    final SlotAndBlockRoot speculativeChain =
        addPeerWithNonfinalizedSlot(recentChainData.getHeadSlot());
    addPeerWithNonfinalizedSlot(suitableSyncTargetSlot.minus(1));

    // Note: speculative syncs are done without triggering sync mode
    assertThat(selector.selectSyncTarget(Optional.of(speculativeTarget(speculativeChain))))
        .isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSwitchNonfinalizedChainsWhenEqualNumberOfPeersToExistingTarget() {
    final SlotAndBlockRoot currentChain = addPeerWithNonfinalizedSlot(suitableSyncTargetSlot);
    addPeerWithNonfinalizedSlot(recentChainData.getHeadSlot().plus(SYNC_THRESHOLD + 1));

    final SyncTarget currentSyncTarget = nonfinalizedTarget(currentChain);
    assertThat(selector.selectSyncTarget(Optional.of(currentSyncTarget)))
        .contains(currentSyncTarget);
  }

  @Test
  void speculative_shouldNotSpeculativelySyncChainWhenHeadAlreadyImported() {
    final SlotAndBlockRoot chain =
        addPendingBlocks(recentChainData.getHeadSlot(), MIN_PENDING_BLOCKS);
    addPeerToNonfinalizedChain(chain);
    when(recentChainData.containsBlock(chain.getBlockRoot())).thenReturn(true);

    assertThat(selector.selectSyncTarget(Optional.of(speculativeTarget(chain)))).isEmpty();
  }

  @Test
  void speculative_shouldStartSpeculativeSyncWhenEnoughBlocksWaitingInPendingPool() {
    final SlotAndBlockRoot chainHead =
        addPendingBlocks(recentChainData.getHeadSlot(), MIN_PENDING_BLOCKS);
    addPeerToNonfinalizedChain(chainHead);
    assertThat(selector.selectSyncTarget(Optional.empty())).contains(speculativeTarget(chainHead));
  }

  @Test
  void speculative_shouldNotSwitchSpeculativeTargetWhenEqualNumberOfPeersToExistingTarget() {
    final SlotAndBlockRoot currentChain =
        addPendingBlocks(recentChainData.getHeadSlot(), MIN_PENDING_BLOCKS);
    final SlotAndBlockRoot alternateChain =
        addPendingBlocks(recentChainData.getHeadSlot().plus(10), MIN_PENDING_BLOCKS);
    addPeerToNonfinalizedChain(currentChain);
    addPeerToNonfinalizedChain(alternateChain);

    final SyncTarget currentSyncTarget = speculativeTarget(currentChain);
    assertThat(selector.selectSyncTarget(Optional.of(currentSyncTarget)))
        .contains(currentSyncTarget);
  }

  @Test
  void speculative_shouldNotStartSpeculativeSyncIfNotEnoughBlocksPending() {
    final SlotAndBlockRoot chainHead =
        addPendingBlocks(recentChainData.getHeadSlot(), MIN_PENDING_BLOCKS - 1);
    addPeerToNonfinalizedChain(chainHead);

    assertThat(selector.selectSyncTarget(Optional.empty())).isEmpty();
  }

  private SyncTarget finalizedTarget(final SlotAndBlockRoot chainHead) {
    return SyncTarget.finalizedTarget(getTargetChain(chainHead));
  }

  private SyncTarget nonfinalizedTarget(final SlotAndBlockRoot chainHead) {
    return SyncTarget.nonfinalizedTarget(getTargetChain(chainHead));
  }

  private SyncTarget speculativeTarget(final SlotAndBlockRoot chainHead) {
    return SyncTarget.speculativeTarget(getTargetChain(chainHead));
  }

  private SlotAndBlockRoot addPendingBlocks(
      final UInt64 headBlockSlot, final int numberOfPendingBlocks) {
    // +1 because the block count is inclusive of the earliest and head blocks
    final long earliestBlockSlot = headBlockSlot.longValue() - numberOfPendingBlocks + 1;
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(earliestBlockSlot - 1);
    for (long slot = earliestBlockSlot; slot <= headBlockSlot.longValue(); slot++) {
      block = dataStructureUtil.randomSignedBeaconBlock(slot, block.getRoot());
      when(pendingBlocks.get(block.getRoot())).thenReturn(Optional.of(block));
    }
    return new SlotAndBlockRoot(block.getSlot(), block.getRoot());
  }

  private TargetChain getTargetChain(final SlotAndBlockRoot chainHead) {
    return Stream.concat(finalizedChains.streamChains(), nonfinalizedChains.streamChains())
        .filter(chain -> chain.getChainHead().equals(chainHead))
        .findFirst()
        .orElseThrow();
  }

  private SlotAndBlockRoot addPeerWithFinalizedSlot(final UInt64 slot) {
    final SlotAndBlockRoot chainHead =
        new SlotAndBlockRoot(slot, dataStructureUtil.randomBytes32());
    addPeerToFinalizedChain(chainHead);
    return chainHead;
  }

  private SlotAndBlockRoot addPeerWithNonfinalizedSlot(final UInt64 slot) {
    final SlotAndBlockRoot chainHead =
        new SlotAndBlockRoot(slot, dataStructureUtil.randomBytes32());
    addPeerToNonfinalizedChain(chainHead);
    return chainHead;
  }

  private void addPeerToFinalizedChain(final SlotAndBlockRoot chainHead) {
    addPeerToChain(finalizedChains, chainHead);
  }

  private void addPeerToNonfinalizedChain(final SlotAndBlockRoot chainHead) {
    addPeerToChain(nonfinalizedChains, chainHead);
  }

  private void addPeerToChain(final TargetChains targetChains, final SlotAndBlockRoot chainHead) {
    targetChains.onPeerStatusUpdated(mock(SyncSource.class), chainHead);
  }
}

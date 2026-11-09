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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoice.BLOCK_CREATION_TOLERANCE_MS;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.multipeer.Sync.SyncProgress;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.EventLogger.TargetChain;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.EpochCachePrimer;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.coordinator.FutureBlockProductionPreparationTrigger;

public class SlotProcessorTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
  private final int secondsPerSlot = millisToSeconds(millisPerSlot).intValue();

  private final BeaconState beaconState = dataStructureUtil.randomBeaconState(ZERO);
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final SyncService syncService = mock(SyncService.class);
  private final ForwardSync forwardSync = mock(ForwardSync.class);
  private final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);
  private final FutureBlockProductionPreparationTrigger blockProductionPreparationTrigger =
      mock(FutureBlockProductionPreparationTrigger.class);
  private final ForkChoiceNotifier forkChoiceNotifier = new NoopForkChoiceNotifier();
  private final Eth2P2PNetwork p2pNetwork = mock(Eth2P2PNetwork.class);
  private final SlotEventsChannel slotEventsChannel = mock(SlotEventsChannel.class);
  private final EpochCachePrimer epochCachePrimer = mock(EpochCachePrimer.class);
  private final SlotProcessor slotProcessor = createSlotProcessor(spec);
  private final UInt64 genesisTime = beaconState.getGenesisTime();
  private final UInt64 genesisTimeMillis = secondsToMillis(genesisTime);
  private final UInt64 desiredSlot = UInt64.valueOf(100L);

  private SlotProcessor createSlotProcessor(final Spec spec) {
    return new SlotProcessor(
        spec,
        recentChainData,
        syncService,
        forkChoiceTrigger,
        blockProductionPreparationTrigger,
        forkChoiceNotifier,
        p2pNetwork,
        slotEventsChannel,
        epochCachePrimer,
        eventLogger);
  }

  @BeforeEach
  public void setup() {
    recentChainData.initializeFromGenesis(beaconState, UInt64.ZERO);
    when(syncService.getForwardSync()).thenReturn(forwardSync);
    when(forwardSync.getSyncProgress()).thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  public void isNextSlotDue_shouldDetectNextSlotIsNotDue() {
    slotProcessor.setCurrentSlot(desiredSlot.plus(ONE));
    final UInt64 currentTime = spec.computeTimeAtSlot(desiredSlot, genesisTime);
    assertThat(slotProcessor.isNextSlotDue(currentTime, genesisTime)).isFalse();
  }

  @Test
  public void isNextSlotDue_shouldDetectNextSlotIsDue() {
    slotProcessor.setCurrentSlot(desiredSlot);
    final UInt64 currentTimeMillis =
        spec.computeTimeMillisAtSlot(desiredSlot.plus(ONE), genesisTimeMillis);
    assertThat(slotProcessor.isNextSlotDue(currentTimeMillis, genesisTimeMillis)).isTrue();
  }

  @Test
  public void isProcessingDueForSlot_shouldHandleNull() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, null)).isTrue();
  }

  @Test
  public void isProcessingDueForSlot_shouldReturnFalseIfPositionMatches() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, desiredSlot)).isFalse();
  }

  @Test
  public void isProcessingDueForSlot_shouldReturnTrueIfPositionIsBehind() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, desiredSlot.minus(ONE))).isTrue();
  }

  @Test
  public void isProcessingDueForSlot_shouldReturnFalseIfPositionIsAhead() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.isProcessingDueForSlot(desiredSlot, desiredSlot.plus(ONE))).isFalse();
  }

  @Test
  public void onTick_shouldNotProcessPreGenesis() {
    slotProcessor.onTick(genesisTimeMillis.minus(1000), Optional.empty());
  }

  @Test
  public void onTick_shouldExitBeforeOtherProcessingIfSyncing() {
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(genesisTimeMillis, Optional.empty());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ONE);

    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);

    verify(syncService).getCurrentSyncState();
    verify(eventLogger).syncEvent(ZERO, ZERO, 1, Optional.empty());
  }

  @Test
  public void onTick_shouldExitBeforeOtherProcessingIfOptimisticSyncing() {
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.OPTIMISTIC_SYNCING);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(genesisTimeMillis, Optional.empty());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ONE);

    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);

    verify(syncService).getCurrentSyncState();
    verify(eventLogger).syncEvent(ZERO, ZERO, 1, Optional.empty());
  }

  @Test
  public void onTick_shouldChangeSyncingMessageWhenWaitingForExecutionSync() {
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.AWAITING_EL);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(genesisTimeMillis, Optional.empty());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ONE);

    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);

    verify(syncService).getCurrentSyncState();
    verify(eventLogger).syncEventAwaitingEL(ZERO, ZERO, 1);
  }

  @Test
  public void onTick_shouldChangeSyncingMessagesIfForwardSyncProgressIsAvailable() {
    final SyncProgress syncProgress =
        new SyncProgress(
            ZERO, ONE, 5, 100, 4, 25, 1, true, dataStructureUtil.randomSlotAndBlockRoot(), 1);

    when(forwardSync.getSyncProgress())
        .thenReturn(SafeFuture.completedFuture(Optional.of(syncProgress)));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(genesisTimeMillis, Optional.empty());

    verify(eventLogger)
        .syncEvent(
            ZERO,
            ZERO,
            1,
            Optional.of(
                new TargetChain(
                    syncProgress.targetChainHead().getBlockRoot(),
                    syncProgress.targetChainHead().getSlot(),
                    syncProgress.targetChainPeers())));
    verify(eventLogger)
        .syncProgressEvent(
            syncProgress.fromSlot(),
            syncProgress.toSlot(),
            syncProgress.batches(),
            syncProgress.downloadingSlots(),
            syncProgress.downloadingBatches(),
            syncProgress.readySlots(),
            syncProgress.readyBatches(),
            syncProgress.importing());
  }

  @Test
  public void onTick_shouldRunStartSlotAtGenesis() {
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(genesisTimeMillis, Optional.empty());
    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(ZERO);
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    verify(eventLogger)
        .epochEvent(
            ZERO,
            recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ZERO);
  }

  @Test
  public void onTick_shouldSkipForward() {
    final UInt64 slot = UInt64.valueOf(secondsPerSlot * 100L);
    slotProcessor.setOnTickSlotAttestation(slot);
    ArgumentCaptor<UInt64> captor = ArgumentCaptor.forClass(UInt64.class);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    UInt64 slotProcessingTimeMillis = genesisTimeMillis.plus(slot.times(millisPerSlot));
    // slot processor starts at slot 0, but fast forwards to slot 100
    slotProcessor.onTick(slotProcessingTimeMillis, Optional.empty());
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(slot);

    // slot event to notify we're at slot 100
    verify(slotEventsChannel).onSlot(captor.capture());
    assertThat(captor.getValue()).isEqualTo(slot);

    // event logger reports slot 100
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    verify(p2pNetwork).onEpoch(epoch);
    verify(eventLogger)
        .epochEvent(
            epoch,
            recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot());

    // node slots missed event to indicate that slots were missed to catch up
    verify(eventLogger).nodeSlotsMissed(ZERO, slot);
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  public void onTick_shouldRunAttestationsDuringProcessing(final Eth2Network eth2Network) {
    Spec spec = TestSpecFactory.create(SpecMilestone.PHASE0, eth2Network);
    int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();

    SlotProcessor slotProcessor = createSlotProcessor(spec);

    // skip the slot start
    final UInt64 slot = slotProcessor.getNodeSlot().getValue();
    slotProcessor.setOnTickSlotStart(slot);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    slotProcessor.onTick(genesisTimeMillis.plus(millisPerSlot / 3L), Optional.empty());

    final Checkpoint justifiedCheckpoint = recentChainData.getStore().getJustifiedCheckpoint();
    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    final MinimalBeaconBlockSummary headBlock = recentChainData.getHeadBlock().orElseThrow();
    verify(eventLogger)
        .slotEvent(
            ZERO,
            recentChainData.getHeadSlot(),
            headBlock.getRoot(),
            justifiedCheckpoint.getEpoch(),
            finalizedCheckpoint.getEpoch(),
            1);
    verify(forkChoiceTrigger).onAttestationsDueForSlot(slot);
  }

  @Test
  void onTick_shouldExitIfUpToDate() {
    slotProcessor.setOnTickSlotStart(ZERO);
    slotProcessor.setOnTickSlotAttestation(ZERO);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    slotProcessor.onTick(genesisTimeMillis, Optional.empty());

    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(ZERO);
  }

  @Test
  public void nodeSlot_shouldStartAtZero() {
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void setNodeSlot_shouldAlterNodeSlotValue() {
    slotProcessor.setCurrentSlot(desiredSlot);
    assertThat(slotProcessor.getNodeSlot().getValue()).isEqualTo(desiredSlot);
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void shouldProgressThroughMultipleSlots(final Eth2Network eth2Network) {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(p2pNetwork.getPeerCount()).thenReturn(1);

    Spec spec = TestSpecFactory.create(SpecMilestone.PHASE0, eth2Network);
    int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();

    SlotProcessor slotProcessor = createSlotProcessor(spec);

    // Slot 0 start
    slotProcessor.onTick(genesisTimeMillis, Optional.empty());
    verify(slotEventsChannel).onSlot(ZERO);
    // Attestation due
    slotProcessor.onTick(genesisTimeMillis.plus(oneThirdMillis(millisPerSlot)), Optional.empty());
    verify(forkChoiceTrigger).onAttestationsDueForSlot(ZERO);
    // Block preparation due
    slotProcessor.onTick(
        genesisTimeMillis.plus(millisPerSlot - BLOCK_CREATION_TOLERANCE_MS), Optional.empty());
    verify(blockProductionPreparationTrigger).onFutureBlockProductionPreparationDue(ZERO);

    // Slot 2 start
    final UInt64 slot1Start = genesisTimeMillis.plus(millisPerSlot);
    slotProcessor.onTick(slot1Start, Optional.empty());
    verify(slotEventsChannel).onSlot(ONE);
    // Attestation due
    slotProcessor.onTick(slot1Start.plus(oneThirdMillis(millisPerSlot)), Optional.empty());
    verify(forkChoiceTrigger).onAttestationsDueForSlot(ONE);

    // Block preparation due
    slotProcessor.onTick(
        slot1Start.plus(millisPerSlot - BLOCK_CREATION_TOLERANCE_MS), Optional.empty());
    verify(blockProductionPreparationTrigger).onFutureBlockProductionPreparationDue(ONE);
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void shouldPrecomputeEpochTransitionJustBeforeFirstSlotOfNextEpoch(
      final Eth2Network eth2Network) {
    final RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getGenesisTimeMillis()).thenReturn(genesisTimeMillis);
    final Optional<MinimalBeaconBlockSummary> headBlock =
        storageSystem.recentChainData().getHeadBlock();
    when(recentChainData.getHeadBlock()).thenReturn(headBlock);
    when(recentChainData.retrieveBlockState(any(SlotAndBlockRoot.class)))
        .thenReturn(new SafeFuture<>());
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    final Spec spec = TestSpecFactory.create(SpecMilestone.PHASE0, eth2Network);
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();

    final SlotProcessor slotProcessor =
        new SlotProcessor(
            spec,
            recentChainData,
            syncService,
            forkChoiceTrigger,
            blockProductionPreparationTrigger,
            forkChoiceNotifier,
            p2pNetwork,
            slotEventsChannel,
            epochCachePrimer,
            eventLogger);

    final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();

    final UInt64 currentSlot = UInt64.valueOf(slotsPerEpoch - 2);
    slotProcessor.setCurrentSlot(currentSlot);
    final UInt64 nextEpochSlotMinusTwo =
        secondsToMillis(spec.computeTimeAtSlot(currentSlot, genesisTime));
    final UInt64 nextEpochSlotMinusOne =
        secondsToMillis(spec.computeTimeAtSlot(currentSlot.plus(1), genesisTime));

    // Progress through to end of initial epoch
    slotProcessor.onTick(nextEpochSlotMinusTwo, Optional.empty());
    slotProcessor.onTick(
        nextEpochSlotMinusTwo.plus(oneThirdMillis(millisPerSlot)), Optional.empty());
    slotProcessor.onTick(
        nextEpochSlotMinusTwo.plus(oneThirdMillis(millisPerSlot) * 2L), Optional.empty());
    slotProcessor.onTick(nextEpochSlotMinusOne, Optional.empty());
    slotProcessor.onTick(
        nextEpochSlotMinusOne.plus(oneThirdMillis(millisPerSlot)), Optional.empty());

    // Shouldn't have precomputed epoch transition yet.
    verify(recentChainData, never()).retrieveBlockState(any(SlotAndBlockRoot.class));

    // But just before the last slot of the epoch ends, we should precompute the next epoch
    slotProcessor.onTick(
        nextEpochSlotMinusOne.plus((oneThirdMillis(millisPerSlot) * 2L) + 5L), Optional.empty());
    verify(epochCachePrimer).primeCacheForEpoch(ONE);

    // Should not repeat computation
    slotProcessor.onTick(
        nextEpochSlotMinusOne.plus(oneThirdMillis(millisPerSlot) * 2 + 1000), Optional.empty());
    slotProcessor.onTick(
        nextEpochSlotMinusOne.plus(oneThirdMillis(millisPerSlot) * 2 + 2000), Optional.empty());
    verify(recentChainData, atMostOnce()).retrieveBlockState(any(SlotAndBlockRoot.class));
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void blockPreparationDueShouldNotHappenTooEarly(final Eth2Network eth2Network) {
    final Spec spec = TestSpecFactory.create(SpecMilestone.PHASE0, eth2Network);
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();

    // let's check that we do not trigger block preparation too early compared to slot time
    assertThat(oneThirdMillis(millisPerSlot) - BLOCK_CREATION_TOLERANCE_MS).isGreaterThan(1000);
  }

  private long oneThirdMillis(final long millis) {
    return millis / 3L;
  }
}

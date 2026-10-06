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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoice.BLOCK_CREATION_TOLERANCE_MS;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.EventLogger.TargetChain;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.statetransition.EpochCachePrimer;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessingPerformance;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.FutureBlockProductionPreparationTrigger;

public class SlotProcessor {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final SyncService syncService;
  private final ForkChoiceTrigger forkChoiceTrigger;
  private final FutureBlockProductionPreparationTrigger futureBlockProductionPreparationTrigger;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final Eth2P2PNetwork p2pNetwork;
  private final SlotEventsChannel slotEventsChannelPublisher;
  private final NodeSlot nodeSlot = new NodeSlot(ZERO);
  private final EpochCachePrimer epochCachePrimer;
  private final EventLogger eventLog;

  private volatile UInt64 onTickSlotStart;
  private volatile UInt64 onTickSlotAttestation;
  private volatile UInt64 onTickSlotPayloadAttestation;
  private volatile UInt64 onTickEpochPrecompute;
  private volatile UInt64 onTickFutureBlockProductionPreparation;

  @VisibleForTesting
  SlotProcessor(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncService syncService,
      final ForkChoiceTrigger forkChoiceTrigger,
      final FutureBlockProductionPreparationTrigger futureBlockProductionPreparationTrigger,
      final ForkChoiceNotifier forkChoiceNotifier,
      final Eth2P2PNetwork p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EpochCachePrimer epochCachePrimer,
      final EventLogger eventLogger) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.syncService = syncService;
    this.forkChoiceTrigger = forkChoiceTrigger;
    this.futureBlockProductionPreparationTrigger = futureBlockProductionPreparationTrigger;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.p2pNetwork = p2pNetwork;
    this.slotEventsChannelPublisher = slotEventsChannelPublisher;
    this.epochCachePrimer = epochCachePrimer;
    this.eventLog = eventLogger;
  }

  public SlotProcessor(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncService syncService,
      final ForkChoiceTrigger forkChoiceTrigger,
      final FutureBlockProductionPreparationTrigger futureBlockProductionPreparationTrigger,
      final ForkChoiceNotifier forkChoiceNotifier,
      final Eth2P2PNetwork p2pNetwork,
      final SlotEventsChannel slotEventsChannelPublisher,
      final EpochCachePrimer epochCachePrimer) {
    this(
        spec,
        recentChainData,
        syncService,
        forkChoiceTrigger,
        futureBlockProductionPreparationTrigger,
        forkChoiceNotifier,
        p2pNetwork,
        slotEventsChannelPublisher,
        epochCachePrimer,
        EventLogger.EVENT_LOG);
  }

  public NodeSlot getNodeSlot() {
    return nodeSlot;
  }

  public void setCurrentSlot(final UInt64 slot) {
    slotEventsChannelPublisher.onSlot(slot);
    nodeSlot.setValue(slot);
  }

  public void onTick(
      final UInt64 currentTimeMillis, final Optional<TickProcessingPerformance> performanceRecord) {

    final UInt64 genesisTimeMillis = recentChainData.getGenesisTimeMillis();
    if (currentTimeMillis.isLessThan(genesisTimeMillis)) {
      return;
    }
    final SyncState currentSyncState = syncService.getCurrentSyncState();
    if (isNextSlotDue(currentTimeMillis, genesisTimeMillis) && !currentSyncState.isInSync()) {
      processSlotWhileSyncing(currentSyncState);
      nodeSlot.inc();
      return;
    }

    final UInt64 calculatedSlot =
        spec.getCurrentSlotFromTimeMillis(currentTimeMillis, genesisTimeMillis);
    // tolerate 1 slot difference, not more
    if (calculatedSlot.isGreaterThan(nodeSlot.getValue().plus(ONE))) {
      eventLog.nodeSlotsMissed(nodeSlot.getValue(), calculatedSlot);
      nodeSlot.setValue(calculatedSlot);
    }

    final UInt64 epoch = spec.computeEpochAtSlot(nodeSlot.getValue());
    final UInt64 nodeSlotStartTimeMillis =
        spec.computeTimeMillisAtSlot(nodeSlot.getValue(), genesisTimeMillis);
    final MiscHelpers miscHelpers = spec.atSlot(nodeSlot.getValue()).miscHelpers();

    if (isSlotStartDue(calculatedSlot)) {
      processSlotStart(epoch);
      performanceRecord.ifPresent(TickProcessingPerformance::startSlotComplete);
    }

    if (isSlotAttestationDue(calculatedSlot, currentTimeMillis, nodeSlotStartTimeMillis)) {
      processSlotAttestation(performanceRecord);
      if (miscHelpers.shouldIncrementNodeSlotWhenAttestationsAreDue()) {
        nodeSlot.inc();
      }
      performanceRecord.ifPresent(TickProcessingPerformance::attestationsDueComplete);
    }

    if (isSlotPayloadAttestationDue(calculatedSlot, currentTimeMillis, nodeSlotStartTimeMillis)) {
      processSlotPayloadAttestation();
      if (miscHelpers.shouldIncrementNodeSlotWhenPayloadAttestationsAreDue()) {
        nodeSlot.inc();
      }
      performanceRecord.ifPresent(TickProcessingPerformance::payloadAttestationsDueComplete);
    }

    if (isEpochPrecalculationDue(epoch, currentTimeMillis, genesisTimeMillis)) {
      processEpochPrecompute(epoch);
      performanceRecord.ifPresent(TickProcessingPerformance::precomputeEpochComplete);
    }

    if (isFutureBlockProductionPreparationDue(
        calculatedSlot, currentTimeMillis, genesisTimeMillis)) {
      onTickFutureBlockProductionPreparation = calculatedSlot;
      futureBlockProductionPreparationTrigger.onFutureBlockProductionPreparationDue(calculatedSlot);
    }
  }

  private void processEpochPrecompute(final UInt64 epoch) {
    onTickEpochPrecompute = spec.computeStartSlotAtEpoch(epoch);
    epochCachePrimer.primeCacheForEpoch(epoch);
  }

  private void processSlotWhileSyncing(final SyncState currentSyncState) {
    final UInt64 slot = nodeSlot.getValue();
    this.forkChoiceTrigger.onSlotStartedWhileSyncing(slot);
    if (currentSyncState == SyncState.AWAITING_EL) {
      eventLog.syncEventAwaitingEL(slot, recentChainData.getHeadSlot(), p2pNetwork.getPeerCount());
    } else {
      syncService
          .getForwardSync()
          .getSyncProgress()
          .finish(
              maybeSyncProgress ->
                  maybeSyncProgress.ifPresentOrElse(
                      syncProgress -> {
                        eventLog.syncEvent(
                            slot,
                            recentChainData.getHeadSlot(),
                            p2pNetwork.getPeerCount(),
                            Optional.of(
                                new TargetChain(
                                    syncProgress.targetChainHead().getBlockRoot(),
                                    syncProgress.targetChainHead().getSlot(),
                                    syncProgress.targetChainPeers())));
                        eventLog.syncProgressEvent(
                            syncProgress.fromSlot(),
                            syncProgress.toSlot(),
                            syncProgress.batches(),
                            syncProgress.downloadingSlots(),
                            syncProgress.downloadingBatches(),
                            syncProgress.readySlots(),
                            syncProgress.readyBatches(),
                            syncProgress.importing());
                      },
                      () ->
                          eventLog.syncEvent(
                              slot,
                              recentChainData.getHeadSlot(),
                              p2pNetwork.getPeerCount(),
                              Optional.empty())),
              throwable -> LOG.warn("Exception retrieving forward sync progress", throwable));
    }

    slotEventsChannelPublisher.onSlot(slot);

    final UInt64 nodeEpoch = spec.computeEpochAtSlot(slot);
    if (slot.equals(spec.computeStartSlotAtEpoch(nodeEpoch))) {
      p2pNetwork.onEpoch(nodeEpoch);
    }
  }

  boolean isNextSlotDue(final UInt64 currentTimeMillis, final UInt64 genesisTimeMillis) {
    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(nodeSlot.getValue(), genesisTimeMillis);
    return currentTimeMillis.isGreaterThanOrEqualTo(slotStartTimeMillis);
  }

  boolean isProcessingDueForSlot(final UInt64 calculatedSlot, final UInt64 currentPosition) {
    return currentPosition == null || calculatedSlot.isGreaterThan(currentPosition);
  }

  boolean isSlotStartDue(final UInt64 calculatedSlot) {
    return isProcessingDueForSlot(calculatedSlot, onTickSlotStart);
  }

  // Attestations are due 1/3 (1/4 in Gloas) of the way through the slots time period
  boolean isSlotAttestationDue(
      final UInt64 calculatedSlot,
      final UInt64 currentTimeMillis,
      final UInt64 nodeSlotStartTimeMillis) {
    if (!isProcessingDueForSlot(calculatedSlot, onTickSlotAttestation)) {
      return false;
    }

    final UInt64 earliestTimeInMillis =
        nodeSlotStartTimeMillis.plus(spec.getAttestationDueMillis(calculatedSlot));

    return spec.isTimeReached(currentTimeMillis, earliestTimeInMillis);
  }

  // Attestations are due 3/4 of the way through the slots time period
  boolean isSlotPayloadAttestationDue(
      final UInt64 calculatedSlot,
      final UInt64 currentTimeMillis,
      final UInt64 nodeSlotStartTimeMillis) {
    return spec.getPayloadAttestationDueMillis(calculatedSlot)
        .filter(__ -> isProcessingDueForSlot(calculatedSlot, onTickSlotPayloadAttestation))
        .map(nodeSlotStartTimeMillis::plus)
        .map(earliestTimeMillis -> spec.isTimeReached(currentTimeMillis, earliestTimeMillis))
        .orElse(false);
  }

  // Precalculate epoch transition 2/3 of the way through the last slot of the epoch
  boolean isEpochPrecalculationDue(
      final UInt64 epoch, final UInt64 currentTimeMillis, final UInt64 genesisTimeMillis) {
    final UInt64 firstSlotOfNextEpoch = spec.computeStartSlotAtEpoch(epoch);
    if (onTickEpochPrecompute == null) {
      onTickEpochPrecompute =
          firstSlotOfNextEpoch.minusMinZero(spec.getSlotsPerEpoch(firstSlotOfNextEpoch));
      return false;
    }
    if (!isProcessingDueForSlot(firstSlotOfNextEpoch, onTickEpochPrecompute)) {
      return false;
    }
    final UInt64 nextEpochStartTimeMillis =
        spec.computeTimeMillisAtSlot(firstSlotOfNextEpoch, genesisTimeMillis);
    final UInt64 earliestTimeInMillis =
        nextEpochStartTimeMillis.minusMinZero(spec.getAttestationDueMillis(firstSlotOfNextEpoch));
    return spec.isTimeReached(currentTimeMillis, earliestTimeInMillis);
  }

  boolean isFutureBlockProductionPreparationDue(
      final UInt64 calculatedSlot, final UInt64 currentTimeMillis, final UInt64 genesisTimeMillis) {
    if (!isProcessingDueForSlot(calculatedSlot, onTickFutureBlockProductionPreparation)) {
      return false;
    }

    final UInt64 earliestTimeInMillis =
        spec.computeTimeMillisAtSlot(calculatedSlot.increment(), genesisTimeMillis)
            .minus(BLOCK_CREATION_TOLERANCE_MS);

    return spec.isTimeReached(currentTimeMillis, earliestTimeInMillis);
  }

  private void processSlotStart(final UInt64 nodeEpoch) {
    onTickSlotStart = nodeSlot.getValue();
    if (nodeSlot.getValue().equals(spec.computeStartSlotAtEpoch(nodeEpoch))) {
      p2pNetwork.onEpoch(nodeEpoch);
      if (!nodeEpoch.isZero()) {
        spec.getForkSchedule().reportActivatingMilestones(nodeEpoch);
      }
      recentChainData
          .getFinalizedCheckpoint()
          .ifPresent(
              finalizedCheckpoint ->
                  eventLog.epochEvent(
                      nodeEpoch,
                      recentChainData.getStore().getJustifiedCheckpoint().getEpoch(),
                      finalizedCheckpoint.getEpoch(),
                      finalizedCheckpoint.getRoot()));
    }
    slotEventsChannelPublisher.onSlot(nodeSlot.getValue());
  }

  private void processSlotAttestation(final Optional<TickProcessingPerformance> performanceRecord) {
    onTickSlotAttestation = nodeSlot.getValue();
    forkChoiceTrigger.onAttestationsDueForSlot(onTickSlotAttestation);
    performanceRecord.ifPresent(TickProcessingPerformance::forkChoiceTriggerUpdated);
    forkChoiceNotifier.onAttestationsDue(onTickSlotAttestation);
    performanceRecord.ifPresent(TickProcessingPerformance::forkChoiceNotifierUpdated);
    recentChainData
        .getChainHead()
        .ifPresent(
            (head) ->
                eventLog.slotEvent(
                    nodeSlot.getValue(),
                    head.getSlot(),
                    head.getRoot(),
                    recentChainData.getJustifiedCheckpoint().map(Checkpoint::getEpoch).orElse(ZERO),
                    recentChainData.getFinalizedCheckpoint().map(Checkpoint::getEpoch).orElse(ZERO),
                    p2pNetwork.getPeerCount()));
  }

  private void processSlotPayloadAttestation() {
    onTickSlotPayloadAttestation = nodeSlot.getValue();
    forkChoiceNotifier.onPayloadAttestationsDue(onTickSlotPayloadAttestation);
    recentChainData
        .getChainHead()
        .ifPresent(
            (head) ->
                eventLog.slotPayloadEvent(
                    nodeSlot.getValue(),
                    head.getSlot(),
                    head.getPayloadStatus() == ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL
                        ? Optional.of(head.getExecutionBlockHash())
                        : Optional.empty(),
                    p2pNetwork.getPeerCount()));
  }

  @VisibleForTesting
  void setOnTickSlotStart(final UInt64 slot) {
    this.onTickSlotStart = slot;
  }

  @VisibleForTesting
  void setOnTickSlotAttestation(final UInt64 slot) {
    this.onTickSlotAttestation = slot;
  }
}

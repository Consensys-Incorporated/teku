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

package tech.pegasys.teku.beacon.sync.events;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

public class CoalescingChainHeadChannel implements ChainHeadChannel, SyncSubscriber {

  private final ChainHeadChannel delegate;
  private boolean syncing = false;

  private Optional<PendingEvent> pendingEvent = Optional.empty();
  private final EventLogger eventLogger;

  public CoalescingChainHeadChannel(
      final ChainHeadChannel delegate, final EventLogger eventLogger) {
    this.delegate = delegate;
    this.eventLogger = eventLogger;
  }

  @Override
  public synchronized void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus,
      final Optional<ReorgContext> optionalReorgContext) {
    if (!syncing) {
      optionalReorgContext.ifPresent(reorg -> logReorg(reorg, slot, bestBlockRoot, payloadStatus));
      delegate.chainHeadUpdated(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          executionOptimistic,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          payloadStatus,
          optionalReorgContext);
    } else {
      pendingEvent =
          pendingEvent
              .map(
                  current ->
                      current.update(
                          slot,
                          stateRoot,
                          bestBlockRoot,
                          epochTransition,
                          executionOptimistic,
                          previousDutyDependentRoot,
                          currentDutyDependentRoot,
                          payloadStatus,
                          optionalReorgContext))
              .or(
                  () ->
                      Optional.of(
                          new PendingEvent(
                              slot,
                              stateRoot,
                              bestBlockRoot,
                              epochTransition,
                              executionOptimistic,
                              previousDutyDependentRoot,
                              currentDutyDependentRoot,
                              payloadStatus,
                              optionalReorgContext)));
    }
  }

  private void logReorg(
      final ReorgContext reorg,
      final UInt64 slot,
      final Bytes32 bestBlockRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus) {
    if (reorg.isPayloadReorg()) {
      eventLogger.payloadReorgEvent(
          bestBlockRoot,
          slot,
          reorg.oldBestExecutionBlockHash(),
          reorg.newBestExecutionBlockHash());
      return;
    }
    eventLogger.reorgEvent(
        reorg.oldBestBlockRoot(),
        reorg.oldBestBlockSlot(),
        bestBlockRoot,
        slot,
        reorg.commonAncestorRoot(),
        reorg.commonAncestorSlot(),
        payloadStatus.flatMap(CoalescingChainHeadChannel::toLoggedPayloadStatus),
        toLoggedPayloadStatus(reorg.oldBestPayloadStatus()),
        toLoggedPayloadStatus(reorg.commonAncestorPayloadStatusOnOldBranch()),
        toLoggedPayloadStatus(reorg.commonAncestorPayloadStatusOnNewBranch()));
  }

  /**
   * The event logger cannot depend on the spec types, so the payload status is passed by name.
   * PENDING carries no information (pre-Gloas, or unresolved) and is not displayed.
   */
  private static Optional<String> toLoggedPayloadStatus(final ForkChoicePayloadStatus status) {
    return switch (status) {
      case PAYLOAD_STATUS_PENDING -> Optional.empty();
      case PAYLOAD_STATUS_EMPTY -> Optional.of("EMPTY");
      case PAYLOAD_STATUS_FULL -> Optional.of("FULL");
    };
  }

  @Override
  public synchronized void onSyncingChange(final boolean isSyncing) {
    syncing = isSyncing;
    if (!syncing) {
      pendingEvent.ifPresent(PendingEvent::send);
      pendingEvent = Optional.empty();
    }
  }

  private class PendingEvent {
    private UInt64 slot;
    private Bytes32 stateRoot;
    private Bytes32 bestBlockRoot;
    private boolean epochTransition;
    private boolean executionOptimistic;
    private Bytes32 previousDutyDependentRoot;
    private Bytes32 currentDutyDependentRoot;
    private Optional<ForkChoicePayloadStatus> payloadStatus;
    private Optional<ReorgContext> reorgContext;

    private PendingEvent(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot,
        final Optional<ForkChoicePayloadStatus> payloadStatus,
        final Optional<ReorgContext> reorgContext) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      this.epochTransition = epochTransition;
      this.executionOptimistic = executionOptimistic;
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      this.payloadStatus = payloadStatus;
      this.reorgContext = reorgContext;
    }

    public void send() {
      delegate.chainHeadUpdated(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          executionOptimistic,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          payloadStatus,
          reorgContext);
    }

    public PendingEvent update(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot,
        final Optional<ForkChoicePayloadStatus> payloadStatus,
        final Optional<ReorgContext> reorgContext) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      if (epochTransition) {
        this.epochTransition = true;
      }
      this.executionOptimistic = executionOptimistic;
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      this.payloadStatus = payloadStatus;
      reorgContext.ifPresent(this::mergeReorgContext);
      return this;
    }

    /**
     * While syncing only the last head update is delivered (without logging), so the pending reorg
     * context must keep whatever the consumers need to act on. A block reorg is what pools, light
     * client pruning and the chain_reorg event act on, so it is never displaced by a payload reorg;
     * between block reorgs the earliest common ancestor covers the most ground. No consumer acts on
     * a payload reorg, so one is only kept while no block reorg is pending (the latest, so that the
     * delivered event is not stripped of a context that was reported) and the delivered payload
     * status reflects the final head regardless of which context is kept.
     */
    private void mergeReorgContext(final ReorgContext incoming) {
      if (this.reorgContext.isEmpty()) {
        this.reorgContext = Optional.of(incoming);
        return;
      }
      final ReorgContext pending = this.reorgContext.get();
      if (pending.isPayloadReorg()) {
        // a block reorg replaces it, a newer payload reorg refreshes it
        this.reorgContext = Optional.of(incoming);
      } else if (incoming.isBlockReorg()
          && incoming.commonAncestorSlot().isLessThan(pending.commonAncestorSlot())) {
        this.reorgContext = Optional.of(incoming);
      }
      // otherwise the pending block reorg is kept
    }
  }
}

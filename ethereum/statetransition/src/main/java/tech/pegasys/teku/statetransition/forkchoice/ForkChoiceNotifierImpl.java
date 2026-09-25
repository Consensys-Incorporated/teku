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

package tech.pegasys.teku.statetransition.forkchoice;

import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceNotifierImpl implements ForkChoiceNotifier {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final ExecutionLayerChannel executionLayerChannel;
  private final RecentChainData recentChainData;
  private final ProposersDataManager proposersDataManager;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final boolean forkChoiceLateBlockReorgEnabled;

  private final Subscribers<ForkChoiceUpdatedResultSubscriber> forkChoiceUpdatedSubscribers =
      Subscribers.create(true);

  private ForkChoiceUpdateData forkChoiceUpdateData = new ForkChoiceUpdateData();

  // Stable block-production state between requested block production and getPayloadId.
  // Ordinary FCUs are suppressed while this is active so getPayloadId can consume a stable record
  // for its parent and slot.
  private Optional<PinnedBlockProductionPreparation> pinnedBlockProductionPreparation =
      Optional.empty();

  private boolean inSync = false; // Assume we are not in sync at startup.

  private record PinnedBlockProductionPreparation(
      UInt64 slot,
      ForkChoiceNode parentBeaconBlock,
      ForkChoiceUpdateData forkChoiceUpdateData,
      SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext) {

    boolean matches(final ForkChoiceNode parentBeaconBlock, final UInt64 slot) {
      return this.slot.equals(slot) && this.parentBeaconBlock.equals(parentBeaconBlock);
    }

    boolean isExpiredBySlot(final UInt64 slot) {
      // This is a tradeoff between giving the current block production more time to complete and
      // the less likely case where we also produce the next block. Successful production clears the
      // pin when the produced block is imported, giving the next slot some preparation time anyway.
      // If production fails, the next block may have less time to prepare, but keeping the pin
      // avoids invalidating an active production request while still bounding how long it can block
      // later fork choice updates.
      return this.slot.isLessThan(slot);
    }

    boolean isClearedByHead(final ForkChoiceState forkChoiceState) {
      return forkChoiceState.headBlockSlot().isGreaterThanOrEqualTo(slot);
    }

    PinnedBlockProductionPreparation withForkChoiceUpdateData(
        final ForkChoiceUpdateData forkChoiceUpdateData) {
      return new PinnedBlockProductionPreparation(
          slot, parentBeaconBlock, forkChoiceUpdateData, executionPayloadContext);
    }
  }

  public ForkChoiceNotifierImpl(
      final EventThread eventThread,
      final TimeProvider timeProvider,
      final Spec spec,
      final ExecutionLayerChannel executionLayerChannel,
      final RecentChainData recentChainData,
      final ProposersDataManager proposersDataManager,
      final boolean forkChoiceLateBlockReorgEnabled) {
    this.eventThread = eventThread;
    this.spec = spec;
    this.executionLayerChannel = executionLayerChannel;
    this.recentChainData = recentChainData;
    this.proposersDataManager = proposersDataManager;
    this.timeProvider = timeProvider;
    this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
  }

  @Override
  public void subscribeToForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultSubscriber subscriber) {
    forkChoiceUpdatedSubscribers.subscribe(subscriber);
  }

  @Override
  public void onForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
    eventThread.execute(
        () -> internalForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot));
  }

  @Override
  public void onAttestationsDue(final UInt64 slot) {
    eventThread.execute(
        () -> {
          eventThread.checkOnEventThread();
          LOG.debug("onAttestationsDue slot {}", slot);
          // when we don't need to notify fCu when we have imported a beacon block (post-Gloas),
          // there is no need to prepare next slot proposals when attestations are due
          if (spec.atSlot(slot).getForkChoiceUtil().shouldNotifyForkChoiceUpdatedOnBlock()) {
            prepareNextSlotProposal(slot);
          }
        });
  }

  @Override
  public void onPayloadAttestationsDue(final UInt64 slot) {
    eventThread.execute(
        () -> {
          eventThread.checkOnEventThread();
          LOG.debug("onPayloadAttestationsDue slot {}", slot);
          prepareNextSlotProposal(slot);
        });
  }

  @Override
  public void onSyncingStatusChanged(final boolean inSync) {
    eventThread.execute(
        () -> {
          this.inSync = inSync;
        });
  }

  @Override
  public SafeFuture<Optional<ExecutionPayloadContext>> getPayloadId(
      final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
    return eventThread.executeFuture(() -> internalGetPayloadId(parentBeaconBlock, blockSlot));
  }

  @Override
  public void onTerminalBlockReached(final Bytes32 executionBlockHash) {
    eventThread.execute(() -> internalTerminalBlockReached(executionBlockHash));
  }

  private void internalTerminalBlockReached(final Bytes32 executionBlockHash) {
    eventThread.checkOnEventThread();
    LOG.debug("internalTerminalBlockReached executionBlockHash {}", executionBlockHash);
    forkChoiceUpdateData = forkChoiceUpdateData.withTerminalBlockHash(executionBlockHash);
    LOG.debug("internalTerminalBlockReached forkChoiceUpdateData {}", forkChoiceUpdateData);
  }

  /**
   * @param parentBeaconBlock fork choice node of the beacon block the new block will be built on
   * @param blockSlot slot of the block being produced, for which the payloadId has been requested
   * @return must return a Future resolving to:
   *     <p>Optional.empty() only when is safe to produce a block with an empty execution payload
   *     (after the bellatrix fork and before Terminal Block arrival)
   *     <p>Optional.of(executionPayloadContext) when one of the following:
   *     <p>1. builds on top of execution head of parentBeaconBlock
   *     <p>2. builds on top of the terminal block
   *     <p>in all other cases it must Throw to avoid block production
   */
  private SafeFuture<Optional<ExecutionPayloadContext>> internalGetPayloadId(
      final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
    eventThread.checkOnEventThread();

    LOG.debug(
        "internalGetPayloadId parentBeaconBlock {} blockSlot {}", parentBeaconBlock, blockSlot);

    final Bytes32 parentExecutionHash =
        recentChainData
            .getExecutionBlockHashForBlock(parentBeaconBlock)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to retrieve execution payload hash from beacon block root"));

    if (parentExecutionHash.isZero() && !forkChoiceUpdateData.hasTerminalBlockHash()) {
      // Pre-merge so ok to use default payload
      return SafeFuture.completedFuture(Optional.empty());
    }

    final PinnedBlockProductionPreparation preparation =
        pinnedBlockProductionPreparation
            .filter(candidate -> candidate.matches(parentBeaconBlock, blockSlot))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No pinned block production preparation for parent "
                            + parentBeaconBlock
                            + " at slot "
                            + blockSlot));

    return preparation
        .executionPayloadContext()
        .thenApply(
            maybeExecutionPayloadContext -> {
              if (maybeExecutionPayloadContext.isEmpty()) {
                throw new IllegalStateException("Unable to obtain an executionPayloadContext");
              }
              final ExecutionPayloadContext executionPayloadContext =
                  maybeExecutionPayloadContext.get();
              final UInt64 timestamp =
                  spec.computeTimeAtSlot(blockSlot, recentChainData.getGenesisTime());
              checkState(
                  executionPayloadContext
                      .getForkChoiceState()
                      .headBlock()
                      .equals(parentBeaconBlock),
                  "Pinned block production head %s does not match requested block production parent %s",
                  executionPayloadContext.getForkChoiceState().headBlock(),
                  parentBeaconBlock);
              checkState(
                  executionPayloadContext.getParentHash().equals(parentExecutionHash)
                      || isTerminalBlockPayload(
                          preparation, parentExecutionHash, executionPayloadContext),
                  "Pinned block production parent execution hash %s does not match requested parent execution hash %s",
                  executionPayloadContext.getParentHash(),
                  parentExecutionHash);
              checkState(
                  executionPayloadContext
                      .getPayloadBuildingAttributes()
                      .timestamp()
                      .equals(timestamp),
                  "Pinned block production timestamp %s does not match requested timestamp %s",
                  executionPayloadContext.getPayloadBuildingAttributes().timestamp(),
                  timestamp);
              return maybeExecutionPayloadContext;
            });
  }

  private boolean isTerminalBlockPayload(
      final PinnedBlockProductionPreparation preparation,
      final Bytes32 parentExecutionHash,
      final ExecutionPayloadContext executionPayloadContext) {
    return parentExecutionHash.isZero()
        && preparation
            .forkChoiceUpdateData()
            .isTerminalBlockHash(executionPayloadContext.getParentHash());
  }

  private void internalForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
    eventThread.checkOnEventThread();

    LOG.debug("internalForkChoiceUpdated forkChoiceState {}", forkChoiceState);

    clearPinnedBlockProductionIfHeadAdvanced(forkChoiceState);

    if (requestedBlockProductionSlot.isPresent()) {
      pinForkChoiceStateForBlockProduction(
          forkChoiceState, requestedBlockProductionSlot.orElseThrow());
      return;
    }

    if (pinnedBlockProductionPreparation.isPresent()) {
      LOG.debug(
          "internalForkChoiceUpdated skipped ordinary FCU because pinned block production is active for slot {}",
          pinnedBlockProductionPreparation.orElseThrow().slot());
      return;
    }

    if (shouldSkipForkChoiceUpdateDueToLateBlockReorg(forkChoiceState)) {
      LOG.debug(
          "internalForkChoiceUpdated skipped ordinary FCU because late block reorg override is active for head {} at slot {}",
          forkChoiceState.headBlock(),
          forkChoiceState.headBlockSlot());
      return;
    }

    final Optional<UInt64> payloadAttributesSlot = calculatePayloadAttributesSlot(forkChoiceState);

    this.forkChoiceUpdateData = this.forkChoiceUpdateData.withForkChoiceState(forkChoiceState);

    LOG.debug("internalForkChoiceUpdated forkChoiceUpdateData {}", forkChoiceUpdateData);

    payloadAttributesSlot.ifPresent(this::updatePayloadAttributes);

    sendForkChoiceUpdated();
  }

  private boolean shouldSkipForkChoiceUpdateDueToLateBlockReorg(
      final ForkChoiceState forkChoiceState) {
    return forkChoiceLateBlockReorgEnabled
        && recentChainData.shouldOverrideForkChoiceUpdate(
            forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot());
  }

  private void completePinnedBlockProductionExceptionally(
      final PinnedBlockProductionPreparation preparation, final String reason) {
    if (!preparation.executionPayloadContext().isDone()) {
      preparation
          .executionPayloadContext()
          .completeExceptionally(
              new IllegalStateException(
                  "Pinned block production for slot " + preparation.slot() + " " + reason));
    }
  }

  private void clearPinnedBlockProductionIfHeadAdvanced(final ForkChoiceState forkChoiceState) {
    pinnedBlockProductionPreparation
        .filter(preparation -> preparation.isClearedByHead(forkChoiceState))
        .ifPresent(
            preparation -> {
              LOG.debug(
                  "Clearing pinned block production for slot {} because head advanced to slot {}",
                  preparation.slot(),
                  forkChoiceState.headBlockSlot());
              completePinnedBlockProductionExceptionally(
                  preparation,
                  "was cleared because head advanced to slot " + forkChoiceState.headBlockSlot());
              pinnedBlockProductionPreparation = Optional.empty();
            });
  }

  private void clearPinnedBlockProductionIfExpired(final UInt64 slot) {
    pinnedBlockProductionPreparation
        .filter(preparation -> preparation.isExpiredBySlot(slot))
        .ifPresent(
            preparation -> {
              LOG.debug(
                  "Clearing pinned block production for slot {} because attestations are due for slot {}",
                  preparation.slot(),
                  slot);
              completePinnedBlockProductionExceptionally(
                  preparation, "expired when attestations were due for slot " + slot);
              pinnedBlockProductionPreparation = Optional.empty();
            });
  }

  private void pinForkChoiceStateForBlockProduction(
      final ForkChoiceState forkChoiceState, final UInt64 requestedBlockProductionSlot) {
    final ForkChoiceUpdateData productionForkChoiceUpdateData =
        getForkChoiceUpdateDataForPinnedBlockProduction(
            forkChoiceState, requestedBlockProductionSlot);
    pinnedBlockProductionPreparation.ifPresent(
        previousPreparation ->
            completePinnedBlockProductionExceptionally(
                previousPreparation,
                "was replaced by pinned block production for slot "
                    + requestedBlockProductionSlot));
    forkChoiceUpdateData = productionForkChoiceUpdateData;
    final PinnedBlockProductionPreparation preparation =
        new PinnedBlockProductionPreparation(
            requestedBlockProductionSlot,
            forkChoiceState.headBlock(),
            productionForkChoiceUpdateData,
            new SafeFuture<>());
    pinnedBlockProductionPreparation = Optional.of(preparation);

    LOG.debug(
        "Pinned block production for slot {} with forkChoiceUpdateData {}",
        requestedBlockProductionSlot,
        productionForkChoiceUpdateData);

    if (hasPayloadAttributesForPinnedBlockProduction(
        productionForkChoiceUpdateData, forkChoiceState, requestedBlockProductionSlot)) {
      // Reuse the payloadId requested by the ordinary fcU. If the EL answered that fcU without a
      // payloadId (e.g. SYNCING), the reused result is unusable, so request a fresh one instead of
      // failing block production with it.
      productionForkChoiceUpdateData
          .getExecutionPayloadContext()
          .thenComposeAsync(
              maybeExecutionPayloadContext ->
                  retryPinnedBlockProductionIfPayloadIdIsMissing(
                      preparation, maybeExecutionPayloadContext),
              eventThread)
          .propagateTo(preparation.executionPayloadContext());
      sendForkChoiceUpdated(productionForkChoiceUpdateData);
    } else {
      updatePayloadAttributesForPinnedBlockProduction(preparation);
    }
  }

  private SafeFuture<Optional<ExecutionPayloadContext>>
      retryPinnedBlockProductionIfPayloadIdIsMissing(
          final PinnedBlockProductionPreparation preparation,
          final Optional<ExecutionPayloadContext> maybeExecutionPayloadContext) {
    eventThread.checkOnEventThread();
    if (maybeExecutionPayloadContext.isPresent() || !isCurrentPinnedBlockProduction(preparation)) {
      return SafeFuture.completedFuture(maybeExecutionPayloadContext);
    }
    LOG.warn(
        "Reused forkChoiceUpdated result for block production at slot {} has no payloadId, requesting a new one from the execution layer",
        preparation.slot());
    // The attributes are unchanged (same head and slot), so resend them as they are rather than
    // recalculating.
    final ForkChoiceUpdateData retryForkChoiceUpdateData =
        preparation.forkChoiceUpdateData().forResend();
    final PinnedBlockProductionPreparation retryPreparation =
        new PinnedBlockProductionPreparation(
            preparation.slot(),
            preparation.parentBeaconBlock(),
            retryForkChoiceUpdateData,
            new SafeFuture<>());
    forkChoiceUpdateData = retryForkChoiceUpdateData;
    pinnedBlockProductionPreparation = Optional.of(retryPreparation);
    retryForkChoiceUpdateData
        .getExecutionPayloadContext()
        .propagateTo(retryPreparation.executionPayloadContext());
    sendForkChoiceUpdated(retryForkChoiceUpdateData);
    return retryPreparation.executionPayloadContext();
  }

  private ForkChoiceUpdateData getForkChoiceUpdateDataForPinnedBlockProduction(
      final ForkChoiceState forkChoiceState, final UInt64 requestedBlockProductionSlot) {
    if (hasPayloadAttributesForPinnedBlockProduction(
        forkChoiceUpdateData, forkChoiceState, requestedBlockProductionSlot)) {
      return forkChoiceUpdateData;
    }

    // Block production must never inherit ordinary payload attributes for a different slot or
    // parent. If there is no exact match above, start from just the requested fork choice head and
    // calculate attributes against that head.
    return forkChoiceUpdateData.withFreshForkChoiceState(forkChoiceState);
  }

  private boolean hasPayloadAttributesForPinnedBlockProduction(
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final ForkChoiceState forkChoiceState,
      final UInt64 requestedBlockProductionSlot) {
    return forkChoiceUpdateData.getForkChoiceState().equals(forkChoiceState)
        && forkChoiceUpdateData
            .getPayloadBuildingAttributes()
            .map(
                payloadBuildingAttributes ->
                    payloadBuildingAttributes.proposalSlot().equals(requestedBlockProductionSlot)
                        && payloadBuildingAttributes
                            .parentBeaconBlock()
                            .equals(forkChoiceState.headBlock()))
            .orElse(false);
  }

  private void updatePayloadAttributesForPinnedBlockProduction(
      final PinnedBlockProductionPreparation preparation) {
    final ForkChoiceUpdateData localForkChoiceUpdateData = preparation.forkChoiceUpdateData();
    proposersDataManager
        .calculatePayloadBuildingAttributes(
            preparation.slot(), inSync, localForkChoiceUpdateData, true)
        .thenAccept(
            payloadBuildingAttributes -> {
              if (payloadBuildingAttributes.isEmpty()) {
                if (isCurrentPinnedBlockProduction(preparation)) {
                  preparation.executionPayloadContext().complete(Optional.empty());
                }
                return;
              }
              if (!isCurrentPinnedBlockProduction(preparation)) {
                completePinnedBlockProductionExceptionally(
                    preparation, "was replaced before payload attributes were resolved");
                LOG.debug(
                    "Ignoring stale pinned block production payload attributes for slot {}",
                    preparation.slot());
                return;
              }

              final ForkChoiceUpdateData updatedForkChoiceUpdateData =
                  localForkChoiceUpdateData.withPayloadBuildingAttributes(
                      payloadBuildingAttributes);
              forkChoiceUpdateData = updatedForkChoiceUpdateData;
              final PinnedBlockProductionPreparation updatedPreparation =
                  preparation.withForkChoiceUpdateData(updatedForkChoiceUpdateData);
              pinnedBlockProductionPreparation = Optional.of(updatedPreparation);
              updatedForkChoiceUpdateData
                  .getExecutionPayloadContext()
                  .propagateTo(updatedPreparation.executionPayloadContext());
              sendForkChoiceUpdated(updatedForkChoiceUpdateData);
            })
        .finish(
            error -> {
              if (isCurrentPinnedBlockProduction(preparation)) {
                preparation.executionPayloadContext().completeExceptionally(error);
              }
              LOG.error(
                  "Failed to calculate pinned block production payload attributes for slot {}",
                  preparation.slot(),
                  error);
            });
  }

  private boolean isCurrentPinnedBlockProduction(
      final PinnedBlockProductionPreparation preparation) {
    return pinnedBlockProductionPreparation
        .map(
            currentPreparation ->
                currentPreparation
                    .executionPayloadContext()
                    .equals(preparation.executionPayloadContext()))
        .orElse(false);
  }

  /**
   * Determine for which slot we should calculate payload attributes (block proposal)
   *
   * <pre>
   * this will guarantee that whenever we calculate a payload attributes for a slot, it will remain stable until:
   * 1. next slot attestation due is reached (internalAttestationsDue forcing attributes calculation for next slot)
   * OR
   * 2. we imported the block for current slot and has become the head
   * </pre>
   */
  private Optional<UInt64> calculatePayloadAttributesSlot(final ForkChoiceState forkChoiceState) {
    final Optional<UInt64> currentSlot = recentChainData.getCurrentSlot();
    if (currentSlot.isEmpty()) {
      // We are pre-genesis, so we don't care about payload attributes
      return Optional.empty();
    }

    final Optional<UInt64> maybeCurrentPayloadAttributesSlot =
        forkChoiceUpdateData
            .getPayloadBuildingAttributes()
            .map(PayloadBuildingAttributes::proposalSlot);

    if (maybeCurrentPayloadAttributesSlot.isPresent()
        // we are still in the same slot as the last proposing slot
        && currentSlot.get().equals(maybeCurrentPayloadAttributesSlot.get())
        // we have not yet imported our own produced block
        && forkChoiceState.headBlockSlot().isLessThan(maybeCurrentPayloadAttributesSlot.get())) {

      LOG.debug(
          "current payload attributes slot has been chosen for payload attributes calculation: {}",
          currentSlot.get());

      // in case we propose two blocks in a row and we fail producing the first block,
      // we won't keep using the same first slot because internalAttestationsDue will
      // update the payload attributes for the second block slot
      return currentSlot;
    }

    // chain advanced since last proposing slot, we should consider attributes for the next slot
    return currentSlot.map(UInt64::increment);
  }

  private void prepareNextSlotProposal(final UInt64 slot) {
    // Assume `slot` is empty and check if we need to prepare to propose in the next slot
    clearPinnedBlockProductionIfExpired(slot);
    updatePayloadAttributes(slot.plus(1));
  }

  private void sendForkChoiceUpdated() {
    sendForkChoiceUpdated(forkChoiceUpdateData);
  }

  private void sendForkChoiceUpdated(final ForkChoiceUpdateData forkChoiceUpdateData) {
    forkChoiceUpdateData
        .send(executionLayerChannel, timeProvider.getTimeInMillis())
        .ifPresent(
            forkChoiceUpdatedResultFuture ->
                forkChoiceUpdatedSubscribers.deliver(
                    ForkChoiceUpdatedResultSubscriber::onForkChoiceUpdatedResult,
                    new ForkChoiceUpdatedResultNotification(
                        forkChoiceUpdateData.getForkChoiceState(),
                        forkChoiceUpdateData.getPayloadBuildingAttributes(),
                        forkChoiceUpdateData.hasTerminalBlockHash(),
                        forkChoiceUpdatedResultFuture)));
  }

  private void updatePayloadAttributes(final UInt64 blockSlot) {
    LOG.debug("updatePayloadAttributes blockSlot {}", blockSlot);

    final ForkChoiceUpdateData localForkChoiceUpdateData = forkChoiceUpdateData;
    proposersDataManager
        .calculatePayloadBuildingAttributes(blockSlot, inSync, localForkChoiceUpdateData, false)
        .thenAccept(
            payloadBuildingAttributes -> {
              if (isStaleForkChoiceUpdateData(localForkChoiceUpdateData)) {
                LOG.debug(
                    "Ignoring stale payload attributes for slot {} because fork choice update data has changed",
                    blockSlot);
                return;
              }
              forkChoiceUpdateData =
                  localForkChoiceUpdateData.withPayloadBuildingAttributes(
                      payloadBuildingAttributes);
              sendForkChoiceUpdated();
            })
        .finish(
            error ->
                LOG.error("Failed to calculate payload attributes for slot {}", blockSlot, error));
  }

  @SuppressWarnings("ReferenceComparison")
  private boolean isStaleForkChoiceUpdateData(
      final ForkChoiceUpdateData localForkChoiceUpdateData) {
    return forkChoiceUpdateData != localForkChoiceUpdateData;
  }
}

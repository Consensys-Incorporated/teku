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

  private Optional<PendingPayloadAttributesPreparation> pendingPayloadAttributesPreparation =
      Optional.empty();

  private boolean inSync = false; // Assume we are not in sync at startup.

  private record PendingPayloadAttributesPreparation(
      UInt64 slot,
      ForkChoiceNode parentBeaconBlock,
      SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext) {

    boolean matches(final ForkChoiceNode parentBeaconBlock, final UInt64 slot) {
      return this.slot.equals(slot) && this.parentBeaconBlock.equals(parentBeaconBlock);
    }
  }

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
  public SafeFuture<Optional<ExecutionPayloadContext>> preparePayloadAttributes(
      final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
    return eventThread.executeFuture(
        () -> internalPreparePayloadAttributes(parentBeaconBlock, blockSlot));
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

    final Bytes32 parentExecutionHash = getParentExecutionHash(parentBeaconBlock);

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
              checkState(
                  isCurrentPinnedBlockProduction(preparation),
                  "Pinned block production was replaced before the payload ID was retrieved");
              return validateExecutionPayloadContext(
                  preparation.forkChoiceUpdateData(),
                  parentBeaconBlock,
                  parentExecutionHash,
                  blockSlot,
                  maybeExecutionPayloadContext);
            });
  }

  private SafeFuture<Optional<ExecutionPayloadContext>> internalPreparePayloadAttributes(
      final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
    eventThread.checkOnEventThread();

    final Bytes32 parentExecutionHash = getParentExecutionHash(parentBeaconBlock);
    if (parentExecutionHash.isZero() && !forkChoiceUpdateData.hasTerminalBlockHash()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    clearPinnedBlockProductionIfExpired(
        blockSlot, "payload attributes were requested for slot " + blockSlot);

    final Optional<PendingPayloadAttributesPreparation> existingPendingPreparation =
        pendingPayloadAttributesPreparation;
    if (existingPendingPreparation
        .filter(preparation -> preparation.matches(parentBeaconBlock, blockSlot))
        .isPresent()) {
      return existingPendingPreparation.orElseThrow().executionPayloadContext();
    }
    existingPendingPreparation.ifPresent(
        preparation ->
            completePendingPayloadAttributesPreparationExceptionally(
                preparation, "was replaced by a newer inclusion list payload preparation"));

    if (pinnedBlockProductionPreparation.isPresent()) {
      final PinnedBlockProductionPreparation preparation =
          pinnedBlockProductionPreparation.orElseThrow();
      checkState(
          preparation.matches(parentBeaconBlock, blockSlot),
          "Pinned block production for parent %s at slot %s does not match requested parent %s at slot %s",
          preparation.parentBeaconBlock(),
          preparation.slot(),
          parentBeaconBlock,
          blockSlot);
      final ForkChoiceUpdateData forkChoiceUpdateDataWithoutPayloadAttributes =
          preparation
              .forkChoiceUpdateData()
              .withFreshForkChoiceState(preparation.forkChoiceUpdateData().getForkChoiceState());
      final PinnedBlockProductionPreparation updatedPreparation =
          new PinnedBlockProductionPreparation(
              preparation.slot(),
              preparation.parentBeaconBlock(),
              forkChoiceUpdateDataWithoutPayloadAttributes,
              new SafeFuture<>());
      completePinnedBlockProductionExceptionally(
          preparation, "was replaced while refreshing inclusion list payload attributes");
      pinnedBlockProductionPreparation = Optional.of(updatedPreparation);
      forkChoiceUpdateData = forkChoiceUpdateDataWithoutPayloadAttributes;
    } else {
      checkState(
          forkChoiceUpdateData.getForkChoiceState().headBlock().equals(parentBeaconBlock),
          "Current fork choice head %s does not match requested payload attributes parent %s",
          forkChoiceUpdateData.getForkChoiceState().headBlock(),
          parentBeaconBlock);
    }

    final PendingPayloadAttributesPreparation preparation =
        new PendingPayloadAttributesPreparation(blockSlot, parentBeaconBlock, new SafeFuture<>());
    pendingPayloadAttributesPreparation = Optional.of(preparation);
    final ForkChoiceUpdateData calculationForkChoiceUpdateData =
        forkChoiceUpdateData.withFreshForkChoiceState(forkChoiceUpdateData.getForkChoiceState());

    proposersDataManager
        .calculatePayloadBuildingAttributes(
            blockSlot, inSync, calculationForkChoiceUpdateData, true)
        .thenAccept(
            payloadBuildingAttributes ->
                completePendingPayloadAttributesPreparation(
                    preparation, payloadBuildingAttributes, parentExecutionHash))
        .finish(
            error ->
                completePendingPayloadAttributesPreparationExceptionally(
                    preparation, "failed: " + error.getMessage()));
    return preparation.executionPayloadContext();
  }

  private void completePendingPayloadAttributesPreparation(
      final PendingPayloadAttributesPreparation preparation,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes,
      final Bytes32 parentExecutionHash) {
    eventThread.checkOnEventThread();
    if (!isCurrentPendingPayloadAttributesPreparation(preparation)) {
      return;
    }
    if (payloadBuildingAttributes.isEmpty()) {
      pendingPayloadAttributesPreparation = Optional.empty();
      pinnedBlockProductionPreparation
          .filter(
              pinnedPreparation ->
                  pinnedPreparation.matches(preparation.parentBeaconBlock(), preparation.slot()))
          .ifPresent(
              pinnedPreparation ->
                  pinnedPreparation.executionPayloadContext().complete(Optional.empty()));
      preparation.executionPayloadContext().complete(Optional.empty());
      return;
    }

    final ForkChoiceUpdateData preparationForkChoiceUpdateData =
        pinnedBlockProductionPreparation
            .filter(
                pinnedPreparation ->
                    pinnedPreparation.matches(preparation.parentBeaconBlock(), preparation.slot()))
            .map(PinnedBlockProductionPreparation::forkChoiceUpdateData)
            .orElseGet(
                () -> {
                  checkState(
                      forkChoiceUpdateData
                          .getForkChoiceState()
                          .headBlock()
                          .equals(preparation.parentBeaconBlock()),
                      "Current fork choice head %s does not match prepared payload attributes parent %s",
                      forkChoiceUpdateData.getForkChoiceState().headBlock(),
                      preparation.parentBeaconBlock());
                  return forkChoiceUpdateData.withFreshForkChoiceState(
                      forkChoiceUpdateData.getForkChoiceState());
                });
    final ForkChoiceUpdateData updatedForkChoiceUpdateData =
        preparationForkChoiceUpdateData.withPayloadBuildingAttributes(payloadBuildingAttributes);
    forkChoiceUpdateData = updatedForkChoiceUpdateData;
    pendingPayloadAttributesPreparation = Optional.empty();
    pinnedBlockProductionPreparation
        .filter(
            pinnedPreparation ->
                pinnedPreparation.matches(preparation.parentBeaconBlock(), preparation.slot()))
        .ifPresent(
            pinnedPreparation -> {
              final PinnedBlockProductionPreparation updatedPinnedPreparation =
                  pinnedPreparation.withForkChoiceUpdateData(updatedForkChoiceUpdateData);
              pinnedBlockProductionPreparation = Optional.of(updatedPinnedPreparation);
              updatedForkChoiceUpdateData
                  .getExecutionPayloadContext()
                  .propagateTo(updatedPinnedPreparation.executionPayloadContext());
            });
    updatedForkChoiceUpdateData
        .getExecutionPayloadContext()
        .thenApply(
            maybeExecutionPayloadContext ->
                validateExecutionPayloadContext(
                    updatedForkChoiceUpdateData,
                    preparation.parentBeaconBlock(),
                    parentExecutionHash,
                    preparation.slot(),
                    maybeExecutionPayloadContext))
        .propagateTo(preparation.executionPayloadContext());
    sendForkChoiceUpdated(updatedForkChoiceUpdateData);
  }

  private boolean isCurrentPendingPayloadAttributesPreparation(
      final PendingPayloadAttributesPreparation preparation) {
    return pendingPayloadAttributesPreparation
        .map(
            currentPreparation ->
                currentPreparation
                    .executionPayloadContext()
                    .equals(preparation.executionPayloadContext()))
        .orElse(false);
  }

  private void completePendingPayloadAttributesPreparationExceptionally(
      final PendingPayloadAttributesPreparation preparation, final String reason) {
    if (isCurrentPendingPayloadAttributesPreparation(preparation)) {
      pendingPayloadAttributesPreparation = Optional.empty();
      final IllegalStateException error =
          new IllegalStateException(
              "Payload attributes preparation for slot " + preparation.slot() + " " + reason);
      pinnedBlockProductionPreparation
          .filter(
              pinnedPreparation ->
                  pinnedPreparation.matches(preparation.parentBeaconBlock(), preparation.slot()))
          .ifPresent(
              pinnedPreparation ->
                  pinnedPreparation.executionPayloadContext().completeExceptionally(error));
      preparation.executionPayloadContext().completeExceptionally(error);
    }
  }

  private Bytes32 getParentExecutionHash(final ForkChoiceNode parentBeaconBlock) {
    return recentChainData
        .getExecutionBlockHashForBlock(parentBeaconBlock)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to retrieve execution payload hash from beacon block root"));
  }

  private Optional<ExecutionPayloadContext> validateExecutionPayloadContext(
      final ForkChoiceUpdateData preparationForkChoiceUpdateData,
      final ForkChoiceNode parentBeaconBlock,
      final Bytes32 parentExecutionHash,
      final UInt64 blockSlot,
      final Optional<ExecutionPayloadContext> maybeExecutionPayloadContext) {
    if (maybeExecutionPayloadContext.isEmpty()) {
      throw new IllegalStateException("Unable to obtain an executionPayloadContext");
    }
    final ExecutionPayloadContext executionPayloadContext = maybeExecutionPayloadContext.get();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        executionPayloadContext.getPayloadBuildingAttributes();
    final UInt64 timestamp = spec.computeTimeAtSlot(blockSlot, recentChainData.getGenesisTime());
    checkState(
        executionPayloadContext.getForkChoiceState().headBlock().equals(parentBeaconBlock),
        "Payload preparation head %s does not match requested parent %s",
        executionPayloadContext.getForkChoiceState().headBlock(),
        parentBeaconBlock);
    checkState(
        executionPayloadContext.getParentHash().equals(parentExecutionHash)
            || isTerminalBlockPayload(
                preparationForkChoiceUpdateData, parentExecutionHash, executionPayloadContext),
        "Payload preparation parent execution hash %s does not match requested parent execution hash %s",
        executionPayloadContext.getParentHash(),
        parentExecutionHash);
    checkState(
        payloadBuildingAttributes.proposalSlot().equals(blockSlot),
        "Payload preparation slot %s does not match requested slot %s",
        payloadBuildingAttributes.proposalSlot(),
        blockSlot);
    checkState(
        payloadBuildingAttributes.parentBeaconBlock().equals(parentBeaconBlock),
        "Payload preparation parent %s does not match requested parent %s",
        payloadBuildingAttributes.parentBeaconBlock(),
        parentBeaconBlock);
    checkState(
        payloadBuildingAttributes.timestamp().equals(timestamp),
        "Payload preparation timestamp %s does not match requested timestamp %s",
        payloadBuildingAttributes.timestamp(),
        timestamp);
    return maybeExecutionPayloadContext;
  }

  private boolean isTerminalBlockPayload(
      final ForkChoiceUpdateData preparationForkChoiceUpdateData,
      final Bytes32 parentExecutionHash,
      final ExecutionPayloadContext executionPayloadContext) {
    return parentExecutionHash.isZero()
        && preparationForkChoiceUpdateData.isTerminalBlockHash(
            executionPayloadContext.getParentHash());
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

    if (hasCurrentInclusionListPayloadAttributes(forkChoiceState)) {
      // Keep the IL-aware attributes stable until block production pins this parent and slot.
      forkChoiceUpdateData =
          forkChoiceUpdateData.withForkChoiceStateRetainingPayloadAttributes(forkChoiceState);
      sendForkChoiceUpdated();
      return;
    }

    final Optional<UInt64> payloadAttributesSlot = calculatePayloadAttributesSlot(forkChoiceState);

    this.forkChoiceUpdateData = this.forkChoiceUpdateData.withForkChoiceState(forkChoiceState);

    LOG.debug("internalForkChoiceUpdated forkChoiceUpdateData {}", forkChoiceUpdateData);

    payloadAttributesSlot.ifPresent(this::updatePayloadAttributes);

    sendForkChoiceUpdated();
  }

  private boolean hasCurrentInclusionListPayloadAttributes(final ForkChoiceState forkChoiceState) {
    return forkChoiceUpdateData
        .getPayloadBuildingAttributes()
        .filter(
            payloadBuildingAttributes ->
                !payloadBuildingAttributes.inclusionListTransactions().isEmpty())
        .filter(
            payloadBuildingAttributes ->
                payloadBuildingAttributes.parentBeaconBlock().equals(forkChoiceState.headBlock()))
        .filter(
            payloadBuildingAttributes ->
                recentChainData
                    .getCurrentSlot()
                    .map(
                        currentSlot ->
                            !payloadBuildingAttributes.proposalSlot().isLessThan(currentSlot))
                    .orElse(false))
        .isPresent();
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
            preparation ->
                clearPinnedBlockProduction(
                    preparation, "head advanced to slot " + forkChoiceState.headBlockSlot()));
  }

  private void clearPinnedBlockProductionIfExpired(
      final UInt64 slot, final String expirationReason) {
    pinnedBlockProductionPreparation
        .filter(preparation -> preparation.isExpiredBySlot(slot))
        .ifPresent(preparation -> clearPinnedBlockProduction(preparation, expirationReason));
  }

  private void clearPinnedBlockProduction(
      final PinnedBlockProductionPreparation preparation, final String reason) {
    LOG.debug(
        "Clearing pinned block production for slot {} because {}", preparation.slot(), reason);
    final String clearingReason = "was cleared because " + reason;
    completePinnedBlockProductionExceptionally(preparation, clearingReason);
    pendingPayloadAttributesPreparation
        .filter(
            pendingPreparation ->
                pendingPreparation.matches(preparation.parentBeaconBlock(), preparation.slot()))
        .ifPresent(
            pendingPreparation ->
                completePendingPayloadAttributesPreparationExceptionally(
                    pendingPreparation, clearingReason));
    pinnedBlockProductionPreparation = Optional.empty();
  }

  private void pinForkChoiceStateForBlockProduction(
      final ForkChoiceState forkChoiceState, final UInt64 requestedBlockProductionSlot) {
    if (pinnedBlockProductionPreparation
        .filter(
            preparation ->
                preparation.matches(forkChoiceState.headBlock(), requestedBlockProductionSlot))
        .isPresent()) {
      LOG.debug(
          "Keeping existing pinned block production for parent {} at slot {}",
          forkChoiceState.headBlock(),
          requestedBlockProductionSlot);
      return;
    }
    final Optional<PendingPayloadAttributesPreparation> matchingPendingPreparation =
        pendingPayloadAttributesPreparation.filter(
            preparation ->
                preparation.matches(forkChoiceState.headBlock(), requestedBlockProductionSlot));
    pendingPayloadAttributesPreparation
        .filter(preparation -> matchingPendingPreparation.isEmpty())
        .ifPresent(
            preparation ->
                completePendingPayloadAttributesPreparationExceptionally(
                    preparation,
                    "was replaced by pinned block production for slot "
                        + requestedBlockProductionSlot));
    final ForkChoiceUpdateData productionForkChoiceUpdateData =
        matchingPendingPreparation
            .map(__ -> forkChoiceUpdateData.withFreshForkChoiceState(forkChoiceState))
            .orElseGet(
                () ->
                    getForkChoiceUpdateDataForPinnedBlockProduction(
                        forkChoiceState, requestedBlockProductionSlot));
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
      productionForkChoiceUpdateData
          .getExecutionPayloadContext()
          .propagateTo(preparation.executionPayloadContext());
      sendForkChoiceUpdated(productionForkChoiceUpdateData);
    } else if (matchingPendingPreparation.isPresent()) {
      LOG.debug(
          "Pinned block production for slot {} is waiting for inclusion list payload attributes",
          requestedBlockProductionSlot);
    } else {
      updatePayloadAttributesForPinnedBlockProduction(preparation);
    }
  }

  private ForkChoiceUpdateData getForkChoiceUpdateDataForPinnedBlockProduction(
      final ForkChoiceState forkChoiceState, final UInt64 requestedBlockProductionSlot) {
    if (hasPayloadAttributesForPinnedBlockProduction(
        forkChoiceUpdateData, forkChoiceState, requestedBlockProductionSlot)) {
      return forkChoiceUpdateData.withForkChoiceStateRetainingPayloadAttributes(forkChoiceState);
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
    return forkChoiceUpdateData.getForkChoiceState().headBlock().equals(forkChoiceState.headBlock())
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
    clearPinnedBlockProductionIfExpired(slot, "attestations were due for slot " + slot);
    final UInt64 proposalSlot = slot.plus(1);
    if (hasInclusionListPayloadAttributesForSlot(proposalSlot)) {
      return;
    }
    updatePayloadAttributes(proposalSlot);
  }

  private boolean hasInclusionListPayloadAttributesForSlot(final UInt64 proposalSlot) {
    return forkChoiceUpdateData
        .getPayloadBuildingAttributes()
        .filter(
            payloadBuildingAttributes ->
                payloadBuildingAttributes.proposalSlot().equals(proposalSlot))
        .filter(
            payloadBuildingAttributes ->
                !payloadBuildingAttributes.inclusionListTransactions().isEmpty())
        .isPresent();
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

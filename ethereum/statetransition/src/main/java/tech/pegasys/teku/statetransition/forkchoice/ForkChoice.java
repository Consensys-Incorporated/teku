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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.logging.P2PLogger.P2P_LOG;
import static tech.pegasys.teku.statetransition.forkchoice.StateRootCollector.addParentStateRoots;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.SlotAndForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.Status;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerificationException;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.attestation.DeferredAttestations;
import tech.pegasys.teku.statetransition.attestation.VoteUpdates;
import tech.pegasys.teku.statetransition.block.BlockImportPerformance;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.ForkChoiceFastConfirmation;
import tech.pegasys.teku.statetransition.payloadattestation.ValidatablePayloadAttestationMessage;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.AttestationStateSelector;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.DeferredVotes;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice implements ForkChoiceUpdatedResultSubscriber {

  public static final int BLOCK_CREATION_TOLERANCE_MS = 500;
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final EventThread forkChoiceExecutor;
  private final ForkChoiceStateProvider forkChoiceStateProvider;
  private final RecentChainData recentChainData;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final MergeTransitionBlockValidator transitionBlockValidator;
  private final AttestationStateSelector attestationStateSelector;
  private final DeferredAttestations deferredAttestations = new DeferredAttestations();

  private final Subscribers<OptimisticHeadSubscriber> optimisticSyncSubscribers =
      Subscribers.create(true);
  private final TickProcessor tickProcessor;
  private final FastConfirmationTracker fastConfirmationTracker;
  private final ForkChoiceFastConfirmation forkChoiceFastConfirmation;
  private final boolean forkChoiceLateBlockReorgEnabled;
  private final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler;
  private Optional<Boolean> optimisticSyncing = Optional.empty();

  private final AtomicReference<UInt64> lastProcessHeadSlot = new AtomicReference<>();

  private final LabelledMetric<Counter> getProposerHeadSelectedCounter;

  private final DebugDataDumper debugDataDumper;
  private final AsyncBLSSignatureVerifier signatureVerifier;

  public ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final TickProcessor tickProcessor,
      final MergeTransitionBlockValidator transitionBlockValidator,
      final FastConfirmationTracker fastConfirmationTracker,
      final boolean forkChoiceLateBlockReorgEnabled,
      final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler,
      final DebugDataDumper debugDataDumper,
      final MetricsSystem metricsSystem,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    this.spec = spec;
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.forkChoiceStateProvider = forkChoiceStateProvider;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.transitionBlockValidator = transitionBlockValidator;
    this.attestationStateSelector =
        new AttestationStateSelector(spec, recentChainData, metricsSystem);
    this.tickProcessor = tickProcessor;
    this.fastConfirmationTracker = fastConfirmationTracker;
    this.forkChoiceFastConfirmation = new ForkChoiceFastConfirmation(spec, fastConfirmationTracker);
    this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
    this.lateBlockReorgPreparationHandler = lateBlockReorgPreparationHandler;
    this.lastProcessHeadSlot.set(UInt64.ZERO);
    LOG.debug("fastConfirmationEnabled is set to {}", fastConfirmationTracker.isEnabled());
    LOG.debug("forkChoiceLateBlockReorgEnabled is set to {}", forkChoiceLateBlockReorgEnabled);
    this.debugDataDumper = debugDataDumper;
    this.signatureVerifier = signatureVerifier;
    getProposerHeadSelectedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "get_proposer_head_selection_total",
            "when late_block_reorg is enabled, counts based on the proposer parent being based on fork choice, head, or parent of head.",
            "selected_source");
    recentChainData.subscribeStoreInitialized(this::onStoreInitialized);
    forkChoiceNotifier.subscribeToForkChoiceUpdatedResult(this);
  }

  @VisibleForTesting
  public ForkChoice(
      final Spec spec,
      final EventThread forkChoiceExecutor,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final MergeTransitionBlockValidator transitionBlockValidator,
      final MetricsSystem metricsSystem) {
    this(
        spec,
        forkChoiceExecutor,
        recentChainData,
        forkChoiceNotifier,
        new ForkChoiceStateProvider(forkChoiceExecutor, recentChainData),
        new TickProcessor(spec, recentChainData),
        transitionBlockValidator,
        FastConfirmationTracker.NOOP,
        false,
        LateBlockReorgPreparationHandler.NOOP,
        DebugDataDumper.NOOP,
        metricsSystem,
        AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
  }

  @Override
  public void onForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification) {
    forkChoiceUpdatedResultNotification
        .forkChoiceUpdatedResultFuture()
        .thenAccept(
            forkChoiceUpdatedResult -> {
              if (forkChoiceUpdatedResultNotification.isTerminalBlockCall()
                  && forkChoiceUpdatedResult.getPayloadStatus().hasInvalidStatus()) {
                LOG.error(
                    "Execution engine considers INVALID recently provided terminal block {}",
                    forkChoiceUpdatedResultNotification.forkChoiceState().headExecutionBlockHash());
                return;
              }
              onForkChoiceUpdatedPayloadResult(
                  forkChoiceUpdatedResultNotification.forkChoiceState(),
                  forkChoiceUpdatedResult.getPayloadStatus());
            })
        .finish(
            error -> {
              final String errorMessage = "Failed to update fork choice. ";
              if (ExceptionUtil.hasCause(error, ConnectException.class)) {
                LOG.error(errorMessage + error.getMessage());
              } else {
                LOG.error(errorMessage, error);
              }
            });
  }

  public SafeFuture<Optional<ChainHead>> processHead() {
    return processHead(Optional.empty(), true);
  }

  /** on_block */
  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final BlockBroadcastValidator blockBroadcastValidator,
      final ExecutionLayerChannel executionLayer) {
    recentChainData.setBlockTimelinessIfEmpty(block);
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(block.getSlot()).getForkChoiceUtil();
    return forkChoiceUtil
        .retrievePreStateRequiredOnBlock(recentChainData.getStore(), block)
        .thenPeek(__ -> blockImportPerformance.ifPresent(BlockImportPerformance::preStateRetrieved))
        .thenCompose(
            blockSlotState ->
                onBlock(
                    block,
                    blockSlotState,
                    blockImportPerformance,
                    blockBroadcastValidator,
                    executionLayer,
                    forkChoiceUtil));
  }

  /** on_execution_payload_envelope */
  public SafeFuture<ExecutionPayloadImportResult> onExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionLayerChannel executionLayer) {
    return recentChainData
        .retrieveBlockAndState(signedEnvelope.getBeaconBlockRoot())
        .thenCompose(
            maybeBlockAndState ->
                onExecutionPayloadEnvelope(signedEnvelope, maybeBlockAndState, executionLayer));
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidatableAttestation attestation) {
    return attestationStateSelector
        .getStateToValidate(attestation.getData())
        .thenCompose(
            maybeState -> {
              final UpdatableStore store = recentChainData.getStore();
              return spec.validateAttestationAsync(
                      store, attestation, maybeState, signatureVerifier)
                  .thenCompose(
                      validationResult -> {
                        if (!validationResult.isSuccessful()) {
                          if (validationResult.getStatus() == Status.DEFER_FORK_CHOICE_PROCESSING) {
                            final IndexedAttestationLight indexedAttestation =
                                getIndexedAttestation(attestation);
                            final boolean fullPayloadHint =
                                getFullPayloadVoteHint(attestation.getData());
                            if (!fullPayloadHint) {
                              // No full payload target to check, so avoid the fork choice thread
                              deferredAttestations.addAttestation(indexedAttestation, false);
                              return SafeFuture.completedFuture(validationResult);
                            }
                            return onForkChoiceThread(
                                () -> {
                                  if (isFullPayloadVoteMissingTarget(
                                      getForkChoiceStrategy(),
                                      indexedAttestation.data().getBeaconBlockRoot(),
                                      true)) {
                                    return AttestationProcessingResult.UNKNOWN_EXECUTION_PAYLOAD;
                                  }
                                  deferredAttestations.addAttestation(indexedAttestation, true);
                                  return validationResult;
                                });
                          }
                          return SafeFuture.completedFuture(validationResult);
                        }
                        return onForkChoiceThread(
                            () -> {
                              final IndexedAttestationLight indexedAttestation =
                                  getIndexedAttestation(attestation);
                              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
                              if (isFullPayloadVoteMissingTarget(
                                  forkChoiceStrategy,
                                  indexedAttestation.data().getBeaconBlockRoot(),
                                  getFullPayloadVoteHint(indexedAttestation.data()))) {
                                return AttestationProcessingResult.UNKNOWN_EXECUTION_PAYLOAD;
                              }
                              final VoteUpdater transaction = recentChainData.startVoteUpdate();
                              forkChoiceStrategy.onAttestation(transaction, indexedAttestation);
                              transaction.commit();
                              return validationResult;
                            });
                      });
            })
        .exceptionallyCompose(
            error -> {
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof InvalidCheckpointException) {
                return SafeFuture.completedFuture(
                    AttestationProcessingResult.invalid(rootCause.getMessage()));
              }
              return SafeFuture.failedFuture(error);
            });
  }

  public SafeFuture<List<ValidatableAttestation>> applyIndexedAttestations(
      final List<ValidatableAttestation> attestations) {
    return onForkChoiceThread(
        () -> {
          final VoteUpdater transaction = recentChainData.startVoteUpdate();
          final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
          final List<ValidatableAttestation> attestationsWaitingForFullPayload = new ArrayList<>();
          attestations.forEach(
              attestation -> {
                final IndexedAttestationLight indexedAttestation =
                    getIndexedAttestation(attestation);
                if (!applyIndexedAttestation(forkChoiceStrategy, transaction, indexedAttestation)) {
                  attestationsWaitingForFullPayload.add(attestation);
                }
              });
          transaction.commit();
          return attestationsWaitingForFullPayload;
        });
  }

  public void onAttesterSlashing(
      final AttesterSlashing slashing,
      final InternalValidationResult validationStatus,
      final boolean fromNetwork) {
    if (!validationStatus.isAccept()) {
      return;
    }
    onForkChoiceThread(
            () -> {
              final VoteUpdater transaction = recentChainData.startVoteUpdate();
              storeEquivocatingIndices(slashing, transaction);
              transaction.commit();
            })
        .finishStackTrace();
  }

  public void onPayloadAttestationMessage(
      final ValidatablePayloadAttestationMessage validatableMessage,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (!result.isAccept()) {
      return;
    }
    final IntSet ptcPositions =
        validatableMessage
            .getPtcPositions()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "PTC positions must be calculated before recording a payload attestation vote"));
    recentChainData
        .getUpdatableForkChoiceStrategy()
        .ifPresent(
            strategy ->
                strategy.onPtcVote(
                    validatableMessage.getData().getBeaconBlockRoot(),
                    ptcPositions,
                    validatableMessage.getData().isPayloadPresent(),
                    validatableMessage.getData().isBlobDataAvailable()));
  }

  public void subscribeToOptimisticHeadChangesAndUpdate(final OptimisticHeadSubscriber subscriber) {
    optimisticSyncSubscribers.subscribe(subscriber);
    getOptimisticSyncing().ifPresent(subscriber::onOptimisticHeadChanged);
  }

  public void onTick(
      final UInt64 currentTimeMillis, final Optional<TickProcessingPerformance> performanceRecord) {
    final UpdatableStore store = recentChainData.getStore();
    final UInt64 slotAtStartOfTick = spec.getCurrentSlot(store);
    tickProcessor.onTick(currentTimeMillis).join();
    performanceRecord.ifPresent(TickProcessingPerformance::tickProcessorComplete);
    final UInt64 currentSlot = spec.getCurrentSlot(store);
    if (currentSlot.isGreaterThan(slotAtStartOfTick)) {
      final SafeFuture<Void> deferredAttestationsFuture = applyDeferredAttestations(currentSlot);
      // When fast confirmation is disabled this is exactly the master behaviour. When enabled, the
      // slot-start work runs entirely off the fork-choice thread (on the fast confirmation runner)
      // with no blocking join; it never gates tick processing.
      if (fastConfirmationTracker.isEnabled()) {
        forkChoiceFastConfirmation.processForSlot(
            currentSlot,
            deferredAttestationsFuture,
            this::processHeadWithoutForkChoiceUpdate,
            this::notifyForkChoiceUpdatedForFastConfirmation);
      } else {
        deferredAttestationsFuture.finishStackTrace();
      }
    }
    performanceRecord.ifPresent(TickProcessingPerformance::deferredAttestationsApplied);
  }

  private void onStoreInitialized() {
    fastConfirmationTracker.initialize(recentChainData.getStore());
    initializeProtoArrayForkChoice();
  }

  private void initializeProtoArrayForkChoice() {
    processHead().join();
  }

  SafeFuture<Optional<ChainHead>> processHead(final UInt64 nodeSlot) {
    return processHead(Optional.of(nodeSlot), true);
  }

  /**
   * Processes the slot head for fast confirmation without sending the fcU. The confirmed root feeds
   * {@code safe_block_hash}, so the fcU is sent afterwards (see {@link
   * ForkChoiceFastConfirmation}), once {@code on_fast_confirmation} has updated it — otherwise the
   * slot-start fcU would carry the previous slot's confirmed root.
   */
  SafeFuture<Optional<ChainHead>> processHeadWithoutForkChoiceUpdate(final UInt64 nodeSlot) {
    return processHead(Optional.of(nodeSlot), false);
  }

  private SafeFuture<Optional<ChainHead>> processHead(
      final Optional<UInt64> nodeSlot, final boolean sendForkChoiceUpdated) {
    final Checkpoint retrievedJustifiedCheckpoint =
        recentChainData.getStore().getJustifiedCheckpoint();
    return recentChainData
        .retrieveCheckpointState(retrievedJustifiedCheckpoint)
        .thenCompose(
            maybeJustifiedCheckpointState ->
                onForkChoiceThread(
                    () -> {
                      final Checkpoint finalizedCheckpoint =
                          recentChainData.getStore().getFinalizedCheckpoint();
                      final Checkpoint justifiedCheckpoint =
                          recentChainData.getStore().getJustifiedCheckpoint();
                      if (!justifiedCheckpoint.equals(retrievedJustifiedCheckpoint)) {
                        LOG.debug(
                            "Skipping head block update as justified checkpoint was updated while loading checkpoint state. Was {} ({}) but now {} ({})",
                            retrievedJustifiedCheckpoint::getEpoch,
                            retrievedJustifiedCheckpoint::getRoot,
                            justifiedCheckpoint::getEpoch,
                            justifiedCheckpoint::getRoot);
                        return recentChainData.getChainHead();
                      }
                      if (maybeJustifiedCheckpointState.isEmpty()) {
                        LOG.debug(
                            "Retrieved justified checkpoint state for {} was empty, cannot update head given current information.",
                            justifiedCheckpoint::getRoot);
                        return recentChainData.getChainHead();
                      }
                      final Optional<ChainHead> newHead =
                          updateHeadTransaction(
                              nodeSlot,
                              maybeJustifiedCheckpointState.orElseThrow(),
                              finalizedCheckpoint,
                              justifiedCheckpoint);
                      nodeSlot.ifPresent(lastProcessHeadSlot::set);

                      // Callers that pass sendForkChoiceUpdated=false send the fcU themselves:
                      // pre-proposal (prepareForBlockProduction) after the proposer-head override,
                      // and fast confirmation after on_fast_confirmation updates the confirmed
                      // root.
                      if (sendForkChoiceUpdated) {
                        notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
                            Optional.empty(), Optional.empty());
                      }
                      return newHead;
                    }));
  }

  private Optional<ChainHead> updateHeadTransaction(
      final Optional<UInt64> nodeSlot,
      final BeaconState justifiedState,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint) {
    if (forkChoiceLateBlockReorgEnabled) {
      recentChainData.getStore().computeBalanceThresholds(justifiedState);
    }
    final VoteUpdater transaction = recentChainData.startVoteUpdate();
    final List<UInt64> justifiedEffectiveBalances =
        spec.getBeaconStateUtil(justifiedState.getSlot())
            .getEffectiveActiveUnslashedBalances(justifiedState);
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final Optional<Bytes32> proposerBoostRoot = recentChainData.getStore().getProposerBoostRoot();
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(currentSlot).getForkChoiceUtil();
    final boolean shouldApplyProposerBoost =
        proposerBoostRoot
            .map(
                root ->
                    forkChoiceUtil.shouldApplyProposerBoost(
                        root,
                        recentChainData,
                        recentChainData.getStore().getReorgThreshold(),
                        justifiedState))
            .orElse(true);
    final UInt64 proposerBoostAmount =
        shouldApplyProposerBoost ? spec.getProposerBoostAmount(justifiedState) : UInt64.ZERO;

    // If a runtime exception occurs while updating protoarray, we could skip the transaction
    // commit.
    // There is no clean way to solve it unless we move to a fully transactional protoarray update.
    // Currently, the assumption is that any exception thrown by design is happening before any
    // update to protoarray, so it is correct to skip the transaction commit.
    final SlotAndForkChoiceNode headNode =
        transaction.applyForkChoiceScoreChanges(
            currentSlot,
            recentChainData.getCurrentEpoch().orElseThrow(),
            finalizedCheckpoint,
            justifiedCheckpoint,
            justifiedEffectiveBalances,
            proposerBoostRoot,
            proposerBoostAmount);

    try {
      recentChainData.updateHead(headNode.node(), nodeSlot.orElse(headNode.slot()));
    } finally {
      // here we just make sure to commit, because protoarray has been updated. We just had an
      // exception while updating recentChainData which will become consistent again on the next
      // successful updateHead call
      transaction.commit();
    }

    return recentChainData.getChainHead();
  }

  /**
   * Import a block to the store. The supplied blockSlotState must already have empty slots
   * processed to the same slot as the block.
   */
  private SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block,
      final Optional<BeaconState> blockSlotState,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final BlockBroadcastValidator blockBroadcastValidator,
      final ExecutionLayerChannel executionLayer,
      final ForkChoiceUtil forkChoiceUtil) {
    if (blockSlotState.isEmpty()) {
      return SafeFuture.completedFuture(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    checkArgument(
        block.getSlot().equals(blockSlotState.get().getSlot()),
        "State must have processed slots up to the block slot. Block slot %s, state slot %s",
        block.getSlot(),
        blockSlotState.get().getSlot());

    final ForkChoicePayloadExecutor payloadExecutor =
        ForkChoicePayloadExecutor.create(spec, recentChainData, block, executionLayer);
    final BlockImportResult preconditionCheckResult =
        forkChoiceUtil.checkOnBlockConditions(
            block, blockSlotState.get(), recentChainData.getStore());
    if (!preconditionCheckResult.isSuccessful()) {
      reportInvalidBlock(block, preconditionCheckResult);
      return SafeFuture.completedFuture(preconditionCheckResult);
    }

    final CapturingIndexedAttestationCache indexedAttestationCache =
        IndexedAttestationCache.capturing();

    final AvailabilityChecker<?> availabilityChecker =
        forkChoiceUtil.createAvailabilityCheckerOnBlock(block);

    availabilityChecker.initiateDataAvailabilityCheck();

    final BeaconState postState;
    try {
      postState =
          spec.getBlockProcessor(block.getSlot())
              .processAndValidateBlock(
                  block,
                  blockSlotState.get(),
                  indexedAttestationCache,
                  Optional.of(payloadExecutor));
    } catch (final StateTransitionException e) {
      final BlockImportResult result = BlockImportResult.failedStateTransition(e);
      reportInvalidBlock(block, result);
      return SafeFuture.completedFuture(result);
    }
    blockImportPerformance.ifPresent(BlockImportPerformance::postStateCreated);

    final SafeFuture<? extends DataAndValidationResult<?>> dataAndValidationResultSafeFuture =
        availabilityChecker
            .getAndLogAvailabilityCheckResult(LOG)
            .thenPeek(
                result -> {
                  blockImportPerformance.ifPresent(BlockImportPerformance::dataAvailabilityChecked);
                  // consensus validation is completed when DA check is completed
                  if (result.isSuccess()) {
                    blockBroadcastValidator.onConsensusValidationSucceeded();
                    // For Fulu, record timeliness at DA completion rather than block body arrival
                    if (forkChoiceUtil.isDataAvailabilityRequiredForTimeliness()) {
                      recentChainData.setBlockTimelinessAfterDataAvailability(
                          block, recentChainData.getStore().getTimeInMillis());
                    }
                  }
                });

    final SafeFuture<PayloadValidationResult> payloadValidationFuture =
        payloadExecutor
            .getExecutionResult()
            .thenPeek(
                __ ->
                    blockImportPerformance.ifPresent(
                        BlockImportPerformance::executionResultReceived));

    return blockBroadcastValidator
        .getResult()
        .thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult.isFailure()) {
                return SafeFuture.completedFuture(BlockImportResult.FAILED_BROADCAST_VALIDATION);
              }

              return payloadValidationFuture.thenCombineAsync(
                  dataAndValidationResultSafeFuture,
                  (payloadResult, dataAndValidationResult) ->
                      importBlockAndState(
                          block,
                          blockSlotState.get(),
                          blockImportPerformance,
                          forkChoiceUtil,
                          indexedAttestationCache,
                          postState,
                          payloadResult,
                          dataAndValidationResult),
                  forkChoiceExecutor);
            });
  }

  /**
   * Import an execution payload envelope to the store. The supplied {@code blockAndState} must
   * contain the block and post-state after processing the block whose root is the beacon block root
   * of the execution payload
   */
  private SafeFuture<ExecutionPayloadImportResult> onExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final Optional<SignedBlockAndState> blockAndState,
      final ExecutionLayerChannel executionLayer) {
    if (blockAndState.isEmpty()) {
      return SafeFuture.completedFuture(
          ExecutionPayloadImportResult.FAILED_UNKNOWN_BEACON_BLOCK_ROOT);
    }

    final SignedBeaconBlock block = blockAndState.get().getBlock();
    final BeaconState state = blockAndState.get().getState();

    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(signedEnvelope.getSlot()).getForkChoiceUtil();

    final AvailabilityChecker<?> availabilityChecker =
        forkChoiceUtil.createAvailabilityCheckerOnExecutionPayloadEnvelope(block, signedEnvelope);
    availabilityChecker.initiateDataAvailabilityCheck();
    final ForkChoicePayloadExecutorGloas payloadExecutor =
        ForkChoicePayloadExecutorGloas.create(signedEnvelope, executionLayer);

    // Verify the execution payload envelope. Use the spec-configured verifier (SIMPLE in
    // production) rather than hardcoding it, so reference tests with bls_setting: 2 can disable
    // signature verification the same way every other verification path does.
    final BLSSignatureVerifier envelopeSignatureVerifier =
        spec.atSlot(signedEnvelope.getSlot()).getConfig().getBLSSignatureVerifier();
    try {
      spec.getExecutionPayloadVerifier(signedEnvelope.getSlot())
          .verifyExecutionPayloadEnvelope(
              signedEnvelope, state, envelopeSignatureVerifier, Optional.of(payloadExecutor));
    } catch (final ExecutionPayloadVerificationException ex) {
      final ExecutionPayloadImportResult result =
          ExecutionPayloadImportResult.failedVerification(ex);
      reportInvalidExecutionPayload(signedEnvelope, result);
      return SafeFuture.completedFuture(result);
    }

    final SafeFuture<? extends DataAndValidationResult<?>> dataAndValidationResultFuture =
        availabilityChecker.getAndLogAvailabilityCheckResult(LOG);

    return payloadExecutor
        .getExecutionResult()
        .thenCombineAsync(
            dataAndValidationResultFuture,
            (payloadResult, dataAndValidationResult) ->
                importExecutionPayload(
                    signedEnvelope, forkChoiceUtil, payloadResult, dataAndValidationResult),
            forkChoiceExecutor);
  }

  private BlockImportResult importBlockAndState(
      final SignedBeaconBlock block,
      final BeaconState blockSlotState,
      final Optional<BlockImportPerformance> blockImportPerformance,
      final ForkChoiceUtil forkChoiceUtil,
      final CapturingIndexedAttestationCache indexedAttestationCache,
      final BeaconState postState,
      final PayloadValidationResult payloadValidationResult,
      final DataAndValidationResult<?> dataAndValidationResult) {
    blockImportPerformance.ifPresent(BlockImportPerformance::beginImporting);
    final PayloadStatus payloadResult = payloadValidationResult.getStatus();
    if (payloadResult.hasInvalidStatus()) {
      final BlockImportResult result =
          BlockImportResult.failedStateTransition(
              new IllegalStateException(
                  "Invalid ExecutionPayload: "
                      + payloadResult.getValidationError().orElse("No reason provided")));
      reportInvalidBlock(block, result);
      payloadValidationResult
          .getInvalidTransitionBlockRoot()
          .ifPresentOrElse(
              invalidTransitionBlockRoot ->
                  getForkChoiceStrategy()
                      .onExecutionPayloadResult(invalidTransitionBlockRoot, payloadResult, true),
              () ->
                  getForkChoiceStrategy()
                      .onExecutionPayloadResult(block.getParentRoot(), payloadResult, false));
      return result;
    }

    if (payloadResult.hasNotValidatedStatus()
        && !spec.atSlot(block.getSlot())
            .getForkChoiceUtil()
            .canOptimisticallyImport(recentChainData.getStore(), block)) {
      return BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;
    }

    if (payloadResult.hasFailedExecution()) {
      return BlockImportResult.failedExecutionPayloadExecution(
          payloadResult.getFailureCause().orElseThrow());
    }

    switch (dataAndValidationResult.validationResult()) {
      case NOT_AVAILABLE -> {
        return BlockImportResult.failedDataAvailabilityCheckNotAvailable(
            dataAndValidationResult.cause());
      }
      case INVALID -> {
        debugDataDumper.saveInvalidSidecars(dataAndValidationResult.data(), block);
        return BlockImportResult.failedDataAvailabilityCheckInvalid(
            dataAndValidationResult.cause());
      }
      default -> {}
    }

    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();

    // Now that we're on the fork choice thread, make sure the block still descends from finalized
    // (which may have changed while we were processing the block)
    if (!forkChoiceUtil.blockDescendsFromLatestFinalizedBlock(
        block.getSlot(), block.getParentRoot(), recentChainData.getStore(), forkChoiceStrategy)) {
      return BlockImportResult.FAILED_INVALID_ANCESTRY;
    }

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    addParentStateRoots(spec, blockSlotState, transaction);

    final Optional<List<BlobSidecar>> blobSidecars =
        extractBlobSidecarsFromValidationResults(dataAndValidationResult, block);
    final Optional<UInt64> earliestBlobSidecarsSlot =
        computeEarliestBlobSidecarsSlot(
            recentChainData.getStore(), dataAndValidationResult, block.getMessage());

    final Optional<ForkChoiceNode> preImportHead =
        recentChainData.getChainHead().map(ChainHead::getForkChoiceNode);

    forkChoiceUtil.applyBlockToStore(
        transaction,
        block,
        postState,
        payloadResult.hasNotValidatedStatus(),
        blobSidecars,
        earliestBlobSidecarsSlot);

    final boolean shouldUpdateProposerBoostRoot =
        preImportHead
            .filter(
                forkChoiceNode ->
                    shouldUpdateProposerBoostRoot(
                        block, forkChoiceNode, forkChoiceStrategy, transaction))
            .isPresent();
    if (shouldUpdateProposerBoostRoot) {
      transaction.setProposerBoostRoot(block.getRoot());
    }

    blockImportPerformance.ifPresent(BlockImportPerformance::transactionReady);
    // Note: not using thenRun here because we want to ensure each step is on the event thread
    transaction.commit().join();
    blockImportPerformance.ifPresent(BlockImportPerformance::transactionCommitted);
    // Invalid payload statuses returned above, so the direct-invalid flag is irrelevant here.
    // Gloas skips this on_block notification entirely.
    if (forkChoiceUtil.shouldNotifyForkChoiceUpdatedOnBlock()) {
      forkChoiceStrategy.onExecutionPayloadResult(block.getRoot(), payloadResult, false);
    }

    final UInt64 currentEpoch = spec.computeEpochAtSlot(spec.getCurrentSlot(transaction));

    // We only need to apply attestations from the current or previous epoch. If the block is from
    // before that, none of the attestations will be applicable so just skip the whole step.
    if (spec.computeEpochAtSlot(block.getSlot())
        .isGreaterThanOrEqualTo(currentEpoch.minusMinZero(1))) {
      final VoteUpdater voteUpdater = recentChainData.startVoteUpdate();
      // We also need to handle recent AttesterSlashings to update equivocating validator indices
      // We don't need any epochs older than previous as it doesn't affect ForkChoice
      applyPayloadAttestationsFromBlock(block);
      applyAttesterSlashingsFromBlock(block, voteUpdater);
      applyVotesFromBlock(forkChoiceStrategy, currentEpoch, indexedAttestationCache, voteUpdater);
      voteUpdater.commit();
    }

    final BlockImportResult result;
    if (payloadResult.hasValidStatus()) {
      result = BlockImportResult.successful(block);
    } else {
      result = BlockImportResult.optimisticallySuccessful(block);
    }
    updateForkChoiceForImportedBlock(
        block, shouldUpdateProposerBoostRoot, result, forkChoiceStrategy);
    if (forkChoiceUtil.shouldNotifyForkChoiceUpdatedOnBlock()) {
      notifyForkChoiceUpdatedAndOptimisticSyncingChanged(Optional.empty(), Optional.empty());
    }
    return result;
  }

  private ExecutionPayloadImportResult importExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ForkChoiceUtil forkChoiceUtil,
      final PayloadStatus payloadStatus,
      final DataAndValidationResult<?> dataAndValidationResult) {
    if (payloadStatus.hasInvalidStatus()) {
      final ExecutionPayloadImportResult result =
          ExecutionPayloadImportResult.failedVerification(
              new IllegalStateException(
                  "Invalid ExecutionPayload: "
                      + payloadStatus.getValidationError().orElse("No reason provided")));
      reportInvalidExecutionPayload(signedEnvelope, result);
      getForkChoiceStrategy()
          .onExecutionPayloadResult(
              signedEnvelope.getParentBeaconBlockRoot(), payloadStatus, false);
      return result;
    }

    if (payloadStatus.hasFailedExecution()) {
      return ExecutionPayloadImportResult.failedExecution(
          payloadStatus.getFailureCause().orElseThrow());
    }

    switch (dataAndValidationResult.validationResult()) {
      case NOT_AVAILABLE -> {
        return ExecutionPayloadImportResult.failedDataAvailabilityCheckNotAvailable(
            dataAndValidationResult.cause());
      }
      case INVALID -> {
        return ExecutionPayloadImportResult.failedDataAvailabilityCheckInvalid(
            dataAndValidationResult.cause());
      }
      default -> {}
    }

    final StoreTransaction transaction = recentChainData.startStoreTransaction();

    final ExecutionPayloadImportResult result;
    if (payloadStatus.hasValidStatus()) {
      result = ExecutionPayloadImportResult.successful(signedEnvelope);
    } else {
      result = ExecutionPayloadImportResult.optimisticallySuccessful(signedEnvelope);
    }

    forkChoiceUtil.applyExecutionPayloadToStore(
        transaction, signedEnvelope, result.isImportedOptimistically());

    // Note: not using thenRun here because we want to ensure each step is on the event thread
    transaction.commit().join();

    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();

    forkChoiceStrategy.onExecutionPayloadResult(
        signedEnvelope.getBeaconBlockRoot(), payloadStatus, false);

    updateForkChoiceForImportedExecutionPayload(forkChoiceStrategy);
    notifyForkChoiceUpdatedAndOptimisticSyncingChanged(Optional.empty(), Optional.empty());

    return result;
  }

  /**
   * Apply the genesis execution payload envelope to the store. No-op for pre-Gloas genesis.
   *
   * <p>Gloas-and-later defines the chain head as a (block, payload) pair, so at genesis we seed a
   * FULL fork-choice node by importing an envelope whose payload carries the genesis EL block hash.
   * Without this, {@code internalGetPayloadId} sees a zero parent execution hash at slot 1 and
   * erroneously falls into the pre-merge branch.
   */
  public SafeFuture<Void> applyGenesisExecutionPayloadForGloas() {
    if (spec.getGenesisSpecConfig().getMilestone().isLessThan(SpecMilestone.GLOAS)) {
      return SafeFuture.COMPLETE;
    }
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.getGenesisSpec().getSchemaDefinitions());
    final BeaconStateGloas genesisState =
        BeaconStateGloas.required(recentChainData.getStore().getLatestFinalized().getState());
    final Bytes32 genesisExecutionBlockHash = genesisState.getLatestBlockHash();
    final ExecutionPayload genesisPayload =
        schemaDefinitions
            .getExecutionPayloadSchema()
            .createExecutionPayload(
                builder ->
                    builder
                        .parentHash(Bytes32.ZERO)
                        .feeRecipient(Bytes20.ZERO)
                        .stateRoot(Bytes32.ZERO)
                        .receiptsRoot(Bytes32.ZERO)
                        .logsBloom(Bytes.wrap(new byte[256]))
                        .prevRandao(Bytes32.ZERO)
                        .blockNumber(UInt64.ZERO)
                        .gasLimit(UInt64.ZERO)
                        .gasUsed(UInt64.ZERO)
                        .timestamp(UInt64.ZERO)
                        .extraData(Bytes.EMPTY)
                        .baseFeePerGas(UInt256.ZERO)
                        .blockHash(genesisExecutionBlockHash)
                        .transactions(List.of())
                        .withdrawals(List::of)
                        .blobGasUsed(() -> UInt64.ZERO)
                        .excessBlobGas(() -> UInt64.ZERO)
                        .blockAccessList(() -> Bytes.EMPTY)
                        .slotNumber(() -> UInt64.ZERO));
    final SignedExecutionPayloadEnvelope envelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(
                schemaDefinitions
                    .getExecutionPayloadEnvelopeSchema()
                    .create(
                        genesisPayload,
                        schemaDefinitions.getExecutionRequestsSchema().getDefault(),
                        UInt64.ZERO,
                        recentChainData.getBestBlockRoot().orElseThrow(),
                        Bytes32.ZERO),
                BLSSignature.empty());

    return onForkChoiceThread(
        () -> {
          final ForkChoiceUtil forkChoiceUtil = spec.getGenesisSpec().getForkChoiceUtil();
          final StoreTransaction transaction = recentChainData.startStoreTransaction();
          forkChoiceUtil.applyExecutionPayloadToStore(transaction, envelope, false);
          // Note: not using thenRun here because we want to ensure each step is on the event thread
          transaction.commit().join();

          final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
          updateForkChoiceForImportedExecutionPayload(forkChoiceStrategy);
          notifyForkChoiceUpdatedAndOptimisticSyncingChanged(Optional.empty(), Optional.empty());
        });
  }

  // from consensus-specs/fork-choice:
  private boolean shouldUpdateProposerBoostRoot(
      final SignedBeaconBlock block,
      final ForkChoiceNode preImportHead,
      final ForkChoiceStrategy forkChoiceStrategy,
      final StoreTransaction transaction) {
    // is_first_block
    if (transaction.getProposerBoostRoot().isPresent()) {
      return false;
    }
    // is_timely
    if (recentChainData.isBlockLate(block.getRoot())) {
      return false;
    }

    final UInt64 currentEpoch = spec.computeEpochAtSlot(spec.getCurrentSlot(transaction));
    final Optional<Bytes32> maybeBlockDependentRoot =
        getShufflingDependentRoot(block, currentEpoch, forkChoiceStrategy);
    final Optional<Bytes32> maybeHeadDependentRoot =
        getShufflingDependentRoot(preImportHead, currentEpoch, forkChoiceStrategy);
    return maybeBlockDependentRoot.isPresent()
        && maybeBlockDependentRoot.equals(maybeHeadDependentRoot);
  }

  private Optional<Bytes32> getShufflingDependentRoot(
      final SignedBeaconBlock block,
      final UInt64 epoch,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final Optional<UInt64> maybeDependentSlot = getShufflingDependentSlot(epoch);
    if (maybeDependentSlot.isEmpty()) {
      return Optional.of(Bytes32.ZERO);
    }
    final UInt64 dependentSlot = maybeDependentSlot.get();
    if (!block.getSlot().isGreaterThan(dependentSlot)) {
      return Optional.of(block.getRoot());
    }
    return forkChoiceStrategy.getAncestor(block.getParentRoot(), dependentSlot);
  }

  private Optional<Bytes32> getShufflingDependentRoot(
      final ForkChoiceNode node, final UInt64 epoch, final ForkChoiceStrategy forkChoiceStrategy) {
    final Optional<UInt64> maybeDependentSlot = getShufflingDependentSlot(epoch);
    if (maybeDependentSlot.isEmpty()) {
      return Optional.of(Bytes32.ZERO);
    }
    return forkChoiceStrategy
        .getAncestorNode(node, maybeDependentSlot.get())
        .map(ForkChoiceNode::blockRoot);
  }

  private Optional<UInt64> getShufflingDependentSlot(final UInt64 epoch) {
    final int minSeedLookahead = spec.getSpecConfig(epoch).getMinSeedLookahead();
    if (epoch.isLessThanOrEqualTo(UInt64.valueOf(minSeedLookahead))) {
      return Optional.empty();
    }
    return Optional.of(spec.computeStartSlotAtEpoch(epoch.minus(minSeedLookahead)).minus(1));
  }

  private Optional<List<BlobSidecar>> extractBlobSidecarsFromValidationResults(
      final DataAndValidationResult<?> dataAndValidationResult, final SignedBeaconBlock block) {
    if (dataAndValidationResult.isNotRequired()) {
      // Outside availability window or pre-Deneb
      return Optional.empty();
    } else if (dataAndValidationResult.isValid()) {
      return dataAndValidationResult.getDataAsBlobSidecars();
    }
    throw new IllegalStateException(
        String.format(
            "Unexpected attempt to store invalid sidecars (%s) for block: %s",
            dataAndValidationResult.data().size(), block.toLogString()));
  }

  /**
   * In order to keep track of DataAvailability Window, we need to compute the earliest slot we can
   * consider data available for the given block. It needs to take in account possible empty slots
   * so the actual slot should be parent block slot + 1. Moreover, we need to manage the case in
   * which the calculated slot is before Deneb activation, so it should never be before the first
   * deneb slot.
   *
   * @param store requires for data availability window calculation
   * @param dataAndValidationResult If it's not required or invalid, we could skip calculation
   * @param block Beacon block
   * @return the slot we could mark as earliestBlobSidecarsSlot if any, otherwise Optional.empty()
   */
  private Optional<UInt64> computeEarliestBlobSidecarsSlot(
      final ReadOnlyStore store,
      final DataAndValidationResult<?> dataAndValidationResult,
      final BeaconBlock block) {
    if (!dataAndValidationResult.isValid()) {
      return Optional.empty();
    }
    final UInt64 earliestAffectedSlot =
        recentChainData
            .getSlotForBlockRoot(block.getParentRoot())
            .map(UInt64::increment)
            .orElse(block.getSlot());

    final Optional<UInt64> maybeEarliestAvailabilityWindowSlotBeforeBlock =
        spec.atSlot(block.getSlot())
            .getForkChoiceUtil()
            .getEarliestAvailabilityWindowSlotBeforeBlock(spec, store, block.getSlot());

    return maybeEarliestAvailabilityWindowSlotBeforeBlock.map(
        earliestAvailabilityWindowSlotBeforeBlock ->
            earliestAvailabilityWindowSlotBeforeBlock.max(earliestAffectedSlot));
  }

  private void onForkChoiceUpdatedPayloadResult(
      final ForkChoiceState forkChoiceState, final PayloadStatus payloadResult) {
    final ForkChoiceNode node = forkChoiceState.headBlock();
    final SafeFuture<PayloadValidationResult> transitionValidatedStatus;
    if (payloadResult.hasValidStatus()) {
      transitionValidatedStatus =
          transitionBlockValidator.verifyAncestorTransitionBlock(node.blockRoot());
    } else {
      transitionValidatedStatus =
          SafeFuture.completedFuture(new PayloadValidationResult(payloadResult));
    }
    transitionValidatedStatus.finishAsync(
        result -> {
          final PayloadStatus resultStatus = result.getStatus();
          final Optional<Bytes32> maybeInvalidTransitionBlockRoot =
              result.getInvalidTransitionBlockRoot();
          final Bytes32 invalidBlockRoot = maybeInvalidTransitionBlockRoot.orElse(node.blockRoot());

          if (resultStatus.hasValidStatus()) {
            // A valid forkchoiceUpdated response validates the exact node sent to the EL. In
            // Gloas, EMPTY and FULL nodes can share a block root, so keep node identity instead of
            // collapsing to block root.
            getForkChoiceStrategy()
                .onForkChoiceUpdatedResult(
                    new SlotAndForkChoiceNode(forkChoiceState.headBlockSlot(), node),
                    resultStatus,
                    false);
          } else {
            // invalidTransitionBlockRoot is only populated when merge-transition validation found a
            // specific Bellatrix transition block to invalidate. Otherwise the EL verdict applies
            // to the exact fork-choice node we sent in forkchoiceUpdated.
            maybeInvalidTransitionBlockRoot.ifPresentOrElse(
                invalidTransitionBlockRoot ->
                    getForkChoiceStrategy()
                        .onExecutionPayloadResult(invalidTransitionBlockRoot, resultStatus, true),
                () ->
                    getForkChoiceStrategy()
                        .onForkChoiceUpdatedResult(
                            new SlotAndForkChoiceNode(forkChoiceState.headBlockSlot(), node),
                            resultStatus,
                            true));
          }

          if (resultStatus.hasInvalidStatus()) {
            LOG.warn("Will run fork choice because head block {} was invalid", invalidBlockRoot);
            processHead().finish(error -> LOG.error("Fork choice updating head failed", error));
          }
        },
        error -> {
          // Pass FatalServiceFailureException up to the uncaught exception handler.
          // This will cause Teku to exit because the error is unrecoverable.
          // We specifically do this here because a FatalServiceFailureException will be thrown if
          // a justified or finalized block is found to be invalid.
          if (ExceptionUtil.hasCause(error, FatalServiceFailureException.class)) {
            Thread.currentThread()
                .getUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), error);
          } else {
            LOG.error("Failed to apply payload result for node {}", node, error);
          }
        },
        forkChoiceExecutor);
  }

  private void updateForkChoiceForImportedBlock(
      final SignedBeaconBlock block,
      final boolean shouldUpdateProposerBoostRoot,
      final BlockImportResult result,
      final ForkChoiceStrategy forkChoiceStrategy) {

    final ChainHead currentHead = recentChainData.getChainHead().orElseThrow();

    final SlotAndForkChoiceNode bestHeadBlock = findNewChainHead(forkChoiceStrategy);
    if (!currentHead.isSameHeadAs(bestHeadBlock)) {
      recentChainData.updateHead(bestHeadBlock.node(), bestHeadBlock.slot());
    }

    if (bestHeadBlock.node().blockRoot().equals(block.getRoot())) {
      result.markAsCanonical();
    }

    if (!result.isBlockOnCanonicalChain() && shouldUpdateProposerBoostRoot) {
      // This is likely a reorging block that requires a full processHead to update the head.
      // Running processHead here will ensure:
      //
      // - node producing a reorging block will attest and produce sync committee for the reorging
      // block
      // - node just importing (not producing it) a reorging block will produce sync committee for
      // the new head
      //
      // It wouldn't be enough to trigger processHead at attestationDue because it will only
      // cover attestation generation and not sync committee message, since it is generated
      // VC side and requires head event to be sent before attestationDue.
      processHead().finish(error -> LOG.error("Fork choice updating head failed", error));
    }
  }

  private void updateForkChoiceForImportedExecutionPayload(
      final ForkChoiceStrategy forkChoiceStrategy) {
    final ChainHead currentHead = recentChainData.getChainHead().orElseThrow();
    final SlotAndForkChoiceNode bestHeadBlock = findNewChainHead(forkChoiceStrategy);
    if (!currentHead.isSameHeadAs(bestHeadBlock)) {
      recentChainData.updateHead(bestHeadBlock.node(), bestHeadBlock.slot());
    }
  }

  private SlotAndForkChoiceNode findNewChainHead(final ForkChoiceStrategy forkChoiceStrategy) {
    // use fork choice to find the new chain head as if this block is on time the proposer weighting
    // may cause us to reorg.
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    return forkChoiceStrategy.findHead(
        recentChainData.getCurrentEpoch().orElseThrow(), justifiedCheckpoint, finalizedCheckpoint);
  }

  private void reportInvalidBlock(final SignedBeaconBlock block, final BlockImportResult result) {
    if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE
        || result.getFailureReason() == FailureReason.UNKNOWN_PARENT_EXECUTION_PAYLOAD) {
      return;
    }
    debugDataDumper.saveInvalidBlock(
        block, result.getFailureReason().name(), result.getFailureCause());
    P2P_LOG.onInvalidBlock(
        block.getSlot(),
        block.getRoot(),
        block.sszSerialize(),
        result.getFailureReason().name(),
        result.getFailureCause());
  }

  private void reportInvalidExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionPayloadImportResult result) {
    debugDataDumper.saveInvalidExecutionPayload(
        signedEnvelope, result.getFailureReason().name(), result.getFailureCause());
    P2P_LOG.onInvalidExecutionPayload(
        signedEnvelope.getSlot(),
        signedEnvelope.getMessage().getBuilderIndex(),
        signedEnvelope.getBeaconBlockRoot(),
        signedEnvelope.sszSerialize(),
        result.getFailureReason().name(),
        result.getFailureCause());
  }

  /**
   * Sends the slot-start fcU on the fork-choice thread after {@code on_fast_confirmation} has run,
   * so {@code safe_block_hash} reflects this slot's confirmed root (see {@link
   * #processHeadWithoutForkChoiceUpdate}).
   */
  private SafeFuture<Void> notifyForkChoiceUpdatedForFastConfirmation() {
    return onForkChoiceThread(
        () ->
            notifyForkChoiceUpdatedAndOptimisticSyncingChanged(Optional.empty(), Optional.empty()));
  }

  private void notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
      final Optional<UInt64> proposingSlot, final Optional<ChainHead> proposingOnHead) {
    checkState(
        proposingSlot.isPresent() == proposingOnHead.isPresent(),
        "proposing slot and proposingOnHead must be consistent");

    final ForkChoiceState forkChoiceState =
        forkChoiceStateProvider.getForkChoiceStateSync(proposingOnHead);

    forkChoiceNotifier.onForkChoiceUpdated(forkChoiceState, proposingSlot);
    getProposerHeadSelectedCounter.labels("fork_choice").inc();

    if (optimisticSyncing
        .map(oldValue -> !oldValue.equals(forkChoiceState.isHeadOptimistic()))
        .orElse(true)) {
      optimisticSyncing = Optional.of(forkChoiceState.isHeadOptimistic());
      optimisticSyncSubscribers.deliver(
          OptimisticHeadSubscriber::onOptimisticHeadChanged, forkChoiceState.isHeadOptimistic());
    }
  }

  private void applyVotesFromBlock(
      final ForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final CapturingIndexedAttestationCache indexedAttestationProvider,
      final VoteUpdater voteUpdater) {
    indexedAttestationProvider.getIndexedAttestations().stream()
        .filter(
            attestation -> validateBlockAttestation(forkChoiceStrategy, currentEpoch, attestation))
        .forEach(
            attestation -> applyIndexedAttestation(forkChoiceStrategy, voteUpdater, attestation));
  }

  private boolean validateBlockAttestation(
      final ForkChoiceStrategy forkChoiceStrategy,
      final UInt64 currentEpoch,
      final IndexedAttestationLight attestation) {
    return spec.atSlot(attestation.data().getSlot())
        .getForkChoiceUtil()
        .validateOnAttestation(forkChoiceStrategy, currentEpoch, attestation.data())
        .isSuccessful();
  }

  private SafeFuture<Void> applyDeferredAttestations(
      final Collection<DeferredVotes> deferredVoteUpdates) {
    return onForkChoiceThread(() -> applyDeferredAttestationsToForkChoice(deferredVoteUpdates));
  }

  private void applyAttesterSlashingsFromBlock(
      final SignedBeaconBlock signedBeaconBlock, final VoteUpdater voteUpdater) {
    signedBeaconBlock
        .getMessage()
        .getBody()
        .getAttesterSlashings()
        .forEach(attesterSlashing -> storeEquivocatingIndices(attesterSlashing, voteUpdater));
  }

  private void applyPayloadAttestationsFromBlock(final SignedBeaconBlock signedBeaconBlock) {
    signedBeaconBlock
        .getMessage()
        .getBody()
        .getOptionalPayloadAttestations()
        .ifPresent(
            payloadAttestations ->
                payloadAttestations.forEach(this::applyPayloadAttestationFromBlock));
  }

  private void applyPayloadAttestationFromBlock(final PayloadAttestation payloadAttestation) {
    recentChainData
        .getStore()
        .getBlockStateIfAvailable(payloadAttestation.getData().getBeaconBlockRoot())
        .filter(
            attestedBlockState ->
                attestedBlockState.getSlot().equals(payloadAttestation.getData().getSlot()))
        .ifPresent(
            attestedBlockState ->
                getPayloadAttestationMessagesFromBlock(attestedBlockState, payloadAttestation)
                    .forEach(
                        validatableMessage -> {
                          validatableMessage.calculatePtcPositions(spec, attestedBlockState);
                          onPayloadAttestationMessage(
                              validatableMessage, InternalValidationResult.ACCEPT, false);
                        }));
  }

  @VisibleForTesting
  List<ValidatablePayloadAttestationMessage> getPayloadAttestationMessagesFromBlock(
      final BeaconState attestedBlockState, final PayloadAttestation payloadAttestation) {
    final UInt64 slot = payloadAttestation.getData().getSlot();
    final IntList ptc = spec.getPtc(attestedBlockState, slot);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    return payloadAttestation
        .getAggregationBits()
        .streamAllSetBits()
        .mapToObj(
            ptcPosition -> {
              final PayloadAttestationMessage message =
                  schemaDefinitions
                      .getPayloadAttestationMessageSchema()
                      .create(
                          UInt64.valueOf(ptc.getInt(ptcPosition)),
                          payloadAttestation.getData(),
                          BLSSignature.empty());
              return ValidatablePayloadAttestationMessage.fromBlock(message);
            })
        .toList();
  }

  private void storeEquivocatingIndices(
      final AttesterSlashing attesterSlashing, final VoteUpdater transaction) {
    attesterSlashing
        .getIntersectingValidatorIndices()
        .forEach(
            validatorIndex -> {
              final VoteTracker voteTracker = transaction.getVote(validatorIndex);
              if (!voteTracker.isEquivocating()) {
                transaction.putVote(validatorIndex, voteTracker.createNextEquivocating());
              }
            });
  }

  public UInt64 getLastProcessHeadSlot() {
    return lastProcessHeadSlot.get();
  }

  @VisibleForTesting
  boolean isFastConfirmationEnabled() {
    return fastConfirmationTracker.isEnabled();
  }

  @VisibleForTesting
  Optional<FastConfirmationStore> getFastConfirmationStore() {
    return fastConfirmationTracker.getFastConfirmationStore();
  }

  SafeFuture<ChainHead> prepareForBlockProduction(
      final UInt64 slot, final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis());
    final UInt64 currentTime = recentChainData.getStore().getTimeInMillis();
    // We haven't yet transitioned into this slot but will do very soon, so make it happen now
    if (!currentTime
        .plus(BLOCK_CREATION_TOLERANCE_MS)
        .isGreaterThanOrEqualTo(slotStartTimeMillis)) {
      // Creating a block too far before the slot start, don't run fork choice at all.
      LOG.warn(
          "Block creation requested more than {}ms before the start of slot {}",
          BLOCK_CREATION_TOLERANCE_MS,
          slot);
      return SafeFuture.completedFuture(recentChainData.getChainHead().orElseThrow());
    }

    return SafeFuture.allOf(
            tickProcessor
                .onTick(slotStartTimeMillis)
                .thenPeek(__ -> blockProductionPerformance.prepareOnTick()),
            applyDeferredAttestations(slot)
                .thenPeek(__ -> blockProductionPerformance.prepareApplyDeferredAttestations()))
        // Block production sends its own fcU (after the proposer-head override), so suppress it
        // here; a chain head is required for block production.
        .thenCompose(__ -> processHead(Optional.of(slot), false))
        .thenApply(
            maybeHead ->
                maybeHead.orElseThrow(
                    () -> new IllegalStateException("Missing chain head for block production")))
        .thenPeek(canonicalHead -> updateFastConfirmationForProposalSlot(slot, canonicalHead))
        .thenCompose(
            canonicalHead ->
                applyProposerHeadOverrideAndNotify(canonicalHead, slot, blockProductionPerformance))
        .thenPeek(__ -> blockProductionPerformance.prepareProcessHead());
  }

  /**
   * Block production advances the store into the proposal slot itself (via {@code
   * tickProcessor.onTick} above), so {@link #onTick} sees no slot boundary and does not run the
   * fast confirmation update for that slot. Run it here so {@code on_fast_confirmation} and the
   * once-per-slot rotation are not skipped on proposal slots (which would leave {@code
   * FastConfirmationStore} stale for later slots).
   *
   * <p>Invoked on the fork-choice event thread — where {@link #onTick} also drives {@code onSlot} —
   * so the tracker's per-slot chain stays single-threaded, and fire-and-forget so it never gates
   * block production. Uses the pre-override canonical head ({@code get_head} at slot start). The
   * tracker dedups if {@code onTick} already ran this slot, and the proposer fcU below already
   * carries the confirmed root, so no extra fcU is sent here.
   */
  private void updateFastConfirmationForProposalSlot(
      final UInt64 slot, final ChainHead canonicalHead) {
    if (!fastConfirmationTracker.isEnabled() || !forkChoiceFastConfirmation.isRunning()) {
      return;
    }
    onForkChoiceThread(
            () ->
                fastConfirmationTracker
                    .onSlot(slot, canonicalHead.getRoot())
                    .finish(error -> logFastConfirmationProposalSlotFailure(slot, error)))
        .finish(error -> logFastConfirmationProposalSlotFailure(slot, error));
  }

  private void logFastConfirmationProposalSlotFailure(final UInt64 slot, final Throwable error) {
    if (forkChoiceFastConfirmation.isRunning()) {
      LOG.error("Fast confirmation update for proposal slot {} failed", slot, error);
    } else {
      LOG.debug(
          "Fast confirmation update for proposal slot {} abandoned during shutdown", slot, error);
    }
  }

  /**
   * The fast confirmation pipeline driven by {@link #onTick}. Its lifecycle is owned by the beacon
   * chain controller, which must stop it before stopping the fork-choice event thread.
   */
  public ForkChoiceFastConfirmation getFastConfirmationService() {
    return forkChoiceFastConfirmation;
  }

  /**
   * If late-block-reorg is enabled and {@code get_proposer_head} flips the proposer to the parent
   * of the canonical head, materialize a {@link ChainHead} for the parent (with the parent's
   * FULL/EMPTY payload variant) and trigger the late-block reorg preparation handler. The
   * proposer-head fcU notification fires from the same event-thread tick (notify requires the
   * fork-choice event thread). Returned future completes after the preparation handler completes,
   * mirroring the previous behavior of {@code CombinedChainDataClient.getStateForBlockProduction}
   * which awaited preparation before returning the state.
   */
  private SafeFuture<ChainHead> applyProposerHeadOverrideAndNotify(
      final ChainHead canonicalHead,
      final UInt64 slot,
      final BlockProductionPerformance performance) {
    return forkChoiceExecutor.executeFuture(
        () -> {
          final Optional<ChainHead> overriddenHead =
              resolveProposerHeadOverride(canonicalHead, slot);
          final ChainHead forkChoiceProposerHead = overriddenHead.orElse(canonicalHead);
          final SafeFuture<Void> preparation;
          if (overriddenHead.isPresent()) {
            // Run preparation in parallel; await it before returning so block-body construction
            // sees the updated operation pools.
            preparation =
                lateBlockReorgPreparationHandler
                    .onLateBlockReorgPreparation(
                        forkChoiceProposerHead.getSlot(), canonicalHead.getRoot())
                    .thenPeek(__ -> performance.lateBlockReorgPreparationCompleted());
          } else {
            preparation = SafeFuture.COMPLETE;
          }
          // Always notify, even when proposerHead is unchanged. The proposing slot turns this into
          // the block-production fcU that pins payload attributes and asks the EL to start
          // building.
          notifyForkChoiceUpdatedAndOptimisticSyncingChanged(
              Optional.of(slot), Optional.of(forkChoiceProposerHead));
          return preparation.thenApply(__ -> forkChoiceProposerHead);
        });
  }

  /**
   * Returns the proposer-head {@link ChainHead} when {@code get_proposer_head} flips the proposer
   * to the parent of the canonical head, or {@link Optional#empty()} when no override is needed.
   */
  private Optional<ChainHead> resolveProposerHeadOverride(
      final ChainHead canonicalHead, final UInt64 slot) {
    forkChoiceExecutor.checkOnEventThread();
    if (!forkChoiceLateBlockReorgEnabled) {
      return Optional.empty();
    }
    final Bytes32 canonicalRoot = canonicalHead.getRoot();
    final ForkChoiceNode proposerHead =
        recentChainData.getProposerHead(canonicalHead.getForkChoiceNode(), slot);
    if (proposerHead.equals(canonicalHead.getForkChoiceNode())) {
      return Optional.empty();
    }

    LOG.debug(
        "prepareForBlockProduction overriding head {} with proposer head {} for slot {}",
        canonicalRoot,
        proposerHead,
        slot);

    final ForkChoiceStrategy strategy = getForkChoiceStrategy();
    final UpdatableStore store = recentChainData.getStore();
    final Optional<ProtoNodeData> parentBlockData = strategy.getNodeData(proposerHead);

    if (parentBlockData.isEmpty()) {
      LOG.warn(
          "Unable to resolve proposer head {} in fork choice; sticking with canonical head {}",
          proposerHead,
          canonicalRoot);
      return Optional.empty();
    }

    final SafeFuture<StateAndBlockSummary> parentStateAndBlock =
        store
            .retrieveStateAndBlockSummary(proposerHead.blockRoot())
            .thenApply(
                maybe ->
                    maybe.orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Unable to load proposer head state for %s while preparing block production at slot %s",
                                    proposerHead, slot))));

    return Optional.of(ChainHead.create(parentBlockData.get(), parentStateAndBlock));
  }

  private SafeFuture<Void> applyDeferredAttestations(final UInt64 slot) {
    final Collection<DeferredVotes> deferredVoteUpdates = deferredAttestations.prune(slot);
    if (deferredVoteUpdates.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    return applyDeferredAttestations(deferredVoteUpdates);
  }

  private boolean applyIndexedAttestation(
      final ForkChoiceStrategy forkChoiceStrategy,
      final VoteUpdater voteUpdater,
      final IndexedAttestationLight attestation) {
    if (isFullPayloadVoteMissingTarget(
        forkChoiceStrategy,
        attestation.data().getBeaconBlockRoot(),
        getFullPayloadVoteHint(attestation.data()))) {
      return false;
    }
    forkChoiceStrategy.onAttestation(voteUpdater, attestation);
    return true;
  }

  private void applyDeferredAttestationsToForkChoice(
      final Collection<DeferredVotes> deferredVoteUpdates) {
    final VoteUpdater transaction = recentChainData.startVoteUpdate();
    final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
    deferredVoteUpdates.forEach(
        update -> {
          final VoteUpdates votesReadyToApply = new VoteUpdates(update.getSlot());
          update.forEachDeferredVote(
              (blockRoot, validatorIndex, fullPayloadHint) -> {
                if (isFullPayloadVoteMissingTarget(
                    forkChoiceStrategy, blockRoot, fullPayloadHint)) {
                  return;
                }
                votesReadyToApply.addVote(blockRoot, validatorIndex, fullPayloadHint);
              });
          if (!votesReadyToApply.isEmpty()) {
            forkChoiceStrategy.applyDeferredAttestations(transaction, votesReadyToApply);
          }
        });
    transaction.commit();
  }

  private boolean isFullPayloadVoteMissingTarget(
      final ForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 blockRoot,
      final boolean fullPayloadHint) {
    return fullPayloadHint
        && forkChoiceStrategy.contains(blockRoot)
        && forkChoiceStrategy
            .getBlockData(blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)
            .isEmpty();
  }

  private boolean getFullPayloadVoteHint(final AttestationData attestationData) {
    return spec.atSlot(attestationData.getSlot())
        .getForkChoiceUtil()
        .getFullPayloadVoteHint(attestationData.getIndex());
  }

  private ForkChoiceStrategy getForkChoiceStrategy() {
    forkChoiceExecutor.checkOnEventThread();
    return recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to perform fork choice operations before store has been initialized"));
  }

  private IndexedAttestationLight getIndexedAttestation(final ValidatableAttestation attestation) {
    return attestation
        .getIndexedAttestation()
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "ValidatableAttestation does not have an IndexedAttestation."));
  }

  private SafeFuture<Void> onForkChoiceThread(final ExceptionThrowingRunnable task) {
    return onForkChoiceThread(
        () -> {
          task.run();
          return null;
        });
  }

  private <T> SafeFuture<T> onForkChoiceThread(final ExceptionThrowingSupplier<T> task) {
    return forkChoiceExecutor.execute(task);
  }

  private Optional<Boolean> getOptimisticSyncing() {
    return optimisticSyncing;
  }

  public interface OptimisticHeadSubscriber {
    void onOptimisticHeadChanged(boolean isHeadOptimistic);
  }
}

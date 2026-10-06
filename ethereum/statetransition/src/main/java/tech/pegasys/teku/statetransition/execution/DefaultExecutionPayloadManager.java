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

package tech.pegasys.teku.statetransition.execution;

import static tech.pegasys.teku.spec.config.Constants.VALID_EXECUTION_PAYLOAD_SET_SIZE;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.BlockRootAndBuilderIndex;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DefaultExecutionPayloadManager
    implements ExecutionPayloadManager, ReceivedBlockEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  // A payload is kept pending while its beacon block is still missing. Payloads from before the
  // finalized slot are never queued, so the cache only needs to span the non-finalized window.
  // Under healthy finality that window is ~2 epochs (2 * SLOTS_PER_EPOCH = 64 slots on mainnet)
  private static final int PENDING_EXECUTION_PAYLOADS_CACHE_SIZE = 64;

  private final Set<Bytes32> executionPayloadsSeenBeforePayloadDue =
      LimitedSet.createSynchronizedNatural(VALID_EXECUTION_PAYLOAD_SET_SIZE);

  // pending pool
  private final Map<BlockRootAndBuilderIndex, PendingExecutionPayload> pendingExecutionPayloads =
      LimitedMap.createSynchronizedNatural(PENDING_EXECUTION_PAYLOADS_CACHE_SIZE);

  private final Subscribers<FailedPayloadExecutionSubscriber> failedPayloadExecutionSubscribers =
      Subscribers.create(true);

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final ExecutionPayloadGossipValidator executionPayloadGossipValidator;
  private final ForkChoice forkChoice;
  private final ExecutionLayerChannel executionLayer;
  private final ReceivedExecutionPayloadEventsChannel
      receivedExecutionPayloadEventsChannelPublisher;
  private final RecentChainData recentChainData;
  private final Set<Bytes32> blockRootsWithInvalidExecutionPayload;
  private final Function<SignedExecutionPayloadEnvelope, SafeFuture<Void>>
      executionPayloadPublisher;

  public DefaultExecutionPayloadManager(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final ExecutionPayloadGossipValidator executionPayloadGossipValidator,
      final ForkChoice forkChoice,
      final ExecutionLayerChannel executionLayer,
      final ReceivedExecutionPayloadEventsChannel receivedExecutionPayloadEventsChannelPublisher,
      final RecentChainData recentChainData,
      final Set<Bytes32> blockRootsWithInvalidExecutionPayload,
      final Function<SignedExecutionPayloadEnvelope, SafeFuture<Void>> executionPayloadPublisher) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.executionPayloadGossipValidator = executionPayloadGossipValidator;
    this.forkChoice = forkChoice;
    this.executionLayer = executionLayer;
    this.receivedExecutionPayloadEventsChannelPublisher =
        receivedExecutionPayloadEventsChannelPublisher;
    this.recentChainData = recentChainData;
    this.blockRootsWithInvalidExecutionPayload = blockRootsWithInvalidExecutionPayload;
    this.executionPayloadPublisher = executionPayloadPublisher;
  }

  @Override
  public boolean isExecutionPayloadRecentlySeen(final Bytes32 beaconBlockRoot) {
    return executionPayloadGossipValidator.isPayloadSeen(beaconBlockRoot);
  }

  @Override
  public boolean isExecutionPayloadSeenBeforeDeadline(final Bytes32 beaconBlockRoot) {
    return executionPayloadsSeenBeforePayloadDue.contains(beaconBlockRoot);
  }

  @Override
  public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return validateAndImportExecutionPayload(
        signedExecutionPayload, Optional.empty(), Optional.empty());
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<UInt64> maybeArrivalTimestamp,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final UInt64 arrivalTimestamp =
        maybeArrivalTimestamp.orElseGet(() -> recentChainData.getStore().getTimeInMillis());
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadGossipValidator.validate(signedExecutionPayload, broadcastValidationLevel);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case ACCEPT -> {
              receivedExecutionPayloadEventsChannelPublisher.onExecutionPayloadValidated(
                  signedExecutionPayload);
              recordIfExecutionPayloadIsSeenBeforeDeadline(
                  signedExecutionPayload, arrivalTimestamp);
              importExecutionPayload(signedExecutionPayload, true).finishError(LOG);
            }
            case SAVE_FOR_FUTURE ->
                saveForFuture(signedExecutionPayload, arrivalTimestamp, broadcastValidationLevel);
            case IGNORE, REJECT -> {}
          }
        });
    return validationResult;
  }

  @Override
  public SafeFuture<ValidateAndImportResult> validateAndImportExecutionPayloadForBroadcast(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final UInt64 arrivalTimestamp = recentChainData.getStore().getTimeInMillis();
    return executionPayloadGossipValidator
        .validate(signedExecutionPayload, broadcastValidationLevel)
        .thenApply(
            result -> {
              switch (result.code()) {
                case ACCEPT -> {
                  receivedExecutionPayloadEventsChannelPublisher.onExecutionPayloadValidated(
                      signedExecutionPayload);
                  recordIfExecutionPayloadIsSeenBeforeDeadline(
                      signedExecutionPayload, arrivalTimestamp);
                  return new ValidateAndImportResult(
                      result, Optional.of(importExecutionPayload(signedExecutionPayload, true)));
                }
                case SAVE_FOR_FUTURE ->
                    saveForFuture(
                        signedExecutionPayload, arrivalTimestamp, broadcastValidationLevel);
                case IGNORE, REJECT -> {}
              }
              return new ValidateAndImportResult(result, Optional.empty());
            });
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void saveForFuture(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final UInt64 arrivalTimestamp,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    if (recentChainData.containsBlock(signedExecutionPayload.getBeaconBlockRoot())) {
      // handles edge case where block was imported while validating the payload
      asyncRunner
          .runAfterDelay(
              () ->
                  validateAndImportExecutionPayload(
                          signedExecutionPayload,
                          Optional.of(arrivalTimestamp),
                          broadcastValidationLevel)
                      .thenCompose(r -> publishPayload(r, signedExecutionPayload)),
              Duration.ofMillis(100))
          .finishError(LOG);
    } else {
      // since no slashing is implemented in order to prevent DoS, we keep one pending
      // payload: the latest candidate received before payload due, or the first candidate
      // if none arrived before payload due
      pendingExecutionPayloads.merge(
          signedExecutionPayload.getBlockRootAndBuilderIndex(),
          new PendingExecutionPayload(signedExecutionPayload, arrivalTimestamp),
          this::selectPendingExecutionPayload);
    }
  }

  @Override
  public SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final boolean payloadCommitmentVerified) {
    return asyncRunner
        .runAsync(
            () ->
                forkChoice.onExecutionPayloadEnvelope(
                    signedExecutionPayload,
                    executionLayer,
                    Optional.of(receivedExecutionPayloadEventsChannelPublisher)))
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                LOG.debug(
                    "Successfully imported execution payload {}",
                    signedExecutionPayload::toLogString);
                receivedExecutionPayloadEventsChannelPublisher.onExecutionPayloadImported(
                    signedExecutionPayload, result.isImportedOptimistically());
                return;
              }
              switch (result.getFailureReason()) {
                case FAILED_EXECUTION -> {
                  LOG.error(
                      "Unable to import execution payload {}. Execution Client returned an error: {}",
                      signedExecutionPayload::toLogString,
                      () -> result.getFailureCause().map(Throwable::getMessage).orElse(""));
                  failedPayloadExecutionSubscribers.deliver(
                      FailedPayloadExecutionSubscriber::onPayloadExecutionFailed,
                      signedExecutionPayload);
                }
                case FAILED_VERIFICATION, FAILED_DATA_AVAILABILITY_CHECK_INVALID -> {
                  if (payloadCommitmentVerified) {
                    blockRootsWithInvalidExecutionPayload.add(
                        signedExecutionPayload.getBeaconBlockRoot());
                  }
                  logFailedExecutionPayloadImport(signedExecutionPayload, result);
                }
                case INTERNAL_ERROR,
                    UNKNOWN_BEACON_BLOCK_ROOT,
                    FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE ->
                    logFailedExecutionPayloadImport(signedExecutionPayload, result);
              }
            })
        .exceptionally(
            ex -> {
              final String internalErrorMessage =
                  String.format(
                      "Internal error while importing execution payload: %s. Execution payload content: %s",
                      signedExecutionPayload.toLogString(),
                      signedExecutionPayload.sszSerialize().toHexString());
              LOG.error(internalErrorMessage, ex);
              return ExecutionPayloadImportResult.internalError(ex);
            });
  }

  private void logFailedExecutionPayloadImport(
      final SignedExecutionPayloadEnvelope executionPayload,
      final ExecutionPayloadImportResult importResult) {
    LOG.debug(
        "Unable to import execution payload for reason {}: {}",
        importResult::toLogString,
        executionPayload::toLogString);
  }

  @Override
  public SafeFuture<ExecutionRequests> getParentExecutionRequestsForBlock(
      final UInt64 slot, final Bytes32 parentRoot, final ForkChoicePayloadStatus payloadStatus) {
    if (!payloadStatus.equals(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)) {
      return SafeFuture.completedFuture(
          SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
              .getExecutionRequestsSchema()
              .getDefault());
    }
    // to avoid querying the EL when unblinding in some cases, we directly query for the blinded
    // execution payload which includes going over the in-memory payloads as well
    return recentChainData
        .retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot)
        .thenApply(
            executionPayload ->
                executionPayload
                    .map(SignedBlindedExecutionPayloadEnvelope::getExecutionRequests)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Execution Requests for parent root %s are not available during block production for slot %s",
                                    parentRoot, slot))));
  }

  @Override
  public void subscribeFailedPayloadExecution(final FailedPayloadExecutionSubscriber subscriber) {
    failedPayloadExecutionSubscribers.subscribe(subscriber);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    // Process pending execution payload
    block
        .getMessage()
        .getBody()
        .toVersionGloas()
        .map(BeaconBlockBodyGloas::getSignedExecutionPayloadBid)
        .map(
            bid ->
                new BlockRootAndBuilderIndex(block.getRoot(), bid.getMessage().getBuilderIndex()))
        .map(pendingExecutionPayloads::remove)
        .ifPresent(
            pendingExecutionPayload ->
                validateAndImportExecutionPayload(
                        pendingExecutionPayload.executionPayload(),
                        Optional.of(pendingExecutionPayload.arrivalTimestamp()))
                    .thenCompose(r -> publishPayload(r, pendingExecutionPayload.executionPayload()))
                    .finishError(LOG));
  }

  private void recordIfExecutionPayloadIsSeenBeforeDeadline(
      final SignedExecutionPayloadEnvelope signedExecutionPayload, final UInt64 arrivalTimestamp) {
    if (isBeforePayloadDue(signedExecutionPayload, arrivalTimestamp)) {
      executionPayloadsSeenBeforePayloadDue.add(signedExecutionPayload.getBeaconBlockRoot());
    }
  }

  private PendingExecutionPayload selectPendingExecutionPayload(
      final PendingExecutionPayload existing, final PendingExecutionPayload candidate) {
    if (isBeforePayloadDue(candidate.executionPayload(), candidate.arrivalTimestamp())) {
      return candidate;
    }
    return existing;
  }

  private boolean isBeforePayloadDue(
      final SignedExecutionPayloadEnvelope signedExecutionPayload, final UInt64 arrivalTimestamp) {
    final UInt64 slot = signedExecutionPayload.getSlot();
    return spec.isBeforeTimeInSlot(
        slot,
        recentChainData.getGenesisTimeMillis(),
        arrivalTimestamp,
        spec.getPayloadDueMillis(slot).orElseThrow());
  }

  // publish payload in cases where initial validation wasn't accepted
  private SafeFuture<Void> publishPayload(
      final InternalValidationResult result,
      final SignedExecutionPayloadEnvelope executionPayload) {
    if (result.isAccept()) {
      return executionPayloadPublisher.apply(executionPayload);
    }
    return SafeFuture.COMPLETE;
  }

  public record PendingExecutionPayload(
      SignedExecutionPayloadEnvelope executionPayload, UInt64 arrivalTimestamp) {}
}

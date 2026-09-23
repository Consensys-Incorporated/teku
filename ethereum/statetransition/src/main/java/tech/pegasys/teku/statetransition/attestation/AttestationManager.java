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

package tech.pegasys.teku.statetransition.attestation;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingAttestationPool;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;

public class AttestationManager extends Service
    implements SlotEventsChannel,
        ReceivedBlockEventsChannel,
        ReceivedExecutionPayloadEventsChannel {

  private static final Logger LOG = LogManager.getLogger();
  private final ActiveValidatorChannel activeValidatorChannel;
  private static final SafeFuture<AttestationProcessingResult> ATTESTATION_SAVED_FOR_FUTURE_RESULT =
      SafeFuture.completedFuture(AttestationProcessingResult.SAVED_FOR_FUTURE);

  private final ForkChoice attestationProcessor;

  private final PendingAttestationPool pendingAttestationPool;
  private final FutureItems<ValidatableAttestation> futureAttestations;
  private final AggregatingAttestationPool aggregatingAttestationPool;

  private final Subscribers<ProcessedAttestationListener> attestationsToSendSubscribers =
      Subscribers.create(true);
  private final Subscribers<ProcessedAttestationListener> allValidAttestationsSubscribers =
      Subscribers.create(true);

  private final AttestationValidator attestationValidator;
  private final AggregateAttestationValidator aggregateValidator;
  // SignatureVerificationService is only included here, so that it's lifecycle can be controlled by
  // AttestationManager
  private final SignatureVerificationService signatureVerificationService;

  AttestationManager(
      final ForkChoice attestationProcessor,
      final PendingAttestationPool pendingAttestationPool,
      final FutureItems<ValidatableAttestation> futureAttestations,
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationValidator attestationValidator,
      final AggregateAttestationValidator aggregateValidator,
      final SignatureVerificationService signatureVerificationService,
      final ActiveValidatorChannel activeValidatorChannel) {
    this.attestationProcessor = attestationProcessor;
    this.pendingAttestationPool = pendingAttestationPool;
    this.futureAttestations = futureAttestations;
    this.aggregatingAttestationPool = aggregatingAttestationPool;
    this.attestationValidator = attestationValidator;
    this.aggregateValidator = aggregateValidator;
    this.signatureVerificationService = signatureVerificationService;
    this.activeValidatorChannel = activeValidatorChannel;
  }

  public static AttestationManager create(
      final PendingAttestationPool pendingAttestationPool,
      final FutureItems<ValidatableAttestation> futureAttestations,
      final ForkChoice attestationProcessor,
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationValidator attestationValidator,
      final AggregateAttestationValidator aggregateValidator,
      final SignatureVerificationService signatureVerificationService,
      final ActiveValidatorChannel activeValidatorChannel) {
    return new AttestationManager(
        attestationProcessor,
        pendingAttestationPool,
        futureAttestations,
        aggregatingAttestationPool,
        attestationValidator,
        aggregateValidator,
        signatureVerificationService,
        activeValidatorChannel);
  }

  public void subscribeToAllValidAttestations(final ProcessedAttestationListener listener) {
    allValidAttestationsSubscribers.subscribe(listener);
  }

  private void notifyAllValidAttestationsSubscribers(final ValidatableAttestation attestation) {
    allValidAttestationsSubscribers.forEach(s -> s.accept(attestation));
  }

  public void subscribeToAttestationsToSend(
      final ProcessedAttestationListener attestationsToSendListener) {
    attestationsToSendSubscribers.subscribe(attestationsToSendListener);
  }

  private void validateForGossipAndNotifySendSubscribers(final ValidatableAttestation attestation) {
    if (attestation.isAggregate() && !attestation.isAcceptedAsGossip()) {
      // We know the Attestation is valid, but need to validate the SignedAggregateAndProof wrapper
      aggregateValidator
          .validate(attestation)
          .finish(
              result -> {
                if (result.isAccept()) {
                  attestationsToSendSubscribers.deliver(
                      ProcessedAttestationListener::accept, attestation);
                }
              },
              error ->
                  LOG.error(
                      "Not gossiping aggregate from slot {} because an error occurred during validation",
                      attestation.getData().getSlot(),
                      error));
    } else {
      // Any attestation that passes the fork choice rules is valid to send as gossip
      attestationsToSendSubscribers.deliver(ProcessedAttestationListener::accept, attestation);
    }
  }

  public SafeFuture<InternalValidationResult> addAttestation(
      final ValidatableAttestation attestation, final Optional<UInt64> arrivalTime) {
    SafeFuture<InternalValidationResult> validationResult =
        attestationValidator.validate(attestation);
    processInternallyValidatedAttestation(validationResult, attestation);
    return validationResult;
  }

  public SafeFuture<InternalValidationResult> addAggregate(
      final ValidatableAttestation attestation, final Optional<UInt64> arrivalTime) {
    SafeFuture<InternalValidationResult> validationResult =
        aggregateValidator.validate(attestation);
    processInternallyValidatedAttestation(validationResult, attestation);
    return validationResult;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void processInternallyValidatedAttestation(
      final SafeFuture<InternalValidationResult> validationResult,
      final ValidatableAttestation attestation) {
    validationResult.thenAccept(
        internalValidationResult -> {
          if (internalValidationResult.code().equals(ValidationResultCode.ACCEPT)
              || internalValidationResult.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            onAttestation(attestation)
                .finish(
                    result ->
                        result.ifInvalid(
                            reason -> LOG.debug("Rejected received attestation: " + reason)),
                    err -> {
                      final Throwable rootException = Throwables.getRootCause(err);
                      if (rootException instanceof RejectedExecutionException) {
                        // these tend to flood the channel, and we're already struggling
                        LOG.trace("Failed to process received attestation.: {}", err.getMessage());
                      } else {
                        LOG.error("Failed to process received attestation.", err);
                      }
                    });
            notifyAllValidAttestationsSubscribers(attestation);
          }
        });
  }

  @Override
  public void onSlot(final UInt64 slot) {
    applyFutureAttestations(slot);
  }

  private void applyFutureAttestations(final UInt64 slot) {
    futureAttestations.onSlot(slot);
    List<ValidatableAttestation> attestations = futureAttestations.prune(slot);
    if (attestations.isEmpty()) {
      return;
    }
    attestationProcessor
        .applyIndexedAttestations(attestations)
        .finish(
            attestationsWaitingForFullPayload -> {
              attestationsWaitingForFullPayload.forEach(
                  pendingAttestationPool::addForMissingFullPayload);
              attestations.stream()
                  .filter(a -> !attestationsWaitingForFullPayload.contains(a))
                  .filter(ValidatableAttestation::isProducedLocally)
                  .filter(a -> !a.isGossiped())
                  .forEach(
                      a -> {
                        validateForGossipAndNotifySendSubscribers(a);
                        notifyAllValidAttestationsSubscribers(a);
                      });
            },
            error -> LOG.error("Failed to process future attestations.", error));
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // No-op
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    activeValidatorChannel.onBlockImported(block);
    pendingAttestationPool
        .removeAttestationsWaitingForBlock(blockRoot)
        .forEach(
            attestation ->
                onAttestation(attestation)
                    .finish(
                        err ->
                            LOG.error(
                                "Failed to process pending attestation dependent on {}",
                                blockRoot,
                                err)));
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {}

  @Override
  public void onExecutionPayloadAvailable(final SignedExecutionPayloadEnvelope executionPayload) {}

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    final Bytes32 blockRoot = executionPayload.getBeaconBlockRoot();
    pendingAttestationPool
        .removeAttestationsWaitingForFullPayload(blockRoot)
        .forEach(
            attestation ->
                onAttestation(attestation)
                    .finish(
                        err ->
                            LOG.error(
                                "Failed to process pending attestation dependent on full payload {}",
                                blockRoot,
                                err)));
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidatableAttestation attestation) {
    if (pendingAttestationPool.contains(attestation)) {
      return ATTESTATION_SAVED_FOR_FUTURE_RESULT;
    }

    return attestationProcessor
        .onAttestation(attestation)
        .thenApply(
            result -> {
              activeValidatorChannel.onAttestation(attestation);

              switch (result.getStatus()) {
                case SUCCESSFUL -> {
                  LOG.trace("Processed attestation {} successfully", attestation::hashTreeRoot);
                  aggregatingAttestationPool.add(attestation);
                  sendToSubscribersIfProducedLocally(attestation);
                }
                case UNKNOWN_BLOCK -> {
                  LOG.trace(
                      "Deferring attestation {} as required block is not yet present",
                      attestation::hashTreeRoot);
                  pendingAttestationPool.addForMissingBlock(attestation);
                }
                case UNKNOWN_EXECUTION_PAYLOAD -> {
                  LOG.trace(
                      "Deferring attestation {} as required full payload is not yet present",
                      attestation::hashTreeRoot);
                  pendingAttestationPool.addForMissingFullPayload(attestation);
                }
                case DEFER_FORK_CHOICE_PROCESSING -> {
                  LOG.trace(
                      "Defer fork choice processing of attestation {}", attestation::hashTreeRoot);
                  sendToSubscribersIfProducedLocally(attestation);
                  aggregatingAttestationPool.add(attestation);
                }
                case SAVED_FOR_FUTURE -> {
                  LOG.trace(
                      "Deferring attestation {} until a future slot", attestation::hashTreeRoot);
                  aggregatingAttestationPool.add(attestation);
                  futureAttestations.add(attestation);
                }
                case INVALID -> {}
                default ->
                    throw new UnsupportedOperationException(
                        "AttestationProcessingResult is unrecognizable");
              }
              return result;
            });
  }

  private void sendToSubscribersIfProducedLocally(final ValidatableAttestation attestation) {
    if (!attestation.isProducedLocally()) {
      return;
    }
    if (attestation.isAggregate()) {
      aggregateValidator.addSeenAggregate(attestation);
    }

    validateForGossipAndNotifySendSubscribers(attestation);
    notifyAllValidAttestationsSubscribers(attestation);
    attestation.markGossiped();
  }

  @Override
  protected SafeFuture<?> doStart() {
    return signatureVerificationService.start();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return signatureVerificationService.stop();
  }
}

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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.config.Constants.VALID_EXECUTION_PAYLOAD_SET_SIZE;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.BlockRootAndBuilderIndex;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;

public class ExecutionPayloadGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final GossipValidationHelper gossipValidationHelper;
  private final BlockGossipValidator blockGossipValidator;
  private final SigningRootUtil signingRootUtil;

  private final Set<BlockRootAndBuilderIndex> seenPayloads =
      LimitedSet.createSynchronizedLRU(VALID_EXECUTION_PAYLOAD_SET_SIZE);

  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;

  public ExecutionPayloadGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final BlockGossipValidator blockGossipValidator,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots) {
    this.gossipValidationHelper = gossipValidationHelper;
    this.blockGossipValidator = blockGossipValidator;
    this.invalidBlockRoots = invalidBlockRoots;
    signingRootUtil = new SigningRootUtil(spec);
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope) {
    return validate(signedExecutionPayloadEnvelope, Optional.empty());
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    if (broadcastValidationLevel.isPresent()
        && broadcastValidationLevel.get().equals(BroadcastValidationLevel.NOT_REQUIRED)) {
      return SafeFuture.completedFuture(ACCEPT);
    }
    final ExecutionPayloadEnvelope envelope = signedExecutionPayloadEnvelope.getMessage();

    final Optional<InternalValidationResult> preBlockValidationResult =
        performPreBlockValidation(envelope);
    if (preBlockValidationResult.isPresent()) {
      return SafeFuture.completedFuture(preBlockValidationResult.get());
    }

    return performWithBlockValidation(envelope)
        .thenCompose(
            maybeResult -> {
              if (maybeResult.isPresent()) {
                return SafeFuture.completedFuture(maybeResult.get());
              }

              return performWithStateValidation(signedExecutionPayloadEnvelope)
                  .thenCompose(
                      result -> {
                        if (result.isAccept()
                            && broadcastValidationLevel
                                .map(CONSENSUS_AND_EQUIVOCATION::equals)
                                .orElse(false)) {
                          // Extra broadcast-level equivocation check
                          return performEquivocationCheck(envelope);
                        }
                        return SafeFuture.completedFuture(markAsSeen(result, envelope));
                      });
            });
  }

  private SafeFuture<InternalValidationResult> performEquivocationCheck(
      final ExecutionPayloadEnvelope envelope) {
    return gossipValidationHelper
        .retrieveBlockByRoot(envelope.getBeaconBlockRoot())
        .thenApply(
            maybeBeaconBlock -> {
              if (maybeBeaconBlock
                  .map(
                      beaconBlock ->
                          blockGossipValidator.isBlockEquivocating(
                              beaconBlock.getSlot(),
                              beaconBlock.getProposerIndex(),
                              beaconBlock.getRoot()))
                  .orElse(false)) {
                return reject(
                    "Execution payload envelope's beacon block with root %s is an equivocation",
                    envelope.getBeaconBlockRoot());
              }
              return markAsSeen(ACCEPT, envelope);
            });
  }

  public boolean isPayloadSeen(final Bytes32 beaconBlockRoot) {
    return seenPayloads.stream()
        .anyMatch(
            blockRootAndBuilderIndex ->
                blockRootAndBuilderIndex.blockRoot().equals(beaconBlockRoot));
  }

  private SafeFuture<Optional<InternalValidationResult>> performWithBlockValidation(
      final ExecutionPayloadEnvelope envelope) {
    return gossipValidationHelper
        .retrieveBlockByRoot(envelope.getBeaconBlockRoot())
        .thenApply(
            maybeBeaconBlock -> {
              if (maybeBeaconBlock.isEmpty()) {
                LOG.trace(
                    "Block with root {} is unavailable. Saving the execution payload envelope for future processing",
                    envelope.getBeaconBlockRoot());
                return Optional.of(SAVE_FOR_FUTURE);
              }

              final BeaconBlock beaconBlock = maybeBeaconBlock.get();
              final Optional<ExecutionPayloadBid> maybeExecutionPayloadBid =
                  beaconBlock
                      .getBody()
                      .getOptionalSignedExecutionPayloadBid()
                      .map(SignedExecutionPayloadBid::getMessage);

              if (maybeExecutionPayloadBid.isEmpty()) {
                LOG.trace(
                    "Missing execution payload bid in block with root {}. Rejecting the execution payload envelope",
                    beaconBlock.getRoot());
                return Optional.of(
                    reject(
                        "Missing execution payload bid in block with root %s",
                        beaconBlock.getRoot().toHexString()));
              }

              final ExecutionPayloadBid bid = maybeExecutionPayloadBid.get();

              /*
               * [REJECT] The envelope is from the builder committed to by the bid
               */
              if (!envelope.getBuilderIndex().equals(bid.getBuilderIndex())) {
                LOG.trace(
                    "Invalid builder index. Execution payload envelope had {} but the block execution payload bid had {}. Rejecting the execution payload envelope",
                    envelope.getBuilderIndex(),
                    bid.getBuilderIndex());
                return Optional.of(
                    reject(
                        "Invalid builder index. Execution payload envelope had %s but the block execution payload bid had %s",
                        envelope.getBuilderIndex(), bid.getBuilderIndex()));
              }
              /*
               * [REJECT] The payload's block hash matches the bid's block hash
               */
              final ExecutionPayload payload = envelope.getPayload();
              final Bytes32 payloadBlockHash = payload.getBlockHash();
              final Bytes32 bidBlockHash = bid.getBlockHash();
              if (!payloadBlockHash.equals(bidBlockHash)) {
                LOG.trace(
                    "Invalid payload block hash. Envelope had {} but bid had {}. Rejecting the execution payload envelope",
                    payloadBlockHash,
                    bidBlockHash);
                return Optional.of(
                    reject(
                        "Invalid payload block hash. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
                        payloadBlockHash, bidBlockHash));
              }

              /*
               * [REJECT] The envelope's execution requests root matches the bid's execution requests root
               */
              final Bytes32 executionRequestsRoot = envelope.getExecutionRequests().hashTreeRoot();
              final Bytes32 bidExecutionRequestsRoot = bid.getExecutionRequestsRoot();
              if (!executionRequestsRoot.equals(bid.getExecutionRequestsRoot())) {
                LOG.trace(
                    "Invalid execution requests. Envelope had execution requests root of {} but bid had {}. Rejecting the execution payload envelope",
                    executionRequestsRoot,
                    bidExecutionRequestsRoot);
                return Optional.of(
                    reject(
                        "Invalid execution requests. Execution Payload Envelope had execution requests root of %s but ExecutionPayload Bid had %s",
                        executionRequestsRoot, bidExecutionRequestsRoot));
              }

              return Optional.empty();
            });
  }

  private Optional<InternalValidationResult> performPreBlockValidation(
      final ExecutionPayloadEnvelope envelope) {
    /*
     * [IGNORE] The node has not seen another valid envelope for this block root from this builder
     */
    if (seenPayloads.contains(envelope.getBlockRootAndBuilderIndex())) {
      return Optional.of(ignoreExecutionPayloadAlreadySeen(envelope));
    }

    /*
     * [REJECT] The envelope's block passes validation
     */
    if (invalidBlockRoots.containsKey(envelope.getBeaconBlockRoot())) {
      LOG.trace(
          "Execution payload envelope block with root {} is invalid",
          envelope.getBeaconBlockRoot());
      return Optional.of(
          reject(
              "Execution payload envelope block with root %s is invalid",
              envelope.getBeaconBlockRoot()));
    }

    final Optional<UInt64> maybeBeaconBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(envelope.getBeaconBlockRoot());

    /*
     * [IGNORE] The envelope's block root has been seen (via gossip or non-gossip sources)
     * (MAY be queued until block is retrieved)
     */
    if (maybeBeaconBlockSlot.isEmpty()) {
      LOG.trace(
          "Block for execution Payload Envelope not yet seen (root: {}). Saving the execution payload envelope for future processing",
          envelope.getBeaconBlockRoot());
      return Optional.of(SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] The envelope is from a slot greater than or equal to the latest finalized slot
     */
    if (gossipValidationHelper.isBeforeFinalizedSlot(envelope.getSlot())) {
      LOG.trace(
          "Signed execution payload envelope slot {} is before the finalized slot. Ignoring the payload execution envelope",
          envelope.getSlot());
      return Optional.of(
          ignore(
              "Signed execution payload envelope slot %s is before the finalized slot",
              envelope.getSlot()));
    }

    /*
     * [REJECT] The block's slot matches the payload's slot number
     */
    final UInt64 beaconBlockSlot = maybeBeaconBlockSlot.get();
    if (!envelope.getSlot().equals(beaconBlockSlot)) {
      LOG.trace(
          "Execution payload envelope slot {} does not match block slot {}. Rejecting the execution payload envelope",
          envelope.getSlot(),
          beaconBlockSlot);
      return Optional.of(
          reject(
              "Execution payload envelope slot %s does not match block slot %s",
              envelope.getSlot(), beaconBlockSlot));
    }

    return Optional.empty();
  }

  private SafeFuture<InternalValidationResult> performWithStateValidation(
      final SignedExecutionPayloadEnvelope envelope) {
    return gossipValidationHelper
        .getStateAtBlockRoot(envelope.getBeaconBlockRoot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "State for block root {} is unavailable. Saving the execution payload envelope for future processing",
                    envelope.getBeaconBlockRoot());
                return SAVE_FOR_FUTURE;
              }
              /*
               * [REJECT] The envelope signature is valid
               */
              if (!isSignatureValid(envelope, maybeState.get())) {
                LOG.trace("Invalid signed execution payload envelope signature. Rejecting");
                return reject("Invalid signed execution payload envelope signature");
              }
              return ACCEPT;
            });
  }

  private boolean isSignatureValid(
      final SignedExecutionPayloadEnvelope envelope, final BeaconState postState) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionPayloadEnvelope(
            envelope.getMessage(), postState.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
        signingRoot, envelope.getMessage().getBuilderIndex(), envelope.getSignature(), postState);
  }

  private InternalValidationResult markAsSeen(
      final InternalValidationResult result, final ExecutionPayloadEnvelope envelope) {
    if (result.isAccept()) {
      if (!seenPayloads.add(envelope.getBlockRootAndBuilderIndex())) {
        return ignoreExecutionPayloadAlreadySeen(envelope);
      }
    }
    return result;
  }

  private InternalValidationResult ignoreExecutionPayloadAlreadySeen(
      final ExecutionPayloadEnvelope envelope) {
    LOG.trace(
        "Already received execution payload envelope with block root {} from builder with index {}. Ignoring the execution payload envelope",
        envelope.getBeaconBlockRoot(),
        envelope.getBuilderIndex());
    return ignore(
        "Already received execution payload envelope with block root %s from builder with index %s",
        envelope.getBeaconBlockRoot(), envelope.getBuilderIndex());
  }
}

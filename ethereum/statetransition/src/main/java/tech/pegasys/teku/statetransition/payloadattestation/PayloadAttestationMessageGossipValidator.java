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

package tech.pegasys.teku.statetransition.payloadattestation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.VALID_PAYLOAD_ATTESTATION_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.errorprone.annotations.FormatMethod;
import it.unimi.dsi.fastutil.ints.IntSet;
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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PayloadAttestationMessageGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final SigningRootUtil signingRootUtil;

  private final Set<ValidatorIndexAndSlot> seenPayloadAttestations =
      LimitedSet.createSynchronizedLRU(VALID_PAYLOAD_ATTESTATION_SET_SIZE);

  public PayloadAttestationMessageGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.invalidBlockRoots = invalidBlockRoots;
    signingRootUtil = new SigningRootUtil(spec);
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidatablePayloadAttestationMessage validatablePayloadAttestationMessage) {
    final PayloadAttestationMessage payloadAttestationMessage =
        validatablePayloadAttestationMessage.getMessage();
    final PayloadAttestationData data = validatablePayloadAttestationMessage.getData();

    /*
     * [IGNORE] The payload attestation's slot is for the current slot
     */
    if (!gossipValidationHelper.isSlotCurrent(data.getSlot())) {
      return completedFuture(
          ignorePayloadAttestation(
              payloadAttestationMessage,
              "Ignoring payload attestation with slot %s from validator with index %s because it's not from the current slot",
              data.getSlot(),
              payloadAttestationMessage.getValidatorIndex()));
    }

    /*
     * [IGNORE] This is the first valid payload attestation from this validator index
     */
    final ValidatorIndexAndSlot key =
        new ValidatorIndexAndSlot(payloadAttestationMessage.getValidatorIndex(), data.getSlot());
    if (seenPayloadAttestations.contains(key)) {
      return completedFuture(
          ignorePayloadAttestationAlreadySeenValidationResult(payloadAttestationMessage));
    }

    /*
     * [REJECT] The payload attestation's block passes validation
     */
    if (invalidBlockRoots.containsKey(data.getBeaconBlockRoot())) {
      return completedFuture(
          rejectPayloadAttestation(
              payloadAttestationMessage,
              "Payload attestations's block with root %s is invalid",
              data.getBeaconBlockRoot()));
    }

    /*
     * [IGNORE] The payload attestation's block has been seen (via gossip or non-gossip sources)
     * (MAY be queued until block is retrieved)
     */
    if (!gossipValidationHelper.isBlockAvailable(data.getBeaconBlockRoot())) {
      return completedFuture(
          savePayloadAttestationForFuture(
              payloadAttestationMessage,
              "Payload attestations's block with root %s is not available",
              data.getBeaconBlockRoot()));
    }

    /*
     * [IGNORE] The payload attestation's block is at the assigned slot
     */
    final Optional<UInt64> maybeBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(data.getBeaconBlockRoot());
    if (maybeBlockSlot.isEmpty()) {
      return completedFuture(
          savePayloadAttestationForFuture(
              payloadAttestationMessage,
              "Payload attestations's block with root %s has no known slot",
              data.getBeaconBlockRoot()));
    }
    final UInt64 blockSlot = maybeBlockSlot.get();
    if (!blockSlot.equals(data.getSlot())) {
      return completedFuture(
          ignorePayloadAttestation(
              payloadAttestationMessage,
              "Payload attestations's block with root %s is at slot %s but attestation is for slot %s",
              data.getBeaconBlockRoot(),
              blockSlot,
              data.getSlot()));
    }

    // The block has just been checked to be at data.slot, so the state to validate against is its
    // own post state. Looking it up by block root avoids the checkpoint state task queue, whose
    // lock every message of the payload committee would otherwise contend for.
    return gossipValidationHelper
        .getStateAtBlockRoot(data.getBeaconBlockRoot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return savePayloadAttestationForFuture(
                    payloadAttestationMessage,
                    "State for block root %s and slot %s is unavailable",
                    data.getBeaconBlockRoot(),
                    data.getSlot());
              }
              final BeaconState state = maybeState.get();
              final UInt64 validatorIndex = payloadAttestationMessage.getValidatorIndex();

              /*
               * [REJECT] The validator index is valid
               */
              if (validatorIndex.isGreaterThanOrEqualTo(state.getValidators().size())) {
                return rejectPayloadAttestation(
                    payloadAttestationMessage,
                    "Payload attestation's validator index %s is out of range for the %s validators in the state",
                    validatorIndex,
                    state.getValidators().size());
              }

              /*
               * [REJECT] The validator is a member of the payload timeliness committee
               */
              final IntSet ptcPositions =
                  validatablePayloadAttestationMessage.calculatePtcPositions(spec, state);
              if (ptcPositions.isEmpty()) {
                return rejectPayloadAttestation(
                    payloadAttestationMessage,
                    "Payload attestation's validator index %s is not in the payload committee",
                    validatorIndex);
              }

              /*
               * [REJECT] The signature is valid
               */
              if (!isSignatureValid(payloadAttestationMessage, state)) {
                return rejectPayloadAttestation(
                    payloadAttestationMessage, "Invalid payload attestation signature");
              }

              if (!seenPayloadAttestations.add(key)) {
                return ignorePayloadAttestationAlreadySeenValidationResult(
                    payloadAttestationMessage);
              } else {
                return acceptPayloadAttestation(payloadAttestationMessage);
              }
            });
  }

  private InternalValidationResult ignorePayloadAttestationAlreadySeenValidationResult(
      final PayloadAttestationMessage payloadAttestationMessage) {
    return ignorePayloadAttestation(
        payloadAttestationMessage,
        "Payload attestation for slot %s and validator index %s already seen",
        payloadAttestationMessage.getData().getSlot(),
        payloadAttestationMessage.getValidatorIndex());
  }

  private InternalValidationResult acceptPayloadAttestation(
      final PayloadAttestationMessage payloadAttestationMessage) {
    LOG.trace(
        "PayloadAttestation Gossip Validation Result: ACCEPT, context: {}",
        formatPayloadAttestationContext(payloadAttestationMessage));
    return ACCEPT;
  }

  @FormatMethod
  private InternalValidationResult rejectPayloadAttestation(
      final PayloadAttestationMessage payloadAttestationMessage,
      final String descriptionTemplate,
      final Object... args) {
    final String message = String.format(descriptionTemplate, args);
    LOG.trace(
        "PayloadAttestation Gossip Validation Result: REJECT, context: {}, reason: {}",
        formatPayloadAttestationContext(payloadAttestationMessage),
        message);
    return reject("%s", message);
  }

  @FormatMethod
  private InternalValidationResult ignorePayloadAttestation(
      final PayloadAttestationMessage payloadAttestationMessage,
      final String descriptionTemplate,
      final Object... args) {
    final String message = String.format(descriptionTemplate, args);
    LOG.trace(
        "PayloadAttestation Gossip Validation Result: IGNORE, context: {}, reason: {}",
        formatPayloadAttestationContext(payloadAttestationMessage),
        message);
    return ignore("%s", message);
  }

  @FormatMethod
  private InternalValidationResult savePayloadAttestationForFuture(
      final PayloadAttestationMessage payloadAttestationMessage,
      final String descriptionTemplate,
      final Object... args) {
    final String message = String.format(descriptionTemplate, args);
    LOG.trace(
        "PayloadAttestation Gossip Validation Result: SAVE_FOR_FUTURE, context: {}, reason: {}",
        formatPayloadAttestationContext(payloadAttestationMessage),
        message);
    return SAVE_FOR_FUTURE;
  }

  private String formatPayloadAttestationContext(
      final PayloadAttestationMessage payloadAttestationMessage) {
    return String.format(
        "validator index %s, slot %s, block root %s",
        payloadAttestationMessage.getValidatorIndex(),
        payloadAttestationMessage.getData().getSlot(),
        payloadAttestationMessage.getData().getBeaconBlockRoot());
  }

  private boolean isSignatureValid(
      final PayloadAttestationMessage payloadAttestationMessage, final BeaconState state) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignPayloadAttestationData(
            payloadAttestationMessage.getData(), state.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot,
        payloadAttestationMessage.getValidatorIndex(),
        payloadAttestationMessage.getSignature(),
        state);
  }

  record ValidatorIndexAndSlot(UInt64 validatorIndex, UInt64 slot) {}
}

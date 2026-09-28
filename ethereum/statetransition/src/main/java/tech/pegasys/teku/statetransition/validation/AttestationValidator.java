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

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.StateTooOldException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil.SlotInclusionGossipValidationResult;
import tech.pegasys.teku.spec.logic.common.util.AttestationValidationResult;
import tech.pegasys.teku.statetransition.util.SeenAttestingValidatorsCache;

public class AttestationValidator {

  private final Spec spec;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final GossipValidationHelper gossipValidationHelper;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final Set<Bytes32> blockRootsWithInvalidExecutionPayload;

  // Current/previous-epoch attestation acceptance window used across forks, plus margin for clock
  // disparity
  private static final int MAX_CACHED_ATTESTATION_EPOCHS = 3;

  /**
   * Tracks, per target epoch, which validators already had a valid attestation accepted from the
   * unaggregated attestation subnets, so that a second attestation from the same validator for the
   * same target epoch is ignored rather than propagated.
   */
  private final SeenAttestingValidatorsCache seenAttestingValidators =
      new SeenAttestingValidatorsCache(MAX_CACHED_ATTESTATION_EPOCHS);

  @VisibleForTesting
  AttestationValidator(
      final Spec spec,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final GossipValidationHelper gossipValidationHelper,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots) {
    this(spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots, Set.of());
  }

  public AttestationValidator(
      final Spec spec,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final GossipValidationHelper gossipValidationHelper,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final Set<Bytes32> blockRootsWithInvalidExecutionPayload) {
    this.spec = spec;
    this.signatureVerifier = signatureVerifier;
    this.gossipValidationHelper = gossipValidationHelper;
    this.invalidBlockRoots = invalidBlockRoots;
    this.blockRootsWithInvalidExecutionPayload = blockRootsWithInvalidExecutionPayload;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidatableAttestation validatableAttestation) {
    if (validatableAttestation.isAcceptedAsGossip()) {
      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
    }
    Attestation attestation = validatableAttestation.getAttestation();
    final InternalValidationResult internalValidationResult = singleAttestationChecks(attestation);
    if (internalValidationResult.code() != ACCEPT) {
      return completedFuture(internalValidationResult);
    }

    return validateSingleOrAggregateAttestation(validatableAttestation);
  }

  /** Runs checks shared by unaggregated and aggregate attestations, excluding envelope checks. */
  public SafeFuture<InternalValidationResult> validateSingleOrAggregateAttestation(
      final ValidatableAttestation validatableAttestation) {
    if (validatableAttestation.isAcceptedAsGossip()) {
      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
    }
    return singleOrAggregateAttestationChecks(
            signatureVerifier,
            validatableAttestation,
            validatableAttestation.getReceivedSubnetId(),
            true,
            true)
        .thenApply(InternalValidationResultWithState::getResult)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                validatableAttestation.setAcceptedAsGossip();
              }
            });
  }

  private InternalValidationResult singleAttestationChecks(final Attestation attestation) {
    // if it is a SingleAttestation type we are guaranteed to be a valid single attestation
    if (attestation.isSingleAttestation()) {
      return InternalValidationResult.ACCEPT;
    }

    // The attestation is unaggregated -- that is, it has exactly one participating validator
    // (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
    final int bitCount = attestation.getAggregationBits().getBitCount();
    if (bitCount != 1) {
      return InternalValidationResult.reject("Attestation has %s bits set instead of 1", bitCount);
    }
    return InternalValidationResult.ACCEPT;
  }

  SafeFuture<InternalValidationResultWithState> singleOrAggregateAttestationChecks(
      final AsyncBLSSignatureVerifier signatureVerifier,
      final ValidatableAttestation validatableAttestation,
      final OptionalInt receivedOnSubnetId) {
    // Aggregate validation path: preserve legacy behaviour where a future-slot attestation is
    // deferred without running signature verification here. The aggregate wrapper uses a batch
    // verifier that is flushed separately, so verifying (and optimistically caching) the signature
    // here would leave an unverified signature cached if the deferral short-circuits the flush.
    // The per-validator duplicate check is also skipped: aggregates are deduplicated by aggregator
    // index and epoch by AggregateAttestationValidator, which is a distinct spec rule.
    return singleOrAggregateAttestationChecks(
        signatureVerifier, validatableAttestation, receivedOnSubnetId, false, false);
  }

  SafeFuture<InternalValidationResultWithState> singleOrAggregateAttestationChecks(
      final AsyncBLSSignatureVerifier signatureVerifier,
      final ValidatableAttestation validatableAttestation,
      final OptionalInt receivedOnSubnetId,
      final boolean verifyFutureSlotAttestationSignature,
      final boolean checkForDuplicateUnaggregatedAttestation) {

    Attestation attestation = validatableAttestation.getAttestation();
    final AttestationData data = attestation.getData();
    final UInt64 targetEpoch = data.getTarget().getEpoch();

    // [IGNORE] No other valid attestation seen for this target epoch and validator.
    // For a SingleAttestation the attester index is carried on the message itself (i.e.
    // attacker-controlled and not yet validated), so per the Electra/Gloas spec this check is the
    // very first thing validated, ahead of every other check. A value that doesn't fit in an int
    // can never be a genuine validator index, so it's excluded here rather than converted -- it
    // will be rejected by later checks on its own merits. For the legacy bitlist format the
    // attester index can only be resolved via the committee, which requires state, so that format
    // is checked further down (see below), matching the phase0/deneb spec ordering.
    final OptionalInt earlyDuplicateAttesterIndex =
        checkForDuplicateUnaggregatedAttestation && attestation.isSingleAttestation()
            ? toSafeIntValidatorIndex(attestation.getValidatorIndexRequired())
            : OptionalInt.empty();
    if (earlyDuplicateAttesterIndex.isPresent()
        && seenAttestingValidators.isAlreadySeen(
            targetEpoch, earlyDuplicateAttesterIndex.getAsInt())) {
      return completedFuture(
          InternalValidationResultWithState.ignore(
              "Already seen an attestation for this target epoch and validator"));
    }

    // [REJECT] 4 - The attestation's epoch matches its target
    if (!data.getTarget().getEpoch().equals(spec.computeEpochAtSlot(data.getSlot()))) {
      return completedFuture(
          InternalValidationResultWithState.reject(
              "Attestation slot %s is not from target epoch %s",
              data.getSlot(), data.getTarget().getEpoch()));
    }

    if (attestation.requiresCommitteeBits()) {
      // [REJECT] len(committee_indices) == 1, where committee_indices =
      // get_committee_indices(attestation)
      if (attestation.getCommitteeBitsRequired().getBitCount() != 1) {
        return SafeFuture.completedFuture(
            InternalValidationResultWithState.reject(
                "Rejecting attestation because committee bits count is not 1"));
      }
    }

    final AttestationUtil attestationUtil = spec.atSlot(data.getSlot()).getAttestationUtil();
    final AttestationValidationResult attestationIndexValidationResult =
        attestationUtil.validateCommitteeIndexValue(attestation.getData().getIndex());
    if (!attestationIndexValidationResult.isValid()) {
      return SafeFuture.completedFuture(
          InternalValidationResultWithState.reject(
              attestationIndexValidationResult.getReason().orElse("Invalid attestation data")));
    }

    final InternalValidationResult payloadStatusValidationResult =
        gossipValidationHelper.validatePayloadStatus(
            attestationUtil, attestation.getData(), blockRootsWithInvalidExecutionPayload);
    if (payloadStatusValidationResult.isReject()) {
      return SafeFuture.completedFuture(
          InternalValidationResultWithState.reject(
              payloadStatusValidationResult.getDescription().orElse("Invalid payload status")));
    }
    if (payloadStatusValidationResult.isSaveForFuture()) {
      return completedFuture(InternalValidationResultWithState.saveForFuture());
    }

    final Optional<SlotInclusionGossipValidationResult> slotInclusionGossipValidationResult =
        attestationUtil.performSlotInclusionGossipValidation(
            attestation,
            gossipValidationHelper.getGenesisTime(),
            gossipValidationHelper.getCurrentTimeMillis());

    if (slotInclusionGossipValidationResult.isPresent()) {
      if (slotInclusionGossipValidationResult.get() == SlotInclusionGossipValidationResult.IGNORE) {
        return completedFuture(InternalValidationResultWithState.ignore());
      }
      // SAVE_FOR_FUTURE: a future-slot attestation is deferred rather than accepted for gossip.
      // When requested, we still verify its signature so that a bogus signature is rejected and its
      // sender penalised, rather than being deferred with a penalty-free Ignore verdict and later
      // forcing a BLS verification in the fork choice path for free. Only an invalid signature
      // rejects here; every other gossip check simply defers (fork choice re-validates the rest
      // once the attestation's slot arrives). A verified signature is cached so fork choice does
      // not re-verify it.
      if (!verifyFutureSlotAttestationSignature) {
        return completedFuture(InternalValidationResultWithState.saveForFuture());
      }
      return checkFutureSlotAttestationSignature(validatableAttestation, signatureVerifier);
    }

    // [REJECT] The block being voted for (attestation.data.beacon_block_root) passes validation.
    // If we have already seen and rejected the block (or one of its ancestors), reject the
    // attestation rather than saving it for future processing.
    if (invalidBlockRoots.containsKey(data.getBeaconBlockRoot())) {
      return completedFuture(
          InternalValidationResultWithState.reject(
              "Attestation votes for a block that failed validation: %s",
              data.getBeaconBlockRoot()));
    }

    // The block being voted for must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    if (!gossipValidationHelper.isBlockAvailable(data.getBeaconBlockRoot())) {
      return completedFuture(InternalValidationResultWithState.saveForFuture());
    }

    return gossipValidationHelper
        .getStateForAttestationValidation(attestation.getData())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                // We know the block is imported but now don't have a state to validate against
                // Must have got pruned between checks
                return completedFuture(InternalValidationResultWithState.ignore());
              }
              final BeaconState state = maybeState.get();

              // The committee index is within the expected range
              if (attestation
                  .getFirstCommitteeIndex()
                  .isGreaterThanOrEqualTo(
                      spec.getCommitteeCountPerSlot(state, data.getTarget().getEpoch()))) {
                return completedFuture(
                    InternalValidationResultWithState.reject(
                        "Committee index %s is out of range", data.getIndex()));
              }

              // The attestation's committee index (attestation.data.index) is for the correct
              // subnet.
              if (receivedOnSubnetId.isPresent()
                  && spec.computeSubnetForAttestation(state, attestation)
                      != receivedOnSubnetId.getAsInt()) {
                return completedFuture(
                    InternalValidationResultWithState.reject(
                        "Attestation received on incorrect subnet (%s) for specified committee index (%s)",
                        attestation.getFirstCommitteeIndex(), receivedOnSubnetId.getAsInt()));
              }

              if (!attestation.isSingleAttestation()) {
                // [REJECT] The number of aggregation bits matches the committee size
                try {
                  final IntList committee =
                      spec.getBeaconCommittee(
                          state, data.getSlot(), attestation.getFirstCommitteeIndex());
                  if (committee.size() != attestation.getAggregationBits().size()) {
                    return completedFuture(
                        InternalValidationResultWithState.reject(
                            "Aggregation bit size %s is greater than committee size %s",
                            attestation.getAggregationBits().size(), committee.size()));
                  }
                } catch (final StateTooOldException e) {
                  return completedFuture(InternalValidationResultWithState.ignore(e.getMessage()));
                }
              }

              // [IGNORE] No other valid attestation seen for this target epoch and validator.
              // SingleAttestation already had its index checked above, before this state-dependent
              // block even ran; the legacy bitlist format resolves its attester index via the
              // committee here, now that state is available -- a real committee member index, so
              // it's always safe to use directly -- and is checked for the first time.
              final OptionalInt attesterIndex =
                  earlyDuplicateAttesterIndex.isPresent()
                      ? earlyDuplicateAttesterIndex
                      : checkForDuplicateUnaggregatedAttestation
                          ? getAttesterIndex(state, attestation)
                          : OptionalInt.empty();
              if (earlyDuplicateAttesterIndex.isEmpty()
                  && attesterIndex.isPresent()
                  && seenAttestingValidators.isAlreadySeen(targetEpoch, attesterIndex.getAsInt())) {
                return completedFuture(
                    InternalValidationResultWithState.ignore(
                        "Already seen an attestation for this target epoch and validator"));
              }

              return spec.isValidIndexedAttestation(
                      state, validatableAttestation, signatureVerifier)
                  .thenApply(
                      signatureResult -> {
                        if (!signatureResult.isSuccessful()) {
                          return InternalValidationResultWithState.reject(
                              "Attestation is not a valid indexed attestation: %s",
                              signatureResult.getInvalidReason());
                        }

                        // The attestation's target block is an ancestor of the block named in the
                        // LMD vote
                        if (!spec.getAncestor(
                                gossipValidationHelper.getForkChoiceStrategy(),
                                data.getBeaconBlockRoot(),
                                spec.computeStartSlotAtEpoch(data.getTarget().getEpoch()))
                            .map(
                                ancestorOfLMDVote ->
                                    ancestorOfLMDVote.equals(data.getTarget().getRoot()))
                            .orElse(false)) {
                          return InternalValidationResultWithState.reject(
                              "Attestation LMD vote block does not descend from target block");
                        }

                        // The current finalized_checkpoint is an ancestor of the block defined by
                        // aggregate.data.beacon_block_root
                        if (!gossipValidationHelper
                            .currentFinalizedCheckpointIsAncestorOfAttestationBlock(
                                data.getBeaconBlockRoot())) {
                          return InternalValidationResultWithState.ignore(
                              "Finalized checkpoint is not an ancestor of block");
                        }

                        // Only mark as seen once fully validated -- signature verification above
                        // guarantees attesterIndex is a real, bounded validator index by this
                        // point -- so that a concurrent duplicate losing the race is ignored rather
                        // than also being accepted.
                        if (attesterIndex.isPresent()
                            && !seenAttestingValidators.addIfAbsent(
                                targetEpoch, attesterIndex.getAsInt())) {
                          return InternalValidationResultWithState.ignore(
                              "Already seen an attestation for this target epoch and validator");
                        }

                        // Save committee shuffling seed since the state is available and
                        // attestation is valid
                        validatableAttestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
                        return InternalValidationResultWithState.accept(state);
                      });
            });
  }

  /*
   * Returns the validator index of the single participating validator of a legacy bitlist
   * unaggregated attestation, i.e. committee[set_bit_indices[0]] -- the first (and only, since
   * this is only reached for a genuinely unaggregated attestation) entry returned by
   * get_attesting_indices. SingleAttestation carries its attester index directly and never
   * reaches this method (see the earlyDuplicateAttesterIndex computation above). Empty when the
   * attestation has no participants.
   */
  private OptionalInt getAttesterIndex(final BeaconState state, final Attestation attestation) {
    return spec.getAttestingIndices(state, attestation).stream()
        .mapToInt(UInt64::intValue)
        .findFirst();
  }

  /**
   * A real validator index always fits in an int (bounded by the actual validator registry size,
   * nowhere near {@link Integer#MAX_VALUE} today or for the foreseeable future), so a raw attester
   * index that doesn't fit can never match a genuine committee member. Returns empty rather than
   * throwing, so an attacker-supplied out-of-range index in an unvalidated SingleAttestation can't
   * be used to force an exception -- the attestation is simply excluded from the early duplicate
   * check and rejected by later checks on its own merits.
   */
  private static OptionalInt toSafeIntValidatorIndex(final UInt64 validatorIndex) {
    return validatorIndex.isLessThanOrEqualTo(Integer.MAX_VALUE)
        ? OptionalInt.of(validatorIndex.intValue())
        : OptionalInt.empty();
  }

  /**
   * Verifies the signature of a future-slot attestation and always defers it (SAVE_FOR_FUTURE),
   * rejecting only when the signature is invalid. The remaining gossip checks are intentionally
   * skipped here: they either only apply to attestations eligible for immediate propagation, or are
   * re-validated by fork choice when the attestation's slot arrives. Rejecting solely on an invalid
   * signature keeps the peer penalty limited to provably-invalid messages, matching how other
   * clients score future-slot attestations, while still avoiding a penalty-free BLS verification in
   * the fork choice path.
   */
  private SafeFuture<InternalValidationResultWithState> checkFutureSlotAttestationSignature(
      final ValidatableAttestation validatableAttestation,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final AttestationData data = validatableAttestation.getAttestation().getData();
    // The signature can't be verified without the block being voted for and its state; defer.
    if (!gossipValidationHelper.isBlockAvailable(data.getBeaconBlockRoot())) {
      return completedFuture(InternalValidationResultWithState.saveForFuture());
    }
    return gossipValidationHelper
        .getStateForAttestationValidation(data)
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return completedFuture(InternalValidationResultWithState.saveForFuture());
              }
              return spec.isValidIndexedAttestation(
                      maybeState.get(), validatableAttestation, signatureVerifier)
                  .thenApply(
                      signatureResult ->
                          signatureResult.isSuccessful()
                              ? InternalValidationResultWithState.saveForFuture()
                              : InternalValidationResultWithState.reject(
                                  "Attestation is not a valid indexed attestation: %s",
                                  signatureResult.getInvalidReason()));
            });
  }
}

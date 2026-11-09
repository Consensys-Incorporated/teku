/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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
import static tech.pegasys.teku.spec.config.Constants.VALID_AGGREGATE_SET_SIZE;
import static tech.pegasys.teku.spec.config.Constants.VALID_ATTESTATION_DATA_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.AsyncBatchBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.util.SeenAggregatesCache;

public class AggregateAttestationValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<AggregatorIndexAndEpoch> receivedAggregatorIndexAndEpochs =
      LimitedSet.createSynchronizedLRU(VALID_AGGREGATE_SET_SIZE);
  private final SeenAggregatesCache<DataHashAndCommitteeIndex> seenAggregationBits =
      new SeenAggregatesCache<>(VALID_ATTESTATION_DATA_SET_SIZE);
  private final AttestationValidator attestationValidator;
  private final Spec spec;
  private final AsyncBLSSignatureVerifier signatureVerifier;

  public AggregateAttestationValidator(
      final Spec spec,
      final AttestationValidator attestationValidator,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    this.attestationValidator = attestationValidator;
    this.spec = spec;
    this.signatureVerifier = signatureVerifier;
  }

  public void addSeenAggregate(final ValidatableAttestation attestation) {
    seenAggregationBits.add(
        DataHashAndCommitteeIndex.from(attestation.getAttestation()),
        attestation.getAttestation().getAggregationBits());
  }

  public SafeFuture<InternalValidationResult> validate(final ValidatableAttestation attestation) {
    final SignedAggregateAndProof signedAggregate = attestation.getSignedAggregateAndProof();
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Attestation aggregate = aggregateAndProof.getAggregate();
    final UInt64 aggregateSlot = aggregate.getData().getSlot();
    final SpecVersion specVersion = spec.atSlot(aggregateSlot);

    if (aggregate.requiresCommitteeBits()) {
      // [REJECT] len(committee_indices) == 1, where committee_indices =
      // get_committee_indices(aggregate)
      if (aggregate.getCommitteeBitsRequired().getBitCount() != 1) {
        return completedFuture(
            reject("Rejecting attestation because committee bits count is not 1"));
      }
    }

    final AggregatorIndexAndEpoch aggregatorIndexAndEpoch =
        new AggregatorIndexAndEpoch(
            aggregateAndProof.getIndex(), spec.computeEpochAtSlot(aggregateSlot));
    if (receivedAggregatorIndexAndEpochs.contains(aggregatorIndexAndEpoch)) {
      return completedFuture(ignore("Ignoring duplicate aggregate"));
    }

    final SszBitlist aggregationBits = attestation.getAttestation().getAggregationBits();
    if (seenAggregationBits.isAlreadySeen(
        DataHashAndCommitteeIndex.from(attestation.getAttestation()), aggregationBits)) {
      return completedFuture(ignore("Ignoring duplicate aggregate based on aggregation bits"));
    }

    if (attestation.isAcceptedAsGossip()) {
      return SafeFuture.completedFuture(
          checkAndAddToSeenAggregates(
              attestation, aggregatorIndexAndEpoch, InternalValidationResult.ACCEPT));
    }

    return verifyAggregate(
            attestation,
            signedAggregate,
            aggregateAndProof,
            aggregate,
            aggregateSlot,
            specVersion,
            aggregatorIndexAndEpoch)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                attestation.setAcceptedAsGossip();
              }
            });
  }

  private SafeFuture<InternalValidationResult> verifyAggregate(
      final ValidatableAttestation attestation,
      final SignedAggregateAndProof signedAggregate,
      final AggregateAndProof aggregateAndProof,
      final Attestation aggregate,
      final UInt64 aggregateSlot,
      final SpecVersion specVersion,
      final AggregatorIndexAndEpoch aggregatorIndexAndEpoch) {
    final AsyncBatchBLSSignatureVerifier signatureVerifier =
        new AsyncBatchBLSSignatureVerifier(this.signatureVerifier);
    return singleOrAggregateAttestationChecks(signatureVerifier, attestation, OptionalInt.empty())
        .thenCompose(
            resultWithState -> {
              if (resultWithState.getResult().isNotProcessable()) {
                LOG.trace("Rejecting aggregate because attestation failed validation");
                return completedFuture(resultWithState.getResult());
              }

              final Optional<BeaconState> maybeState = resultWithState.getState();
              if (maybeState.isEmpty()) {
                // State isn't yet available. We've already handled ignore and reject conditions
                // as not processable above so need to save for when the state is available. No
                // signature has been queued in the batch at this point, so the attestation isn't
                // marked as verified and fork choice verifies it once its block and state are
                // available.
                LOG.trace("Saving aggregate for future because state is not yet available");
                return SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
              }

              final BeaconState state = maybeState.get();

              // [REJECT] The aggregate attestation has participants
              final List<UInt64> attestingIndices = spec.getAttestingIndices(state, aggregate);
              if (attestingIndices.isEmpty()) {
                return SafeFuture.completedFuture(
                    reject(
                        "Rejecting aggregate attestation because it does not have participants"));
              }

              final Optional<BLSPublicKey> aggregatorPublicKey =
                  spec.getValidatorPubKey(state, aggregateAndProof.getIndex());
              if (aggregatorPublicKey.isEmpty()) {
                return SafeFuture.completedFuture(reject("Rejecting aggregate with invalid index"));
              }

              if (!isSelectionProofValid(
                  signatureVerifier,
                  aggregateSlot,
                  state,
                  aggregatorPublicKey.get(),
                  aggregateAndProof.getSelectionProof())) {
                return SafeFuture.completedFuture(
                    reject("Rejecting aggregate with incorrect selection proof"));
              }

              final IntList beaconCommittee =
                  spec.getBeaconCommittee(state, aggregateSlot, aggregate.getFirstCommitteeIndex());

              final int aggregatorModulo =
                  specVersion.getValidatorsUtil().getAggregatorModulo(beaconCommittee.size());
              if (!specVersion
                  .getValidatorsUtil()
                  .isAggregator(aggregateAndProof.getSelectionProof(), aggregatorModulo)) {
                return SafeFuture.completedFuture(
                    reject(
                        "Rejecting aggregate because selection proof does not select validator as aggregator"));
              }

              /*
               * [REJECT] The aggregator is a member of the committee
               */
              if (!beaconCommittee.contains(aggregateAndProof.getIndex().intValue())) {
                return SafeFuture.completedFuture(
                    reject(
                        "Rejecting aggregate because attester %s is not in committee. Should have been one of %s",
                        aggregateAndProof.getIndex(), beaconCommittee));
              }

              if (!validateSignature(
                  signatureVerifier,
                  signedAggregate,
                  aggregateSlot,
                  state,
                  aggregatorPublicKey.get())) {
                return SafeFuture.completedFuture(
                    reject("Rejecting aggregate with invalid signature"));
              }

              // Capture only the result, not the state: the batch can wait in the signature
              // verification queue for a while and the state must not be kept reachable for that
              final InternalValidationResult attestationResult = resultWithState.getResult();
              return signatureVerifier
                  .batchVerify()
                  .thenApply(
                      signatureValid -> {
                        if (!signatureValid) {
                          return reject("Rejecting aggregate with invalid batch signature");
                        }

                        // SAVE_FOR_FUTURE with a state means the attestation signature was queued
                        // in the batch, and the attestation already marked as a valid indexed
                        // attestation so that fork choice won't verify it again. Such an aggregate
                        // can only be deferred once the batch has been verified. As before, a
                        // deferred aggregate isn't marked as seen, since only accepted ones are. It
                        // isn't propagated either: gossip ignores it, and identical copies received
                        // later are dropped by the gossip seen cache before reaching this
                        // validator.
                        if (attestationResult.isSaveForFuture()) {
                          return attestationResult;
                        }

                        return checkAndAddToSeenAggregates(
                            attestation, aggregatorIndexAndEpoch, attestationResult);
                      });
            });
  }

  private InternalValidationResult checkAndAddToSeenAggregates(
      final ValidatableAttestation attestation,
      final AggregatorIndexAndEpoch aggregatorIndexAndEpoch,
      final InternalValidationResult result) {
    if (!receivedAggregatorIndexAndEpochs.add(aggregatorIndexAndEpoch)) {
      return ignore("Ignoring duplicate aggregate");
    }
    if (!seenAggregationBits.add(
        DataHashAndCommitteeIndex.from(attestation.getAttestation()),
        attestation.getAttestation().getAggregationBits())) {
      return ignore("Ignoring duplicate aggregate based on aggregation bits");
    }
    return result;
  }

  private boolean validateSignature(
      final AsyncBatchBLSSignatureVerifier signatureVerifier,
      final SignedAggregateAndProof signedAggregate,
      final UInt64 aggregateSlot,
      final BeaconState state,
      final BLSPublicKey aggregatorPublicKey) {
    final UInt64 aggregateEpoch = spec.computeEpochAtSlot(aggregateSlot);
    final Fork aggregateFork = spec.fork(aggregateEpoch);

    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Bytes32 domain =
        spec.getDomain(
            Domain.AGGREGATE_AND_PROOF,
            aggregateEpoch,
            aggregateFork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(aggregateAndProof, domain);
    return signatureVerifier.verify(
        aggregatorPublicKey, signingRoot, signedAggregate.getSignature());
  }

  private boolean isSelectionProofValid(
      final BLSSignatureVerifier signatureVerifier,
      final UInt64 aggregateSlot,
      final BeaconState state,
      final BLSPublicKey aggregatorPublicKey,
      final BLSSignature selectionProof) {

    final UInt64 aggregateEpoch = spec.computeEpochAtSlot(aggregateSlot);
    final Fork aggregateFork = spec.fork(aggregateEpoch);

    final Bytes32 domain =
        spec.getDomain(
            Domain.SELECTION_PROOF,
            aggregateEpoch,
            aggregateFork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(aggregateSlot, domain);
    return signatureVerifier.verify(aggregatorPublicKey, signingRoot, selectionProof);
  }

  SafeFuture<InternalValidationResultWithState> singleOrAggregateAttestationChecks(
      final AsyncBatchBLSSignatureVerifier signatureVerifier,
      final ValidatableAttestation validatableAttestation,
      final OptionalInt receivedOnSubnetId) {
    return attestationValidator.singleOrAggregateAttestationChecks(
        AsyncBLSSignatureVerifier.wrap(signatureVerifier),
        validatableAttestation,
        receivedOnSubnetId);
  }

  record DataHashAndCommitteeIndex(Bytes32 hash, UInt64 committeeIndex) {
    static DataHashAndCommitteeIndex from(final Attestation attestation) {
      return new DataHashAndCommitteeIndex(
          attestation.getData().hashTreeRoot(), attestation.getFirstCommitteeIndex());
    }
  }

  private record AggregatorIndexAndEpoch(UInt64 aggregatorIndex, UInt64 epoch) {}
}

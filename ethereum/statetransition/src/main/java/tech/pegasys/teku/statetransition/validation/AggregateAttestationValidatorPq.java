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
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.OptionalInt;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.util.AttestationUtilPq;
import tech.pegasys.teku.statetransition.util.SeenAggregatesCache;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator.DataHashAndCommitteeIndex;

/**
 * Gossip validation of PQ aggregates (validate_beacon_aggregate_and_proof_gossip). A PQ aggregate
 * has no signature and no selection proof: its aggregator must be in the aggregation committee,
 * signs the AggregateAndProof with XMSS, and proves the attesters' signatures with the aggregate's
 * STARK proof.
 *
 * <p>The PQ checks run against the attestation's state first. The aggregate is then marked as a
 * verified indexed attestation and goes through {@link AttestationValidator}'s checks shared with
 * single attestations (epoch, target and finalized ancestry, payload status), which don't verify it
 * again.
 */
public class AggregateAttestationValidatorPq {

  // Checks that only run once the aggregate is verified: the signature is already known valid
  private static final AsyncBLSSignatureVerifier ALREADY_VERIFIED =
      AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP);

  private final Set<AggregatorIndexAndEpoch> receivedAggregatorIndexAndEpochs =
      LimitedSet.createSynchronizedLRU(VALID_AGGREGATE_SET_SIZE);
  private final SeenAggregatesCache<DataHashAndCommitteeIndex> seenAggregationBits =
      new SeenAggregatesCache<>(VALID_ATTESTATION_DATA_SET_SIZE);
  private final Spec spec;
  private final AttestationValidator attestationValidator;
  private final GossipValidationHelper gossipValidationHelper;

  public AggregateAttestationValidatorPq(
      final Spec spec,
      final AttestationValidator attestationValidator,
      final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.attestationValidator = attestationValidator;
    this.gossipValidationHelper = gossipValidationHelper;
  }

  /**
   * Validates {@code signedAggregate}. On acceptance, {@code attestation} (built from the
   * aggregate) holds the verified indexed attestation and the aggregate's proof.
   */
  public SafeFuture<InternalValidationResult> validate(
      final SignedAggregateAndProofPq signedAggregate, final ValidatableAttestation attestation) {
    final AggregateAndProofPq aggregateAndProof = signedAggregate.getMessage();
    final AttestationPq aggregate = aggregateAndProof.getAggregate();
    final AttestationData data = aggregate.getData();

    // [REJECT] Exactly one committee is specified by the committee bits
    if (aggregate.getCommitteeBitsRequired().getBitCount() != 1) {
      return completedFuture(reject("Aggregate committee bits must specify exactly one committee"));
    }

    // [IGNORE] A valid aggregate with a superset of aggregation bits has not already been seen
    final DataHashAndCommitteeIndex dataKey = DataHashAndCommitteeIndex.from(aggregate);
    final SszBitlist aggregationBits = aggregate.getAggregationBits();
    if (seenAggregationBits.isAlreadySeen(dataKey, aggregationBits)) {
      return completedFuture(ignore("Ignoring duplicate aggregate based on aggregation bits"));
    }
    // [IGNORE] This is the first valid aggregate for this epoch and aggregator
    final AggregatorIndexAndEpoch aggregatorIndexAndEpoch =
        new AggregatorIndexAndEpoch(
            aggregateAndProof.getAggregatorIndex(), data.getTarget().getEpoch());
    if (receivedAggregatorIndexAndEpochs.contains(aggregatorIndexAndEpoch)) {
      return completedFuture(ignore("Ignoring duplicate aggregate"));
    }

    return gossipValidationHelper
        .getStateForAttestationValidation(data)
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return completedFuture(ignore("No state to validate the aggregate against"));
              }
              final InternalValidationResult pqResult =
                  validateAgainstState(signedAggregate, attestation, maybeState.get());
              if (!pqResult.isAccept()) {
                return completedFuture(pqResult);
              }
              return attestationValidator
                  .singleOrAggregateAttestationChecks(
                      ALREADY_VERIFIED, attestation, OptionalInt.empty())
                  .thenApply(InternalValidationResultWithState::getResult);
            })
        .thenApply(
            result -> {
              if (!result.isAccept()) {
                return result;
              }
              // Mark as seen only once fully validated, so a concurrent duplicate is ignored
              if (!receivedAggregatorIndexAndEpochs.add(aggregatorIndexAndEpoch)) {
                return ignore("Ignoring duplicate aggregate");
              }
              if (!seenAggregationBits.add(dataKey, aggregationBits)) {
                return ignore("Ignoring duplicate aggregate based on aggregation bits");
              }
              attestation.setAcceptedAsGossip();
              return result;
            });
  }

  private InternalValidationResult validateAgainstState(
      final SignedAggregateAndProofPq signedAggregate,
      final ValidatableAttestation attestation,
      final BeaconState state) {
    final AggregateAndProofPq aggregateAndProof = signedAggregate.getMessage();
    final AttestationPq aggregate = aggregateAndProof.getAggregate();
    final AttestationData data = aggregate.getData();
    final UInt64 committeeIndex = aggregate.getFirstCommitteeIndex();
    final AttestationUtilPq attestationUtil =
        (AttestationUtilPq) spec.atSlot(data.getSlot()).getAttestationUtil();

    // [REJECT] The aggregate attestation's epoch matches its target. The state is chosen by the
    // target epoch, so the aggregation committee can only be read once they match.
    if (!data.getTarget().getEpoch().equals(spec.computeEpochAtSlot(data.getSlot()))) {
      return reject("Attestation epoch does not match target epoch");
    }
    // [REJECT] The committee index is within the expected range
    if (committeeIndex.isGreaterThanOrEqualTo(
        spec.getCommitteeCountPerSlot(state, data.getTarget().getEpoch()))) {
      return reject("Committee index %s is out of range", committeeIndex);
    }
    // [REJECT] The number of aggregation bits matches the committee size
    final IntList committee = spec.getBeaconCommittee(state, data.getSlot(), committeeIndex);
    if (aggregate.getAggregationBits().size() != committee.size()) {
      return reject(
          "Aggregation bits length %s does not match committee size %s",
          aggregate.getAggregationBits().size(), committee.size());
    }
    // [REJECT] The aggregate attestation has participants
    if (aggregate.getAggregationBits().getBitCount() < 1) {
      return reject("Aggregate has no participants");
    }
    // [REJECT] The validator is one of the committee's aggregators
    if (!attestationUtil.isAggregator(
        state, data.getSlot(), committeeIndex, aggregateAndProof.getAggregatorIndex())) {
      return reject("Validator is not selected as aggregator");
    }
    // [REJECT] The aggregator signature is valid
    final Bytes32 domain =
        spec.getDomain(
            Domain.AGGREGATE_AND_PROOF,
            data.getTarget().getEpoch(),
            state.getFork(),
            state.getGenesisValidatorsRoot());
    if (!gossipValidationHelper.isXmssSignatureValidWithRespectToValidatorIndex(
        spec.atSlot(data.getSlot()).miscHelpers().computeSigningRoot(aggregateAndProof, domain),
        aggregateAndProof.getAggregatorIndex(),
        gossipValidationHelper.computeXmssLeafIndex(
            data.getSlot(), LEAF_OFFSET_AGGREGATE_AND_PROOF),
        signedAggregate.getSignature(),
        state)) {
      return reject("Aggregator signature is invalid");
    }
    // [REJECT] The aggregate stark is valid
    final Bytes proof = aggregateAndProof.getProof();
    if (!attestationUtil.isValidAggregateProof(state, aggregate, proof).isSuccessful()) {
      return reject("Aggregate proof is invalid");
    }
    attestation.setAggregateProof(proof);
    attestation.setIndexedAttestation(attestationUtil.getIndexedAttestation(state, aggregate));
    attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
    attestation.setValidIndexedAttestation();
    return InternalValidationResult.ACCEPT;
  }

  private record AggregatorIndexAndEpoch(UInt64 aggregatorIndex, UInt64 epoch) {}
}

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

package tech.pegasys.teku.spec.logic.versions.pq.util;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/**
 * Attestation helpers for PQ, where an attestation carries no signature: the signatures of a
 * block's attestations are proven together by the block's STARK proof, and an indexed attestation
 * in an attester slashing carries a proof of its own.
 */
public class AttestationUtilPq extends AttestationUtilGloas {

  private final SpecConfigPq specConfigPq;
  private final MiscHelpersPq miscHelpersPq;
  private final StarkProofVerifier starkProofVerifier;
  private final XmssSignatureVerifier xmssSignatureVerifier;
  private final BeaconStateAccessorsPq beaconStateAccessorsPq;

  public AttestationUtilPq(
      final SpecConfigPq specConfig,
      final SchemaDefinitionsPq schemaDefinitions,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final MiscHelpersPq miscHelpers,
      final StarkProofVerifier starkProofVerifier,
      final XmssSignatureVerifier xmssSignatureVerifier) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
    this.specConfigPq = specConfig;
    this.miscHelpersPq = miscHelpers;
    this.starkProofVerifier = starkProofVerifier;
    this.xmssSignatureVerifier = xmssSignatureVerifier;
    this.beaconStateAccessorsPq = beaconStateAccessors;
  }

  /**
   * The attesters of an attestation, for processing. A PQ attestation has no signature, so the
   * light indexed attestation carries the empty signature, which nothing reads.
   */
  @Override
  public IndexedAttestationLight getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    if (attestation instanceof SingleAttestationPq single) {
      return new IndexedAttestationLight(
          List.of(single.getAttesterIndex()), single.getData(), BLSSignature.empty());
    }
    return new IndexedAttestationLight(
        getAttestingIndices(state, attestation), attestation.getData(), BLSSignature.empty());
  }

  /**
   * Gossip validation of a PQ attestation's signature. A single attestation is signed with XMSS at
   * its target epoch's attestation leaf, by an attester who must be in the committee
   * (validate_beacon_attestation_gossip). An aggregate is proven by its aggregator's proof, which
   * the attestation itself does not carry, so it can't be validated here.
   */
  @Override
  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {
    if (attestation.isValidIndexedAttestation()
        && attestation.getIndexedAttestation().isPresent()) {
      return SafeFuture.completedFuture(AttestationProcessingResult.SUCCESSFUL);
    }
    if (attestation.isValidIndexedAttestation()) {
      // Proven elsewhere, as by the proof of the block that included it, so only its attesters
      // are needed (on_attestation, modified in PQ)
      final IndexedAttestationLight indexedAttestation =
          getIndexedAttestation(state, attestation.getAttestation());
      if (indexedAttestation.attestingIndices().isEmpty()) {
        return SafeFuture.completedFuture(
            AttestationProcessingResult.invalid("Attestation has no attesters"));
      }
      attestation.setIndexedAttestation(indexedAttestation);
      attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
      return SafeFuture.completedFuture(AttestationProcessingResult.SUCCESSFUL);
    }
    if (!(attestation.getAttestation() instanceof SingleAttestationPq single)) {
      return SafeFuture.completedFuture(
          AttestationProcessingResult.invalid(
              "A PQ aggregate is validated with its aggregator's proof"));
    }
    final AttestationProcessingResult result = isValidSingleAttestation(state, single);
    if (result.isSuccessful()) {
      // As in Electra, the pool and fork choice take the aggregated format: one bit in the
      // committee. The single attestation, with its XMSS signature, is kept for aggregation.
      attestation.convertToAggregatedFormatFromSingleAttestation(
          convertSingleAttestationToAggregated(state, single));
      attestation.setIndexedAttestation(getIndexedAttestation(state, single));
      attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
      attestation.setValidIndexedAttestation();
    }
    return SafeFuture.completedFuture(result);
  }

  /**
   * is_aggregator, modified in PQ: the validator is in the slot and committee's aggregation
   * committee. There are no selection proofs.
   */
  public boolean isAggregator(
      final BeaconState state,
      final UInt64 slot,
      final UInt64 committeeIndex,
      final UInt64 validatorIndex) {
    return getAggregatorPosition(state, slot, committeeIndex, validatorIndex).isPresent();
  }

  /**
   * The validator's position in the aggregation committee, which staggers its collection cutoff and
   * aggregate due time (get_aggregation_cutoff_ms, get_aggregate_due_ms), or empty when it isn't an
   * aggregator.
   */
  public Optional<Integer> getAggregatorPosition(
      final BeaconState state,
      final UInt64 slot,
      final UInt64 committeeIndex,
      final UInt64 validatorIndex) {
    if (!validatorIndex.isLessThan(Integer.MAX_VALUE)) {
      return Optional.empty();
    }
    final int position =
        beaconStateAccessorsPq
            .getAggregationCommittee(state, slot, committeeIndex)
            .indexOf(validatorIndex.intValue());
    return position < 0 ? Optional.empty() : Optional.of(position);
  }

  /**
   * Whether {@code proof} proves the attesters' signatures of {@code aggregate}, as gossip
   * validates an aggregator's proof: stark.VerifyAggregates over get_attestation_statements.
   */
  public AttestationProcessingResult isValidAggregateProof(
      final BeaconState state, final Attestation aggregate, final Bytes proof) {
    if (!starkProofVerifier.verifyAggregates(
        beaconStateAccessorsPq.getAttestationStatements(state, aggregate), proof)) {
      return AttestationProcessingResult.invalid("Aggregate proof is invalid");
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  /** A validated PQ single attestation as an aggregate of one attester. */
  public AttestationPq convertSingleAttestationToAggregated(
      final BeaconState state, final SingleAttestationPq single) {
    final AttestationPqSchema schema =
        SchemaDefinitionsPq.required(schemaDefinitions)
            .getPqAttestationSchemas()
            .getAttestationSchema();
    final IntList committee =
        beaconStateAccessors.getBeaconCommittee(
            state, single.getData().getSlot(), single.getCommitteeIndex());
    return schema.create(
        schema
            .getAggregationBitsSchema()
            .ofBits(committee.size(), committee.indexOf(single.getAttesterIndex().intValue())),
        single.getData(),
        schema
            .getCommitteeBitsSchema()
            .orElseThrow()
            .ofBits(single.getCommitteeIndex().intValue()));
  }

  private AttestationProcessingResult isValidSingleAttestation(
      final BeaconState state, final SingleAttestationPq single) {
    final AttestationData data = single.getData();
    final UInt64 attesterIndex = single.getAttesterIndex();
    if (attesterIndex.isGreaterThanOrEqualTo(state.getValidators().size())) {
      return AttestationProcessingResult.invalid("Attester is not a validator");
    }
    final IntList committee =
        beaconStateAccessors.getBeaconCommittee(state, data.getSlot(), single.getCommitteeIndex());
    if (!committee.contains(attesterIndex.intValue())) {
      return AttestationProcessingResult.invalid("Attester is not a member of the committee");
    }
    final UInt64 targetEpoch = data.getTarget().getEpoch();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.BEACON_ATTESTER, targetEpoch, state.getFork(), state.getGenesisValidatorsRoot());
    final Bytes32 publicKey =
        ((ValidatorPq) state.getValidators().get(attesterIndex.intValue())).getXmssPublicKey();
    if (!xmssSignatureVerifier.verify(
        publicKey,
        miscHelpersPq.computeXmssAttestationLeafIndex(targetEpoch),
        Bytes32.wrap(miscHelpers.computeSigningRoot(data, domain)),
        single.getSignature())) {
      return AttestationProcessingResult.invalid("Attestation signature is invalid");
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  /** is_valid_indexed_attestation, modified in PQ to verify the attestation's STARK proof. */
  @Override
  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork,
      final BeaconState state,
      final IndexedAttestation indexedAttestation,
      final BLSSignatureVerifier signatureVerifier) {
    if (!(indexedAttestation instanceof IndexedAttestationPq indexedAttestationPq)) {
      return AttestationProcessingResult.invalid("Expected a PQ indexed attestation");
    }
    final List<UInt64> indices = indexedAttestationPq.getAttestingIndices().asListUnboxed();
    if (indices.isEmpty()) {
      return AttestationProcessingResult.invalid("Attesting indices must not be empty");
    }
    if (indices.size()
        > (long) specConfigPq.getMaxValidatorsPerCommittee()
            * specConfigPq.getMaxCommitteesPerSlotPq()) {
      return AttestationProcessingResult.invalid("Too many attesting indices");
    }
    final List<Bytes32> pubkeys = new ArrayList<>(indices.size());
    UInt64 lastIndex = null;
    for (final UInt64 index : indices) {
      if (lastIndex != null && index.isLessThanOrEqualTo(lastIndex)) {
        return AttestationProcessingResult.invalid("Attesting indices are not sorted and unique");
      }
      lastIndex = index;
      if (index.isGreaterThanOrEqualTo(state.getValidators().size())) {
        return AttestationProcessingResult.invalid(
            "Attesting indices include non-existent validator");
      }
      pubkeys.add(((ValidatorPq) state.getValidators().get(index.intValue())).getXmssPublicKey());
    }
    final AttestationData data = indexedAttestationPq.getData();
    final UInt64 targetEpoch = data.getTarget().getEpoch();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.BEACON_ATTESTER, targetEpoch, state.getFork(), state.getGenesisValidatorsRoot());
    final XmssAggregateStatement statement =
        new XmssAggregateStatement(
            pubkeys,
            miscHelpersPq.computeXmssAttestationLeafIndex(targetEpoch),
            Bytes32.wrap(miscHelpers.computeSigningRoot(data, domain)));
    if (!starkProofVerifier.verifyAggregates(List.of(statement), indexedAttestationPq.getProof())) {
      return AttestationProcessingResult.invalid("Attestation proof is invalid");
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }
}

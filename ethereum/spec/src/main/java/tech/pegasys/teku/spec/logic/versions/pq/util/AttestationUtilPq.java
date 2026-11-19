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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
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

  public AttestationUtilPq(
      final SpecConfigPq specConfig,
      final SchemaDefinitionsPq schemaDefinitions,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final MiscHelpersPq miscHelpers,
      final StarkProofVerifier starkProofVerifier) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
    this.specConfigPq = specConfig;
    this.miscHelpersPq = miscHelpers;
    this.starkProofVerifier = starkProofVerifier;
  }

  /**
   * The attesters of an attestation, for processing. A PQ attestation has no signature, so the
   * light indexed attestation carries the empty signature, which nothing reads.
   */
  @Override
  public IndexedAttestationLight getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    return new IndexedAttestationLight(
        getAttestingIndices(state, attestation), attestation.getData(), BLSSignature.empty());
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

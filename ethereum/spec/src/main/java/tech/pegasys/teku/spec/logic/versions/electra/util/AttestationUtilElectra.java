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

package tech.pegasys.teku.spec.logic.versions.electra.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.AttestationValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.util.AttestationUtilDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilElectra extends AttestationUtilDeneb {
  private static final Logger LOG = LogManager.getLogger();

  public AttestationUtilElectra(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  /**
   * Return the attesting indices corresponding to ``aggregation_bits`` and ``committee_bits``.
   *
   * @param state
   * @param attestation
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/master/specs/electra/beacon-chain.md#modified-get_attesting_indices</a>
   */
  @Override
  public List<UInt64> getAttestingIndices(final BeaconState state, final Attestation attestation) {
    final List<UInt64> committeeIndices = attestation.getCommitteeIndicesRequired();
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final List<UInt64> attestingIndices = new ArrayList<>(aggregationBits.getBitCount());
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : committeeIndices) {
      final IntList committee =
          beaconStateAccessors.getBeaconCommittee(
              state, attestation.getData().getSlot(), committeeIndex);
      streamCommitteeAttesters(committee, aggregationBits, committeeOffset)
          .mapToObj(UInt64::valueOf)
          .forEach(attestingIndices::add);
      committeeOffset += committee.size();
    }
    return attestingIndices;
  }

  public IntStream streamCommitteeAttesters(
      final IntList committee, final SszBitlist aggregationBits, final int committeeOffset) {
    return IntStream.range(committeeOffset, committeeOffset + committee.size())
        .filter(aggregationBits::isSet)
        .map(attesterIndex -> committee.getInt(attesterIndex - committeeOffset));
  }

  /**
   * In electra, attestationData must have committee index set to 0
   *
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/master/specs/electra/validator.md#construct-attestation</a>
   */
  @Override
  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    return super.getGenericAttestationData(slot, state, block, UInt64.ZERO);
  }

  @Override
  public IndexedAttestationLight getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    if (attestation.isSingleAttestation()) {
      return getIndexedAttestationFromSingleAttestation(attestation.toSingleAttestationRequired());
    }
    return super.getIndexedAttestation(state, attestation);
  }

  private IndexedAttestationLight getIndexedAttestationFromSingleAttestation(
      final SingleAttestation attestation) {
    return new IndexedAttestationLight(
        List.of(attestation.getValidatorIndexRequired()),
        attestation.getData(),
        attestation.getSignature());
  }

  @Override
  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {

    if (!attestation.getAttestation().isSingleAttestation()) {
      // we can use the default implementation for aggregate attestation
      return super.isValidIndexedAttestationAsync(fork, state, attestation, blsSignatureVerifier);
    }

    // single attestation flow

    // 1. verify signature first
    // 2. verify call getSingleAttestationAggregationBits which also validates the validatorIndex
    // and the committee against the state
    // 3. convert attestation inside ValidatableAttestation to AttestationElectra
    // 4. set the indexed attestation into ValidatableAttestation
    // 5. set the attestation as valid indexed attestation

    return validateSingleAttestationSignature(
            fork,
            state,
            attestation.getAttestation().toSingleAttestationRequired(),
            blsSignatureVerifier)
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                final SingleAttestation singleAttestation =
                    attestation.getAttestation().toSingleAttestationRequired();
                final IndexedAttestationLight indexedAttestation =
                    getIndexedAttestationFromSingleAttestation(singleAttestation);

                final Attestation convertedAttestation =
                    convertSingleAttestationToAggregated(state, singleAttestation);

                attestation.convertToAggregatedFormatFromSingleAttestation(convertedAttestation);
                attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
                attestation.setIndexedAttestation(indexedAttestation);
                attestation.setValidIndexedAttestation();
              }
              return result;
            })
        .exceptionallyCompose(
            err -> {
              if (err.getCause() instanceof IllegalArgumentException) {
                LOG.debug("on_attestation: Attestation is not valid: ", err);
                return SafeFuture.completedFuture(
                    AttestationProcessingResult.invalid(err.getMessage()));
              } else {
                return SafeFuture.failedFuture(err);
              }
            });
  }

  @Override
  public Attestation convertSingleAttestationToAggregated(
      final BeaconState state, final SingleAttestation singleAttestation) {
    final AttestationElectraSchema attestationElectraSchema =
        schemaDefinitions.getAttestationSchema().toVersionElectra().orElseThrow();
    final SszBitlist singleAttestationAggregationBits =
        getSingleAttestationAggregationBits(state, singleAttestation, attestationElectraSchema);
    return attestationElectraSchema.create(
        singleAttestationAggregationBits,
        singleAttestation.getData(),
        singleAttestation.getAggregateSignature(),
        attestationElectraSchema
            .getCommitteeBitsSchema()
            .orElseThrow()
            .ofBits(singleAttestation.getFirstCommitteeIndex().intValue()));
  }

  @Override
  public AttestationValidationResult validateCommitteeIndexValue(final UInt64 index) {
    // [REJECT] attestation.data.index == 0
    if (!index.isZero()) {
      return AttestationValidationResult.invalid(
          () -> String.format("Attestation data index must be 0 for Electra, but was %s.", index));
    }
    return AttestationValidationResult.VALID;
  }

  private SszBitlist getSingleAttestationAggregationBits(
      final BeaconState state,
      final SingleAttestation singleAttestation,
      final AttestationElectraSchema attestationElectraSchema) {
    final IntList committee =
        beaconStateAccessors.getBeaconCommittee(
            state,
            singleAttestation.getData().getSlot(),
            singleAttestation.getFirstCommitteeIndex());

    final int validatorIndex = singleAttestation.getValidatorIndexRequired().intValue();
    final int validatorCommitteeBit = committee.indexOf(validatorIndex);

    checkArgument(
        validatorCommitteeBit >= 0,
        "Validator index %s is not part of the committee %s",
        validatorIndex,
        singleAttestation.getFirstCommitteeIndex());

    return attestationElectraSchema.createAggregationBitsOf(
        committee.size(), validatorCommitteeBit);
  }

  private SafeFuture<AttestationProcessingResult> validateSingleAttestationSignature(
      final Fork fork,
      final BeaconState state,
      final SingleAttestation singleAttestation,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final Optional<BLSPublicKey> pubkey =
        beaconStateAccessors.getValidatorPubKey(
            state, singleAttestation.getValidatorIndexRequired());

    if (pubkey.isEmpty()) {
      return completedFuture(
          AttestationProcessingResult.invalid("Attesting index include non-existent validator"));
    }

    return validateAttestationDataSignature(
        fork,
        state,
        List.of(pubkey.get()),
        singleAttestation.getSignature(),
        singleAttestation.getData(),
        signatureVerifier);
  }
}

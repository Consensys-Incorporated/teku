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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public abstract class AttestationUtil {

  private static final Logger LOG = LogManager.getLogger();

  protected final SchemaDefinitions schemaDefinitions;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final MiscHelpers miscHelpers;
  protected final SpecConfig specConfig;

  public AttestationUtil(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  /**
   * Returns the state to use for non-consensus attestation reward calculations. Forks may project
   * block-processing data that is already known to the caller but has not yet been applied to the
   * pre-state.
   */
  public BeaconState getStateForAttestationRewardCalculation(
      final BeaconState state, final boolean parentPayloadAvailable) {
    return state;
  }

  /**
   * Check if ``data_1`` and ``data_2`` are slashable according to Casper FFG rules.
   *
   * @param data1
   * @param data2
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_slashable_attestation_data</a>
   */
  public boolean isSlashableAttestationData(
      final AttestationData data1, final AttestationData data2) {
    return (
    // case 1: double vote || case 2: surround vote
    (!data1.equals(data2) && data1.getTarget().getEpoch().equals(data2.getTarget().getEpoch()))
        || (data1.getSource().getEpoch().compareTo(data2.getSource().getEpoch()) < 0
            && data2.getTarget().getEpoch().compareTo(data1.getTarget().getEpoch()) < 0));
  }

  /**
   * Return the indexed attestation corresponding to ``attestation``.
   *
   * @param state
   * @param attestation
   * @return
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_indexed_attestation</a>
   */
  public IndexedAttestationLight getIndexedAttestation(
      final BeaconState state, final Attestation attestation) {
    // Indices are produced via aggregation-bit positions over a committee, so they are unique by
    // construction. Order is irrelevant for downstream light consumers and for BLS aggregate
    // signature verification (commutative). The spec's sorted-by-validator-index form is only
    // enforced for SSZ-derived IndexedAttestations reaching isValidIndexedAttestation via the
    // AttesterSlashing path.
    return new IndexedAttestationLight(
        getAttestingIndices(state, attestation),
        attestation.getData(),
        attestation.getAggregateSignature());
  }

  /**
   * Return the attesting indices corresponding to ``data`` and ``bits``, in committee-position
   * order (i.e. the order in which aggregation bits are set over the committee). NOT sorted by
   * validator index — committees are a pseudorandom permutation of active validators. Callers that
   * need the spec-canonical sorted form (e.g. when serialising to an SSZ {@link IndexedAttestation}
   * embedded in an {@code AttesterSlashing}) must sort explicitly; see {@link
   * IndexedAttestationLight#toSsz}.
   *
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/beacon-chain.md#get_attesting_indices</a>
   */
  public List<UInt64> getAttestingIndices(final BeaconState state, final Attestation attestation) {
    return streamAttestingIndices(state, attestation.getData(), attestation.getAggregationBits())
        .mapToObj(UInt64::valueOf)
        .toList();
  }

  public IntStream streamAttestingIndices(
      final BeaconState state, final AttestationData data, final SszBitlist aggregationBits) {
    final IntList committee =
        beaconStateAccessors.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        aggregationBits.size() == committee.size(),
        "Aggregation bitlist size (%s) does not match committee size (%s)",
        aggregationBits.size(),
        committee.size());
    return IntStream.range(0, committee.size())
        .filter(aggregationBits::getBit)
        .map(committee::getInt);
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {
    if (attestation.isValidIndexedAttestation()
        && attestation.getIndexedAttestation().isPresent()) {
      return completedFuture(AttestationProcessingResult.SUCCESSFUL);
    }

    return SafeFuture.of(
            () -> {
              // getIndexedAttestation() throws, so wrap it in a future
              final IndexedAttestationLight indexedAttestation =
                  getIndexedAttestation(state, attestation.getAttestation());
              attestation.setIndexedAttestation(indexedAttestation);
              return indexedAttestation;
            })
        .thenCompose(
            att -> {
              if (attestation.isValidIndexedAttestation()) {
                return completedFuture(AttestationProcessingResult.SUCCESSFUL);
              }
              return isValidIndexedAttestationAsync(fork, state, att, blsSignatureVerifier);
            })
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
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

  /**
   * Verify validity of ``indexed_attestation``.
   *
   * @param state
   * @param indexedAttestation
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_indexed_attestation</a>
   */
  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork, final BeaconState state, final IndexedAttestation indexedAttestation) {
    return isValidIndexedAttestation(
        fork, state, indexedAttestation, specConfig.getBLSSignatureVerifier());
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork,
      final BeaconState state,
      final IndexedAttestation indexedAttestation,
      final BLSSignatureVerifier signatureVerifier) {
    // SSZ-derived path (reached only via AttesterSlashing received off the wire): enforce the
    // spec-mandated sorted-and-unique property on the indices. Internally-produced light
    // attestations skip this check because they are unique by construction and order-agnostic
    // for downstream consumers.
    final IndexedAttestationLight light = IndexedAttestationLight.fromSsz(indexedAttestation);
    final AttestationProcessingResult sortedCheck = checkSortedAndUnique(light.attestingIndices());
    if (!sortedCheck.isSuccessful()) {
      return sortedCheck;
    }
    return isValidIndexedAttestation(fork, state, light, signatureVerifier);
  }

  private AttestationProcessingResult checkSortedAndUnique(final List<UInt64> indices) {
    UInt64 lastIndex = null;
    for (final UInt64 index : indices) {
      if (lastIndex != null && index.isLessThanOrEqualTo(lastIndex)) {
        return AttestationProcessingResult.invalid("Attesting indices are not sorted");
      }
      lastIndex = index;
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  public AttestationProcessingResult isValidIndexedAttestation(
      final Fork fork,
      final BeaconState state,
      final IndexedAttestationLight indexedAttestation,
      final BLSSignatureVerifier signatureVerifier) {
    final SafeFuture<AttestationProcessingResult> result =
        isValidIndexedAttestationAsync(
            fork, state, indexedAttestation, AsyncBLSSignatureVerifier.wrap(signatureVerifier));

    return result.getImmediately();
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestationAsync(
      final Fork fork,
      final BeaconState state,
      final IndexedAttestationLight indexedAttestation,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final List<UInt64> indices = indexedAttestation.attestingIndices();

    if (indices.isEmpty()) {
      return completedFuture(
          AttestationProcessingResult.invalid("Attesting indices must not be empty"));
    }

    // Sorted+unique is enforced by the SSZ-taking overload for AttesterSlashing-derived inputs.
    // Internally-produced light attestations have unique indices by construction, and downstream
    // consumers are order-agnostic, so we skip the check here.
    final List<BLSPublicKey> pubkeys = new ArrayList<>(indices.size());

    for (final UInt64 index : indices) {
      final Optional<BLSPublicKey> validatorPubKey =
          beaconStateAccessors.getValidatorPubKey(state, index);
      if (validatorPubKey.isEmpty()) {
        return completedFuture(
            AttestationProcessingResult.invalid(
                "Attesting indices include non-existent validator"));
      }
      pubkeys.add(validatorPubKey.get());
    }

    return validateAttestationDataSignature(
        fork,
        state,
        Collections.unmodifiableList(pubkeys),
        indexedAttestation.signature(),
        indexedAttestation.data(),
        signatureVerifier);
  }

  protected SafeFuture<AttestationProcessingResult> validateAttestationDataSignature(
      final Fork fork,
      final BeaconState state,
      final List<BLSPublicKey> publicKeys,
      final BLSSignature signature,
      final AttestationData attestationData,
      final AsyncBLSSignatureVerifier signatureVerifier) {

    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.BEACON_ATTESTER,
            attestationData.getTarget().getEpoch(),
            fork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpers.computeSigningRoot(attestationData, domain);

    return signatureVerifier
        .verify(publicKeys, signingRoot, signature)
        .thenApply(
            isValidSignature -> {
              if (isValidSignature) {
                return AttestationProcessingResult.SUCCESSFUL;
              } else {
                LOG.debug("AttestationUtil.validateAttestationDataSignature: Verify signature");
                return AttestationProcessingResult.invalid("Signature is invalid");
              }
            });
  }

  // Get attestation data that does not include attester specific shard or crosslink information
  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    // Get variables necessary that can be shared among Attestations of all validators
    final Bytes32 beaconBlockRoot = block.getRoot();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    final Bytes32 epochBoundaryBlockRoot =
        startSlot.compareTo(slot) == 0 || state.getSlot().compareTo(startSlot) <= 0
            ? block.getRoot()
            : beaconStateAccessors.getBlockRootAtSlot(state, startSlot);
    final Checkpoint source = state.getCurrentJustifiedCheckpoint();
    final Checkpoint target = new Checkpoint(epoch, epochBoundaryBlockRoot);

    // Set attestation data
    return new AttestationData(slot, committeeIndex, beaconBlockRoot, source, target);
  }

  public abstract Optional<SlotInclusionGossipValidationResult>
      performSlotInclusionGossipValidation(
          Attestation attestation, UInt64 genesisTime, UInt64 currentTimeMillis);

  public abstract Attestation convertSingleAttestationToAggregated(
      BeaconState state, SingleAttestation singleAttestation);

  public abstract AttestationValidationResult validateCommitteeIndexValue(UInt64 committeeIndex);

  public abstract AttestationValidationResult validatePayloadStatus(
      AttestationData attestationData, Optional<UInt64> maybeBlockSlot);

  public enum SlotInclusionGossipValidationResult {
    IGNORE,
    SAVE_FOR_FUTURE
  }
}

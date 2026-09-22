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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import com.google.common.collect.Comparators;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.util.AttestationValidationResult;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class AttestationUtilGloas extends AttestationUtilElectra {

  public AttestationUtilGloas(
      final SpecConfigGloas specConfig,
      final SchemaDefinitionsGloas schemaDefinitions,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  @Override
  public BeaconState getStateForAttestationRewardCalculation(
      final BeaconState state, final boolean parentPayloadAvailable) {
    if (!parentPayloadAvailable) {
      return state;
    }

    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);
    final int parentSlotIndex =
        stateGloas
            .getLatestBlockHeader()
            .getSlot()
            .mod(specConfig.getSlotsPerHistoricalRoot())
            .intValue();
    if (stateGloas.getExecutionPayloadAvailability().getBit(parentSlotIndex)) {
      return state;
    }

    // Block processing sets this bit before processing attestations. Reward calculation starts from
    // the pre-state, so project the known parent status without mutating that shared state.
    return state.updated(
        mutableState -> {
          final MutableBeaconStateGloas mutableStateGloas =
              MutableBeaconStateGloas.required(mutableState);
          mutableStateGloas.setExecutionPayloadAvailability(
              mutableStateGloas.getExecutionPayloadAvailability().withBit(parentSlotIndex));
        });
  }

  /**
   * is_valid_indexed_payload_attestation
   *
   * <p>Check if ``attestation`` is non-empty, has sorted indices, and has a valid aggregate
   * signature.
   */
  public boolean isValidIndexedPayloadAttestation(
      final BeaconState state, final IndexedPayloadAttestationLight attestation) {
    // Verify indices are non-empty and sorted
    final List<UInt64> indices = attestation.attestingIndices();
    if (indices.isEmpty() || !Comparators.isInOrder(indices, UInt64::compareTo)) {
      return false;
    }
    // Verify aggregate signature
    final List<BLSPublicKey> pubKeys =
        indices.stream()
            .map(index -> state.getValidators().get(index.intValue()).getPublicKey())
            .toList();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.PTC_ATTESTER,
            miscHelpers.computeEpochAtSlot(attestation.data().getSlot()));
    final Bytes signingRoot = miscHelpers.computeSigningRoot(attestation.data(), domain);
    return specConfig
        .getBLSSignatureVerifier()
        .verify(pubKeys, signingRoot, attestation.signature());
  }

  @Override
  public AttestationValidationResult validateCommitteeIndexValue(final UInt64 index) {
    // [REJECT] attestation.data.index < 2
    if (!index.isLessThan(2)) {
      return AttestationValidationResult.invalid(
          () ->
              String.format("Attestation data index must be 0 or 1 for Gloas, but was %s.", index));
    }
    return AttestationValidationResult.VALID;
  }

  @Override
  public AttestationValidationResult validatePayloadStatus(
      final AttestationData attestationData, final Optional<UInt64> maybeBlockSlot) {
    // [REJECT] attestation.data.index == 0 if block.slot == attestation.data.slot.
    return maybeBlockSlot
        .map(
            blockSlot -> {
              if (blockSlot.equals(attestationData.getSlot())
                  && !attestationData.getIndex().isZero()) {
                return AttestationValidationResult.invalid(
                    () ->
                        String.format(
                            "Payload status must be 0, but was %s.", attestationData.getIndex()));
              } else {
                return AttestationValidationResult.VALID;
              }
            })
        .orElse(AttestationValidationResult.VALID);
  }

  @Override
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
}

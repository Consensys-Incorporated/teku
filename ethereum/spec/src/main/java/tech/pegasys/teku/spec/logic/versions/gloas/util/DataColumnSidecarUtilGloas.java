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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;
import tech.pegasys.teku.spec.logic.common.util.GloasTrackingKey;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

/**
 * Gloas-specific implementation of {@link DataColumnSidecarUtil}.
 *
 * <p>Implements Gloas gossip validation rules as per <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#data_column_sidecar_subnet_id">data_column_sidecar_subnet_id
 * p2p gossip rules</a>
 *
 * <p>Gloas sidecars do NOT include signed block headers, resulting in simplified validation
 */
public class DataColumnSidecarUtilGloas implements DataColumnSidecarUtil {

  private final MiscHelpersGloas miscHelpersGloas;

  public DataColumnSidecarUtilGloas(final MiscHelpersGloas miscHelpersGloas) {
    this.miscHelpersGloas = miscHelpersGloas;
  }

  @Override
  public Optional<DataColumnSidecarValidationError> performSlotTimingValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFromFuture) {
    // Non applicable for Gloas
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecarValidationError> performSlotFinalizationValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFinalized) {
    // Non applicable for Gloas
    return Optional.empty();
  }

  @Override
  public boolean isBlockParentSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    // Not applicable to Gloas as sidecars don't contain block headers with parent references.
    return true;
  }

  /**
   * Check if the referenced block has been seen. Gossip rule: [IGNORE] A valid block for the
   * sidecar's slot has been seen (via gossip or non-gossip sources). If not yet seen, a client MUST
   * queue the sidecar for deferred validation and possible processing once the block is received or
   * retrieved.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isBlockRootSeen function to check if a block root has been seen
   * @return true if the referenced block has been seen
   */
  @Override
  public boolean isBlockSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    return isBlockRootSeen.apply(dataColumnSidecar.getBeaconBlockRoot());
  }

  /**
   * Validate that the sidecar's slot matches the referenced block's slot. Gossip rule: [REJECT] The
   * sidecar's slot matches the slot of the block with root beacon_block_root
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param getSlotForBlockRoot function to get the slot for a block root
   * @return validation result
   */
  @Override
  public Optional<DataColumnSidecarValidationError> validateBlockSlot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot) {
    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();
    final Optional<UInt64> blockSlot = getSlotForBlockRoot.apply(beaconBlockRoot);
    if (blockSlot.isEmpty()) {
      return Optional.of(
          DataColumnSidecarValidationError.BadTiming.format(
              "DataColumnSidecar's beacon_block_root %s does not correspond to a known block. It will be saved for future processing",
              beaconBlockRoot));
    }
    if (!blockSlot.get().equals(dataColumnSidecar.getSlot())) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "DataColumnSidecar's slot %s does not match the block slot %s for beacon_block_root %s",
              dataColumnSidecar.getSlot(), blockSlot.get(), beaconBlockRoot));
    }
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecarValidationError> validateParentBlock(
      final DataColumnSidecar dataColumnSidecar,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final Function<Bytes32, Optional<UInt64>> getBlockSlot,
      final BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock) {
    // Gloas does not validate parent block (no header in Gloas sidecars)
    return Optional.empty();
  }

  /**
   * Extract tracking key from block header and dataColumnSidecar for late validation check.
   *
   * @param maybeBeaconBlockHeader maybe the beacon block header (empty for Gloas)
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return the fork-appropriate tracking key
   */
  @Override
  public DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      final Optional<BeaconBlockHeader> maybeBeaconBlockHeader,
      final DataColumnSidecar dataColumnSidecar) {
    return extractTrackingKey(dataColumnSidecar);
  }

  /**
   * Extract the tracking key from a data column dataColumnSidecar.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return the fork-appropriate tracking key
   */
  @Override
  public DataColumnSidecarTrackingKey extractTrackingKey(
      final DataColumnSidecar dataColumnSidecar) {
    return new GloasTrackingKey(
        dataColumnSidecar.getBeaconBlockRoot(), dataColumnSidecar.getIndex());
  }

  /**
   * Verify structural validity of the data column dataColumnSidecar.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return true if structure is valid
   */
  @Override
  public boolean verifyDataColumnSidecarStructure(final DataColumnSidecar dataColumnSidecar) {
    // Partially checking the structure (except kzg commitments). Gloas needs the corresponding
    // bid's kzg commitments to fully validate the Data Column Sidecar which is done in
    // validateWithBlock
    return miscHelpersGloas.verifyDataColumnSidecar(dataColumnSidecar);
  }

  @Override
  public boolean verifyInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    // Gloas doesn't have inclusion proof requirement (no header in Gloas sidecars)
    return true;
  }

  @Override
  public SszList<SszKZGCommitment> getKzgCommitments(final BeaconBlock block) {
    return BeaconBlockBodyGloas.required(block.getBody())
        .getSignedExecutionPayloadBid()
        .getMessage()
        .getBlobKzgCommitments();
  }

  @Override
  public List<DataColumnSidecar> constructDataColumnSidecars(
      final Optional<SignedBeaconBlockHeader> maybeSignedBeaconBlockHeader,
      final SlotAndBlockRoot slotAndBlockRoot,
      final Optional<SszList<SszKZGCommitment>> maybeSszKZGCommitments,
      final Optional<List<Bytes32>> maybeKzgCommitmentsInclusionProof,
      final List<BlobAndCellProofs> blobAndCellProofsList) {
    return miscHelpersGloas.constructDataColumnSidecars(
        maybeSignedBeaconBlockHeader,
        slotAndBlockRoot,
        maybeSszKZGCommitments,
        maybeKzgCommitmentsInclusionProof,
        blobAndCellProofsList);
  }

  @Override
  public Optional<List<Bytes32>> computeDataColumnKzgCommitmentsInclusionProof(
      final BeaconBlockBody beaconBlockBody) {
    // No kzg commitments inclusion proof in Gloas
    return Optional.empty();
  }

  @Override
  public Optional<SszBytes32Vector> getMaybeKzgCommitmentsInclusionProof(
      final DataColumnSidecar dataColumnSidecar) {
    // No kzg commitments proof in Gloas
    return Optional.empty();
  }

  @Override
  public Optional<SszList<SszKZGCommitment>> getMaybeKzgCommitments(
      final DataColumnSidecar dataColumnSidecar) {
    // Gloas sidecars carry none; the bid's commitments are the only source
    return Optional.empty();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecarValidationError>> validateAndVerifyKzgProofs(
      final DataColumnSidecar dataColumnSidecar,
      final Function<DataColumnSidecar, SafeFuture<Optional<SszList<SszKZGCommitment>>>>
          retrieveBlobKzgCommitments) {

    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();

    return retrieveBlobKzgCommitments
        .apply(dataColumnSidecar)
        .thenApply(
            maybeBlobKzgCommitments -> {
              if (maybeBlobKzgCommitments.isEmpty()) {
                return Optional.of(
                    DataColumnSidecarValidationError.BadTiming.format(
                        "DataColumnSidecar's beacon_block_root %s does not correspond to a known block",
                        beaconBlockRoot));
              }
              final SszList<SszKZGCommitment> blobKzgCommitments = maybeBlobKzgCommitments.get();
              final Optional<DataColumnSidecarValidationError> maybeVerifyDataColumnSidecarResult =
                  verifyDataColumnSidecar(dataColumnSidecar, blobKzgCommitments);
              return maybeVerifyDataColumnSidecarResult.or(
                  () -> verifyDataColumnSidecarKzgProofs(dataColumnSidecar, blobKzgCommitments));
            });
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecarValidationError>> validateWithState(
      final DataColumnSidecar dataColumnSidecar,
      final Spec spec,
      final Set<InclusionProofInfo> validInclusionProofInfoSet,
      final Set<Bytes32> validSignedBlockHeaders,
      final Function<Bytes32, Optional<UInt64>> getBlockSlot,
      final Function<StateRetrievalData, SafeFuture<Optional<BeaconState>>> retrieveBeaconState,
      final Function<ProposerValidationData, Boolean> isProposerTheExpectedProposer,
      final Function<SignatureVerificationData, Boolean>
          isSignatureValidWithRespectToProposerIndex) {
    // Non-applicable for Gloas, no state validation required
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public List<DataColumnSidecar> reconstructAllDataColumnSidecars(
      final List<DataColumnSidecar> dataColumnSidecars) {
    return miscHelpersGloas.reconstructAllDataColumnSidecars(dataColumnSidecars);
  }

  @Override
  public Optional<InclusionProofInfo> getInclusionProofCacheKey(
      final DataColumnSidecar dataColumnSidecar) {
    // Gloas doesn't have inclusion proof requirement (no header in Gloas sidecars)
    return Optional.empty();
  }

  private Optional<DataColumnSidecarValidationError> verifyDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar,
      final SszList<SszKZGCommitment> blobKzgCommitments) {
    final boolean validDataColumnSidecar =
        miscHelpersGloas.verifyDataColumnSidecarWithCommitments(
            dataColumnSidecar, blobKzgCommitments);
    if (!validDataColumnSidecar) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format("Invalid DataColumnSidecar"));
    }
    return Optional.empty();
  }

  private Optional<DataColumnSidecarValidationError> verifyDataColumnSidecarKzgProofs(
      final DataColumnSidecar dataColumnSidecar,
      final SszList<SszKZGCommitment> blobKzgCommitments) {
    final boolean validDataColumnSidecar =
        miscHelpersGloas.verifyDataColumnSidecarKzgProofs(dataColumnSidecar, blobKzgCommitments);
    if (!validDataColumnSidecar) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "DataColumnSidecar's KZG proofs do not match the bid's KZG proofs"));
    }
    return Optional.empty();
  }
}

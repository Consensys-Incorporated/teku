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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
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
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

/**
 * Fork-specific utility for data column sidecar gossip validation.
 *
 * <p>Implementations provide fork-specific validation logic (e.g., Fulu validates signed headers
 * and inclusion proofs, Gloas validates execution payload bids).
 *
 * <p>Return conventions: validation methods return {@code Optional.empty()} when validation passes
 * or is not applicable; boolean methods return {@code true} for valid/applicable, {@code false}
 * otherwise.
 */
public interface DataColumnSidecarUtil {

  Optional<DataColumnSidecarValidationError> performSlotTimingValidation(
      DataColumnSidecar dataColumnSidecar, Predicate<UInt64> isSlotFromFuture);

  Optional<DataColumnSidecarValidationError> performSlotFinalizationValidation(
      DataColumnSidecar dataColumnSidecar, Predicate<UInt64> isSlotFinalized);

  boolean isBlockParentSeen(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Boolean> isBlockRootSeen);

  boolean isBlockSeen(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Boolean> isBlockRootSeen);

  Optional<DataColumnSidecarValidationError> validateBlockSlot(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot);

  Optional<DataColumnSidecarValidationError> validateParentBlock(
      DataColumnSidecar dataColumnSidecar,
      Map<Bytes32, BlockImportResult> invalidBlockRoots,
      Function<Bytes32, Optional<UInt64>> getBlockSlot,
      BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock);

  SafeFuture<Optional<DataColumnSidecarValidationError>> validateAndVerifyKzgProofs(
      DataColumnSidecar dataColumnSidecar,
      Function<DataColumnSidecar, SafeFuture<Optional<SszList<SszKZGCommitment>>>>
          retrieveBlobKzgCommitments);

  SafeFuture<Optional<DataColumnSidecarValidationError>> validateWithState(
      DataColumnSidecar dataColumnSidecar,
      Spec spec,
      Set<InclusionProofInfo> validInclusionProofInfoSet,
      Set<Bytes32> validSignedBlockHeaders,
      Function<Bytes32, Optional<UInt64>> getBlockSlot,
      Function<StateRetrievalData, SafeFuture<Optional<BeaconState>>> retrieveBeaconState,
      Function<ProposerValidationData, Boolean> isProposerTheExpectedProposer,
      Function<SignatureVerificationData, Boolean> isSignatureValidWithRespectToProposerIndex);

  DataColumnSidecarTrackingKey extractTrackingKey(DataColumnSidecar dataColumnSidecar);

  DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      Optional<BeaconBlockHeader> maybeBeaconBlockHeader, DataColumnSidecar dataColumnSidecar);

  boolean verifyDataColumnSidecarStructure(DataColumnSidecar dataColumnSidecar);

  boolean verifyInclusionProof(DataColumnSidecar dataColumnSidecar);

  SszList<SszKZGCommitment> getKzgCommitments(BeaconBlock block);

  List<DataColumnSidecar> constructDataColumnSidecars(
      Optional<SignedBeaconBlockHeader> maybeSignedBeaconBlockHeader,
      SlotAndBlockRoot slotAndBlockRoot,
      Optional<SszList<SszKZGCommitment>> maybeSszKZGCommitments,
      Optional<List<Bytes32>> maybeKzgCommitmentsInclusionProof,
      List<BlobAndCellProofs> blobAndCellProofsList);

  Optional<List<Bytes32>> computeDataColumnKzgCommitmentsInclusionProof(
      BeaconBlockBody beaconBlockBody);

  Optional<SszBytes32Vector> getMaybeKzgCommitmentsInclusionProof(
      DataColumnSidecar dataColumnSidecar);

  /** The sidecar's own commitments, where the fork binds them to the block; empty otherwise. */
  Optional<SszList<SszKZGCommitment>> getMaybeKzgCommitments(DataColumnSidecar dataColumnSidecar);

  List<DataColumnSidecar> reconstructAllDataColumnSidecars(
      List<DataColumnSidecar> dataColumnSidecars);

  Optional<InclusionProofInfo> getInclusionProofCacheKey(DataColumnSidecar dataColumnSidecar);

  record InclusionProofInfo(
      Bytes32 commitmentsRoot, Bytes32 inclusionProofRoot, Bytes32 bodyRoot) {}

  record SignatureVerificationData(
      Bytes signingRoot, UInt64 proposerIndex, BLSSignature signature, BeaconState state) {}

  record StateRetrievalData(UInt64 parentBlockSlot, Bytes32 parentBlockRoot, UInt64 slot) {}

  record ProposerValidationData(UInt64 proposerIndex, UInt64 slot, BeaconState postState) {}
}

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

package tech.pegasys.teku.reference.phase0.forkchoice;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

/** Simplified version of {@link BlobSidecarManager} which is used in fork choice reference tests */
class StubBlobSidecarManager implements BlobSidecarManager {

  private final Map<Bytes32, BlobsAndProofs> blobsAndProofsByBlockRoot = new HashMap<>();

  private final KZG kzg;

  StubBlobSidecarManager(final KZG kzg) {
    this.kzg = kzg;
  }

  /** Prepare the blobs and proofs for a block provided by the reference test * */
  public void prepareBlobsAndProofsForBlock(
      final SignedBeaconBlock block, final List<Blob> blobs, final List<KZGProof> proofs) {
    blobsAndProofsByBlockRoot.put(block.getRoot(), new BlobsAndProofs(blobs, proofs));
  }

  @Override
  public SafeFuture<InternalValidationResult> validateAndPrepareForBlockImport(
      final BlobSidecar blobSidecar, final Optional<UInt64> arrivalTimestamp) {
    return SafeFuture.failedFuture(
        new UnsupportedOperationException("Not available in fork choice reference tests"));
  }

  @Override
  public void prepareForBlockImport(final BlobSidecar blobSidecar, final RemoteOrigin origin) {
    // NOOP
  }

  @Override
  public void subscribeToReceivedBlobSidecar(
      final ReceivedBlobSidecarListener receivedBlobSidecarListener) {
    // NOOP
  }

  @Override
  public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
    // NOOP
    return false;
  }

  /**
   * Creates an implementation of {@link AvailabilityChecker} which uses {@link
   * KZG#verifyBlobKzgProofBatch(List, List, List)} method with the blobs and proofs that the
   * reference test has provided
   */
  @Override
  public AvailabilityChecker<BlobSidecar> createAvailabilityChecker(final SignedBeaconBlock block) {
    return new AvailabilityChecker<BlobSidecar>() {
      @Override
      public boolean initiateDataAvailabilityCheck() {
        return true;
      }

      @Override
      public SafeFuture<DataAndValidationResult<BlobSidecar>> getAvailabilityCheckResult() {
        final BlobsAndProofs blobsAndProofs = blobsAndProofsByBlockRoot.remove(block.getRoot());
        if (blobsAndProofs == null) {
          return SafeFuture.completedFuture(DataAndValidationResult.notRequired());
        }
        return SafeFuture.completedFuture(validateImmediately(block, blobsAndProofs));
      }

      private DataAndValidationResult<BlobSidecar> validateImmediately(
          final SignedBeaconBlock block, final BlobsAndProofs blobsAndProofs) {
        final List<KZGCommitment> kzgCommitments =
            BeaconBlockBodyDeneb.required(block.getMessage().getBody())
                .getBlobKzgCommitments()
                .stream()
                .map(SszKZGCommitment::getKZGCommitment)
                .toList();
        final List<Bytes> blobs = blobsAndProofs.blobs.stream().map(Blob::getBytes).toList();
        try {
          if (!kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, blobsAndProofs.proofs)) {
            return DataAndValidationResult.invalidResult(Collections.emptyList());
          }
        } catch (final Exception ex) {
          return DataAndValidationResult.invalidResult(Collections.emptyList(), ex);
        }
        return DataAndValidationResult.validResult(Collections.emptyList());
      }
    };
  }

  @Override
  public DataAndValidationResult<BlobSidecar> createAvailabilityCheckerAndValidateImmediately(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    throw new UnsupportedOperationException("Not available in fork choice reference tests");
  }

  private record BlobsAndProofs(List<Blob> blobs, List<KZGProof> proofs) {}
}

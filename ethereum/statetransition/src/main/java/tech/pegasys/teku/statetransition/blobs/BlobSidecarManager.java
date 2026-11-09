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

package tech.pegasys.teku.statetransition.blobs;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface BlobSidecarManager extends AvailabilityCheckerFactory<BlobSidecar> {
  BlobSidecarManager NOOP =
      new BlobSidecarManager() {

        @Override
        public SafeFuture<InternalValidationResult> validateAndPrepareForBlockImport(
            final BlobSidecar blobSidecar, final Optional<UInt64> arrivalTimestamp) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public void prepareForBlockImport(
            final BlobSidecar blobSidecar, final RemoteOrigin remoteOrigin) {}

        @Override
        public void subscribeToReceivedBlobSidecar(
            final ReceivedBlobSidecarListener receivedBlobSidecarListener) {}

        @Override
        public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
          return false;
        }

        @Override
        public AvailabilityChecker<BlobSidecar> createAvailabilityChecker(
            final SignedBeaconBlock block) {
          return AvailabilityChecker.NOOP_BLOB_SIDECAR;
        }

        @Override
        public DataAndValidationResult<BlobSidecar> createAvailabilityCheckerAndValidateImmediately(
            final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
          return DataAndValidationResult.notRequired();
        }
      };

  SafeFuture<InternalValidationResult> validateAndPrepareForBlockImport(
      BlobSidecar blobSidecar, Optional<UInt64> arrivalTimestamp);

  void prepareForBlockImport(BlobSidecar blobSidecar, RemoteOrigin origin);

  void subscribeToReceivedBlobSidecar(ReceivedBlobSidecarListener receivedBlobSidecarListener);

  boolean isAvailabilityRequiredAtSlot(UInt64 slot);

  @Override
  AvailabilityChecker<BlobSidecar> createAvailabilityChecker(SignedBeaconBlock block);

  DataAndValidationResult<BlobSidecar> createAvailabilityCheckerAndValidateImmediately(
      SignedBeaconBlock block, List<BlobSidecar> blobSidecars);

  interface ReceivedBlobSidecarListener {
    void onBlobSidecarReceived(BlobSidecar blobSidecar);
  }
}

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

package tech.pegasys.teku.spec.logic.common.statetransition.availability;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

@FunctionalInterface
public interface AvailabilityCheckerFactory<T> {
  AvailabilityCheckerFactory<BlobSidecar> NOOP_BLOB_SIDECAR =
      block -> AvailabilityChecker.NOOP_BLOB_SIDECAR;
  AvailabilityCheckerFactory<UInt64> NOOP_DATACOLUMN_SIDECAR =
      new AvailabilityCheckerFactory<>() {
        @Override
        public AvailabilityChecker<UInt64> createAvailabilityChecker(
            final SignedBeaconBlock block) {
          return AvailabilityChecker.NOOP_DATACOLUMN_SIDECAR;
        }

        @Override
        public AvailabilityChecker<UInt64> createAvailabilityChecker(
            final BeaconState state, final SignedExecutionPayloadEnvelope signedEnvelope) {
          return AvailabilityChecker.NOOP_DATACOLUMN_SIDECAR;
        }
      };

  AvailabilityChecker<T> createAvailabilityChecker(SignedBeaconBlock block);

  default AvailabilityChecker<T> createAvailabilityChecker(
      final BeaconState state, final SignedExecutionPayloadEnvelope signedEnvelope) {
    throw new UnsupportedOperationException(
        "Execution payload envelope availability is not supported");
  }
}

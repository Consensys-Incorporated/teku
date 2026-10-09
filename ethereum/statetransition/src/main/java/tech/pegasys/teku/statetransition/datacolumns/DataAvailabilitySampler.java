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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.BlockEventsListener;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadEventsListener;

public interface DataAvailabilitySampler
    extends BlockEventsListener, ExecutionPayloadEventsListener {

  enum SamplingEligibilityStatus {
    NOT_REQUIRED_OLD_EPOCH,
    NOT_REQUIRED_NO_BLOBS,
    NOT_REQUIRED_BEFORE_FULU,
    REQUIRED
  }

  DataAvailabilitySampler NOOP =
      new DataAvailabilitySampler() {
        @Override
        public SafeFuture<List<UInt64>> checkDataAvailability(
            final UInt64 slot, final Bytes32 blockRoot) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public boolean isDataAvailable(final SignedBeaconBlock block) {
          return true;
        }

        @Override
        public void flush() {}

        @Override
        public SamplingEligibilityStatus checkSamplingEligibility(
            final UInt64 slot, final boolean hasBlobs) {
          return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
        }

        @Override
        public void onNewValidatedDataColumnSidecar(
            final DataColumnSlotAndIdentifier columnId, final RemoteOrigin origin) {}

        @Override
        public void onNewBlock(
            final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {}

        @Override
        public void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {}

        @Override
        public void onExecutionPayloadImported(final SlotAndBlockRoot slotAndBlockRoot) {}

        @Override
        public void enableBlockImportOnCompletion(final SignedBeaconBlock block) {}
      };

  /**
   * Schedules availability check. To initiate all scheduled availability checks immediately call
   * the {@link #flush()} method
   */
  SafeFuture<List<UInt64>> checkDataAvailability(UInt64 slot, Bytes32 blockRoot);

  /** Returns true if data availability has already been confirmed for the given block. */
  boolean isDataAvailable(SignedBeaconBlock block);

  /**
   * Immediately initiates sampling. When doing batch sampling it would be more effective to invoke
   * flush after submitting all requests
   */
  void flush();

  default SamplingEligibilityStatus checkSamplingEligibility(final BeaconBlock block) {
    return checkSamplingEligibility(
        block.getSlot(),
        !block.getBody().getOptionalBlobKzgCommitments().map(SszList::isEmpty).orElse(true));
  }

  SamplingEligibilityStatus checkSamplingEligibility(UInt64 slot, boolean hasBlobs);

  void onNewValidatedDataColumnSidecar(DataColumnSlotAndIdentifier columnId, RemoteOrigin origin);

  default void onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    onNewValidatedDataColumnSidecar(
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar), remoteOrigin);
  }

  @Override
  default void onExecutionPayloadImported(final SlotAndBlockRoot slotAndBlockRoot) {
    removeAllForBlock(slotAndBlockRoot);
  }
}

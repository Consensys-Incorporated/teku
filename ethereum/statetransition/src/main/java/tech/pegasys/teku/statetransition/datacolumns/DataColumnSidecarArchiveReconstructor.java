/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Optional;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public interface DataColumnSidecarArchiveReconstructor
    extends FinalizedCheckpointChannel, SlotEventsChannel {

  DataColumnSidecarArchiveReconstructor NOOP =
      new DataColumnSidecarArchiveReconstructor() {
        @Override
        public int onRequest() {
          return 0;
        }

        @Override
        public SafeFuture<Optional<DataColumnSidecar>> reconstructDataColumnSidecar(
            final SignedBeaconBlock block, final UInt64 index, final int requestId) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public boolean isSidecarPruned(final UInt64 slot, final UInt64 index) {
          return false;
        }

        @Override
        public void onRequestCompleted(final int requestId) {}

        @Override
        public void onNewFinalizedCheckpoint(
            final Checkpoint checkpoint, final boolean fromOptimisticBlock) {}

        @Override
        public void onSlot(final UInt64 slot) {}
      };

  /**
   * Should be called on request start
   *
   * @return assigned request id
   */
  int onRequest();

  SafeFuture<Optional<DataColumnSidecar>> reconstructDataColumnSidecar(
      SignedBeaconBlock block, UInt64 index, int requestId);

  boolean isSidecarPruned(UInt64 slot, UInt64 index);

  void onRequestCompleted(int requestId);
}

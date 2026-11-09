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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Optional;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public interface DataColumnSidecarRecoveringCustody
    extends DataColumnSidecarCustody, SlotEventsChannel {
  DataColumnSidecarRecoveringCustody NOOP =
      new DataColumnSidecarRecoveringCustody() {
        @Override
        public void onSlot(UInt64 slot) {}

        @Override
        public SafeFuture<Void> onNewValidatedDataColumnSidecar(
            final DataColumnSidecar dataColumnSidecar, final RemoteOrigin origin) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
          return AsyncStream.empty();
        }

        @Override
        public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
            DataColumnSlotAndIdentifier columnId) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
            DataColumnSlotAndIdentifier columnId) {
          return SafeFuture.completedFuture(false);
        }

        @Override
        public void onSyncingStatusChanged(boolean inSync) {}

        @Override
        public void subscribeToRecoveredColumnSidecar(
            final ValidDataColumnSidecarsListener subscriber) {}
      };

  void onSyncingStatusChanged(boolean inSync);

  void subscribeToRecoveredColumnSidecar(ValidDataColumnSidecarsListener subscriber);
}

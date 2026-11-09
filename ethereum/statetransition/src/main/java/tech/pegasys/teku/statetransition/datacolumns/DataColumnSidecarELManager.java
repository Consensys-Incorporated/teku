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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public interface DataColumnSidecarELManager extends SlotEventsChannel {

  DataColumnSidecarELManager NOOP =
      new DataColumnSidecarELManager() {
        @Override
        public void onSlot(final UInt64 slot) {}

        @Override
        public void onNewBlock(
            final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {}

        @Override
        public void onNewDataColumnSidecar(
            final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {}

        @Override
        public void onSyncingStatusChanged(boolean inSync) {}

        @Override
        public void subscribeToRecoveredColumnSidecar(ValidDataColumnSidecarsListener subscriber) {}
      };

  void onNewDataColumnSidecar(DataColumnSidecar dataColumnSidecar, RemoteOrigin remoteOrigin);

  void onNewBlock(SignedBeaconBlock block, Optional<RemoteOrigin> remoteOrigin);

  void onSyncingStatusChanged(boolean inSync);

  void subscribeToRecoveredColumnSidecar(ValidDataColumnSidecarsListener subscriber);
}

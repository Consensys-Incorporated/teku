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

package tech.pegasys.teku.statetransition.datacolumns.db;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

interface DataColumnSidecarCoreDB {

  // read

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnSlotAndIdentifier identifier);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getColumnIdentifiers(UInt64 slot);

  default SafeFuture<List<DataColumnSlotAndIdentifier>> getCustodyColumnIdentifiers(
      final UInt64 slot) {
    return getColumnIdentifiers(slot);
  }

  default SafeFuture<List<DataColumnSlotAndIdentifier>> getColumnIdentifiers(
      final SlotAndBlockRoot blockId) {
    return getColumnIdentifiers(blockId.getSlot())
        .thenApply(
            ids ->
                ids.stream().filter(id -> id.blockRoot().equals(blockId.getBlockRoot())).toList());
  }

  // update
  SafeFuture<Void> addSidecar(DataColumnSidecar sidecar);
}

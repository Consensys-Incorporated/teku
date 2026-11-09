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

package tech.pegasys.teku.spec.datastructures.util;

import java.util.Comparator;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public record DataColumnSlotAndIdentifier(UInt64 slot, Bytes32 blockRoot, UInt64 columnIndex)
    implements Comparable<DataColumnSlotAndIdentifier> {

  public DataColumnSlotAndIdentifier(
      final UInt64 slot, final DataColumnIdentifier dataColumnIdentifier) {
    this(slot, dataColumnIdentifier.blockRoot(), dataColumnIdentifier.columnIndex());
  }

  public static DataColumnSlotAndIdentifier fromDataColumn(
      final DataColumnSidecar dataColumnSidecar) {
    return new DataColumnSlotAndIdentifier(
        dataColumnSidecar.getSlot(),
        dataColumnSidecar.getBeaconBlockRoot(),
        dataColumnSidecar.getIndex());
  }

  public static DataColumnSlotAndIdentifier minimalComparableForSlot(final UInt64 slot) {
    return new DataColumnSlotAndIdentifier(slot, Bytes32.ZERO, UInt64.ZERO);
  }

  public DataColumnIdentifier toDataColumnIdentifier() {
    return new DataColumnIdentifier(blockRoot, columnIndex);
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return new SlotAndBlockRoot(slot, blockRoot);
  }

  @Override
  public int compareTo(@NotNull final DataColumnSlotAndIdentifier o) {
    return Comparator.comparing(DataColumnSlotAndIdentifier::slot)
        .thenComparing(DataColumnSlotAndIdentifier::blockRoot)
        .thenComparing(DataColumnSlotAndIdentifier::columnIndex)
        .compare(this, o);
  }
}

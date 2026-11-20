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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import static tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.MAX_BLOCK_ROOT;
import static tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.MIN_BLOCK_ROOT;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.SlotRangeDataFile;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;

/**
 * The keys of a slot-keyed column between two slots, inclusive.
 *
 * @param column the column
 * @param from the first key of the first slot
 * @param to the last key of the last slot
 * @param slotOfKey gets the slot of a key
 * @param <K> the key type of the column
 */
record SlotKeyRange<K>(KvStoreColumn<K, ?> column, K from, K to, Function<K, UInt64> slotOfKey) {

  static SlotKeyRange<UInt64> ofSlots(
      final KvStoreColumn<UInt64, ?> column, final UInt64 startSlot, final UInt64 endSlot) {
    return new SlotKeyRange<>(column, startSlot, endSlot, Function.identity());
  }

  static SlotKeyRange<SlotAndBlockRootAndBlobIndex> ofBlobSidecars(
      final KvStoreColumn<SlotAndBlockRootAndBlobIndex, ?> column,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    return new SlotKeyRange<>(
        column,
        new SlotAndBlockRootAndBlobIndex(startSlot, MIN_BLOCK_ROOT, UInt64.ZERO),
        new SlotAndBlockRootAndBlobIndex(endSlot, MAX_BLOCK_ROOT, UInt64.MAX_VALUE),
        SlotAndBlockRootAndBlobIndex::getSlot);
  }

  static SlotKeyRange<DataColumnSlotAndIdentifier> ofDataColumnSidecars(
      final KvStoreColumn<DataColumnSlotAndIdentifier, ?> column,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    return new SlotKeyRange<>(
        column,
        new DataColumnSlotAndIdentifier(startSlot, MIN_BLOCK_ROOT, UInt64.ZERO),
        new DataColumnSlotAndIdentifier(endSlot, MAX_BLOCK_ROOT, UInt64.MAX_VALUE),
        DataColumnSlotAndIdentifier::slot);
  }

  void compact(final KvStoreAccessor db) {
    db.compactRange(column, from, to);
  }

  OptionalLong countTombstones(final KvStoreAccessor db) {
    return db.countTombstones(column, from, to);
  }

  Optional<List<SlotRangeDataFile>> getDataFiles(final KvStoreAccessor db) {
    return db.getDataFiles(column, from, to)
        .map(
            files ->
                files.stream()
                    .map(
                        file ->
                            new SlotRangeDataFile(
                                slotOfKey.apply(file.smallestKey()),
                                slotOfKey.apply(file.largestKey()),
                                file.sizeBytes()))
                    .toList());
  }
}

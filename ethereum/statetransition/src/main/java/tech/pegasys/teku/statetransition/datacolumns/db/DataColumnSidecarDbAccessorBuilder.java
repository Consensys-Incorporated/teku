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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

public class DataColumnSidecarDbAccessorBuilder {

  // is roughly 600Kb (cache entry for one slot is about 60 bytes)
  private static final int DEFAULT_COLUMN_ID_READ_CACHE_MAX_SLOT_COUNT = 10 * 1024;
  private static final int DEFAULT_COLUMN_ID_WRITE_CACHE_MAX_COUNT = 3 * 128;

  private final DataColumnSidecarDB db;
  private Spec spec;
  private int columnIdReadCacheSlotCount = DEFAULT_COLUMN_ID_READ_CACHE_MAX_SLOT_COUNT;
  private int columnIdWriteCacheCount = DEFAULT_COLUMN_ID_WRITE_CACHE_MAX_COUNT;

  DataColumnSidecarDbAccessorBuilder(final DataColumnSidecarDB db) {
    this.db = db;
  }

  public DataColumnSidecarDbAccessorBuilder spec(final Spec spec) {
    this.spec = spec;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder columnIdCacheSlotCount(
      final int columnIdCacheSlotCount) {
    this.columnIdReadCacheSlotCount = columnIdCacheSlotCount;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder columnIdWriteCacheCount(
      final int columnIdWriteCacheCount) {
    this.columnIdWriteCacheCount = columnIdWriteCacheCount;
    return this;
  }

  private int getNumberOfColumnsForSlot(final UInt64 slot) {
    return spec.atSlot(slot)
        .getConfig()
        .toVersionFulu()
        .map(SpecConfigFulu::getNumberOfColumns)
        .orElse(0);
  }

  public DataColumnSidecarDbAccessor build() {
    final ColumnIdCachingDasDb columnIdCachingDasDb =
        new ColumnIdCachingDasDb(
            db,
            this::getNumberOfColumnsForSlot,
            columnIdReadCacheSlotCount,
            columnIdWriteCacheCount);
    return new DasDb(columnIdCachingDasDb);
  }
}

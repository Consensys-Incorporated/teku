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

package tech.pegasys.teku.storage.server.rocksdb;

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedSnapshotState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

public class RocksDbDatabaseFactory {

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final KvStoreConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {

    final V6SchemaCombinedSnapshot combinedSchema = V6SchemaCombinedSnapshot.createV4(spec);
    final SchemaHotAdapter schemaHot = combinedSchema.asSchemaHot();
    final SchemaFinalizedSnapshotStateAdapter schemaFinalized = combinedSchema.asSchemaFinalized();
    final KvStoreAccessor hotDb =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE_HOT_DB,
            hotConfiguration,
            schemaHot.getAllColumns(),
            schemaHot.getDeletedColumnIds(),
            schemaHot.getAllVariables(),
            schemaHot.getDeletedVariableIds());
    final KvStoreAccessor finalizedDb =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE_FINALIZED_DB,
            finalizedConfiguration,
            schemaFinalized.getAllColumns(),
            schemaFinalized.getDeletedColumnIds(),
            schemaFinalized.getAllVariables(),
            schemaFinalized.getDeletedVariableIds());
    return KvStoreDatabase.createV4(
        metricsSystem,
        hotDb,
        finalizedDb,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createV6Tree(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Spec spec) {

    final V6SchemaCombinedTreeState schema = new V6SchemaCombinedTreeState(spec);
    final KvStoreAccessor db =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE,
            hotConfiguration,
            schema.getAllColumns(),
            schema.getDeletedColumnIds(),
            schema.getAllVariables(),
            schema.getDeletedVariableIds());
    return KvStoreDatabase.createWithStateTree(
        metricsSystem,
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        maxKnownNodeCacheSize,
        spec);
  }

  public static Database createV6(
      final MetricsSystem metricsSystem,
      final KvStoreConfiguration hotConfiguration,
      final SchemaCombinedSnapshotState schema,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {

    final KvStoreAccessor db =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE,
            hotConfiguration,
            schema.getAllColumns(),
            schema.getDeletedColumnIds(),
            schema.getAllVariables(),
            schema.getDeletedVariableIds());

    return KvStoreDatabase.createWithStateSnapshots(
        db,
        schema,
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec,
        metricsSystem);
  }
}

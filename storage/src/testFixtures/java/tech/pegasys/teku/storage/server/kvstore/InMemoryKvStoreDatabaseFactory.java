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

package tech.pegasys.teku.storage.server.kvstore;

import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

public class InMemoryKvStoreDatabaseFactory {
  public static Database createV4(
      final MockKvStoreInstance hotDb,
      final MockKvStoreInstance coldDb,
      final StateStorageMode storageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {

    final V6SchemaCombinedSnapshot combinedSchema = V6SchemaCombinedSnapshot.createV4(spec);
    final SchemaHotAdapter schemaHot = combinedSchema.asSchemaHot();
    final SchemaFinalizedSnapshotStateAdapter schemaFinalized = combinedSchema.asSchemaFinalized();
    return KvStoreDatabase.createV4(
        new StubMetricsSystem(),
        hotDb,
        coldDb,
        schemaHot,
        schemaFinalized,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createV6(
      final MockKvStoreInstance db,
      final StateStorageMode storageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final V6SchemaCombinedSnapshot combinedSchema = V6SchemaCombinedSnapshot.createV6(spec);
    return KvStoreDatabase.createWithStateSnapshots(
        db,
        combinedSchema,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec,
        new StubMetricsSystem());
  }

  public static Database createTree(
      final MockKvStoreInstance db,
      final StateStorageMode storageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final V6SchemaCombinedTreeState schema = new V6SchemaCombinedTreeState(spec);
    return KvStoreDatabase.createWithStateTree(
        new StubMetricsSystem(), db, schema, storageMode, storeNonCanonicalBlocks, 1000, spec);
  }
}

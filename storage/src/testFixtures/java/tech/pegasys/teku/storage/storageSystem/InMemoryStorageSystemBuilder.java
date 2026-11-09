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

package tech.pegasys.teku.storage.storageSystem;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.InMemoryKvStoreDatabaseFactory;
import tech.pegasys.teku.storage.server.kvstore.MockKvStoreInstance;
import tech.pegasys.teku.storage.server.kvstore.schema.Schema;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;
import tech.pegasys.teku.storage.store.StoreConfig;

public class InMemoryStorageSystemBuilder {
  // Optional
  private DatabaseVersion version = DatabaseVersion.DEFAULT_VERSION;
  private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
  private StoreConfig storeConfig = StoreConfig.createDefault();
  private int numberOfValidators = 8;
  private long stateStorageFrequency = 1L;
  private boolean storeNonCanonicalBlocks = false;

  private int stateRebuildTimeoutSeconds = 120;

  private Spec spec = TestSpecFactory.createMinimalPhase0();

  // Internal variables
  MockKvStoreInstance unifiedDb;
  private MockKvStoreInstance hotDb;
  private MockKvStoreInstance coldDb;

  private InMemoryStorageSystemBuilder() {}

  public static InMemoryStorageSystemBuilder create() {
    return new InMemoryStorageSystemBuilder();
  }

  public static StorageSystem buildDefault() {
    return create().build();
  }

  public static StorageSystem buildDefault(final StateStorageMode storageMode) {
    return create().storageMode(storageMode).build();
  }

  public static StorageSystem buildDefault(final StateStorageMode storageMode, final Spec spec) {
    return create().storageMode(storageMode).specProvider(spec).build();
  }

  public static StorageSystem buildDefault(final int numberOfValidators, final Spec spec) {
    return create().numberOfValidators(numberOfValidators).specProvider(spec).build();
  }

  public static StorageSystem buildDefault(final Spec spec) {
    return create().specProvider(spec).build();
  }

  public StorageSystem build() {
    final Database database =
        switch (version) {
          case LEVELDB_TREE, ROCKSDB_TREE ->
              createTreeDatabase(); // Tree only varies by db type which doesn't apply to
          // in-memory
          case LEVELDB2, V6 ->
              createV6Database(); // Leveldb only varies by db type which doesn't apply to in-memory
          case LEVELDB1, V5 -> createV5Database();
          case V4 -> createV4Database();
          default ->
              throw new UnsupportedOperationException("Unsupported database version: " + version);
        };

    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numberOfValidators);
    return StorageSystem.create(
        database,
        createRestartSupplier(),
        storageMode,
        storeConfig,
        spec,
        ChainBuilder.create(spec, validatorKeys),
        stateRebuildTimeoutSeconds);
  }

  public InMemoryStorageSystemBuilder specProvider(final Spec spec) {
    this.spec = spec;
    return this;
  }

  private InMemoryStorageSystemBuilder copy() {
    final InMemoryStorageSystemBuilder copy =
        create()
            .version(version)
            .storageMode(storageMode)
            .stateStorageFrequency(stateStorageFrequency)
            .storeConfig(storeConfig)
            .numberOfValidators(numberOfValidators);

    copy.unifiedDb = unifiedDb;
    copy.hotDb = hotDb;
    copy.coldDb = coldDb;
    copy.spec = spec;

    return copy;
  }

  public InMemoryStorageSystemBuilder version(final DatabaseVersion version) {
    checkNotNull(version);
    this.version = version;
    return this;
  }

  public InMemoryStorageSystemBuilder storeNonCanonicalBlocks(
      final boolean storeNonCanonicalBlocks) {
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    return this;
  }

  public InMemoryStorageSystemBuilder storageMode(final StateStorageMode storageMode) {
    checkNotNull(storageMode);
    this.storageMode = storageMode;
    return this;
  }

  public InMemoryStorageSystemBuilder stateStorageFrequency(final long stateStorageFrequency) {
    this.stateStorageFrequency = stateStorageFrequency;
    return this;
  }

  public InMemoryStorageSystemBuilder stateRebuildTimeoutSeconds(
      final int stateRebuildTimeoutSeconds) {
    this.stateRebuildTimeoutSeconds = stateRebuildTimeoutSeconds;
    return this;
  }

  public InMemoryStorageSystemBuilder storeConfig(final StoreConfig storeConfig) {
    checkNotNull(storeConfig);
    this.storeConfig = storeConfig;
    return this;
  }

  public InMemoryStorageSystemBuilder numberOfValidators(final int numberOfValidators) {
    this.numberOfValidators = numberOfValidators;
    return this;
  }

  private StorageSystem.RestartedStorageSupplier createRestartSupplier() {
    return (mode) -> {
      final InMemoryStorageSystemBuilder copy = copy().storageMode(mode);
      copy.reopenDatabases();
      return copy.build();
    };
  }

  private Database createTreeDatabase() {
    if (hotDb == null) {
      final V6SchemaCombinedTreeState schema = new V6SchemaCombinedTreeState(spec);
      hotDb = MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());
    }
    return InMemoryKvStoreDatabaseFactory.createTree(
        hotDb, storageMode, storeNonCanonicalBlocks, spec);
  }

  private Database createV6Database() {
    if (hotDb == null) {
      final V6SchemaCombinedSnapshot schema = V6SchemaCombinedSnapshot.createV6(spec);
      hotDb = MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());
    }
    return InMemoryKvStoreDatabaseFactory.createV6(
        hotDb, storageMode, stateStorageFrequency, storeNonCanonicalBlocks, spec);
  }

  // V5 only differs by the RocksDB configuration which doesn't apply to the in-memory version
  private Database createV5Database() {
    return createV4Database();
  }

  private Database createV4Database() {
    final V6SchemaCombinedSnapshot combinedSchema = V6SchemaCombinedSnapshot.createV4(spec);
    if (hotDb == null) {
      final Schema v4SchemaHot = combinedSchema.asSchemaHot();
      hotDb =
          MockKvStoreInstance.createEmpty(
              v4SchemaHot.getAllColumns(), v4SchemaHot.getAllVariables());
    }
    if (coldDb == null) {
      final SchemaFinalizedSnapshotStateAdapter v4SchemaFinalized =
          combinedSchema.asSchemaFinalized();
      coldDb =
          MockKvStoreInstance.createEmpty(
              v4SchemaFinalized.getAllColumns(), v4SchemaFinalized.getAllVariables());
    }
    return InMemoryKvStoreDatabaseFactory.createV4(
        hotDb, coldDb, storageMode, stateStorageFrequency, storeNonCanonicalBlocks, spec);
  }

  private void reopenDatabases() {
    if (hotDb != null) {
      hotDb = hotDb.reopen();
    }
    if (coldDb != null) {
      coldDb = coldDb.reopen();
    }
    if (unifiedDb != null) {
      unifiedDb = unifiedDb.reopen();
    }
  }
}

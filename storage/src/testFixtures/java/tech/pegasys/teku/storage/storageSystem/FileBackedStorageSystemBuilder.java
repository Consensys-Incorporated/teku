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
import static com.google.common.base.Preconditions.checkState;

import java.nio.file.Path;
import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.leveldb.LevelDbDatabaseFactory;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabaseFactory;
import tech.pegasys.teku.storage.store.StoreConfig;

public class FileBackedStorageSystemBuilder {
  // Optional
  private DatabaseVersion version = DatabaseVersion.DEFAULT_VERSION;
  private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
  private StoreConfig storeConfig = StoreConfig.createDefault();
  private int numberOfValidators = 8;
  private Spec spec;

  // Version-dependent fields
  private Path dataDir;
  private Path hotDir;
  private Path archiveDir;
  private long stateStorageFrequency = 1L;
  private boolean storeNonCanonicalBlocks = false;

  private int stateRebuildTimeoutSeconds = 120;

  private FileBackedStorageSystemBuilder() {}

  public static FileBackedStorageSystemBuilder create() {
    return new FileBackedStorageSystemBuilder();
  }

  public StorageSystem build() {
    final Database database = buildDatabase();

    validate();

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

  private Database buildDatabase() {
    return switch (version) {
      case ROCKSDB_TREE -> createRocksDbTreeDatabase();
      case LEVELDB_TREE -> createLevelDbTrieDatabase();
      case LEVELDB2 -> createLevelDb2Database();
      case LEVELDB1 -> createLevelDb1Database();
      case V6 -> createV6Database();
      case V5 -> createV5Database();
      case V4 -> createV4Database();
      default ->
          throw new UnsupportedOperationException("Unsupported database version: " + version);
    };
  }

  private FileBackedStorageSystemBuilder copy() {
    return create()
        .specProvider(spec)
        .version(version)
        .dataDir(dataDir)
        .storageMode(storageMode)
        .stateStorageFrequency(stateStorageFrequency)
        .storeConfig(storeConfig)
        .numberOfValidators(numberOfValidators);
  }

  private void validate() {
    checkNotNull(spec, "Must specify spec");
    checkState(dataDir != null);
  }

  public FileBackedStorageSystemBuilder version(final DatabaseVersion version) {
    checkNotNull(version);
    this.version = version;
    return this;
  }

  public FileBackedStorageSystemBuilder storeNonCanonicalBlocks(
      final boolean storeNonCanonicalBlocks) {
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    return this;
  }

  public FileBackedStorageSystemBuilder specProvider(final Spec spec) {
    this.spec = spec;
    return this;
  }

  public FileBackedStorageSystemBuilder dataDir(final Path dataDir) {
    checkNotNull(dataDir);
    this.dataDir = dataDir;
    this.hotDir = dataDir.resolve("hot");
    this.archiveDir = dataDir.resolve("archive");
    return this;
  }

  public FileBackedStorageSystemBuilder storageMode(final StateStorageMode storageMode) {
    checkNotNull(storageMode);
    this.storageMode = storageMode;
    return this;
  }

  public FileBackedStorageSystemBuilder stateRebuildTimeoutSeconds(
      final int stateRebuildTimeoutSeconds) {
    this.stateRebuildTimeoutSeconds = stateRebuildTimeoutSeconds;
    return this;
  }

  public FileBackedStorageSystemBuilder stateStorageFrequency(final long stateStorageFrequency) {
    this.stateStorageFrequency = stateStorageFrequency;
    return this;
  }

  public FileBackedStorageSystemBuilder storeConfig(final StoreConfig storeConfig) {
    checkNotNull(storeConfig);
    this.storeConfig = storeConfig;
    return this;
  }

  public FileBackedStorageSystemBuilder numberOfValidators(final int numberOfValidators) {
    this.numberOfValidators = numberOfValidators;
    return this;
  }

  private StorageSystem.RestartedStorageSupplier createRestartSupplier() {
    return (mode) -> copy().storageMode(mode).build();
  }

  private Database createLevelDb1Database() {
    return LevelDbDatabaseFactory.createLevelDb(
        new StubMetricsSystem(),
        KvStoreConfiguration.v5HotDefaults().withDatabaseDir(hotDir),
        KvStoreConfiguration.v5ArchiveDefaults().withDatabaseDir(archiveDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  private Database createV6Database() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();

    final V6SchemaCombinedSnapshot schema = V6SchemaCombinedSnapshot.createV6(spec);
    return RocksDbDatabaseFactory.createV6(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        schema,
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  private Database createLevelDb2Database() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();
    return LevelDbDatabaseFactory.createLevelDbV2(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  private Database createRocksDbTreeDatabase() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();
    return RocksDbDatabaseFactory.createV6Tree(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        storageMode,
        storeNonCanonicalBlocks,
        10_000,
        spec);
  }

  private Database createLevelDbTrieDatabase() {
    KvStoreConfiguration configDefault = KvStoreConfiguration.v6SingleDefaults();
    return LevelDbDatabaseFactory.createLevelDbTree(
        new StubMetricsSystem(),
        configDefault.withDatabaseDir(hotDir),
        storageMode,
        storeNonCanonicalBlocks,
        10_000,
        spec);
  }

  private Database createV5Database() {
    return RocksDbDatabaseFactory.createV4(
        new StubMetricsSystem(),
        KvStoreConfiguration.v5HotDefaults().withDatabaseDir(hotDir),
        KvStoreConfiguration.v5ArchiveDefaults().withDatabaseDir(archiveDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  private Database createV4Database() {
    return RocksDbDatabaseFactory.createV4(
        new StubMetricsSystem(),
        KvStoreConfiguration.v4Settings(hotDir),
        KvStoreConfiguration.v4Settings(archiveDir),
        storageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }
}

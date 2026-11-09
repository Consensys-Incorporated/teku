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

package tech.pegasys.teku.storage.server;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.leveldb.LevelDbDatabaseFactory;
import tech.pegasys.teku.storage.server.metadata.V5DatabaseMetadata;
import tech.pegasys.teku.storage.server.metadata.V6DatabaseMetadata;
import tech.pegasys.teku.storage.server.network.DatabaseNetwork;
import tech.pegasys.teku.storage.server.noop.NoOpDatabase;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabaseFactory;

public class VersionedDatabaseFactory implements DatabaseFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static final String DB_PATH = "db";
  public static final String ARCHIVE_PATH = "archive";
  public static final String DB_VERSION_FILENAME = "db.version";

  public static final String STORAGE_MODE_FILENAME = "data-storage-mode.txt";
  public static final String BLOB_DB_MODE_FILENAME = "blob-db-mode.txt";
  public static final String METADATA_FILENAME = "metadata.yml";
  public static final String NETWORK_FILENAME = "network.yml";

  public static final String SLASHING_PROTECTION_PATH = "slashprotection";
  private final MetricsSystem metricsSystem;
  private final File dataDirectory;
  private final int maxKnownNodeCacheSize;
  private final File dbDirectory;
  private final File v5ArchiveDirectory;
  private final File dbVersionFile;
  private final File dbStorageModeFile;
  private final File dbBlobDbModeFile;
  private final StateStorageMode stateStorageMode;
  private final DatabaseVersion createDatabaseVersion;
  private final long stateStorageFrequency;
  private final Eth1Address eth1Address;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;
  private final boolean rocksdbBlobDbEnabled;
  private final SyncDataAccessor dbSettingFileSyncDataAccessor;
  private final Optional<Eth2Network> maybeNetwork;

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final Path dataPath,
      final StorageConfiguration config,
      final Optional<Eth2Network> maybeNetwork) {

    this.metricsSystem = metricsSystem;
    this.dataDirectory = dataPath.toFile();
    this.maybeNetwork = maybeNetwork;

    this.createDatabaseVersion = config.getDataStorageCreateDbVersion();
    this.maxKnownNodeCacheSize = config.getMaxKnownNodeCacheSize();
    this.stateStorageFrequency = config.getDataStorageFrequency();
    this.eth1Address = config.getEth1DepositContract();
    this.storeNonCanonicalBlocks = config.isStoreNonCanonicalBlocksEnabled();
    this.rocksdbBlobDbEnabled = config.isRocksdbBlobDbEnabled();
    this.spec = config.getSpec();

    this.dbDirectory = this.dataDirectory.toPath().resolve(DB_PATH).toFile();
    this.v5ArchiveDirectory = this.dataDirectory.toPath().resolve(ARCHIVE_PATH).toFile();
    this.dbVersionFile = this.dataDirectory.toPath().resolve(DB_VERSION_FILENAME).toFile();
    this.dbStorageModeFile = this.dataDirectory.toPath().resolve(STORAGE_MODE_FILENAME).toFile();
    this.dbBlobDbModeFile = this.dataDirectory.toPath().resolve(BLOB_DB_MODE_FILENAME).toFile();

    dbSettingFileSyncDataAccessor = SyncDataAccessor.create(dataDirectory.toPath());
    this.stateStorageMode = config.getDataStorageMode();
  }

  @Override
  public Database createDatabase() {
    LOG.info("Beacon data directory set to: {}", dataDirectory.getAbsolutePath());
    validateDataPaths();
    final DatabaseVersion dbVersion = getDatabaseVersion();
    createDirectories(dbVersion);
    saveDatabaseVersion(dbVersion);
    saveStorageMode(stateStorageMode);

    Database database;
    switch (dbVersion) {
      case NOOP -> {
        database = new NoOpDatabase();
        LOG.info("Created no-op database");
      }
      case V4 -> {
        database = createV4Database();
        LOG.info(
            "Created RocksDB V4 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        LOG.info(
            "Created RocksDB V4 Finalized database ({}) at {}",
            dbVersion.getValue(),
            v5ArchiveDirectory.getAbsolutePath());
      }
      case V5 -> {
        database = createV5Database();
        LOG.info(
            "Created RocksDB V5 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        LOG.info(
            "Created RocksDB V5 Finalized database ({}) at {}",
            dbVersion.getValue(),
            v5ArchiveDirectory.getAbsolutePath());
      }
      case V6 -> {
        database = createV6Database();
        LOG.info(
            "Created RocksDB V6 Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
      }
      case LEVELDB1 -> {
        database = createLevelDbV1Database();
        LOG.info(
            "Created leveldb1 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        warnLevelDbDeprecation();
        LOG.info(
            "Created leveldb1 Finalized database ({}) at {}",
            dbVersion.getValue(),
            v5ArchiveDirectory.getAbsolutePath());
      }
      case LEVELDB2 -> {
        database = createLevelDbV2Database();
        warnLevelDbDeprecation();
        LOG.info(
            "Created leveldb2 Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
      }
      case ROCKSDB_TREE -> {
        warnArchiveFrequencyIgnoredForTreeDatabase();
        database = createRocksDbTreeDatabase();
        LOG.info(
            "Created RocksDB tree Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
      }
      case LEVELDB_TREE -> {
        warnLevelDbDeprecation();
        warnArchiveFrequencyIgnoredForTreeDatabase();
        database = createLevelDbTreeDatabase();
        LOG.info(
            "Created leveldb_tree Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
      }
      default -> throw new UnsupportedOperationException("Unhandled database version " + dbVersion);
    }
    initDatabaseVersionMetrics(metricsSystem, dbVersion, stateStorageMode);

    return database;
  }

  private void warnLevelDbDeprecation() {
    LOG.warn(
        "NOTE: Leveldb support has been deprecated and may be removed in a future release. Please refer to https://docs.teku.consensys.io/how-to/migrate-database to migrate.");
  }

  private void warnArchiveFrequencyIgnoredForTreeDatabase() {
    if (stateStorageFrequency != StorageConfiguration.DEFAULT_STORAGE_FREQUENCY) {
      LOG.warn(
          "--data-storage-archive-frequency ({}) has no effect on tree database formats. "
              + "States are stored at the hot-state-persistence rate (approximately one per epoch). "
              + "Use --Xhot-state-persistence-frequency to control storage frequency.",
          stateStorageFrequency);
    }
  }

  public StateStorageMode getStateStorageMode() {
    return stateStorageMode;
  }

  private Database createV4Database() {
    try {
      final boolean blobDbEnabled = resolveAndPersistBlobDbMode();
      DatabaseNetwork.init(
          getNetworkFile(),
          spec.getGenesisSpecConfig().getGenesisForkVersion(),
          eth1Address,
          spec.getGenesisSpecConfig().getDepositChainId(),
          maybeNetwork);
      return RocksDbDatabaseFactory.createV4(
          metricsSystem,
          KvStoreConfiguration.v4Settings(dbDirectory.toPath()).withBlobDbEnabled(blobDbEnabled),
          KvStoreConfiguration.v4Settings(v5ArchiveDirectory.toPath())
              .withBlobDbEnabled(blobDbEnabled),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read configuration file", e);
    }
  }

  /**
   * V5 database is identical to V4 except for the RocksDB configuration
   *
   * @return the created database
   */
  private Database createV5Database() {
    try {
      final boolean blobDbEnabled = resolveAndPersistBlobDbMode();
      final V5DatabaseMetadata metaData =
          V5DatabaseMetadata.init(getMetadataFile(), V5DatabaseMetadata.v5Defaults());
      DatabaseNetwork.init(
          getNetworkFile(),
          spec.getGenesisSpecConfig().getGenesisForkVersion(),
          eth1Address,
          spec.getGenesisSpecConfig().getDepositChainId(),
          maybeNetwork);
      return RocksDbDatabaseFactory.createV4(
          metricsSystem,
          metaData
              .getHotDbConfiguration()
              .withDatabaseDir(dbDirectory.toPath())
              .withBlobDbEnabled(blobDbEnabled),
          metaData
              .getArchiveDbConfiguration()
              .withDatabaseDir(v5ArchiveDirectory.toPath())
              .withBlobDbEnabled(blobDbEnabled),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createV6Database() {
    try {
      final boolean blobDbEnabled = resolveAndPersistBlobDbMode();
      final KvStoreConfiguration dbConfiguration = initV6Configuration();

      final V6SchemaCombinedSnapshot schema = V6SchemaCombinedSnapshot.createV6(spec);
      return RocksDbDatabaseFactory.createV6(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()).withBlobDbEnabled(blobDbEnabled),
          schema,
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  /**
   * LevelDb database uses same setup as v4 but backed by leveldb instead of RocksDb
   *
   * @return the created database
   */
  private Database createLevelDbV1Database() {
    try {
      final V5DatabaseMetadata metaData =
          V5DatabaseMetadata.init(getMetadataFile(), V5DatabaseMetadata.v5Defaults());
      DatabaseNetwork.init(
          getNetworkFile(),
          spec.getGenesisSpecConfig().getGenesisForkVersion(),
          eth1Address,
          spec.getGenesisSpecConfig().getDepositChainId(),
          maybeNetwork);
      return LevelDbDatabaseFactory.createLevelDb(
          metricsSystem,
          metaData.getHotDbConfiguration().withDatabaseDir(dbDirectory.toPath()),
          metaData.getArchiveDbConfiguration().withDatabaseDir(v5ArchiveDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createLevelDbV2Database() {
    try {
      final KvStoreConfiguration dbConfiguration = initV6Configuration();

      return LevelDbDatabaseFactory.createLevelDbV2(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createRocksDbTreeDatabase() {
    try {
      final boolean blobDbEnabled = resolveAndPersistBlobDbMode();
      final KvStoreConfiguration dbConfiguration = initV6Configuration();
      return RocksDbDatabaseFactory.createV6Tree(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()).withBlobDbEnabled(blobDbEnabled),
          stateStorageMode,
          storeNonCanonicalBlocks,
          maxKnownNodeCacheSize,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createLevelDbTreeDatabase() {
    try {
      final KvStoreConfiguration dbConfiguration = initV6Configuration();

      return LevelDbDatabaseFactory.createLevelDbTree(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()),
          stateStorageMode,
          storeNonCanonicalBlocks,
          maxKnownNodeCacheSize,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private KvStoreConfiguration initV6Configuration() throws IOException {
    final V6DatabaseMetadata metaData =
        V6DatabaseMetadata.init(getMetadataFile(), V6DatabaseMetadata.singleDBDefault());

    DatabaseNetwork.init(
        getNetworkFile(),
        spec.getGenesisSpecConfig().getGenesisForkVersion(),
        eth1Address,
        spec.getGenesisSpecConfig().getDepositChainId(),
        maybeNetwork);

    return metaData.getSingleDbConfiguration().getConfiguration();
  }

  private File getMetadataFile() {
    return dataDirectory.toPath().resolve(METADATA_FILENAME).toFile();
  }

  private File getNetworkFile() {
    return dataDirectory.toPath().resolve(NETWORK_FILENAME).toFile();
  }

  private void validateDataPaths() {
    if (dbDirectory.exists() && !dbVersionFile.exists()) {
      throw DatabaseStorageException.unrecoverable(
          String.format(
              "No database version file was found, and the database path %s exists.",
              dataDirectory.getAbsolutePath()));
    }
  }

  private void createDirectories(final DatabaseVersion dbVersion) {
    if (!dbDirectory.mkdirs() && !dbDirectory.isDirectory()) {
      throw DatabaseStorageException.unrecoverable(
          String.format(
              "Unable to create the path to store database files at %s",
              dbDirectory.getAbsolutePath()));
    }

    switch (dbVersion) {
      case V4, V5, LEVELDB1 -> {
        if (!v5ArchiveDirectory.mkdirs() && !v5ArchiveDirectory.isDirectory()) {
          throw DatabaseStorageException.unrecoverable(
              String.format(
                  "Unable to create the path to store archive files at %s",
                  v5ArchiveDirectory.getAbsolutePath()));
        }
      }
      default -> {
        // do nothing
      }
    }
  }

  @VisibleForTesting
  DatabaseVersion getDatabaseVersion() {
    if (dbVersionFile.exists()) {
      try {
        final String versionValue = Files.readString(dbVersionFile.toPath()).trim();
        return DatabaseVersion.fromString(versionValue)
            .orElseThrow(
                () ->
                    DatabaseStorageException.unrecoverable(
                        "Unrecognized database version: " + versionValue));
      } catch (IOException e) {
        throw DatabaseStorageException.unrecoverable(
            String.format(
                "Unable to read database version from file %s", dbVersionFile.getAbsolutePath()),
            e);
      }
    }
    return this.createDatabaseVersion;
  }

  private void saveDatabaseVersion(final DatabaseVersion version) {
    if (!dbVersionFile.exists()) {
      try {
        Files.writeString(dbVersionFile.toPath(), version.getValue(), StandardOpenOption.CREATE);
      } catch (IOException e) {
        throw DatabaseStorageException.unrecoverable(
            "Failed to write database version to file " + dbVersionFile.getAbsolutePath(), e);
      }
    }
  }

  private void saveStorageMode(final StateStorageMode storageMode) {
    try {
      dbSettingFileSyncDataAccessor.syncedWrite(
          dbStorageModeFile.toPath(),
          Bytes.of(storageMode.name().getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw DatabaseStorageException.unrecoverable(
          "Failed to write database storage mode to file " + dbStorageModeFile.getAbsolutePath(),
          e);
    }
  }

  /**
   * Determines whether the RocksDB BlobDB feature should be used for static-data columns and
   * persists that decision so it remains fixed for the lifetime of the database.
   *
   * <p>BlobDB may only be enabled when creating a fresh database. An already populated database
   * must keep persisting those columns as SST so the on-disk format never changes underneath it.
   * Whether a real database already exists is determined by the presence of RocksDB's {@code
   * CURRENT} file in the database directory rather than by side files such as {@code db.version},
   * so that wiping the database directory (while leaving metadata behind) is correctly treated as a
   * fresh start.
   *
   * <ul>
   *   <li>Existing database with a recorded mode: the recorded value is authoritative (the mode was
   *       fixed when the database was created) and the configured flag is ignored.
   *   <li>Existing database without a recorded mode: it predates this tracking, so BlobDB stays
   *       disabled (SST).
   *   <li>Fresh database: the configured flag is honoured, replacing any stale recorded mode left
   *       behind by a previous database in the same directory.
   * </ul>
   *
   * @return the effective BlobDB mode to apply
   */
  private boolean resolveAndPersistBlobDbMode() {
    final boolean databaseExists = rocksDbDataExists();
    final boolean blobDbEnabled =
        databaseExists ? readBlobDbMode().orElse(false) : rocksdbBlobDbEnabled;
    saveBlobDbMode(blobDbEnabled);
    LOG.info(
        "RocksDB BlobDB for static data columns is {} for this database",
        blobDbEnabled ? "enabled" : "disabled");
    return blobDbEnabled;
  }

  /**
   * @return whether a RocksDB database already exists, detected via the presence of RocksDB's
   *     {@code CURRENT} pointer file. The archive directory is also checked so that V4/V5 databases
   *     (which use a separate hot and archive RocksDB) are still treated as existing if only the
   *     hot directory was wiped. For V6 the archive directory is never created, so that check is a
   *     no-op.
   */
  private boolean rocksDbDataExists() {
    return rocksDbDataExists(dbDirectory) || rocksDbDataExists(v5ArchiveDirectory);
  }

  private static boolean rocksDbDataExists(final File directory) {
    return directory.toPath().resolve("CURRENT").toFile().exists();
  }

  private Optional<Boolean> readBlobDbMode() {
    try {
      return dbSettingFileSyncDataAccessor
          .read(dbBlobDbModeFile.toPath())
          .map(bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8).trim())
          .map(this::parseBlobDbMode);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable(
          "Failed to read blob db mode from file " + dbBlobDbModeFile.getAbsolutePath(), e);
    }
  }

  private boolean parseBlobDbMode(final String value) {
    // This marker is authoritative for an existing database, so reject anything we didn't write
    // rather than silently defaulting (which could flip an existing BlobDB database to SST).
    if ("true".equals(value)) {
      return true;
    }
    if ("false".equals(value)) {
      return false;
    }
    throw DatabaseStorageException.unrecoverable(
        String.format(
            "Unrecognized blob db mode '%s' in file %s",
            value, dbBlobDbModeFile.getAbsolutePath()));
  }

  private void saveBlobDbMode(final boolean blobDbEnabled) {
    try {
      dbSettingFileSyncDataAccessor.syncedWrite(
          dbBlobDbModeFile.toPath(),
          Bytes.of(Boolean.toString(blobDbEnabled).getBytes(StandardCharsets.UTF_8)));
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable(
          "Failed to write blob db mode to file " + dbBlobDbModeFile.getAbsolutePath(), e);
    }
  }

  private void initDatabaseVersionMetrics(
      final MetricsSystem metricsSystem,
      final DatabaseVersion dbVersion,
      final StateStorageMode storageMode) {
    final String version = dbVersion.getValue() + "_" + storageMode.name();
    final LabelledMetric<Counter> versionCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            VersionProvider.CLIENT_IDENTITY + "_db_version_total",
            "Teku DB version and storage mode",
            "version");
    versionCounter.labels(version).inc();
  }
}

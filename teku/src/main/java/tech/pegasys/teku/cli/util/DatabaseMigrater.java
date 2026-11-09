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

package tech.pegasys.teku.cli.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase;

public class DatabaseMigrater {
  private final DataDirLayout dataDirLayout;
  private final Consumer<String> statusUpdater;
  private final int batchSize;
  private final Spec spec;
  private final String network;
  private final StateStorageMode storageMode;
  final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
  private KvStoreDatabase originalDatabase;

  KvStoreDatabase getOriginalDatabase() {
    return originalDatabase;
  }

  KvStoreDatabase getNewDatabase() {
    return newDatabase;
  }

  private KvStoreDatabase newDatabase;

  private DatabaseMigrater(
      final DataDirLayout dataDirLayout,
      final String network,
      final StateStorageMode storageMode,
      final Spec spec,
      final int batchSize,
      final Consumer<String> statusUpdater) {
    this.dataDirLayout = dataDirLayout;
    this.network = network;
    this.storageMode = storageMode;
    this.spec = spec;
    this.batchSize = batchSize;
    this.statusUpdater = statusUpdater;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void migrateDatabase(
      final DatabaseVersion sourceDatabaseVersion, final DatabaseVersion targetDatabaseVersion)
      throws DatabaseMigraterError {
    try {
      duplicateBeaconFolderContents();
    } catch (IOException ex) {
      throw new DatabaseMigraterError(
          "Failed to create new database structure: " + ex.getMessage());
    }

    openDatabases(sourceDatabaseVersion, targetDatabaseVersion);

    statusUpdater.accept("Migrating data to the new database");
    migrateData();

    closeDatabases();
    statusUpdater.accept("Swapping new database to be active");
    swapActiveDatabase();
  }

  @VisibleForTesting
  void openDatabases(
      final DatabaseVersion sourceDatabaseVersion, final DatabaseVersion targetDatabaseVersion)
      throws DatabaseMigraterError {
    final Path newDatabasePath = getNewBeaconFolderPath();
    final Path originalDatabasePath = dataDirLayout.getBeaconDataDirectory();

    statusUpdater.accept("Opening original database...");
    originalDatabase = createDatabase(originalDatabasePath, sourceDatabaseVersion);
    statusUpdater.accept("Creating a new database...");
    newDatabase = createDatabase(newDatabasePath, targetDatabaseVersion);
  }

  @VisibleForTesting
  void swapActiveDatabase() throws DatabaseMigraterError {
    try {
      Files.move(dataDirLayout.getBeaconDataDirectory(), getMovedOldBeaconFolderPath());
    } catch (IOException ex) {
      statusUpdater.accept(ex.getMessage());
      throw new DatabaseMigraterError(
          "Failed to move old database to " + getMovedOldBeaconFolderPath().toString());
    }
    try {
      Files.move(getNewBeaconFolderPath(), dataDirLayout.getBeaconDataDirectory());
    } catch (IOException ex) {
      statusUpdater.accept(ex.getMessage());
      throw new DatabaseMigraterError(
          "Failed to move new database to " + dataDirLayout.getBeaconDataDirectory().toString());
    }
  }

  @VisibleForTesting
  void closeDatabases() {
    try {
      originalDatabase.close();
    } catch (Exception e) {
      statusUpdater.accept("Failed to close original database cleanly: " + e.getMessage());
    }
    try {
      newDatabase.close();
    } catch (Exception e) {
      statusUpdater.accept("Failed to close new database cleanly: " + e.getMessage());
    }
  }

  @VisibleForTesting
  void duplicateBeaconFolderContents() throws IOException {
    final Path newBeaconFolderPath = getNewBeaconFolderPath();
    // any 'beacon.new' folder that already exists is from an incomplete
    // migrate-data command, so we can just remove it and re-initialise
    // If the process had finished, it would have been moved to 'beacon'
    if (newBeaconFolderPath.toFile().isDirectory()) {
      FileUtils.deleteDirectory(newBeaconFolderPath.toFile());
    }
    newBeaconFolderPath.toFile().mkdir();
    for (String currentEntry : List.of("network.yml", "kvstore")) {
      Path currentPath = dataDirLayout.getBeaconDataDirectory().resolve(currentEntry);
      if (Files.exists(currentPath)) {
        if (currentPath.toFile().isDirectory()) {
          FileUtils.copyDirectory(
              currentPath.toFile(), newBeaconFolderPath.resolve(currentEntry).toFile());
        } else {
          Files.copy(currentPath, newBeaconFolderPath.resolve(currentEntry));
        }
      }
    }
  }

  @VisibleForTesting
  void migrateData() throws DatabaseMigraterError {
    try {
      newDatabase.ingestDatabase(originalDatabase, batchSize, statusUpdater);
    } catch (Exception ex) {
      throw new DatabaseMigraterError(
          "Failed to migrate data into the new database: " + ex.getCause(), ex);
    }
  }

  @VisibleForTesting
  KvStoreDatabase createDatabase(final Path databasePath, final DatabaseVersion databaseVersion)
      throws DatabaseMigraterError {
    final Eth2NetworkConfiguration config = Eth2NetworkConfiguration.builder(network).build();
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            databasePath,
            StorageConfiguration.builder()
                .dataStorageMode(storageMode)
                .specProvider(spec)
                .storeNonCanonicalBlocks(true)
                .eth1DepositContract(config.getEth1DepositContractAddress())
                .dataStorageCreateDbVersion(databaseVersion)
                .build(),
            Optional.empty());
    final Database database = databaseFactory.createDatabase();
    if (!(database instanceof KvStoreDatabase)) {
      throw new DatabaseMigraterError(
          "Expected the database at "
              + databasePath.toFile()
              + " to be a KV store, but it was not, not able to migrate data.");
    }
    return (KvStoreDatabase) database;
  }

  public Path getMovedOldBeaconFolderPath() {
    return dataDirLayout.getBeaconDataDirectory().getParent().resolve("beacon.old");
  }

  public Path getNewBeaconFolderPath() {
    return dataDirLayout.getBeaconDataDirectory().getParent().resolve("beacon.new");
  }

  public static class Builder {
    private int batchSize = 500;
    private DataDirLayout dataDirLayout;
    private Consumer<String> statusUpdater;
    private String network;
    private StateStorageMode storageMode = StateStorageMode.ARCHIVE;
    private Spec spec;

    public Builder dataOptions(final ValidatorClientDataOptions dataOptions) {
      if (dataDirLayout == null) {
        dataDirLayout = DataDirLayout.createFrom(dataOptions.getDataConfig());
      }
      return this;
    }

    public Builder batchSize(final int batchSize) {
      if (batchSize < 0) {
        throw new InvalidConfigurationException(String.format("Invalid batchSize: %d", batchSize));
      }
      this.batchSize = batchSize;
      return this;
    }

    public Builder dataDirLayout(final DataDirLayout dataDirLayout) {
      this.dataDirLayout = dataDirLayout;
      return this;
    }

    public Builder network(final String network) {
      this.network = network;
      if (spec == null) {
        spec = Eth2NetworkConfiguration.builder().applyNetworkDefaults(network).build().getSpec();
      }
      return this;
    }

    public Builder storageMode(final StateStorageMode storageMode) {
      this.storageMode = storageMode;
      return this;
    }

    public Builder statusUpdater(final Consumer<String> statusUpdater) {
      this.statusUpdater = statusUpdater;
      return this;
    }

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public DatabaseMigrater build() {
      checkNotNull(dataDirLayout);
      checkNotNull(spec);
      return new DatabaseMigrater(
          dataDirLayout, network, storageMode, spec, batchSize, statusUpdater);
    }
  }
}

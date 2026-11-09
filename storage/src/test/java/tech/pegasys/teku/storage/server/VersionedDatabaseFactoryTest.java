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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.storageSystem.SupportedDatabaseVersionArgumentsProvider;

public class VersionedDatabaseFactoryTest {

  private static final StateStorageMode DATA_STORAGE_MODE = PRUNE;
  private final Eth1Address eth1Address =
      Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC");

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  @TempDir Path dataDir;

  @Test
  public void createDatabase_fromEmptyDataDir() throws Exception {
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            StorageConfiguration.builder()
                .specProvider(spec)
                .eth1DepositContract(eth1Address)
                .build(),
            Optional.empty());
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();

      assertDbVersionSaved(dataDir, DatabaseVersion.DEFAULT_VERSION);
      assertStorageModeSaved(dataDir, StateStorageMode.PRUNE);
    }
  }

  @Test
  public void createDatabase_fromExistingDataDir() throws Exception {
    final DatabaseVersion nonDefaultDatabaseVersion =
        SupportedDatabaseVersionArgumentsProvider.supportedDatabaseVersions().stream()
            .filter(version -> version != DatabaseVersion.DEFAULT_VERSION)
            .findAny()
            .orElseThrow();
    createDbDirectory(dataDir);
    createVersionFile(dataDir, nonDefaultDatabaseVersion);

    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            StorageConfiguration.builder()
                .specProvider(spec)
                .eth1DepositContract(eth1Address)
                .build(),
            Optional.empty());

    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
    }
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(nonDefaultDatabaseVersion);
    assertThat(dbFactory.getStateStorageMode()).isEqualTo(StateStorageMode.PRUNE);
  }

  @Test
  public void createDatabase_invalidVersionFile() throws Exception {
    createDbDirectory(dataDir);
    createVersionFile(dataDir, "bla");

    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            StorageConfiguration.builder()
                .specProvider(spec)
                .eth1DepositContract(eth1Address)
                .build(),
            Optional.empty());

    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("Unrecognized database version: bla");
  }

  @Test
  public void createDatabase_dbExistsButNoVersionIsSaved() {
    createDbDirectory(dataDir);

    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            StorageConfiguration.builder()
                .specProvider(spec)
                .eth1DepositContract(eth1Address)
                .build(),
            Optional.empty());

    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("No database version file was found");
  }

  @ParameterizedTest
  @ArgumentsSource(SupportedDatabaseVersionArgumentsProvider.class)
  public void createDatabase_shouldAllowAllSupportedDatabases(final DatabaseVersion version) {
    createDbDirectory(dataDir);
    StorageConfiguration.builder().build();
    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            StorageConfiguration.builder()
                .eth1DepositContract(eth1Address)
                .dataStorageMode(DATA_STORAGE_MODE)
                .dataStorageCreateDbVersion(version)
                .build(),
            Optional.empty());
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(version);
  }

  @Test
  public void createDatabase_freshDbWithBlobDbEnabled_persistsEnabledMode() throws Exception {
    createAndCloseDatabase(blobDbFactory(true));
    assertBlobDbModeSaved(dataDir, true);
  }

  @Test
  public void createDatabase_freshDbWithBlobDbDisabled_persistsDisabledMode() throws Exception {
    createAndCloseDatabase(blobDbFactory(false));
    assertBlobDbModeSaved(dataDir, false);
  }

  @Test
  public void createDatabase_existingBlobDbDatabase_staysEnabledWhenFlagDisabled()
      throws Exception {
    createAndCloseDatabase(blobDbFactory(true));
    assertBlobDbModeSaved(dataDir, true);

    // Re-opening an existing BlobDB database must keep using BlobDB regardless of the flag.
    createAndCloseDatabase(blobDbFactory(false));
    assertBlobDbModeSaved(dataDir, true);
  }

  @Test
  public void createDatabase_existingSstDatabase_staysDisabledWhenFlagEnabled() throws Exception {
    createAndCloseDatabase(blobDbFactory(false));
    assertBlobDbModeSaved(dataDir, false);

    // Re-opening an existing SST database must keep using SST regardless of the flag.
    createAndCloseDatabase(blobDbFactory(true));
    assertBlobDbModeSaved(dataDir, false);
  }

  @Test
  public void createDatabase_existingDatabaseWithoutModeFile_keepsSst() throws Exception {
    // Simulate a database created before blob-db mode tracking existed: real data on disk but no
    // recorded mode.
    createAndCloseDatabase(blobDbFactory(true));
    Files.delete(dataDir.resolve(VersionedDatabaseFactory.BLOB_DB_MODE_FILENAME));

    createAndCloseDatabase(blobDbFactory(true));
    assertBlobDbModeSaved(dataDir, false);
  }

  @Test
  public void createDatabase_corruptedModeFile_failsFast() throws Exception {
    createAndCloseDatabase(blobDbFactory(true));
    Files.writeString(
        dataDir.resolve(VersionedDatabaseFactory.BLOB_DB_MODE_FILENAME), "not-a-boolean");

    assertThatThrownBy(() -> createAndCloseDatabase(blobDbFactory(true)))
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("Unrecognized blob db mode");
  }

  @Test
  public void createDatabase_wipedDbDirectory_isTreatedAsFresh() throws Exception {
    // A populated SST database, recorded as such.
    createAndCloseDatabase(blobDbFactory(false));
    assertBlobDbModeSaved(dataDir, false);

    // The user wipes the db directory but leaves the side files (db.version, blob-db-mode.txt)
    // behind. The recorded mode is now stale and must not govern the fresh database.
    deleteRecursively(dataDir.resolve(VersionedDatabaseFactory.DB_PATH));

    createAndCloseDatabase(blobDbFactory(true));
    assertBlobDbModeSaved(dataDir, true);
  }

  @Test
  public void createDatabase_v5WithOnlyHotDirectoryWiped_isTreatedAsExisting() throws Exception {
    // V4/V5 use a separate hot and archive RocksDB. A populated SST database, recorded as such.
    createAndCloseDatabase(blobDbFactory(false, DatabaseVersion.V5));
    assertBlobDbModeSaved(dataDir, false);

    // Only the hot db directory is wiped; the archive RocksDB still exists, so this is not a fresh
    // database and BlobDB must not be enabled on the surviving SST archive.
    deleteRecursively(dataDir.resolve(VersionedDatabaseFactory.DB_PATH));

    createAndCloseDatabase(blobDbFactory(true, DatabaseVersion.V5));
    assertBlobDbModeSaved(dataDir, false);
  }

  private VersionedDatabaseFactory blobDbFactory(final boolean blobDbEnabled) {
    return blobDbFactory(blobDbEnabled, DatabaseVersion.DEFAULT_VERSION);
  }

  private VersionedDatabaseFactory blobDbFactory(
      final boolean blobDbEnabled, final DatabaseVersion createDbVersion) {
    return new VersionedDatabaseFactory(
        new StubMetricsSystem(),
        dataDir,
        StorageConfiguration.builder()
            .specProvider(spec)
            .eth1DepositContract(eth1Address)
            .dataStorageCreateDbVersion(createDbVersion)
            .rocksdbBlobDbEnabled(blobDbEnabled)
            .build(),
        Optional.empty());
  }

  private void createAndCloseDatabase(final DatabaseFactory dbFactory) throws Exception {
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
    }
  }

  private static void deleteRecursively(final Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (final Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (final IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  private void createDbDirectory(final Path dataPath) {
    final File dbDirectory =
        Paths.get(dataPath.toAbsolutePath().toString(), VersionedDatabaseFactory.DB_PATH).toFile();
    dbDirectory.mkdirs();
    assertThat(dbDirectory).isEmptyDirectory();
  }

  private void createVersionFile(final Path dataPath, final DatabaseVersion version)
      throws Exception {
    createVersionFile(dataPath, version.getValue());
    assertDbVersionSaved(dataPath, version);
  }

  private void createVersionFile(final Path dataPath, final String version) throws IOException {
    Path versionPath = dataPath.resolve(VersionedDatabaseFactory.DB_VERSION_FILENAME);
    Files.writeString(versionPath, version);
  }

  private void assertDbVersionSaved(final Path dataDirectory, final DatabaseVersion defaultVersion)
      throws IOException {
    final String versionValue =
        Files.readString(dataDirectory.resolve(VersionedDatabaseFactory.DB_VERSION_FILENAME));
    assertThat(versionValue).isEqualTo(defaultVersion.getValue());
  }

  private void assertStorageModeSaved(
      final Path dataDirectory, final StateStorageMode expectedStorageMode) throws IOException {
    final String storageModeValue =
        Files.readString(dataDirectory.resolve(VersionedDatabaseFactory.STORAGE_MODE_FILENAME));
    assertThat(storageModeValue).isEqualTo(expectedStorageMode.toString());
  }

  private void assertBlobDbModeSaved(final Path dataDirectory, final boolean expectedBlobDbEnabled)
      throws IOException {
    final String blobDbModeValue =
        Files.readString(dataDirectory.resolve(VersionedDatabaseFactory.BLOB_DB_MODE_FILENAME));
    assertThat(Boolean.parseBoolean(blobDbModeValue.trim())).isEqualTo(expectedBlobDbEnabled);
  }
}

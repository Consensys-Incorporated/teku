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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.mock;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.techu.service.serviceutils.layout.SimpleDataDirLayout;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.TestKvStoreDatabase;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.HotUpdater;

public class DatabaseMigraterTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SubCommandLogger subCommandLogger = mock(SubCommandLogger.class);
  private final Consumer<String> logger = subCommandLogger::display;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @BeforeEach
  void setUp() {
    assumeThat(DatabaseVersion.tryLoadLeveldbNativeLibrary())
        .describedAs("LevelDB support required")
        .isTrue();

    assumeThat(DatabaseVersion.tryLoadRocksdbLibrary())
        .describedAs("RocksDB support required")
        .isTrue();
  }

  @Test
  void shouldSupplyOriginalDatabasePath(@TempDir final Path tmpDir) throws IOException {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "leveldb1");

    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);
    assertThat(migrater.getMovedOldBeaconFolderPath()).isEqualTo(tmpDir.resolve("beacon.old"));
  }

  @Test
  void shouldSupplyNewDatabasePath(@TempDir final Path tmpDir) throws IOException {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "leveldb1");

    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);
    assertThat(migrater.getNewBeaconFolderPath()).isEqualTo(tmpDir.resolve("beacon.new"));
  }

  @Test
  void shouldCreateNewDatabaseFolderStructure(@TempDir final Path tmpDir) throws IOException {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "leveldb1");
    final Path generatedDatabase = tmpDir.resolve("beacon.new");
    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);
    migrater.duplicateBeaconFolderContents();

    assertThat(generatedDatabase.toFile().isDirectory()).isTrue();
    assertThat(generatedDatabase.resolve("network.yml").toFile().isFile()).isTrue();
    final Path kvStore = generatedDatabase.resolve("kvstore");
    assertThat(kvStore.toFile().isDirectory()).isTrue();
    assertThat(kvStore.resolve("local-enr-seqno.dat").toFile().isFile()).isTrue();
  }

  @Test
  void shouldOpenDatabases(@TempDir final Path tmpDir) throws IOException, DatabaseMigraterError {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "5");
    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);

    migrater.openDatabases(DatabaseVersion.V5, DatabaseVersion.LEVELDB2);
    migrater.closeDatabases();

    assertThat(tmpDir.resolve("beacon").toFile().isDirectory()).isTrue();
    assertThat(tmpDir.resolve("beacon.new").toFile().isDirectory()).isTrue();
    assertThat(tmpDir.resolve("beacon.old").toFile().isDirectory()).isFalse();
    assertThat(dbVersionInPath(dataDirLayout.getBeaconDataDirectory())).isEqualTo("5");
    assertThat(dbVersionInPath(migrater.getNewBeaconFolderPath())).isEqualTo("leveldb2");
  }

  @Test
  void shouldSwapActiveDatabase(@TempDir final Path tmpDir)
      throws IOException, DatabaseMigraterError {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "5");
    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);

    migrater.openDatabases(DatabaseVersion.V5, DatabaseVersion.LEVELDB2);
    migrater.closeDatabases();

    migrater.swapActiveDatabase();
    assertThat(tmpDir.resolve("beacon").toFile().isDirectory()).isTrue();
    assertThat(tmpDir.resolve("beacon.old").toFile().isDirectory()).isTrue();
    assertThat(tmpDir.resolve("beacon.new").toFile().isDirectory()).isFalse();
    assertThat(dbVersionInPath(dataDirLayout.getBeaconDataDirectory())).isEqualTo("leveldb2");
    assertThat(dbVersionInPath(migrater.getMovedOldBeaconFolderPath())).isEqualTo("5");
  }

  @Test
  void shouldCopyColumnData(@TempDir final Path tmpDir) throws IOException, DatabaseMigraterError {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "5");
    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);
    final BeaconBlockAndState blockAndState = dataStructureUtil.randomBlockAndState(1_000_000);
    migrater.openDatabases(DatabaseVersion.V5, DatabaseVersion.LEVELDB2);
    TestKvStoreDatabase originalDb = new TestKvStoreDatabase(migrater.getOriginalDatabase());
    try (HotUpdater updater = originalDb.hotUpdater()) {
      updater.addHotState(blockAndState.getBlock().getRoot(), blockAndState.getState());
      updater.commit();
    }
    migrater.migrateData();
    TestKvStoreDatabase newDb = new TestKvStoreDatabase(migrater.getNewDatabase());
    assertThat(newDb.getHotDao().getHotState(blockAndState.getRoot()))
        .contains(blockAndState.getState());

    migrater.closeDatabases();
  }

  @Test
  void shouldCopyVariablesFromHotDb(@TempDir final Path tmpDir) throws Exception {
    final DataDirLayout dataDirLayout = prepareTempDir(tmpDir, "5");
    final DatabaseMigrater migrater = getDatabaseMigrater(dataDirLayout);
    final UInt64 genesis = dataStructureUtil.randomUInt64();
    final Checkpoint finalizedCheckpoint = dataStructureUtil.randomCheckpoint();
    migrater.openDatabases(DatabaseVersion.V5, DatabaseVersion.LEVELDB2);
    final TestKvStoreDatabase originalDb = new TestKvStoreDatabase(migrater.getOriginalDatabase());
    try (final HotUpdater updater = originalDb.hotUpdater()) {
      updater.setGenesisTime(genesis);
      updater.setFinalizedCheckpoint(finalizedCheckpoint);
      updater.commit();
    }

    migrater.migrateData();
    final TestKvStoreDatabase newDb = new TestKvStoreDatabase(migrater.getNewDatabase());
    assertThat(newDb.getHotDao().getGenesisTime())
        .isEqualTo(originalDb.getHotDao().getGenesisTime());
    assertThat(newDb.getHotDao().getGenesisTime()).contains(genesis);

    assertThat(newDb.getHotDao().getFinalizedCheckpoint())
        .isEqualTo(originalDb.getHotDao().getFinalizedCheckpoint());
    assertThat(newDb.getHotDao().getFinalizedCheckpoint()).contains(finalizedCheckpoint);

    migrater.closeDatabases();
  }

  private DataDirLayout prepareTempDir(final Path tempDir, final String dbVersionString)
      throws IOException {
    final Path originalBeaconFolder = tempDir.resolve("beacon");
    assertThat(originalBeaconFolder.toFile().mkdir()).isTrue();
    final Path kvStoreFolder = originalBeaconFolder.resolve("kvstore");
    assertThat(kvStoreFolder.toFile().mkdir()).isTrue();

    Files.copy(
        Resources.getResource("network.yml").openStream(),
        originalBeaconFolder.resolve("network.yml"));
    Files.createFile(kvStoreFolder.resolve("local-enr-seqno.dat"));

    return makeDatabaseStructure(originalBeaconFolder, dbVersionString);
  }

  private String dbVersionInPath(final Path beaconDataDirectory) throws IOException {
    return FileUtils.readFileToString(
        beaconDataDirectory.resolve("db.version").toFile(), Charset.defaultCharset());
  }

  private DatabaseMigrater getDatabaseMigrater(final DataDirLayout dataDirLayout) {
    return DatabaseMigrater.builder()
        .dataDirLayout(dataDirLayout)
        .storageMode(StateStorageMode.ARCHIVE)
        .network("minimal")
        .spec(spec)
        .statusUpdater(logger)
        .build();
  }

  private DataDirLayout makeDatabaseStructure(final Path beaconFolder, final String dbVersionString)
      throws IOException {
    final Path dbFolder = beaconFolder.resolve("db");
    final Path dbVersionFile = beaconFolder.resolve("db.version");
    FileUtils.write(dbVersionFile.toFile(), dbVersionString, Charset.defaultCharset());
    assertThat(dbFolder.toFile().mkdir()).isTrue();
    assertThat(dbVersionFile.toFile().isFile()).isTrue();

    return new SimpleDataDirLayout(beaconFolder);
  }
}

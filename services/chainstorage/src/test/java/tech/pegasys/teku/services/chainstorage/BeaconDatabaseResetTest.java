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

package tech.pegasys.teku.services.chainstorage;

import static java.nio.file.Files.createTempDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.ARCHIVE_PATH;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.DB_PATH;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.DB_VERSION_FILENAME;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.METADATA_FILENAME;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.NETWORK_FILENAME;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.SLASHING_PROTECTION_PATH;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.STORAGE_MODE_FILENAME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

class BeaconDatabaseResetTest {

  @Mock private ServiceConfig serviceConfig;

  @Mock private VersionedDatabaseFactory dbFactory;

  @Mock private DataDirLayout dataDirLayout;
  private Path beaconDataDir;
  private Path dbDataDir;
  private Path resolvedSlashProtectionDir;
  private Path networkFilePath;
  @Mock private Database database;

  private BeaconDatabaseReset beaconDatabaseReset;

  @BeforeEach
  void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    beaconDatabaseReset = spy(new BeaconDatabaseReset());
    beaconDataDir = createTempDirectory("beacon");
    dbDataDir = beaconDataDir.resolve(DB_PATH);
    Files.createDirectory(dbDataDir);
    Path networkFile = beaconDataDir.resolve(NETWORK_FILENAME);
    Files.createFile(networkFile);
    networkFilePath = networkFile;

    final Path validatorDataDir = createTempDirectory("validator");
    resolvedSlashProtectionDir = validatorDataDir.resolve(SLASHING_PROTECTION_PATH);

    when(serviceConfig.getDataDirLayout()).thenReturn(dataDirLayout);
    when(dataDirLayout.getBeaconDataDirectory()).thenReturn(beaconDataDir);
    when(dataDirLayout.getValidatorDataDirectory()).thenReturn(validatorDataDir);
    when(dataDirLayout.getValidatorDataDirectory().resolve(SLASHING_PROTECTION_PATH))
        .thenReturn(resolvedSlashProtectionDir);
  }

  @Test
  void shouldResetSpecificDirectoriesAndCreateDatabase() throws IOException {
    final Path kvStoreDir = beaconDataDir.resolve("kvstore");
    Files.createDirectory(kvStoreDir);
    final Path dbVersion = beaconDataDir.resolve(DB_VERSION_FILENAME);
    Files.createFile(dbVersion);

    when(dbFactory.createDatabase()).thenReturn(database);

    final Database result =
        beaconDatabaseReset.resetStorageForEphemeryAndCreate(serviceConfig, dbFactory);
    verify(beaconDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    verify(beaconDatabaseReset).deleteDirectoryRecursively(resolvedSlashProtectionDir);

    verify(dbFactory).createDatabase();
    verifyNoMoreInteractions(dbFactory);

    assertTrue(Files.exists(kvStoreDir));
    assertTrue(Files.exists(dbVersion));
    assertFalse(Files.exists(networkFilePath));
    assertEquals(database, result);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenDirectoryDeletionFails() throws IOException {
    doThrow(new IOException("Failed to delete directory"))
        .when(beaconDatabaseReset)
        .deleteDirectoryRecursively(dbDataDir);
    final InvalidConfigurationException exception =
        assertThrows(
            InvalidConfigurationException.class,
            () -> {
              beaconDatabaseReset.resetStorageForEphemeryAndCreate(serviceConfig, dbFactory);
            });
    assertEquals(
        "The existing ephemery database was old, and was unable to reset it.",
        exception.getMessage());
    verify(dbFactory, never()).createDatabase();
    verify(beaconDatabaseReset, never()).deleteDirectoryRecursively(resolvedSlashProtectionDir);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenDatabaseCreationFails() throws IOException {
    doNothing().when(beaconDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    doNothing().when(beaconDatabaseReset).deleteDirectoryRecursively(resolvedSlashProtectionDir);
    when(dbFactory.createDatabase()).thenThrow(new RuntimeException("Database creation failed"));
    final InvalidConfigurationException exception =
        assertThrows(
            InvalidConfigurationException.class,
            () -> {
              beaconDatabaseReset.resetStorageForEphemeryAndCreate(serviceConfig, dbFactory);
            });
    assertEquals(
        "The existing ephemery database was old, and was unable to reset it.",
        exception.getMessage());
    verify(beaconDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    verify(beaconDatabaseReset).deleteDirectoryRecursively(resolvedSlashProtectionDir);
    verify(dbFactory).createDatabase();
  }

  @Test
  void clearBeaconDatabase_shouldDeleteBeaconFilesOnly() throws IOException {
    // Create beacon chain files
    final Path archiveDir = beaconDataDir.resolve(ARCHIVE_PATH);
    Files.createDirectory(archiveDir);
    final Path metadataFile = beaconDataDir.resolve(METADATA_FILENAME);
    Files.createFile(metadataFile);
    final Path dbVersionFile = beaconDataDir.resolve(DB_VERSION_FILENAME);
    Files.createFile(dbVersionFile);
    final Path storageModeFile = beaconDataDir.resolve(STORAGE_MODE_FILENAME);
    Files.createFile(storageModeFile);

    // Create slashing protection directory (should not be deleted)
    Files.createDirectory(resolvedSlashProtectionDir);
    final Path slashingProtectionFile =
        resolvedSlashProtectionDir.resolve(SLASHING_PROTECTION_PATH);
    Files.createFile(slashingProtectionFile);

    beaconDatabaseReset.clearBeaconDatabase(serviceConfig);

    // Verify beacon directories are deleted
    verify(beaconDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    verify(beaconDatabaseReset).deleteDirectoryRecursively(archiveDir);

    // Verify beacon files are deleted
    assertFalse(Files.exists(networkFilePath));
    assertFalse(Files.exists(metadataFile));
    assertFalse(Files.exists(dbVersionFile));
    assertFalse(Files.exists(storageModeFile));

    // Verify slashing protection NOT deleted
    verify(beaconDatabaseReset, never()).deleteDirectoryRecursively(resolvedSlashProtectionDir);
    assertTrue(Files.exists(resolvedSlashProtectionDir));
    assertTrue(Files.exists(slashingProtectionFile));
  }

  @Test
  void clearBeaconDatabase_shouldThrowExceptionOnError() throws IOException {
    doThrow(new IOException("Failed to delete directory"))
        .when(beaconDatabaseReset)
        .deleteDirectoryRecursively(dbDataDir);

    final InvalidConfigurationException exception =
        assertThrows(
            InvalidConfigurationException.class,
            () -> beaconDatabaseReset.clearBeaconDatabase(serviceConfig));

    assertEquals(
        "Failed to clear beacon database with --force-clear-db flag.", exception.getMessage());
  }
}

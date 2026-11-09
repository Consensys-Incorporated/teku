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
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

public class BeaconDatabaseReset {

  /** This method is called only on Ephemery network when reset is due. */
  Database resetStorageForEphemeryAndCreate(
      final ServiceConfig serviceConfig, final VersionedDatabaseFactory dbFactory) {
    try {
      final Path beaconDataDir = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
      final Path dbDataDir = beaconDataDir.resolve(DB_PATH);
      final Path networkFile = beaconDataDir.resolve(NETWORK_FILENAME);
      final Path validatorDataDir = serviceConfig.getDataDirLayout().getValidatorDataDirectory();
      final Path slashProtectionDir;
      if (validatorDataDir.endsWith(SLASHING_PROTECTION_PATH)) {
        slashProtectionDir = validatorDataDir;
      } else {
        slashProtectionDir = validatorDataDir.resolve(SLASHING_PROTECTION_PATH);
      }
      deleteDirectoryRecursively(dbDataDir);
      deleteDirectoryRecursively(networkFile);
      deleteDirectoryRecursively(slashProtectionDir);
      return dbFactory.createDatabase();
    } catch (final Exception ex) {
      throw new InvalidConfigurationException(
          "The existing ephemery database was old, and was unable to reset it.", ex);
    }
  }

  public void clearBeaconDatabase(final ServiceConfig serviceConfig) {
    try {
      final Path beaconDataDir = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
      deleteDirectoryRecursively(beaconDataDir.resolve(DB_PATH));
      deleteDirectoryRecursively(beaconDataDir.resolve(ARCHIVE_PATH));
      deleteFileIfExists(beaconDataDir.resolve(NETWORK_FILENAME));
      deleteFileIfExists(beaconDataDir.resolve(METADATA_FILENAME));
      deleteFileIfExists(beaconDataDir.resolve(DB_VERSION_FILENAME));
      deleteFileIfExists(beaconDataDir.resolve(STORAGE_MODE_FILENAME));
    } catch (final Exception ex) {
      throw new InvalidConfigurationException(
          "Failed to clear beacon database with --force-clear-db flag.", ex);
    }
  }

  private void deleteFileIfExists(final Path path) throws IOException {
    if (Files.exists(path)) {
      Files.delete(path);
    }
  }

  void deleteDirectoryRecursively(final Path path) throws IOException {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        try (var stream = Files.walk(path)) {
          stream
              .sorted((o1, o2) -> o2.compareTo(o1))
              .forEach(
                  p -> {
                    try {
                      Files.delete(p);
                    } catch (IOException e) {
                      throw new RuntimeException("Failed to delete file/directory: " + p, e);
                    }
                  });
        }
      } else {
        try {
          Files.delete(path);
        } catch (IOException e) {
          throw new RuntimeException("Failed to delete file: " + path, e);
        }
      }
    }
  }
}

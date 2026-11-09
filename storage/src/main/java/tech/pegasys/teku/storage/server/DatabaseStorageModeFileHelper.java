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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseStorageModeFileHelper {

  private static final Logger LOG = LogManager.getLogger();

  public static Optional<StateStorageMode> readStateStorageMode(final Path path) {
    if (!Files.exists(path)) {
      return Optional.empty();
    }

    try {
      final StateStorageMode dbStorageMode =
          StateStorageMode.valueOf(Files.readString(path).trim());
      LOG.debug("Read previous storage mode as {}", dbStorageMode);
      return Optional.of(dbStorageMode);
    } catch (final IllegalArgumentException ex) {
      throw DatabaseStorageException.unrecoverable(
          "Invalid database storage mode file ("
              + path
              + "). Run your node using '--data-storage-mode' option to configure the correct storage mode.",
          ex);
    } catch (final IOException ex) {
      throw new UncheckedIOException("Failed to read storage mode from file", ex);
    }
  }
}

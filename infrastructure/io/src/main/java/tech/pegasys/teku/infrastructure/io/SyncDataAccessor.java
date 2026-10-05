/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

/**
 * A wrapper class for reading and writing files, ensuring that the contents is immediately flushed
 * to disk.
 */
public class SyncDataAccessor {

  private static final Logger LOG = LogManager.getLogger();
  private final boolean atomicFileMoveSupport;

  SyncDataAccessor(final boolean atomicFileMoveSupport) {
    this.atomicFileMoveSupport = atomicFileMoveSupport;
  }

  public static SyncDataAccessor create(final Path path) {

    boolean atomicFileMoveSupport;
    final Path tmpFile;
    if (Files.isDirectory(path.toAbsolutePath())) {
      tmpFile = path.toAbsolutePath().resolve("syncWriteTest.tmp");
    } else {
      tmpFile = path.toAbsolutePath().getParent().resolve("syncWriteTest.tmp");
    }

    try {
      ensurePathExists(path);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create file at: " + e.getMessage());
    }

    try {
      atomicSyncedWrite(tmpFile, Bytes32.ZERO);

      atomicFileMoveSupport = true;
    } catch (AtomicMoveNotSupportedException e) {
      LOG.debug("File system doesn't support atomic move");
      atomicFileMoveSupport = false;
    } catch (IOException e) {
      LOG.error(String.format("Failed to write in %s", path), e);
      throw new InvalidConfigurationException(String.format("Cannot write to folder %s", path), e);
    } finally {
      try {
        Files.deleteIfExists(tmpFile);
      } catch (IOException e) {
        LOG.debug("Failed to delete the temporary file ", e);
      }
    }
    return new SyncDataAccessor(atomicFileMoveSupport);
  }

  /**
   * Reads the content of the specified path, if it exists.
   *
   * @param path the path to read
   * @return Optional containing the entire file contents or empty optional if the file does not
   *     exist
   * @throws IOException if an IO error occurs while reading
   */
  public Optional<Bytes> read(final Path path) throws IOException {
    if (path.toFile().exists()) {
      return Optional.of(Bytes.wrap(Files.readAllBytes(path)));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Writes data to the specified path, ensuring both the file content and metadata is immediately
   * flushed to hardware storage. If the filesystem support atomic operation it creates a temporary
   * file before writing the actual file. If the file exists it is overwritten, otherwise it is
   * created.
   *
   * @param path the path to write to
   * @param data the data to write
   * @throws IOException if an IO error occurs while writing
   */
  public void syncedWrite(final Path path, final Bytes data) throws IOException {
    if (atomicFileMoveSupport) {
      atomicSyncedWrite(path, data);
    } else {
      nonAtomicSyncedWrite(path, data);
    }
  }

  private static void nonAtomicSyncedWrite(final Path path, final Bytes data) throws IOException {
    Files.write(
        path,
        data.toArrayUnsafe(),
        StandardOpenOption.SYNC,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void atomicSyncedWrite(final Path path, final Bytes data) throws IOException {
    final Path absolutePath = path.toAbsolutePath();
    final Path tmpFile = Paths.get(path + ".tmp");
    nonAtomicSyncedWrite(tmpFile, data);
    Files.move(
        tmpFile, absolutePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private static void ensurePathExists(final Path absolutePath) throws IOException {
    if (absolutePath != null) {
      final File file = absolutePath.toFile();
      if (!file.mkdirs() && !file.isDirectory()) {
        throw new IOException("Unable to create directory " + file);
      }
    }
  }
}

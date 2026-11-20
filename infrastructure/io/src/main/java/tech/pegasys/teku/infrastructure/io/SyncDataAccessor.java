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

package tech.pegasys.teku.infrastructure.io;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
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
 *
 * <p>Creating or renaming a file changes its parent directory, and on POSIX filesystems that change
 * is only durable once the directory itself is synced. Where the platform allows it, the parent
 * directory is synced after each write, so a host crash or power loss cannot roll the file back
 * to its previous version or remove it.
 */
public class SyncDataAccessor {

  private static final Logger LOG = LogManager.getLogger();

  static final DirectorySync FSYNC_DIRECTORY =
      directory -> {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
          channel.force(true);
        }
      };
  static final DirectorySync NO_DIRECTORY_SYNC = directory -> {};

  private final boolean atomicFileMoveSupport;
  private final boolean ownerOnly;
  private final DirectorySync directorySync;

  SyncDataAccessor(
      final boolean atomicFileMoveSupport,
      final boolean ownerOnly,
      final DirectorySync directorySync) {
    this.atomicFileMoveSupport = atomicFileMoveSupport;
    this.ownerOnly = ownerOnly;
    this.directorySync = directorySync;
  }

  public static SyncDataAccessor create(final Path path) {
    return create(path, false);
  }

  /**
   * Creates an accessor whose written files are readable and writable only by their owner. Use it
   * for files holding secrets, see {@link SecretFiles}.
   */
  public static SyncDataAccessor createOwnerOnly(final Path path) {
    return create(path, true);
  }

  private static SyncDataAccessor create(final Path path, final boolean ownerOnly) {

    boolean atomicFileMoveSupport;
    final Path directory;
    if (Files.isDirectory(path.toAbsolutePath())) {
      directory = path.toAbsolutePath();
    } else {
      directory = path.toAbsolutePath().getParent();
    }
    final Path tmpFile = directory.resolve("syncWriteTest.tmp");

    try {
      ensurePathExists(path);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create file at: " + e.getMessage());
    }

    try {
      writeAndMove(tmpFile, Bytes32.ZERO, false);

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
    return new SyncDataAccessor(
        atomicFileMoveSupport, ownerOnly, probeDirectorySync(directory, FSYNC_DIRECTORY));
  }

  /**
   * Checks that {@code directorySync} works on {@code directory}. Some platforms, such as Windows,
   * cannot open a directory to sync it; there the parent directory is not synced.
   */
  static DirectorySync probeDirectorySync(final Path directory, final DirectorySync directorySync) {
    try {
      directorySync.sync(directory);
      return directorySync;
    } catch (IOException | UnsupportedOperationException e) {
      LOG.debug("File system doesn't support syncing directory {}", directory, e);
      return NO_DIRECTORY_SYNC;
    }
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
   * created. If this accessor was created with {@link #createOwnerOnly(Path)} the file is
   * restricted to its owner before any data is written. Where supported, the parent directory is
   * then synced so the new or replaced file survives a host crash.
   *
   * @param path the path to write to
   * @param data the data to write
   * @throws IOException if an IO error occurs while writing
   */
  public void syncedWrite(final Path path, final Bytes data) throws IOException {
    if (atomicFileMoveSupport) {
      atomicSyncedWrite(path, data, ownerOnly);
    } else {
      nonAtomicSyncedWrite(path, data, ownerOnly);
    }
  }

  private void nonAtomicSyncedWrite(final Path path, final Bytes data, final boolean ownerOnly)
      throws IOException {
    writeSynced(path, data, ownerOnly);
    // Synced on every write, not only when the file is created: if an earlier sync failed, the
    // file now exists but its directory entry may still not be durable.
    syncParentDirectory(path);
  }

  private void atomicSyncedWrite(final Path path, final Bytes data, final boolean ownerOnly)
      throws IOException {
    writeAndMove(path, data, ownerOnly);
    syncParentDirectory(path);
  }

  private void syncParentDirectory(final Path path) throws IOException {
    directorySync.sync(path.toAbsolutePath().getParent());
  }

  private static void writeSynced(final Path path, final Bytes data, final boolean ownerOnly)
      throws IOException {
    if (ownerOnly) {
      SecretFiles.restrictToOwner(path);
    }
    Files.write(
        path,
        data.toArrayUnsafe(),
        StandardOpenOption.SYNC,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static void writeAndMove(final Path path, final Bytes data, final boolean ownerOnly)
      throws IOException {
    final Path absolutePath = path.toAbsolutePath();
    final Path tmpFile = Paths.get(path + ".tmp");
    // The move keeps the temporary file's permissions
    writeSynced(tmpFile, data, ownerOnly);
    Files.move(
        tmpFile, absolutePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  @FunctionalInterface
  interface DirectorySync {
    void sync(Path directory) throws IOException;
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

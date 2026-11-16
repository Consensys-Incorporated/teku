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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Writes files holding secrets (JWT secrets, private keys, API tokens, keystore passwords).
 *
 * <p>On file systems supporting POSIX permissions the secret is only ever written to a new file
 * restricted to owner read/write ({@code rw-------}). An existing file is replaced, never rewritten
 * in place, so the secret is exposed neither through permissions derived from the process umask nor
 * to a process that opened the previous file while it had wider permissions. On other file systems
 * (e.g. Windows) the file is written with the default permissions inherited from its parent
 * directory.
 */
public final class SecretFiles {

  static final Set<PosixFilePermission> OWNER_READ_WRITE =
      PosixFilePermissions.fromString("rw-------");

  private SecretFiles() {}

  /**
   * Writes {@code content} to {@code path}, creating it or replacing an existing file.
   *
   * <p>On POSIX file systems the content is written to a temporary file in the same directory,
   * readable only by its owner, which is then atomically moved over {@code path}. Readers see
   * either the previous file or the complete new one, never a partially written file.
   */
  public static void writeOwnerOnly(final Path path, final byte[] content) throws IOException {
    if (!supportsPosixPermissions(path)) {
      Files.write(path, content);
      return;
    }
    final Path target = path.toAbsolutePath();
    final Path tmpFile =
        Files.createTempFile(
            target.getParent(),
            target.getFileName() + ".",
            ".tmp",
            PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
    try {
      // Set explicitly: permissions requested at creation are still masked by the umask.
      Files.setPosixFilePermissions(tmpFile, OWNER_READ_WRITE);
      Files.write(tmpFile, content);
      Files.move(
          tmpFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      // Only left behind if writing or moving failed
      Files.deleteIfExists(tmpFile);
    }
  }

  /** Writes {@code content} encoded as UTF-8, see {@link #writeOwnerOnly}. */
  public static void writeStringOwnerOnly(final Path path, final String content)
      throws IOException {
    writeOwnerOnly(path, content.getBytes(UTF_8));
  }

  /**
   * Returns true if any local user can read the file. Always false on file systems without POSIX
   * permissions.
   */
  public static boolean isReadableByOthers(final Path path) throws IOException {
    return supportsPosixPermissions(path)
        && Files.getPosixFilePermissions(path).contains(PosixFilePermission.OTHERS_READ);
  }

  /**
   * On POSIX file systems, creates the file if missing and sets its permissions to {@code
   * rw-------}. Existing content is left untouched. Does nothing on other file systems.
   */
  static void restrictToOwner(final Path path) throws IOException {
    if (!supportsPosixPermissions(path)) {
      return;
    }
    try {
      Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
    } catch (final FileAlreadyExistsException e) {
      // Restricted below; the existing file may have been created with wider permissions.
    }
    // Set explicitly: permissions requested at creation are still masked by the umask.
    Files.setPosixFilePermissions(path, OWNER_READ_WRITE);
  }

  private static boolean supportsPosixPermissions(final Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }
}

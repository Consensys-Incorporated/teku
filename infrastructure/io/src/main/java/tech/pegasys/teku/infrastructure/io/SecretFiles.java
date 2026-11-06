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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Writes files holding secrets (JWT secrets, private keys, API tokens, keystore passwords).
 *
 * <p>On file systems supporting POSIX permissions the file is restricted to owner read/write
 * ({@code rw-------}) before any content is written, so the secret is never exposed through
 * permissions derived from the process umask, including when an existing file is overwritten. On
 * other file systems (e.g. Windows) the file is written with the default permissions inherited from
 * its parent directory.
 */
public final class SecretFiles {

  static final Set<PosixFilePermission> OWNER_READ_WRITE =
      PosixFilePermissions.fromString("rw-------");

  private SecretFiles() {}

  /**
   * Writes {@code content} to {@code path}, creating or overwriting it, after restricting the file
   * to its owner.
   *
   * @param options open options passed to {@link Files#write(Path, byte[], OpenOption...)}; when
   *     none are given the file is created or truncated
   */
  public static void writeOwnerOnly(
      final Path path, final byte[] content, final OpenOption... options) throws IOException {
    restrictToOwner(path);
    Files.write(path, content, options);
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

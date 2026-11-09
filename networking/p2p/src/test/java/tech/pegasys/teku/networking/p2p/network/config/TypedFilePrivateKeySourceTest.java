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

package tech.pegasys.teku.networking.p2p.network.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypedFilePrivateKeySourceTest {

  @Test
  public void privateKeyFile_ioError(@TempDir final Path tempDir) throws IOException {
    final Path path = tempDir.resolve("myFile");

    final Set<PosixFilePermission> noPermissions = Collections.emptySet();
    FileAttribute<Set<PosixFilePermission>> fileAttributes =
        PosixFilePermissions.asFileAttribute(noPermissions);
    Files.createFile(path, fileAttributes);

    final TypedFilePrivateKeySource typedFilePrivateKeySource =
        new TypedFilePrivateKeySource(
            path.toAbsolutePath().toString(), PrivateKeySource.Type.SECP256K1);
    assertThatThrownBy(() -> typedFilePrivateKeySource.getPrivateKeyBytes())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("could not be read: AccessDeniedException");
  }
}

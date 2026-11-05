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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.io.SecretFiles.OWNER_READ_WRITE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class SecretFilesTest {

  @TempDir Path tempDir;

  @Test
  void writeStringOwnerOnly_shouldCreateFileReadableOnlyByOwner() throws IOException {
    final Path file = tempDir.resolve("secret");

    SecretFiles.writeStringOwnerOnly(file, "secret-value");

    assertThat(Files.readString(file)).isEqualTo("secret-value");
    assertThat(Files.getPosixFilePermissions(file)).isEqualTo(OWNER_READ_WRITE);
  }

  @Test
  void writeStringOwnerOnly_shouldRestrictAndOverwriteExistingWorldReadableFile()
      throws IOException {
    final Path file = tempDir.resolve("secret");
    Files.writeString(file, "a much longer previous value");
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-rw-"));

    SecretFiles.writeStringOwnerOnly(file, "new-value");

    assertThat(Files.readString(file)).isEqualTo("new-value");
    assertThat(Files.getPosixFilePermissions(file)).isEqualTo(OWNER_READ_WRITE);
  }

  @Test
  void writeOwnerOnly_shouldApplyOpenOptions() throws IOException {
    final Path file = tempDir.resolve("secret");
    Files.writeString(file, "a");

    SecretFiles.writeOwnerOnly(file, new byte[] {'b'}, StandardOpenOption.APPEND);

    assertThat(Files.readString(file)).isEqualTo("ab");
    assertThat(Files.getPosixFilePermissions(file)).isEqualTo(OWNER_READ_WRITE);
  }

  @Test
  void isReadableByOthers_shouldDetectWorldReadableFile() throws IOException {
    final Path file = tempDir.resolve("secret");
    Files.writeString(file, "secret-value");

    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
    assertThat(SecretFiles.isReadableByOthers(file)).isTrue();

    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
    assertThat(SecretFiles.isReadableByOthers(file)).isFalse();

    Files.setPosixFilePermissions(file, OWNER_READ_WRITE);
    assertThat(SecretFiles.isReadableByOthers(file)).isFalse();
  }
}

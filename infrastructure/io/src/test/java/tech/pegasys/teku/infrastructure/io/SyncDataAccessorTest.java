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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.OSUtils;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class SyncDataAccessorTest {

  @Test
  public void shouldThrowInvalidConfigurationExceptionWhenDirectoryNotWritable(
      @TempDir final Path tempDir) throws IOException {

    OSUtils.makeNonWritable(tempDir);
    assumeThat(tempDir.toFile().canWrite()).describedAs("Directory %s writable").isFalse();
    assertThatThrownBy(() -> SyncDataAccessor.create(tempDir))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Cannot write to folder");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldWriteAndOverwriteFileContent(
      final boolean useAtomicMove, @TempDir final Path tempDir) throws IOException {
    final Path filePath = Paths.get(tempDir.toString() + "/myfile.tmp");
    assertThat(Files.exists(filePath)).isFalse();
    final SyncDataAccessor syncDataAccessor = new SyncDataAccessor(useAtomicMove);
    syncDataAccessor.syncedWrite(filePath, Bytes.fromHexString("0x41"));
    assertThat(Files.exists(filePath)).isTrue();

    String content = Files.readString(filePath);
    assertThat(content).isEqualTo("A");

    assertThat(Files.exists(filePath)).isTrue();
    syncDataAccessor.syncedWrite(filePath, Bytes.fromHexString("0x42"));

    content = Files.readString(filePath);
    assertThat(content).isEqualTo("B");
  }
}

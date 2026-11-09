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

package tech.pegasys.teku.cli.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.MultilineEntriesReader;

class MultilineEntriesReaderTest {

  @Test
  public void shouldReadAllEntriesInFile(@TempDir final Path tempDir) throws IOException {
    final List<String> expectedEntries = List.of("foo", "bar", "ziz");

    final String filepath = writeLinesToFile(tempDir, expectedEntries);

    final List<String> readEntries = MultilineEntriesReader.readEntries(filepath);

    assertThat(readEntries).isEqualTo(expectedEntries);
  }

  @Test
  public void shouldIgnoreComments(@TempDir final Path tempDir) throws IOException {
    final String filepath = writeLinesToFile(tempDir, List.of("foo", "#bar", "ziz"));

    final List<String> entries = MultilineEntriesReader.readEntries(filepath);

    assertThat(entries).isEqualTo(List.of("foo", "ziz"));
  }

  @Test
  public void shouldIgnoreEmptyLines(@TempDir final Path tempDir) throws IOException {
    final String filepath = writeLinesToFile(tempDir, List.of("foo", "", "ziz"));

    final List<String> entries = MultilineEntriesReader.readEntries(filepath);

    assertThat(entries).isEqualTo(List.of("foo", "ziz"));
  }

  @Test
  public void shouldReadNoEntriesFromEmptyFile(@TempDir final Path tempDir) throws IOException {
    final Path entriesFile = Files.createFile(tempDir.resolve("entries.txt"));

    final List<String> readEntries =
        MultilineEntriesReader.readEntries(entriesFile.toAbsolutePath().toString());

    assertThat(readEntries).isEmpty();
  }

  @Test
  public void shouldThrowInvalidConfigurationWhenFileDoesNotExist() {
    assertThatThrownBy(() -> MultilineEntriesReader.readEntries("/foo/bar/no_file.txt"))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Failed reading entries from resource /foo/bar/no_file.txt");
  }

  public static String writeLinesToFile(final Path dir, final Collection<String> lines)
      throws IOException {
    final Path entriesFile = Files.createFile(dir.resolve("entries.txt"));
    lines.forEach(
        line -> {
          try {
            Files.writeString(
                entriesFile, line + System.lineSeparator(), StandardOpenOption.APPEND);
          } catch (IOException e) {
            fail("Error creating file for test", e);
          }
        });
    return entriesFile.toAbsolutePath().toString();
  }
}

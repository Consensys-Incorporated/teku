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

package tech.pegasys.teku.ethtests.finder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceTestRootTest {

  @TempDir Path tmp;

  @AfterEach
  void clearProperties() {
    System.clearProperty(ReferenceTestRoot.ARCHIVES_DIR_PROPERTY);
    System.clearProperty(ReferenceTestRoot.ROOT_DIR_PROPERTY);
  }

  @Test
  void archivesDir_shouldListOneSpecDirectoryPerZipNamedAfterTheZip() throws IOException {
    final Path archives = tmp.resolve("archives");
    zip(archives.resolve("minimal.zip"), Map.of("minimal/phase0/x.yaml", "1"));
    zip(archives.resolve("mainnet.zip"), Map.of("mainnet/phase0/y.yaml", "2"));
    Files.writeString(archives.resolve("not-a-zip.txt"), "ignored");

    final ReferenceTestRoot root = ReferenceTestRoot.ofArchivesDir(archives);
    final List<Path> specDirs;
    try (Stream<Path> stream = root.listSpecDirectories()) {
      specDirs = stream.toList();
    }

    assertThat(specDirs)
        .extracting(p -> p.getFileName().toString())
        .containsExactly("mainnet", "minimal");
    assertThat(Files.exists(specDirs.get(0).resolve("phase0").resolve("y.yaml"))).isTrue();
    assertThat(Files.readString(specDirs.get(1).resolve("phase0/x.yaml"))).isEqualTo("1");
  }

  @Test
  void archivesDir_shouldResolveSpecDirectoryFromZipOfThatName() throws IOException {
    final Path archives = tmp.resolve("archives");
    zip(archives.resolve("minimal.zip"), Map.of("minimal/phase0/x.yaml", "1"));

    final Path specDir = ReferenceTestRoot.ofArchivesDir(archives).getSpecDirectory("minimal");

    assertThat(Files.isDirectory(specDir)).isTrue();
    assertThat(Files.exists(specDir.resolve("phase0/x.yaml"))).isTrue();
    assertThat(Files.exists(specDir.resolve("phase0/missing.yaml"))).isFalse();
  }

  @Test
  void archivesDir_shouldReturnSameMountForRepeatedLookups() throws IOException {
    final Path archives = tmp.resolve("archives");
    zip(archives.resolve("minimal.zip"), Map.of("minimal/phase0/x.yaml", "1"));
    final ReferenceTestRoot root = ReferenceTestRoot.ofArchivesDir(archives);

    final Path first = root.getSpecDirectory("minimal");
    final Path second = ReferenceTestRoot.ofArchivesDir(archives).getSpecDirectory("minimal");

    assertThat(first.getFileSystem()).isSameAs(second.getFileSystem());
  }

  @Test
  void archivesDir_shouldFailClearlyWhenZipIsMissing() throws IOException {
    final Path archives = Files.createDirectories(tmp.resolve("archives"));

    assertThatThrownBy(() -> ReferenceTestRoot.ofArchivesDir(archives).getSpecDirectory("mainnet"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mainnet.zip")
        .hasMessageContaining("convertRefTests");
  }

  @Test
  void rootDir_shouldUsePlainDirectories() throws IOException {
    final Path rootDir = tmp.resolve("tests");
    Files.createDirectories(rootDir.resolve("minimal/phase0"));
    Files.writeString(rootDir.resolve("minimal/phase0/x.yaml"), "1");

    final ReferenceTestRoot root = ReferenceTestRoot.ofDirectory(rootDir);
    try (Stream<Path> stream = root.listSpecDirectories()) {
      assertThat(stream.toList()).containsExactly(rootDir.resolve("minimal"));
    }
    assertThat(root.getSpecDirectory("minimal")).isEqualTo(rootDir.resolve("minimal"));
  }

  @Test
  void fromSystemProperties_shouldPreferArchivesDir() throws IOException {
    final Path archives = tmp.resolve("archives");
    zip(archives.resolve("minimal.zip"), Map.of("minimal/phase0/x.yaml", "1"));
    System.setProperty(ReferenceTestRoot.ARCHIVES_DIR_PROPERTY, archives.toString());

    final Path specDir = ReferenceTestRoot.fromSystemProperties().getSpecDirectory("minimal");

    assertThat(Files.exists(specDir.resolve("phase0/x.yaml"))).isTrue();
  }

  @Test
  void fromSystemProperties_shouldUseRootDir() throws IOException {
    final Path rootDir = Files.createDirectories(tmp.resolve("tests"));
    System.setProperty(ReferenceTestRoot.ROOT_DIR_PROPERTY, rootDir.toString());

    assertThat(ReferenceTestRoot.fromSystemProperties().getSpecDirectory("minimal"))
        .isEqualTo(rootDir.resolve("minimal"));
  }

  @Test
  void fromSystemProperties_shouldFailClearlyWhenNothingIsConfigured() {
    assertThatThrownBy(ReferenceTestRoot::fromSystemProperties)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(ReferenceTestRoot.ARCHIVES_DIR_PROPERTY)
        .hasMessageContaining(ReferenceTestRoot.ROOT_DIR_PROPERTY);
  }

  private static void zip(final Path zipFile, final Map<String, String> entries)
      throws IOException {
    Files.createDirectories(zipFile.getParent());
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipFile))) {
      for (final Map.Entry<String, String> entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue().getBytes(UTF_8));
        out.closeEntry();
      }
    }
  }
}

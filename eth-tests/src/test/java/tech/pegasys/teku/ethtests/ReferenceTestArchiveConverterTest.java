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

package tech.pegasys.teku.ethtests;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.ethtests.ReferenceTestArchiveConverter.Options;

class ReferenceTestArchiveConverterTest {

  @TempDir Path tmp;

  @Test
  void shouldCopyFilesAndSkipDirectoryEntries() throws IOException {
    final Path tarGz = tmp.resolve("in.tar.gz");
    try (TarGzTestWriter tar = new TarGzTestWriter(tarGz)) {
      tar.directory("tests/");
      tar.directory("tests/minimal/");
      tar.file("tests/minimal/meta.yaml", "a: 1\n".getBytes(UTF_8));
      tar.file("tests/minimal/pre.ssz_snappy", "abc".getBytes(UTF_8));
    }
    final Path zip = tmp.resolve("out.zip");

    ReferenceTestArchiveConverter.convert(tarGz, zip, Options.DEFAULT);

    assertThat(entries(zip))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("tests/minimal/meta.yaml", "a: 1\n", "tests/minimal/pre.ssz_snappy", "abc"));
  }

  @Test
  void shouldDeflateEveryEntry() throws IOException {
    // snappy leaves a lot on the table: deflating .ssz_snappy states shrinks mainnet ~3x
    final Path tarGz = tmp.resolve("in.tar.gz");
    try (TarGzTestWriter tar = new TarGzTestWriter(tarGz)) {
      tar.file("a/meta.yaml", "a: 1\n".repeat(100).getBytes(UTF_8));
      tar.file("a/pre.ssz_snappy", new byte[4096]);
    }
    final Path zip = tmp.resolve("out.zip");

    ReferenceTestArchiveConverter.convert(tarGz, zip, Options.DEFAULT);

    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      assertThat(zipFile.getEntry("a/pre.ssz_snappy").getMethod()).isEqualTo(ZipEntry.DEFLATED);
      assertThat(zipFile.getEntry("a/pre.ssz_snappy").getCompressedSize()).isLessThan(4096);
      assertThat(zipFile.getEntry("a/meta.yaml").getMethod()).isEqualTo(ZipEntry.DEFLATED);
    }
  }

  @Test
  void shouldStripPrefixAndAddPrefix() throws IOException {
    final Path tarGz = tmp.resolve("in.tar.gz");
    try (TarGzTestWriter tar = new TarGzTestWriter(tarGz)) {
      tar.file("tests/minimal/meta.yaml", "x".getBytes(UTF_8));
      tar.file("./verify/case.yaml", "y".getBytes(UTF_8));
    }
    final Path zip = tmp.resolve("out.zip");

    ReferenceTestArchiveConverter.convert(tarGz, zip, new Options("tests/", "bls/", null, false));

    // "./" is always dropped; the strip prefix is only removed when present
    assertThat(entries(zip).keySet())
        .containsExactlyInAnyOrder("bls/minimal/meta.yaml", "bls/verify/case.yaml");
  }

  @Test
  void shouldFilterByRegexAndFlatten() throws IOException {
    final Path tarGz = tmp.resolve("in.tar.gz");
    try (TarGzTestWriter tar = new TarGzTestWriter(tarGz)) {
      tar.file("repo-1.0/README.md", "no".getBytes(UTF_8));
      tar.file("repo-1.0/tests/generated/a.json", "{}".getBytes(UTF_8));
      tar.file("repo-1.0/tests/generated/nested/b.json", "[]".getBytes(UTF_8));
    }
    final Path zip = tmp.resolve("out.zip");

    ReferenceTestArchiveConverter.convert(
        tarGz,
        zip,
        new Options(
            null, "slashing-protection-interchange/", ".*/tests/generated/[^/]+\\.json", true));

    assertThat(entries(zip))
        .containsExactly(Map.entry("slashing-protection-interchange/a.json", "{}"));
  }

  @Test
  void shouldHandleGnuLongNamesAndPaxPaths() throws IOException {
    final String longDir = "tests/minimal/" + "very_long_directory_name_".repeat(6) + "/";
    final Path tarGz = tmp.resolve("in.tar.gz");
    try (TarGzTestWriter tar = new TarGzTestWriter(tarGz)) {
      tar.fileWithGnuLongName(longDir + "gnu.yaml", "g".getBytes(UTF_8));
      tar.fileWithPaxPath(longDir + "pax.yaml", "p".getBytes(UTF_8));
      tar.file("tests/minimal/short.yaml", "s".getBytes(UTF_8));
    }
    final Path zip = tmp.resolve("out.zip");

    ReferenceTestArchiveConverter.convert(tarGz, zip, Options.DEFAULT);

    assertThat(entries(zip))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                longDir + "gnu.yaml",
                "g",
                longDir + "pax.yaml",
                "p",
                "tests/minimal/short.yaml",
                "s"));
  }

  @Test
  void shouldParseCommandLine() throws IOException {
    final Path tarGz = tmp.resolve("in.tar.gz");
    try (TarGzTestWriter tar = new TarGzTestWriter(tarGz)) {
      tar.file("tests/general/x.yaml", "x".getBytes(UTF_8));
    }
    final Path zip = tmp.resolve("out.zip");

    ReferenceTestArchiveConverter.main(
        new String[] {tarGz.toString(), zip.toString(), "--strip-prefix", "tests/"});

    assertThat(entries(zip).keySet()).containsExactly("general/x.yaml");
  }

  @Test
  void shouldRejectFlagWithoutValue() {
    final Path tarGz = tmp.resolve("in.tar.gz");
    final Path zip = tmp.resolve("out.zip");

    assertThatThrownBy(
            () ->
                ReferenceTestArchiveConverter.main(
                    new String[] {tarGz.toString(), zip.toString(), "--strip-prefix"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--strip-prefix");
  }

  @Test
  void shouldReportCorruptHeaderAsIoException() throws IOException {
    final Path tarGz = tmp.resolve("in.tar.gz");
    final byte[] header = new byte[512];
    System.arraycopy("bad".getBytes(UTF_8), 0, header, 0, 3);
    System.arraycopy("zz".getBytes(UTF_8), 0, header, 124, 2); // size field is not octal
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tarGz))) {
      out.write(header);
      out.write(new byte[1024]);
    }
    final Path zip = tmp.resolve("out.zip");

    assertThatThrownBy(() -> ReferenceTestArchiveConverter.convert(tarGz, zip, Options.DEFAULT))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("tar header");
  }

  private static Map<String, String> entries(final Path zip) throws IOException {
    final Map<String, String> result = new LinkedHashMap<>();
    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      for (final ZipEntry entry : zipFile.stream().toList()) {
        assertThat(entry.isDirectory()).as("directory entry %s", entry.getName()).isFalse();
        result.put(
            entry.getName(), new String(zipFile.getInputStream(entry).readAllBytes(), UTF_8));
      }
    }
    return result;
  }
}

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
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceTestFinderTest {

  @TempDir Path tmp;
  private Path archives;

  @BeforeEach
  void createArchives() throws IOException {
    archives = tmp.resolve("archives");
    zip(
        archives.resolve("minimal.zip"),
        List.of(
            "minimal/phase0/sanity/blocks/pyspec_tests/case_a/meta.yaml",
            "minimal/phase0/sanity/blocks/pyspec_tests/case_a/pre.ssz_snappy",
            "minimal/phase0/ssz_static/Checkpoint/ssz_random/case_0/serialized.ssz_snappy",
            "minimal/phase0/shuffling/core/shuffle/mapping.yaml",
            "minimal/phase0/bls/sign/case_1/data.yaml",
            "minimal/deneb/kzg/blob_to_kzg_commitment/case_1/data.yaml",
            "minimal/deneb/merkle_proof/BeaconBlockBody/blob_kzg_commitments_merkle_proof/case_1/proof.yaml"));
    zip(archives.resolve("bls.zip"), List.of("bls/verify/verify_case_1.yaml"));
    zip(
        archives.resolve("slashing-protection-interchange.zip"),
        List.of("slashing-protection-interchange/a.json"));
    System.setProperty(ReferenceTestRoot.ARCHIVES_DIR_PROPERTY, archives.toString());
  }

  @AfterEach
  void clearProperties() {
    System.clearProperty(ReferenceTestRoot.ARCHIVES_DIR_PROPERTY);
  }

  @Test
  void shouldFindEveryTestTypeInsideArchives() throws IOException {
    final List<TestDefinition> tests;
    try (Stream<TestDefinition> stream =
        ReferenceTestFinder.findReferenceTests(ReferenceTestRoot.ofArchivesDir(archives))) {
      tests = stream.toList();
    }

    assertThat(tests)
        .extracting(
            TestDefinition::getFork,
            TestDefinition::getConfigName,
            TestDefinition::getTestType,
            TestDefinition::getTestName,
            TestDefinition::getPathFromPhaseTestDir)
        .containsExactlyInAnyOrder(
            tuple("", "bls", "bls/verify", "verify_case_1.yaml", "verify"),
            tuple("phase0", "minimal", "bls/sign", "case_1", "bls/sign/case_1"),
            tuple(
                "phase0",
                "minimal",
                "ssz_static/Checkpoint",
                "ssz_random/case_0",
                "ssz_static/Checkpoint/ssz_random/case_0"),
            tuple("phase0", "minimal", "shuffling", "core/shuffle", "shuffling/core/shuffle"),
            tuple(
                "phase0",
                "minimal",
                "sanity/blocks",
                "case_a",
                "sanity/blocks/pyspec_tests/case_a"),
            tuple(
                "deneb",
                "minimal",
                "kzg/blob_to_kzg_commitment",
                "case_1",
                "kzg/blob_to_kzg_commitment/case_1"),
            tuple(
                "deneb",
                "minimal",
                "merkle_proof/BeaconBlockBody",
                "blob_kzg_commitments_merkle_proof/case_1",
                "merkle_proof/BeaconBlockBody/blob_kzg_commitments_merkle_proof/case_1"),
            tuple(
                "",
                "slashing-protection-interchange",
                "slashing-protection-interchange",
                "a.json",
                ""));
  }

  @Test
  void testDirectory_shouldResolveIntoTheArchive() {
    final TestDefinition pyspec =
        new TestDefinition(
            "phase0", "minimal", "sanity/blocks", "case_a", "sanity/blocks/pyspec_tests/case_a");
    final TestDefinition blsRef =
        new TestDefinition("", "bls", "bls/verify", "verify_case_1.yaml", "verify");
    final TestDefinition slashing =
        new TestDefinition(
            "", "slashing-protection-interchange", "slashing-protection-interchange", "a.json", "");

    assertThat(Files.exists(pyspec.getTestDirectory().resolve("meta.yaml"))).isTrue();
    assertThat(Files.exists(pyspec.getTestDirectory().resolve("post.ssz_snappy"))).isFalse();
    assertThat(Files.exists(blsRef.getTestDirectory().resolve("verify_case_1.yaml"))).isTrue();
    assertThat(Files.exists(slashing.getTestDirectory().resolve("a.json"))).isTrue();
  }

  @Test
  void testDirectory_shouldAcceptWindowsSeparatorsInRelativePath() {
    final TestDefinition pyspec =
        new TestDefinition(
            "phase0",
            "minimal",
            "sanity\\blocks",
            "case_a",
            "sanity\\blocks\\pyspec_tests\\case_a");

    assertThat(pyspec.getTestType()).isEqualTo("sanity/blocks");
    assertThat(pyspec.getPathFromPhaseTestDir()).isEqualTo("sanity/blocks/pyspec_tests/case_a");
    assertThat(Files.exists(pyspec.getTestDirectory().resolve("meta.yaml"))).isTrue();
  }

  private static void zip(final Path zipFile, final List<String> entries) throws IOException {
    Files.createDirectories(zipFile.getParent());
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipFile))) {
      for (final String entry : entries) {
        out.putNextEntry(new ZipEntry(entry));
        out.write("{}".getBytes(UTF_8));
        out.closeEntry();
      }
    }
  }
}

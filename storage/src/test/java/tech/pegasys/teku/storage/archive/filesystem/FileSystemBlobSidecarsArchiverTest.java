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

package tech.pegasys.teku.storage.archive.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class FileSystemBlobSidecarsArchiverTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalDeneb();
  private final Predicates predicates = new Predicates(SPEC.getGenesisSpecConfig());
  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
      SchemaDefinitionsDeneb.required(SPEC.getGenesisSchemaDefinitions());
  private final MiscHelpersDeneb miscHelpersDeneb =
      new MiscHelpersDeneb(
          SPEC.getGenesisSpecConfig().toVersionDeneb().orElseThrow(),
          predicates,
          schemaDefinitionsDeneb);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

  static Path testTempDir;
  static FileSystemBlobSidecarsArchiver blobSidecarsArchiver;

  @BeforeAll
  static void beforeEach() throws IOException {
    testTempDir = Files.createTempDirectory("blobs");
    blobSidecarsArchiver = new FileSystemBlobSidecarsArchiver(SPEC, testTempDir);
  }

  @AfterEach
  public void tearDown() throws IOException {
    // Delete the temporary directory after each test
    if (Files.exists(testTempDir)) {
      try (Stream<Path> walk = Files.walk(testTempDir)) {
        walk.map(Path::toFile)
            .forEach(
                file -> {
                  if (!file.delete()) {
                    file.deleteOnExit();
                  }
                });
      }
    }
  }

  @Test
  void testResolveArchivePath() {
    final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex =
        new SlotAndBlockRootAndBlobIndex(
            UInt64.ONE, dataStructureUtil.randomBytes32(), UInt64.ZERO);
    final Path path =
        blobSidecarsArchiver.resolveArchivePath(
            slotAndBlockRootAndBlobIndex.getSlotAndBlockRoot().getBlockRoot());

    // Check if the file path is correct. Doesn't check the intermediate directories.
    assertTrue(path.toString().startsWith(testTempDir.toString()));
    assertTrue(
        path.toString()
            .endsWith(slotAndBlockRootAndBlobIndex.getBlockRoot().toUnprefixedHexString()));
  }

  @ParameterizedTest
  @MethodSource("testResolveIndexPathArguments")
  void testResolveIndexPath(final UInt64 slot, final String expectedIndexFileName) {
    final Path actualIndexFile = blobSidecarsArchiver.resolveIndexFile(slot);
    assertThat(actualIndexFile).hasFileName(expectedIndexFileName);
  }

  @Test
  void testArchiveWithEmptyList() {
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    // archiving
    blobSidecarsArchiver.archive(slotAndBlockRoot, List.of());

    // retrieving
    final Optional<List<BlobSidecar>> blobSidecars =
        blobSidecarsArchiver.retrieve(slotAndBlockRoot);
    // empty list
    assertThat(blobSidecars).hasValue(List.of());
  }

  @Test
  void testArchiveAndRetrieveBlobSidecars() {
    final UInt64 slot = UInt64.valueOf(42);
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(slot, dataStructureUtil.randomBytes32());
    final List<BlobSidecar> blobSidecars =
        List.of(createBlobSidecar(slot), createBlobSidecar(slot));

    // archiving
    blobSidecarsArchiver.archive(slotAndBlockRoot, blobSidecars);

    // test index file exists
    assertThat(testTempDir.resolve("0-99999_index.dat")).exists();

    // retrieving by slot and block root
    final Optional<List<BlobSidecar>> retrievedBlobSidecarsByRoot =
        blobSidecarsArchiver.retrieve(slotAndBlockRoot);

    assertThat(retrievedBlobSidecarsByRoot).hasValue(blobSidecars);

    // retrieving by slot (using index file)
    final Optional<List<BlobSidecar>> retrievedBlobSidecarsBySlot =
        blobSidecarsArchiver.retrieve(slotAndBlockRoot.getSlot());

    assertThat(retrievedBlobSidecarsBySlot).hasValue(blobSidecars);
  }

  private BlobSidecar createBlobSidecar(final UInt64 slot) {
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(slot, 1);
    final Blob blob = dataStructureUtil.randomValidBlob();
    final SszKZGProof proof = dataStructureUtil.randomSszKZGProof();
    return miscHelpersDeneb.constructBlobSidecar(signedBeaconBlock, UInt64.ZERO, blob, proof);
  }

  private static Stream<Arguments> testResolveIndexPathArguments() {
    return Stream.of(
        Arguments.of(UInt64.valueOf(0L), "0-99999_index.dat"),
        Arguments.of(UInt64.valueOf(42L), "0-99999_index.dat"),
        Arguments.of(UInt64.valueOf(99999L), "0-99999_index.dat"),
        Arguments.of(UInt64.valueOf(100000L), "100000-199999_index.dat"),
        Arguments.of(UInt64.valueOf(100001L), "100000-199999_index.dat"),
        Arguments.of(UInt64.valueOf(199999L), "100000-199999_index.dat"),
        Arguments.of(UInt64.valueOf(200000L), "200000-299999_index.dat"),
        Arguments.of(UInt64.valueOf(999999999L), "999900000-999999999_index.dat"),
        Arguments.of(UInt64.valueOf(1000000000L), "1000000000-1000099999_index.dat"),
        Arguments.of(UInt64.valueOf(1L), "0-99999_index.dat"),
        Arguments.of(UInt64.valueOf(99998L), "0-99999_index.dat"),
        Arguments.of(UInt64.valueOf(99999L), "0-99999_index.dat"),
        Arguments.of(UInt64.valueOf(100000L), "100000-199999_index.dat"),
        Arguments.of(UInt64.valueOf(123456789L), "123400000-123499999_index.dat"));
  }
}

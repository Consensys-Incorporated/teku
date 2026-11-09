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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExtensionBlobReconstructorGloasTest extends BlobReconstructionAbstractTest {
  private DataStructureUtil gloasDataStructureUtil;
  private BlobSchema blobSchema;
  private ExtensionBlobReconstructor extensionBlobReconstructor;
  private SignedBeaconBlock testBlock;
  private List<DataColumnSidecar> dataColumnSidecars;
  private final int commitmentCount = 2;
  private int numberOfColumns;

  @BeforeEach
  void setupGloas() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final MiscHelpersGloas miscHelpersGloas =
        MiscHelpersGloas.required(gloasSpec.forMilestone(SpecMilestone.GLOAS).miscHelpers());
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(
            gloasSpec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
    numberOfColumns = gloasSpec.getNumberOfDataColumns().orElseThrow();
    blobSchema = schemaDefinitionsElectra.getBlobSchema();
    extensionBlobReconstructor = new ExtensionBlobReconstructor(gloasSpec, () -> blobSchema);
    final BeaconBlock beaconBlock =
        gloasDataStructureUtil.randomBeaconBlock(
            UInt64.ZERO,
            gloasDataStructureUtil.randomBeaconBlockBodyWithCommitments(commitmentCount));
    testBlock = gloasDataStructureUtil.signedBlock(beaconBlock);
    final List<BlobAndCellProofs> blobAndCellProofs =
        IntStream.range(0, commitmentCount)
            .mapToObj(
                i ->
                    new BlobAndCellProofs(
                        gloasDataStructureUtil.randomValidBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> gloasDataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    dataColumnSidecars =
        miscHelpersGloas.constructDataColumnSidecars(
            Optional.empty(),
            new SlotAndBlockRoot(testBlock.getSlot(), testBlock.getRoot()),
            Optional.empty(),
            Optional.empty(),
            blobAndCellProofs);
  }

  @Test
  public void shouldNotBuildIfNotFirstHalfOfSidecars() {
    final List<DataColumnSidecar> almostHalfSidecars =
        dataColumnSidecars.subList(1, numberOfColumns / 2);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), almostHalfSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);

    final List<DataColumnSidecar> notFirstHalfSidecars =
        dataColumnSidecars.subList(1, numberOfColumns / 2 + 1);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), notFirstHalfSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
  }

  @Test
  public void shouldBuildAndFilterBlobsFromSidecars() {
    final List<DataColumnSidecar> halfSidecars = dataColumnSidecars.subList(0, numberOfColumns / 2);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), halfSidecars, List.of()))
        .succeedsWithin(Duration.ofSeconds(5))
        .matches(result -> result.isPresent() && result.orElseThrow().size() == commitmentCount);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(),
                halfSidecars,
                List.of(UInt64.ZERO, UInt64.valueOf(1))))
        .succeedsWithin(Duration.ofSeconds(5))
        .matches(result -> result.isPresent() && result.orElseThrow().size() == commitmentCount);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO)))
        .succeedsWithin(Duration.ofSeconds(5))
        .matches(result -> result.isPresent() && result.orElseThrow().size() == 1);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(),
                halfSidecars,
                List.of(UInt64.ZERO, UInt64.valueOf(2))))
        .succeedsWithin(Duration.ofSeconds(5))
        .matches(result -> result.isPresent() && result.orElseThrow().size() == 1);
  }
}

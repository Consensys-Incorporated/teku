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

public class CryptoBlobReconstructorGloasTest extends BlobReconstructionAbstractTest {
  private DataStructureUtil gloasDataStructureUtil;
  private BlobSchema blobSchema;
  private CryptoBlobReconstructor cryptoBlobReconstructor;
  private SignedBeaconBlock testBlock;
  private int numberOfColumns;
  private List<DataColumnSidecar> dataColumnSidecars;

  @BeforeEach
  void setupGloas() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final MiscHelpersGloas miscHelpersGloas =
        MiscHelpersGloas.required(gloasSpec.forMilestone(SpecMilestone.GLOAS).miscHelpers());
    SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(
            gloasSpec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
    blobSchema = schemaDefinitionsElectra.getBlobSchema();
    cryptoBlobReconstructor = new CryptoBlobReconstructor(gloasSpec, () -> blobSchema);

    // test block with commitments
    final int commitmentCount = 2;
    final BeaconBlock beaconBlock =
        gloasDataStructureUtil.randomBeaconBlock(
            UInt64.ZERO,
            gloasDataStructureUtil.randomBeaconBlockBodyWithCommitments(commitmentCount));
    testBlock = gloasDataStructureUtil.signedBlock(beaconBlock);
    numberOfColumns = gloasSpec.getNumberOfDataColumns().orElseThrow();
    List<BlobAndCellProofs> blobAndCellProofs =
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
  public void shouldNotBuildIfNotHalfOfSidecars() {
    final List<DataColumnSidecar> almostHalfSidecars =
        dataColumnSidecars.subList(1, numberOfColumns / 2);

    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), almostHalfSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
  }

  @Test
  public void shouldBuildAndFilterBlobsFromSidecars() {
    final List<DataColumnSidecar> halfSidecars =
        dataColumnSidecars.subList(5, numberOfColumns / 2 + 5);

    // we have non-operational KZG in tests so it will not match,
    // but at least we could check the size
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), halfSidecars, List.of()))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 2);
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(),
                halfSidecars,
                List.of(UInt64.ZERO, UInt64.valueOf(1))))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 2);
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO)))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 1);
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(),
                halfSidecars,
                List.of(UInt64.ZERO, UInt64.valueOf(2))))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 1);
  }
}

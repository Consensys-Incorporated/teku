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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobReconstructorGloasTest extends BlobReconstructionAbstractTest {
  private DataStructureUtil gloasDataStructureUtil;
  private BlobSchema blobSchema;
  private BlobReconstructor blobReconstructor;
  private List<DataColumnSidecar> halfSidecars;
  private final int commitmentCount = 2;

  @BeforeEach
  void setupGloas() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final MiscHelpersGloas miscHelpersGloas =
        MiscHelpersGloas.required(gloasSpec.forMilestone(SpecMilestone.GLOAS).miscHelpers());
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(
            gloasSpec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
    blobSchema = schemaDefinitionsElectra.getBlobSchema();
    blobReconstructor =
        new BlobReconstructor(gloasSpec, () -> blobSchema) {
          @Override
          SafeFuture<Optional<List<Blob>>> reconstructBlobs(
              final SlotAndBlockRoot slotAndBlockRoot,
              final List<DataColumnSidecar> existingSidecars,
              final List<UInt64> blobIndices) {
            return SafeFuture.completedFuture(Optional.empty());
          }
        };
    final BeaconBlock beaconBlock =
        gloasDataStructureUtil.randomBeaconBlock(
            UInt64.ZERO,
            gloasDataStructureUtil.randomBeaconBlockBodyWithCommitments(commitmentCount));
    final SignedBeaconBlock block = gloasDataStructureUtil.signedBlock(beaconBlock);
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
    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersGloas.constructDataColumnSidecars(
            Optional.empty(),
            new SlotAndBlockRoot(block.getSlot(), block.getRoot()),
            Optional.empty(),
            Optional.empty(),
            blobAndCellProofs);
    int numberOfColumns = gloasSpec.getNumberOfDataColumns().orElseThrow();
    halfSidecars = dataColumnSidecars.subList(0, numberOfColumns / 2);
  }

  @Test
  public void shouldBuildBlobFromSidecars() {
    assertThat(blobReconstructor.constructBlob(halfSidecars, 0, blobSchema)).isNotNull();
    assertThat(blobReconstructor.constructBlob(halfSidecars, 1, blobSchema)).isNotNull();
    assertThatThrownBy(() -> blobReconstructor.constructBlob(halfSidecars, 2, blobSchema));
  }

  @Test
  public void shouldBuildAndFilterBlobsFromSidecars() {
    // empty blob indices (should return all blobs)
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(), blobSchema))
        .matches(blobs -> blobs.size() == commitmentCount);

    // specific blob indices
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(1)), blobSchema))
        .matches(blobs -> blobs.size() == commitmentCount);

    // single blob index
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(UInt64.ZERO), blobSchema))
        .matches(blobs -> blobs.size() == 1);

    // out of range blob index
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(2)), blobSchema))
        .matches(blobs -> blobs.size() == 1);
  }
}

/*
 * Copyright (c) 2024 Consensys Incorporated. All rights reserved.
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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlobReconstructorFuluTest extends BlobReconstructionAbstractTest {
  final MiscHelpersFulu miscHelpersFulu =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(
          spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
  final BlobSchema blobSchema = schemaDefinitionsElectra.getBlobSchema();

  private final BlobReconstructor blobReconstructor =
      new BlobReconstructor(spec, () -> blobSchema) {
        @Override
        SafeFuture<Optional<List<Blob>>> reconstructBlobs(
            final SlotAndBlockRoot slotAndBlockRoot,
            final List<DataColumnSidecar> existingSidecars,
            final List<UInt64> blobIndices) {
          return SafeFuture.completedFuture(Optional.empty());
        }
      };

  @Test
  public void shouldBuildBlobFromSidecars() {
    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(2);
    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<DataColumnSidecar> halfSidecars = dataColumnSidecars.subList(0, numberOfColumns / 2);
    assertThat(blobsAndMatrix.blobs()).hasSize(2);

    assertThat(blobReconstructor.constructBlob(halfSidecars, 0, blobSchema))
        .isEqualTo(blobsAndMatrix.blobs().get(0));
    assertThat(blobReconstructor.constructBlob(halfSidecars, 1, blobSchema))
        .isEqualTo(blobsAndMatrix.blobs().get(1));
    assertThatThrownBy(() -> blobReconstructor.constructBlob(halfSidecars, 2, blobSchema));
  }

  @Test
  public void shouldBuildAndFilterBlobsFromSidecars() {
    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(2);

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<DataColumnSidecar> halfSidecars = dataColumnSidecars.subList(0, numberOfColumns / 2);
    assertThat(blobsAndMatrix.blobs()).hasSize(2);
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(), blobSchema))
        .isEqualTo(blobsAndMatrix.blobs());
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(1)), blobSchema))
        .isEqualTo(blobsAndMatrix.blobs());
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(UInt64.ZERO), blobSchema))
        .isEqualTo(blobsAndMatrix.blobs().subList(0, 1));
    assertThat(
            blobReconstructor.reconstructBlobsFromFirstHalfDataColumns(
                halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(2)), blobSchema))
        .isEqualTo(blobsAndMatrix.blobs().subList(0, 1));
  }
}

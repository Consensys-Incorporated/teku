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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ExtensionBlobReconstructorFuluTest extends BlobReconstructionAbstractTest {
  final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(
          spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
  final BlobSchema blobSchema = schemaDefinitionsElectra.getBlobSchema();
  final MiscHelpersFulu miscHelpersFulu =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());

  private final ExtensionBlobReconstructor extensionBlobReconstructor =
      new ExtensionBlobReconstructor(spec, () -> blobSchema);

  @Test
  public void shouldNotBuildIfNotFirstHalfOfSidecars() {
    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(2);

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    assertThat(blobsAndMatrix.blobs()).hasSize(2);

    final List<DataColumnSidecar> almostHalfSidecars =
        dataColumnSidecars.subList(1, numberOfColumns / 2);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), almostHalfSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);

    final List<DataColumnSidecar> notFirstHalfSidecars =
        dataColumnSidecars.subList(1, numberOfColumns / 2 + 1);
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), notFirstHalfSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
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
            extensionBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of()))
        .isCompletedWithValueMatching(
            result -> result.orElseThrow().equals(blobsAndMatrix.blobs()));
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(1))))
        .isCompletedWithValueMatching(
            result -> result.orElseThrow().equals(blobsAndMatrix.blobs()));
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO)))
        .isCompletedWithValueMatching(
            result -> result.orElseThrow().equals(blobsAndMatrix.blobs().subList(0, 1)));
    assertThat(
            extensionBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(2))))
        .isCompletedWithValueMatching(
            result -> result.orElseThrow().equals(blobsAndMatrix.blobs().subList(0, 1)));
  }
}

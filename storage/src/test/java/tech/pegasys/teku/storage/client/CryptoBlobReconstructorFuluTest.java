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

public class CryptoBlobReconstructorFuluTest extends BlobReconstructionAbstractTest {
  final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(
          spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
  final BlobSchema blobSchema = schemaDefinitionsElectra.getBlobSchema();
  final MiscHelpersFulu miscHelpersFulu =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());

  private final CryptoBlobReconstructor cryptoBlobReconstructor =
      new CryptoBlobReconstructor(spec, () -> blobSchema);

  @Test
  public void shouldNotBuildIfNotHalfOfSidecars() {
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
            cryptoBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), almostHalfSidecars, List.of()))
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
    final List<DataColumnSidecar> halfSidecars =
        dataColumnSidecars.subList(5, numberOfColumns / 2 + 5);
    assertThat(blobsAndMatrix.blobs()).hasSize(2);

    // we have non-operational KZG in tests so it will not match,
    // but at least we could check the size
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of()))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 2);
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(1))))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 2);
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO)))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 1);
    assertThat(
            cryptoBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), halfSidecars, List.of(UInt64.ZERO, UInt64.valueOf(2))))
        .isCompletedWithValueMatching(result -> result.orElseThrow().size() == 1);
  }
}

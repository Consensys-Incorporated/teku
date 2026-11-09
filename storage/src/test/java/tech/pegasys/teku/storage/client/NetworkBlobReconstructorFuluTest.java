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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;

public class NetworkBlobReconstructorFuluTest extends BlobReconstructionAbstractTest {
  final SchemaDefinitionsElectra schemaDefinitionsElectra =
      SchemaDefinitionsElectra.required(
          spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
  final BlobSchema blobSchema = schemaDefinitionsElectra.getBlobSchema();
  final MiscHelpersFulu miscHelpersFulu =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  final DataColumnSidecarNetworkRetriever dataColumnSidecarNetworkRetriever =
      mock(DataColumnSidecarNetworkRetriever.class);

  private final NetworkBlobReconstructor networkBlobReconstructor =
      new NetworkBlobReconstructor(spec, () -> blobSchema, dataColumnSidecarNetworkRetriever);

  @Test
  public void shouldNotRetrieveIfDisabled() {
    when(dataColumnSidecarNetworkRetriever.isEnabled()).thenReturn(false);

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(2);

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());
    assertThat(
            networkBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), dataColumnSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
    verify(dataColumnSidecarNetworkRetriever).isEnabled();
    verifyNoMoreInteractions(dataColumnSidecarNetworkRetriever);
  }

  @Test
  public void shouldBuildAndFilterBlobsFromSidecars() {
    when(dataColumnSidecarNetworkRetriever.isEnabled()).thenReturn(true);
    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(2);

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<DataColumnSidecar> halfSidecarsWithGap =
        dataColumnSidecars.subList(0, numberOfColumns / 2 - 1);
    assertThat(blobsAndMatrix.blobs()).hasSize(2);

    final DataColumnSidecar missingSidecar = dataColumnSidecars.get(numberOfColumns / 2 - 1);
    when(dataColumnSidecarNetworkRetriever.retrieveDataColumnSidecars(
            List.of(DataColumnSlotAndIdentifier.fromDataColumn(missingSidecar))))
        .thenReturn(SafeFuture.completedFuture(List.of(missingSidecar)));
    assertThat(
            networkBlobReconstructor.reconstructBlobs(
                missingSidecar.getSlotAndBlockRoot(), halfSidecarsWithGap, List.of()))
        .isCompletedWithValueMatching(
            result -> result.orElseThrow().equals(blobsAndMatrix.blobs()));
  }

  @Test
  public void shouldReturnEmptyWhenNetworkRetrieverReturnsPartialResults() {
    when(dataColumnSidecarNetworkRetriever.isEnabled()).thenReturn(true);
    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(2);

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();

    // Pass sidecars with 2 missing
    final List<DataColumnSidecar> sidecarsWithGaps =
        dataColumnSidecars.subList(0, numberOfColumns / 2 - 2);

    // Network retriever returns only 1 of the 2 missing sidecars
    final DataColumnSidecar missingSidecar1 = dataColumnSidecars.get(numberOfColumns / 2 - 2);
    when(dataColumnSidecarNetworkRetriever.retrieveDataColumnSidecars(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(missingSidecar1)));

    assertThat(
            networkBlobReconstructor.reconstructBlobs(
                block.getSlotAndBlockRoot(), sidecarsWithGaps, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
  }
}

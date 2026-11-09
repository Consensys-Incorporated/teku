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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;

public class NetworkBlobReconstructorGloasTest extends BlobReconstructionAbstractTest {
  private DataStructureUtil gloasDataStructureUtil;
  private BlobSchema blobSchema;
  private SignedBeaconBlock testBlock;
  private final DataColumnSidecarNetworkRetriever dataColumnSidecarNetworkRetriever =
      mock(DataColumnSidecarNetworkRetriever.class);
  private NetworkBlobReconstructor networkBlobReconstructor;
  private List<DataColumnSidecar> dataColumnSidecars;
  private int numberOfColumns;
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
    networkBlobReconstructor =
        new NetworkBlobReconstructor(
            gloasSpec, () -> blobSchema, dataColumnSidecarNetworkRetriever);
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
    numberOfColumns = gloasSpec.getNumberOfDataColumns().orElseThrow();
  }

  @Test
  public void shouldNotRetrieveIfDisabled() {
    when(dataColumnSidecarNetworkRetriever.isEnabled()).thenReturn(false);
    assertThat(
            networkBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), dataColumnSidecars, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
    verify(dataColumnSidecarNetworkRetriever).isEnabled();
    verifyNoMoreInteractions(dataColumnSidecarNetworkRetriever);
  }

  @Test
  public void shouldBuildAndFilterBlobsFromSidecars() {
    when(dataColumnSidecarNetworkRetriever.isEnabled()).thenReturn(true);
    final List<DataColumnSidecar> halfSidecarsWithGap =
        dataColumnSidecars.subList(0, numberOfColumns / 2 - 1);

    final DataColumnSidecar missingSidecar = dataColumnSidecars.get(numberOfColumns / 2 - 1);
    when(dataColumnSidecarNetworkRetriever.retrieveDataColumnSidecars(
            List.of(DataColumnSlotAndIdentifier.fromDataColumn(missingSidecar))))
        .thenReturn(SafeFuture.completedFuture(List.of(missingSidecar)));
    assertThat(
            networkBlobReconstructor.reconstructBlobs(
                missingSidecar.getSlotAndBlockRoot(), halfSidecarsWithGap, List.of()))
        .succeedsWithin(Duration.ofSeconds(5))
        .matches(result -> result.isPresent() && result.orElseThrow().size() == commitmentCount);
  }

  @Test
  public void shouldReturnEmptyWhenNetworkRetrieverReturnsPartialResults() {
    when(dataColumnSidecarNetworkRetriever.isEnabled()).thenReturn(true);
    // sidecars with 2 missing
    final List<DataColumnSidecar> sidecarsWithGaps =
        dataColumnSidecars.subList(0, numberOfColumns / 2 - 2);
    // retriever returns only 1 of the 2 missing sidecars
    final DataColumnSidecar missingSidecar = dataColumnSidecars.get(numberOfColumns / 2 - 2);
    when(dataColumnSidecarNetworkRetriever.retrieveDataColumnSidecars(any()))
        .thenReturn(SafeFuture.completedFuture(List.of(missingSidecar)));

    assertThat(
            networkBlobReconstructor.reconstructBlobs(
                testBlock.getSlotAndBlockRoot(), sidecarsWithGaps, List.of()))
        .isCompletedWithValueMatching(Optional::isEmpty);
  }
}

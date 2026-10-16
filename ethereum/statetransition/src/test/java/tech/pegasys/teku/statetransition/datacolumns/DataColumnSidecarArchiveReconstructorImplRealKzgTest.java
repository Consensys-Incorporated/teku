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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.KzgUtil;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * End-to-end correctness of the archive reconstruction: unlike {@link
 * DataColumnSidecarArchiveReconstructorImplTest}, this test uses a real KZG trusted setup and does
 * not mock the sidecar construction, so it verifies the reconstructed extension sidecars are
 * bit-for-bit identical to the originals.
 */
public class DataColumnSidecarArchiveReconstructorImplRealKzgTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final KZG kzg = KZG.getInstance(false);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final MiscHelpersFulu miscHelpers =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  private final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
  private final int halfColumns = numberOfColumns / 2;

  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final SidecarArchivePrunableChannel sidecarArchivePrunableChannel =
      mock(SidecarArchivePrunableChannel.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final DataColumnSidecarArchiveReconstructorImpl reconstructor =
      new DataColumnSidecarArchiveReconstructorImpl(
          chainDataClient,
          SyncAsyncRunner.SYNC_RUNNER,
          () -> true,
          spec,
          0,
          sidecarArchivePrunableChannel,
          metricsSystem,
          StubTimeProvider.withTimeInMillis(0));

  public DataColumnSidecarArchiveReconstructorImplRealKzgTest() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  @Test
  void reconstructsExtensionColumnsIdenticalToOriginalSidecars() {
    final List<BlobAndCellProofs> blobsAndCellProofs =
        IntStream.range(0, 4)
            .mapToObj(
                __ -> KzgUtil.computeBlobAndCellProofs(kzg, dataStructureUtil.randomValidBlob()))
            .toList();
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blobsAndCellProofs.size());

    // the canonical full set of column sidecars, built from the real blobs with real KZG
    final List<DataColumnSidecar> originalSidecars =
        miscHelpers.constructDataColumnSidecars(
            block.getMessage(),
            block.asHeader(),
            miscHelpers.computeExtendedMatrix(blobsAndCellProofs));
    assertThat(originalSidecars).hasSize(numberOfColumns);

    // supernodes retain the first half of the sidecars plus the extension columns' proofs
    final List<DataColumnSidecar> firstHalfSidecars = originalSidecars.subList(0, halfColumns);
    final List<List<KZGProof>> extensionProofs =
        originalSidecars.subList(halfColumns, numberOfColumns).stream()
            .map(sidecar -> sidecar.getKzgProofs().stream().map(SszKZGProof::getKZGProof).toList())
            .toList();

    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(extensionProofs));

    final int requestId = reconstructor.onRequest();
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      for (int index = halfColumns; index < numberOfColumns; index++) {
        final Optional<DataColumnSidecar> reconstructed =
            reconstructor
                .reconstructDataColumnSidecar(block, UInt64.valueOf(index), requestId)
                .join();
        assertThat(reconstructed).contains(originalSidecars.get(index));
      }

      // the reconstruction is observable in the logs at debug level
      assertThat(logCaptor.getDebugLogs())
          .anySatisfy(
              log ->
                  assertThat(log)
                      .contains("Reconstructed")
                      .contains("extension data column sidecars")
                      .contains("slot " + block.getSlot()));
    }

    // all indices share a single reconstruction task for the block: one success, no failures
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON,
                "data_column_sidecar_archive_reconstruction_successes_total"))
        .isEqualTo(1);
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON,
                "data_column_sidecar_archive_reconstruction_failures_total"))
        .isZero();
  }
}

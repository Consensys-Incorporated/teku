/*
 * Copyright Consensys Software Inc., 2025
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(
    signatureVerifierNoop = true,
    milestone = {SpecMilestone.FULU, SpecMilestone.GLOAS})
public class DataColumnSidecarArchiveReconstructorImplTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);
  private final SyncAsyncRunner asyncRunner = SyncAsyncRunner.SYNC_RUNNER;
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final SidecarArchivePrunableChannel sidecarArchivePrunableChannel =
      mock(SidecarArchivePrunableChannel.class);
  private final UpdatableStore store = mock(UpdatableStore.class);

  private int halfColumns;
  private DataColumnSidecarArchiveReconstructorImpl reconstructor;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
    halfColumns = specConfigFulu.getNumberOfColumns() / 2;

    when(chainDataClient.getStore()).thenReturn(store);
    when(store.getTimeSeconds()).thenReturn(UInt64.valueOf(1000));
    when(store.getGenesisTime()).thenReturn(ZERO);

    reconstructor =
        new DataColumnSidecarArchiveReconstructorImpl(
            chainDataClient,
            asyncRunner,
            () -> true,
            spec,
            0,
            sidecarArchivePrunableChannel,
            metricsSystem,
            timeProvider);
  }

  @TestTemplate
  public void shouldReturnEmptyWhenNoSidecarsAvailable() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final int requestId = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result =
        reconstructor
            .reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns), requestId)
            .join();

    assertThat(result).isEmpty();
    // an unsuccessful reconstruction is recorded as a failure and never as a success
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON,
                "data_column_sidecar_archive_reconstruction_failures_total"))
        .isEqualTo(1);
    assertThat(
            metricsSystem.getCounterValue(
                TekuMetricCategory.BEACON,
                "data_column_sidecar_archive_reconstruction_successes_total"))
        .isZero();
  }

  @TestTemplate
  public void shouldReturnEmptyWhenFirstHalfSidecarsAreForDifferentBlockRoot() {
    final UInt64 slot = UInt64.ONE;
    final SignedBeaconBlock block = createBlockWithBlobs(slot);
    final SignedBeaconBlock competingBlock = createBlockWithBlobs(slot);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(competingBlock);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int requestId = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result =
        reconstructor
            .reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns), requestId)
            .join();

    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void shouldReconstructSecondHalfSidecars() {
    final Spec spiedSpec = spy(spec);
    final DataColumnSidecarUtil mockUtil = mock(DataColumnSidecarUtil.class);
    doReturn(mockUtil).when(spiedSpec).getDataColumnSidecarUtil(any());

    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        new DataColumnSidecarArchiveReconstructorImpl(
            chainDataClient,
            asyncRunner,
            () -> true,
            spiedSpec,
            0,
            sidecarArchivePrunableChannel,
            metricsSystem,
            timeProvider);

    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    final UInt64 targetIndex = UInt64.valueOf(halfColumns);
    final DataColumnSidecar expectedSidecar =
        dataStructureUtil.randomDataColumnSidecar(block, targetIndex);
    final int numberOfColumns = halfColumns * 2;
    final List<DataColumnSidecar> allSidecars =
        IntStream.range(0, numberOfColumns)
            .mapToObj(
                i ->
                    i == targetIndex.intValue()
                        ? expectedSidecar
                        : dataStructureUtil.randomDataColumnSidecar(block, UInt64.valueOf(i)))
            .toList();

    when(mockUtil.getKzgCommitments(any())).thenReturn(mock());
    when(mockUtil.computeDataColumnKzgCommitmentsInclusionProof(any()))
        .thenReturn(Optional.empty());
    when(mockUtil.constructDataColumnSidecars(any(), any(), any(), any(), anyList()))
        .thenReturn(allSidecars);

    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int requestId = spiedReconstructor.onRequest();
    final Optional<DataColumnSidecar> result =
        spiedReconstructor.reconstructDataColumnSidecar(block, targetIndex, requestId).join();

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(expectedSidecar);
    verify(mockUtil).constructDataColumnSidecars(any(), any(), any(), any(), anyList());
  }

  @TestTemplate
  public void shouldRejectFirstHalfIndex() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final int requestId = reconstructor.onRequest();

    assertThatThrownBy(() -> reconstructor.reconstructDataColumnSidecar(block, ZERO, requestId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void shouldReuseTaskForSameBlockInSameRequest() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int requestId = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result1 =
        reconstructor
            .reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns), requestId)
            .join();
    final Optional<DataColumnSidecar> result2 =
        reconstructor
            .reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns + 1), requestId)
            .join();

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    // Should have only called getDataColumnSidecars once (same task reused)
    verify(chainDataClient).getDataColumnSidecars(any(UInt64.class), anyList());
  }

  @TestTemplate
  public void shouldReuseReconstructionAcrossRequests() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int firstRequest = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result1 =
        reconstructor
            .reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns), firstRequest)
            .join();
    reconstructor.onRequestCompleted(firstRequest);

    // a separate, later request for the same block reuses the cached result
    final int secondRequest = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result2 =
        reconstructor
            .reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns + 1), secondRequest)
            .join();

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    // reconstruction ran only once despite two independent requests
    verify(chainDataClient).getDataColumnSidecars(any(UInt64.class), anyList());
  }

  @TestTemplate
  @SuppressWarnings("FutureReturnValueIgnored")
  public void shouldRetryReconstructionAfterEmptyResult() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final int requestId = reconstructor.onRequest();
    reconstructor.reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns), requestId);

    // an empty (failed) reconstruction is not cached, so a later request retries it
    final int newRequestId = reconstructor.onRequest();
    reconstructor.reconstructDataColumnSidecar(block, UInt64.valueOf(halfColumns), newRequestId);

    verify(chainDataClient, times(2)).getDataColumnSidecars(any(UInt64.class), anyList());
  }

  @TestTemplate
  public void shouldReuseWithinRetentionWindowThenReconstructAfterExpiry() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final UInt64 index = UInt64.valueOf(halfColumns);
    reconstructor.reconstructDataColumnSidecar(block, index, reconstructor.onRequest()).join();

    // 25s later, still within the 30s retention window: the cached result is reused
    timeProvider.advanceTimeBySeconds(25);
    reconstructor.reconstructDataColumnSidecar(block, index, reconstructor.onRequest()).join();
    verify(chainDataClient, times(1)).getDataColumnSidecars(any(UInt64.class), anyList());

    // 10s more (35s total, past retention): the cached result is pruned and reconstruction reruns
    timeProvider.advanceTimeBySeconds(10);
    reconstructor.reconstructDataColumnSidecar(block, index, reconstructor.onRequest()).join();
    verify(chainDataClient, times(2)).getDataColumnSidecars(any(UInt64.class), anyList());
  }

  @TestTemplate
  public void shouldNotPruneWhenBoundaryEpochIsZero() {
    when(chainDataClient.getStore()).thenReturn(store);
    when(store.getTimeSeconds()).thenReturn(ZERO);
    when(store.getGenesisTime()).thenReturn(ZERO);

    reconstructor.onNewFinalizedCheckpoint(
        new Checkpoint(ZERO, dataStructureUtil.randomBytes32()), false);

    verify(sidecarArchivePrunableChannel, never()).onSidecarArchivePrunableSlot(any());
  }

  @TestTemplate
  public void shouldPruneWithCorrectBoundary() {
    final UInt64 finalizedEpoch = UInt64.valueOf(10);
    when(chainDataClient.getStore()).thenReturn(store);
    when(store.getTimeSeconds()).thenReturn(UInt64.valueOf(10000));
    when(store.getGenesisTime()).thenReturn(ZERO);

    reconstructor.onNewFinalizedCheckpoint(
        new Checkpoint(finalizedEpoch, dataStructureUtil.randomBytes32()), false);

    final UInt64 expectedLastPrunableSlot =
        spec.computeStartSlotAtEpoch(finalizedEpoch).minusMinZero(1);
    verify(sidecarArchivePrunableChannel).onSidecarArchivePrunableSlot(expectedLastPrunableSlot);
  }

  @TestTemplate
  public void shouldPruneWithRespectToRetentionEpochs() {
    final int retentionEpochs = 4;
    reconstructor =
        new DataColumnSidecarArchiveReconstructorImpl(
            chainDataClient,
            asyncRunner,
            () -> true,
            spec,
            retentionEpochs,
            sidecarArchivePrunableChannel,
            metricsSystem,
            timeProvider);
    when(chainDataClient.getStore()).thenReturn(store);
    when(store.getTimeSeconds()).thenReturn(UInt64.valueOf(10000));
    when(store.getGenesisTime()).thenReturn(ZERO);

    final UInt64 currentEpoch = spec.getCurrentEpoch(chainDataClient.getStore());
    final UInt64 finalizedEpoch = currentEpoch.minusMinZero(2);

    reconstructor.onNewFinalizedCheckpoint(
        new Checkpoint(finalizedEpoch, dataStructureUtil.randomBytes32()), false);

    final UInt64 expectedLastPrunableSlot =
        spec.computeStartSlotAtEpoch(currentEpoch.minus(retentionEpochs)).minusMinZero(1);
    verify(sidecarArchivePrunableChannel).onSidecarArchivePrunableSlot(expectedLastPrunableSlot);
  }

  @TestTemplate
  public void shouldNotSignalPrunableSlotWithDefaultRetentionEpochs() {
    // Integer.MAX_VALUE is DEFAULT_DATA_COLUMN_SIDECAR_EXTENSION_RETENTION_EPOCHS: all extension
    // columns are retained forever, so archiving is disabled by default even on a supernode.
    final DataColumnSidecarArchiveReconstructorImpl defaultRetention =
        newReconstructor(spec, () -> true, Integer.MAX_VALUE);
    when(store.getTimeSeconds()).thenReturn(UInt64.valueOf(10000));

    defaultRetention.onNewFinalizedCheckpoint(
        new Checkpoint(UInt64.valueOf(10), dataStructureUtil.randomBytes32()), false);

    verify(sidecarArchivePrunableChannel, never()).onSidecarArchivePrunableSlot(any());
  }

  @TestTemplate
  public void shouldNotSignalPrunableSlotWhenNotSuperNode() {
    // a default (non-supernode) node never archives extension columns
    final DataColumnSidecarArchiveReconstructorImpl nonSuperNode =
        newReconstructor(spec, () -> false, 0);
    when(store.getTimeSeconds()).thenReturn(UInt64.valueOf(10000));

    nonSuperNode.onNewFinalizedCheckpoint(
        new Checkpoint(UInt64.valueOf(10), dataStructureUtil.randomBytes32()), false);

    verify(sidecarArchivePrunableChannel, never()).onSidecarArchivePrunableSlot(any());
  }

  @TestTemplate
  public void isSidecarPruned_falseWhenNotSuperNode() {
    final DataColumnSidecarArchiveReconstructorImpl nonSuperNode =
        newReconstructor(spec, () -> false, 0);

    assertThat(nonSuperNode.isSidecarPruned(UInt64.valueOf(8), UInt64.valueOf(halfColumns)))
        .isFalse();
  }

  @TestTemplate
  public void isSidecarPruned_falseForFirstHalfIndex() {
    assertThat(reconstructor.isSidecarPruned(UInt64.valueOf(8), UInt64.valueOf(halfColumns - 1)))
        .isFalse();
  }

  @TestTemplate
  public void isSidecarPruned_falseWhenAvailabilityNotRequired() {
    final Spec spiedSpec = spy(spec);
    doReturn(false).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        newReconstructor(spiedSpec, () -> true, 0);

    assertThat(spiedReconstructor.isSidecarPruned(UInt64.valueOf(8), UInt64.valueOf(halfColumns)))
        .isFalse();
  }

  @TestTemplate
  public void isSidecarPruned_falseWhenNoFinalizedBlock() {
    final Spec spiedSpec = spy(spec);
    doReturn(true).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    when(chainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.empty());
    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        newReconstructor(spiedSpec, () -> true, 0);

    assertThat(spiedReconstructor.isSidecarPruned(UInt64.valueOf(8), UInt64.valueOf(halfColumns)))
        .isFalse();
  }

  @TestTemplate
  public void isSidecarPruned_trueWhenSlotBeforePruningBoundary() {
    final Spec spiedSpec = spy(spec);
    doReturn(true).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    final UInt64 currentEpoch = spec.getCurrentEpoch(store);
    // finalized at the current epoch, so the pruning boundary is well above epoch 0
    when(chainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(spec.computeStartSlotAtEpoch(currentEpoch)));
    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        newReconstructor(spiedSpec, () -> true, 0);

    assertThat(spiedReconstructor.isSidecarPruned(ZERO, UInt64.valueOf(halfColumns))).isTrue();
  }

  @TestTemplate
  public void isSidecarPruned_falseWhenSlotAtOrAfterPruningBoundary() {
    final Spec spiedSpec = spy(spec);
    doReturn(true).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    final UInt64 currentEpoch = spec.getCurrentEpoch(store);
    when(chainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(spec.computeStartSlotAtEpoch(currentEpoch)));
    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        newReconstructor(spiedSpec, () -> true, 0);

    // a slot in the current epoch is at/after the boundary and therefore not pruned
    final UInt64 recentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    assertThat(spiedReconstructor.isSidecarPruned(recentSlot, UInt64.valueOf(halfColumns)))
        .isFalse();
  }

  private DataColumnSidecarArchiveReconstructorImpl newReconstructor(
      final Spec reconstructorSpec,
      final Supplier<Boolean> isSuperNodeSupplier,
      final int retentionEpochs) {
    return new DataColumnSidecarArchiveReconstructorImpl(
        chainDataClient,
        asyncRunner,
        isSuperNodeSupplier,
        reconstructorSpec,
        retentionEpochs,
        sidecarArchivePrunableChannel,
        metricsSystem,
        timeProvider);
  }

  private List<UInt64> firstHalfIndices() {
    return Stream.iterate(ZERO, UInt64::increment).limit(halfColumns).toList();
  }

  private List<KZGProof> randomKzgProofs(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> dataStructureUtil.randomKZGProof()).toList();
  }

  private SignedBeaconBlock createBlockWithBlobs(final UInt64 slot) {
    return dataStructureUtil.randomSignedBeaconBlock(slot);
  }

  private List<DataColumnSidecar> createFirstHalfSidecars(final SignedBeaconBlock block) {
    return firstHalfIndices().stream()
        .map(index -> dataStructureUtil.randomDataColumnSidecar(block, index))
        .toList();
  }

  private List<List<KZGProof>> createSecondHalfProofs(final SignedBeaconBlock block) {
    final int blobCount =
        BeaconBlockBodyDeneb.required(block.getMessage().getBody()).getBlobKzgCommitments().size();
    return IntStream.range(0, halfColumns).mapToObj(__ -> randomKzgProofs(blobCount)).toList();
  }
}

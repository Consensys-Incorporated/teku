/*
 * Copyright (c) 2025 Consensys Incorporated. All rights reserved.
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.QueueIsFullException;
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
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
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
  private final StubAsyncRunner timeoutAsyncRunner = new StubAsyncRunner(timeProvider);
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

    reconstructor = newReconstructor(spec, () -> true, 0);
  }

  @TestTemplate
  public void shouldNotReadSidecarsOrBlockUntilCompleteProofsAreAvailable() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final SafeFuture<List<List<KZGProof>>> pendingProofs = new SafeFuture<>();
    when(chainDataClient.getDataColumnSidecarProofs(block.getSlot())).thenReturn(pendingProofs);
    final SafeFuture<Optional<DataColumnSidecar>> result =
        reconstructor.reconstructDataColumnSidecar(
            block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns), reconstructor.onRequest());

    assertThat(result).isNotDone();
    verify(chainDataClient, never()).getBlockByBlockRoot(any());
    verify(chainDataClient, never()).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
    pendingProofs.complete(createSecondHalfProofs(block).subList(0, halfColumns - 1));
    assertThat(result).isCompletedWithValue(Optional.empty());
    verify(chainDataClient, never()).getBlockByBlockRoot(any());
    verify(chainDataClient, never()).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldNotReuseFailedEntryAfterRequestDeadline() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final SafeFuture<List<List<KZGProof>>> pendingProofs = new SafeFuture<>();
    when(chainDataClient.getDataColumnSidecarProofs(block.getSlot())).thenReturn(pendingProofs);
    final int requestId = reconstructor.onRequest();
    final UInt64 index = UInt64.valueOf(halfColumns);
    final SafeFuture<Optional<DataColumnSidecar>> result =
        reconstructor.reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), index, requestId);
    timeProvider.advanceTimeBySeconds(6);
    timeoutAsyncRunner.executeDueActions();
    assertThat(result).isCompletedWithValue(Optional.empty());
    pendingProofs.complete(List.of());

    try (final LogCaptor logs =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.TRACE)) {
      assertThat(
              reconstructor.reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), index, requestId))
          .isCompletedWithValue(Optional.empty());
      assertThat(logs.getTraceLogs())
          .containsExactly(
              requestLog(
                  block,
                  index,
                  requestId,
                  "past the request deadline, no completed result to serve"));
    }
    verify(chainDataClient).getDataColumnSidecarProofs(block.getSlot());
  }

  @TestTemplate
  public void shouldPruneAndLogFailedEntriesOnSlot() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecarProofs(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    assertThat(
            reconstructor.reconstructDataColumnSidecar(
                block.getSlotAndBlockRoot(),
                UInt64.valueOf(halfColumns),
                reconstructor.onRequest()))
        .isCompletedWithValue(Optional.empty());
    try (final LogCaptor logs =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      reconstructor.onSlot(UInt64.valueOf(2));
      assertThat(logs.getDebugLogs())
          .containsExactly(
              "Pruned 0 reconstructed results unrequested for over 30s and 1 failed reconstructions at slot 2");
    }
    try (final LogCaptor logs =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      reconstructor.onSlot(UInt64.valueOf(3));
      assertThat(logs.getDebugLogs()).isEmpty();
    }
  }

  @TestTemplate
  public void shouldReturnEmptyWhenNoSidecarsAvailable() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));

    final int requestId = reconstructor.onRequest();
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      final Optional<DataColumnSidecar> result =
          reconstructor
              .reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns), requestId)
              .join();

      assertThat(result).isEmpty();
      // not an error, so the reason is logged at debug
      assertThat(logCaptor.getDebugLogs())
          .contains(
              String.format(
                  "Cannot reconstruct extension data column sidecars for slot %s blockRoot %s: "
                      + "found 0 of %s first-half sidecars and %s of %s extension proofs",
                  block.getSlot(), block.getRoot(), halfColumns, halfColumns, halfColumns));
    }
    // an unsuccessful reconstruction is recorded as a failure and never as a success
    assertThat(failures("unavailable")).isEqualTo(1);
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

    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int requestId = reconstructor.onRequest();
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      final Optional<DataColumnSidecar> result =
          reconstructor
              .reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns), requestId)
              .join();

      assertThat(result).isEmpty();
      assertThat(logCaptor.getDebugLogs())
          .contains(
              String.format(
                  "Cannot reconstruct extension data column sidecars for slot %s blockRoot %s: "
                      + "stored sidecar 0 is index 0 of %s",
                  slot, block.getRoot(), competingBlock.getSlotAndBlockRoot()));
    }
  }

  @TestTemplate
  public void shouldReconstructSecondHalfSidecars() {
    final Spec spiedSpec = spy(spec);
    final DataColumnSidecarUtil mockUtil = mock(DataColumnSidecarUtil.class);
    doReturn(mockUtil).when(spiedSpec).getDataColumnSidecarUtil(any());

    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        newReconstructor(spiedSpec, () -> true, 0);

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

    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int requestId = spiedReconstructor.onRequest();
    final Optional<DataColumnSidecar> result =
        spiedReconstructor
            .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), targetIndex, requestId)
            .join();

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(expectedSidecar);
    verify(mockUtil).constructDataColumnSidecars(any(), any(), any(), any(), anyList());
  }

  @TestTemplate
  public void shouldRejectOutOfRangeIndexWithoutStorageReads() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    for (final UInt64 index : List.of(UInt64.valueOf(halfColumns * 2L), UInt64.MAX_VALUE)) {
      assertThatThrownBy(
              () ->
                  reconstructor.reconstructDataColumnSidecar(
                      block.getSlotAndBlockRoot(), index, reconstructor.onRequest()))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(reconstructor.isSidecarPruned(block.getSlot(), index)).isFalse();
    }
    verify(chainDataClient, never()).getDataColumnSidecarProofs(any());
    verify(chainDataClient, never()).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldRejectFirstHalfIndex() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final int requestId = reconstructor.onRequest();

    assertThatThrownBy(
            () ->
                reconstructor.reconstructDataColumnSidecar(
                    block.getSlotAndBlockRoot(), ZERO, requestId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void shouldReuseTaskForSameBlockInSameRequest() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int requestId = reconstructor.onRequest();
    final UInt64 firstIndex = UInt64.valueOf(halfColumns);
    final UInt64 secondIndex = UInt64.valueOf(halfColumns + 1);
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.TRACE)) {
      final Optional<DataColumnSidecar> result1 =
          reconstructor
              .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), firstIndex, requestId)
              .join();
      final Optional<DataColumnSidecar> result2 =
          reconstructor
              .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), secondIndex, requestId)
              .join();

      assertThat(result1).isPresent();
      assertThat(result2).isPresent();
      assertThat(logCaptor.getTraceLogs())
          .containsExactly(
              requestLog(block, firstIndex, requestId, "starting a new reconstruction"),
              requestLog(block, secondIndex, requestId, "reusing the completed result"));
    }
    // Should have only called getDataColumnSidecars once (same task reused)
    verify(chainDataClient).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
    // and loaded the block only to start the reconstruction, not to serve the reused result
    verify(chainDataClient).getBlockByBlockRoot(block.getRoot());
  }

  @TestTemplate
  public void shouldReuseReconstructionAcrossRequests() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final int firstRequest = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result1 =
        reconstructor
            .reconstructDataColumnSidecar(
                block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns), firstRequest)
            .join();
    reconstructor.onRequestCompleted(firstRequest);

    // a separate, later request for the same block reuses the cached result
    final int secondRequest = reconstructor.onRequest();
    final Optional<DataColumnSidecar> result2 =
        reconstructor
            .reconstructDataColumnSidecar(
                block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns + 1), secondRequest)
            .join();

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    // reconstruction ran only once despite two independent requests
    verify(chainDataClient).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldJoinRunningReconstructionAfterRequestTimeout() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    final DataColumnSidecarArchiveReconstructorImpl slowReconstructor =
        newReconstructor(stubAsyncRunner);
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final SafeFuture<List<DataColumnSidecar>> firstHalfSidecars = new SafeFuture<>();
    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(firstHalfSidecars);
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    // the first caller gives up after the timeout while the reconstruction is still running
    final SafeFuture<Optional<DataColumnSidecar>> timedOut =
        slowReconstructor.reconstructDataColumnSidecar(
            block.getSlotAndBlockRoot(), index, slowReconstructor.onRequest());
    stubAsyncRunner.executeDueActions();
    timeProvider.advanceTimeBySeconds(6);
    timeoutAsyncRunner.executeDueActions();
    assertThat(timedOut).isCompletedWithValue(Optional.empty());

    // a later caller joins the running reconstruction instead of starting an overlapping one
    final int joiningRequestId = slowReconstructor.onRequest();
    final SafeFuture<Optional<DataColumnSidecar>> joined;
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.TRACE)) {
      joined =
          slowReconstructor.reconstructDataColumnSidecar(
              block.getSlotAndBlockRoot(), index, joiningRequestId);
      assertThat(logCaptor.getTraceLogs())
          .containsExactly(
              requestLog(block, index, joiningRequestId, "joining the running reconstruction"));
    }
    firstHalfSidecars.complete(createFirstHalfSidecars(block));
    stubAsyncRunner.executeDueActions();

    assertThat(joined).isCompleted();
    assertThat(joined.join()).isPresent();
    verify(chainDataClient, times(1)).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldFailHungReconstructionSoNextRequestRetries() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    final DataColumnSidecarArchiveReconstructorImpl hungReconstructor =
        newReconstructor(stubAsyncRunner);
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    // the first read never completes, the retry's read does
    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(new SafeFuture<>())
        .thenReturn(SafeFuture.completedFuture(createFirstHalfSidecars(block)));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    final SafeFuture<Optional<DataColumnSidecar>> hung =
        hungReconstructor.reconstructDataColumnSidecar(
            block.getSlotAndBlockRoot(), index, hungReconstructor.onRequest());
    stubAsyncRunner.executeDueActions();
    // past the hung-task backstop: the stuck reconstruction is failed
    timeProvider.advanceTimeBySeconds(61);
    timeoutAsyncRunner.executeDueActions();
    assertThat(hung).isCompletedWithValue(Optional.empty());
    assertThat(failures("timeout")).isEqualTo(1);

    // the next request starts a fresh reconstruction instead of waiting on the hung one
    final SafeFuture<Optional<DataColumnSidecar>> retried =
        hungReconstructor.reconstructDataColumnSidecar(
            block.getSlotAndBlockRoot(), index, hungReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();

    assertThat(retried).isCompleted();
    assertThat(retried.join()).isPresent();
    verify(chainDataClient, times(2)).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldServeOnlyCompletedReconstructionsForRestOfRequestAfterTimeout() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    final DataColumnSidecarArchiveReconstructorImpl slowReconstructor =
        newReconstructor(stubAsyncRunner);
    final SignedBeaconBlock stalledBlock = createBlockWithBlobs(UInt64.ONE);
    final SignedBeaconBlock completedBlock = createBlockWithBlobs(UInt64.valueOf(2));
    final SignedBeaconBlock unreconstructedBlock = createBlockWithBlobs(UInt64.valueOf(3));
    stubArchivedBlock(stalledBlock, new SafeFuture<>());
    stubArchivedBlock(
        completedBlock, SafeFuture.completedFuture(createFirstHalfSidecars(completedBlock)));
    stubArchivedBlock(
        unreconstructedBlock,
        SafeFuture.completedFuture(createFirstHalfSidecars(unreconstructedBlock)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    // another request has already reconstructed completedBlock
    final SafeFuture<Optional<DataColumnSidecar>> earlier =
        slowReconstructor.reconstructDataColumnSidecar(
            completedBlock.getSlotAndBlockRoot(), index, slowReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(earlier).isCompletedWithValueMatching(Optional::isPresent);

    // this request waits out the deadline on stalledBlock
    final int requestId = slowReconstructor.onRequest();
    final SafeFuture<Optional<DataColumnSidecar>> timedOut =
        slowReconstructor.reconstructDataColumnSidecar(
            stalledBlock.getSlotAndBlockRoot(), index, requestId);
    stubAsyncRunner.executeDueActions();
    timeProvider.advanceTimeBySeconds(6);
    timeoutAsyncRunner.executeDueActions();
    assertThat(timedOut).isCompletedWithValue(Optional.empty());

    // from then on it waits for nothing: the stalled block's next column and a block not yet
    // reconstructed resolve empty at once, while a completed reconstruction is still served
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.TRACE)) {
      assertThat(
              slowReconstructor.reconstructDataColumnSidecar(
                  stalledBlock.getSlotAndBlockRoot(), index.plus(1), requestId))
          .isCompletedWithValue(Optional.empty());
      assertThat(
              slowReconstructor.reconstructDataColumnSidecar(
                  unreconstructedBlock.getSlotAndBlockRoot(), index, requestId))
          .isCompletedWithValue(Optional.empty());
      assertThat(
              slowReconstructor.reconstructDataColumnSidecar(
                  completedBlock.getSlotAndBlockRoot(), index, requestId))
          .isCompletedWithValueMatching(Optional::isPresent);
      final String noResult = "past the request deadline, no completed result to serve";
      assertThat(logCaptor.getTraceLogs())
          .containsExactly(
              requestLog(stalledBlock, index.plus(1), requestId, noResult),
              requestLog(unreconstructedBlock, index, requestId, noResult),
              requestLog(
                  completedBlock,
                  index,
                  requestId,
                  "past the request deadline, reusing the completed result"));
    }
    // and it starts no new reconstruction
    stubAsyncRunner.executeDueActionsRepeatedly();
    verify(chainDataClient, never())
        .getDataColumnSidecars(eq(unreconstructedBlock.getSlotAndBlockRoot()), anyList());
    // nor does it load blocks it is not going to reconstruct
    verify(chainDataClient, never()).getBlockByBlockRoot(unreconstructedBlock.getRoot());

    // a completed request leaves no state behind, so its id waits for reconstructions again
    slowReconstructor.onRequestCompleted(requestId);
    final SafeFuture<Optional<DataColumnSidecar>> afterCompletion =
        slowReconstructor.reconstructDataColumnSidecar(
            unreconstructedBlock.getSlotAndBlockRoot(), index, requestId);
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(afterCompletion).isCompletedWithValueMatching(Optional::isPresent);
  }

  @TestTemplate
  public void shouldTimeOutWhileReconstructionRunnerIsBusy() {
    // nothing queued on the reconstruction runner gets to run, as when all its workers are busy
    final StubAsyncRunner busyAsyncRunner = new StubAsyncRunner(timeProvider);
    final DataColumnSidecarArchiveReconstructorImpl busyReconstructor =
        newReconstructor(busyAsyncRunner);
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);

    final SafeFuture<Optional<DataColumnSidecar>> result =
        busyReconstructor.reconstructDataColumnSidecar(
            block.getSlotAndBlockRoot(),
            UInt64.valueOf(halfColumns),
            busyReconstructor.onRequest());
    timeProvider.advanceTimeBySeconds(6);
    timeoutAsyncRunner.executeDueActions();

    // the deadline does not queue behind the reconstruction work
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @TestTemplate
  public void shouldRejectReconstructionWhenQueueIsFullAndRetryOnNextRequest() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    // room for one running and one queued reconstruction
    final DataColumnSidecarArchiveReconstructorImpl limitedReconstructor =
        newReconstructor(stubAsyncRunner, 1, 1, 10);
    final SignedBeaconBlock runningBlock = createBlockWithBlobs(UInt64.ONE);
    final SignedBeaconBlock queuedBlock = createBlockWithBlobs(UInt64.valueOf(2));
    final SignedBeaconBlock rejectedBlock = createBlockWithBlobs(UInt64.valueOf(3));
    final SafeFuture<List<DataColumnSidecar>> runningBlockSidecars = new SafeFuture<>();
    stubArchivedBlock(runningBlock, runningBlockSidecars);
    stubArchivedBlock(
        queuedBlock, SafeFuture.completedFuture(createFirstHalfSidecars(queuedBlock)));
    stubArchivedBlock(
        rejectedBlock, SafeFuture.completedFuture(createFirstHalfSidecars(rejectedBlock)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    final SafeFuture<Optional<DataColumnSidecar>> running =
        limitedReconstructor.reconstructDataColumnSidecar(
            runningBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    final SafeFuture<Optional<DataColumnSidecar>> queued =
        limitedReconstructor.reconstructDataColumnSidecar(
            queuedBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    // with the queue full the next reconstruction is rejected at once and counted as a failure
    assertThat(
            limitedReconstructor.reconstructDataColumnSidecar(
                rejectedBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest()))
        .isCompletedWithValue(Optional.empty());
    assertThat(failures("rejected")).isEqualTo(1);

    // the queued reconstruction only starts once the running one frees its slot
    stubAsyncRunner.executeDueActionsRepeatedly();
    verify(chainDataClient, never())
        .getDataColumnSidecars(eq(queuedBlock.getSlotAndBlockRoot()), anyList());
    runningBlockSidecars.complete(createFirstHalfSidecars(runningBlock));
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(running).isCompletedWithValueMatching(Optional::isPresent);
    assertThat(queued).isCompletedWithValueMatching(Optional::isPresent);

    // the rejection is not cached, so the next request for that block reconstructs it
    final SafeFuture<Optional<DataColumnSidecar>> retried =
        limitedReconstructor.reconstructDataColumnSidecar(
            rejectedBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(retried).isCompletedWithValueMatching(Optional::isPresent);
  }

  @TestTemplate
  public void shouldSkipQueuedReconstructionLeftUnrequestedForRetention() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    final DataColumnSidecarArchiveReconstructorImpl limitedReconstructor =
        newReconstructor(stubAsyncRunner, 1, 10, 10);
    final SignedBeaconBlock runningBlock = createBlockWithBlobs(UInt64.ONE);
    final SignedBeaconBlock abandonedBlock = createBlockWithBlobs(UInt64.valueOf(2));
    final SignedBeaconBlock rerequestedBlock = createBlockWithBlobs(UInt64.valueOf(3));
    final SafeFuture<List<DataColumnSidecar>> runningBlockSidecars = new SafeFuture<>();
    stubArchivedBlock(runningBlock, runningBlockSidecars);
    stubArchivedBlock(
        abandonedBlock, SafeFuture.completedFuture(createFirstHalfSidecars(abandonedBlock)));
    stubArchivedBlock(
        rerequestedBlock, SafeFuture.completedFuture(createFirstHalfSidecars(rerequestedBlock)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    // every caller gives up while two of the reconstructions are still queued
    final List<SafeFuture<Optional<DataColumnSidecar>>> timedOut =
        Stream.of(runningBlock, abandonedBlock, rerequestedBlock)
            .map(
                block ->
                    limitedReconstructor.reconstructDataColumnSidecar(
                        block.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest()))
            .toList();
    stubAsyncRunner.executeDueActions();
    timeProvider.advanceTimeBySeconds(31);
    timeoutAsyncRunner.executeDueActions();
    assertThat(timedOut)
        .allSatisfy(future -> assertThat(future).isCompletedWithValue(Optional.empty()));

    // past the result retention, only one of the queued blocks is requested again
    final SafeFuture<Optional<DataColumnSidecar>> rerequested =
        limitedReconstructor.reconstructDataColumnSidecar(
            rerequestedBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    runningBlockSidecars.complete(createFirstHalfSidecars(runningBlock));
    stubAsyncRunner.executeDueActionsRepeatedly();

    // the abandoned reconstruction is skipped, as a failure, while the re-requested one runs
    verify(chainDataClient, never())
        .getDataColumnSidecars(eq(abandonedBlock.getSlotAndBlockRoot()), anyList());
    assertThat(failures("skipped")).isEqualTo(1);
    assertThat(rerequested).isCompletedWithValueMatching(Optional::isPresent);

    // the skipped block is reconstructed by its next request
    final SafeFuture<Optional<DataColumnSidecar>> retried =
        limitedReconstructor.reconstructDataColumnSidecar(
            abandonedBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(retried).isCompletedWithValueMatching(Optional::isPresent);
  }

  @TestTemplate
  public void shouldEvictLeastRecentlyRequestedResultBeyondRetainedLimit() {
    // keeps at most two successful results
    final DataColumnSidecarArchiveReconstructorImpl limitedReconstructor =
        newReconstructor(asyncRunner, 10, 10, 2);
    final SignedBeaconBlock firstBlock = createBlockWithBlobs(UInt64.ONE);
    final SignedBeaconBlock secondBlock = createBlockWithBlobs(UInt64.valueOf(2));
    final SignedBeaconBlock thirdBlock = createBlockWithBlobs(UInt64.valueOf(3));
    Stream.of(firstBlock, secondBlock, thirdBlock)
        .forEach(
            block ->
                stubArchivedBlock(
                    block, SafeFuture.completedFuture(createFirstHalfSidecars(block))));
    final UInt64 index = UInt64.valueOf(halfColumns);

    // firstBlock is requested again after secondBlock, so secondBlock is the least recently
    // requested result once thirdBlock's completes
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      for (final SignedBeaconBlock block :
          List.of(firstBlock, secondBlock, firstBlock, thirdBlock)) {
        timeProvider.advanceTimeBySeconds(1);
        assertThat(
                limitedReconstructor.reconstructDataColumnSidecar(
                    block.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest()))
            .isCompletedWithValueMatching(Optional::isPresent);
      }
      // secondBlock was last requested at 2s and evicted at 4s
      assertThat(logCaptor.getDebugLogs())
          .filteredOn(log -> log.startsWith("Evicted"))
          .containsExactly(
              String.format(
                  "Evicted reconstructed sidecars for slot %s blockRoot %s (last requested 2000 ms "
                      + "ago) to keep at most 2 results",
                  secondBlock.getSlot(), secondBlock.getRoot()));
    }

    // firstBlock and thirdBlock are still served from the cache, secondBlock is rebuilt
    for (final SignedBeaconBlock block : List.of(firstBlock, thirdBlock, secondBlock)) {
      timeProvider.advanceTimeBySeconds(1);
      assertThat(
              limitedReconstructor.reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest()))
          .isCompletedWithValueMatching(Optional::isPresent);
    }
    verify(chainDataClient).getDataColumnSidecars(eq(firstBlock.getSlotAndBlockRoot()), anyList());
    verify(chainDataClient).getDataColumnSidecars(eq(thirdBlock.getSlotAndBlockRoot()), anyList());
    verify(chainDataClient, times(2))
        .getDataColumnSidecars(eq(secondBlock.getSlotAndBlockRoot()), anyList());
  }

  @TestTemplate
  public void shouldNotEvictRunningReconstructionForRetainedLimit() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    // keeps a single successful result
    final DataColumnSidecarArchiveReconstructorImpl limitedReconstructor =
        newReconstructor(stubAsyncRunner, 10, 10, 1);
    final SignedBeaconBlock runningBlock = createBlockWithBlobs(UInt64.ONE);
    final SignedBeaconBlock firstBlock = createBlockWithBlobs(UInt64.valueOf(2));
    final SignedBeaconBlock secondBlock = createBlockWithBlobs(UInt64.valueOf(3));
    final SafeFuture<List<DataColumnSidecar>> runningBlockSidecars = new SafeFuture<>();
    stubArchivedBlock(runningBlock, runningBlockSidecars);
    stubArchivedBlock(firstBlock, SafeFuture.completedFuture(createFirstHalfSidecars(firstBlock)));
    stubArchivedBlock(
        secondBlock, SafeFuture.completedFuture(createFirstHalfSidecars(secondBlock)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    final SafeFuture<Optional<DataColumnSidecar>> running =
        limitedReconstructor.reconstructDataColumnSidecar(
            runningBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    // two later blocks complete while runningBlock is still in flight, exceeding the limit
    timeProvider.advanceTimeBySeconds(1);
    final SafeFuture<Optional<DataColumnSidecar>> first =
        limitedReconstructor.reconstructDataColumnSidecar(
            firstBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    timeProvider.advanceTimeBySeconds(1);
    final SafeFuture<Optional<DataColumnSidecar>> second =
        limitedReconstructor.reconstructDataColumnSidecar(
            secondBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(first).isCompletedWithValueMatching(Optional::isPresent);
    assertThat(second).isCompletedWithValueMatching(Optional::isPresent);

    // the in-flight reconstruction, though requested before both, was kept: a new request joins it
    final SafeFuture<Optional<DataColumnSidecar>> joined =
        limitedReconstructor.reconstructDataColumnSidecar(
            runningBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    runningBlockSidecars.complete(createFirstHalfSidecars(runningBlock));
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(running).isCompletedWithValueMatching(Optional::isPresent);
    assertThat(joined).isCompletedWithValueMatching(Optional::isPresent);
    verify(chainDataClient)
        .getDataColumnSidecars(eq(runningBlock.getSlotAndBlockRoot()), anyList());
  }

  @TestTemplate
  public void shouldNotEvictResultThatWaitedInQueueWhenItCompletes() {
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    // keeps a single successful result
    final DataColumnSidecarArchiveReconstructorImpl limitedReconstructor =
        newReconstructor(stubAsyncRunner, 10, 10, 1);
    final SignedBeaconBlock slowBlock = createBlockWithBlobs(UInt64.ONE);
    final SignedBeaconBlock otherBlock = createBlockWithBlobs(UInt64.valueOf(2));
    final SafeFuture<List<DataColumnSidecar>> slowBlockSidecars = new SafeFuture<>();
    stubArchivedBlock(slowBlock, slowBlockSidecars);
    stubArchivedBlock(otherBlock, SafeFuture.completedFuture(createFirstHalfSidecars(otherBlock)));
    final UInt64 index = UInt64.valueOf(halfColumns);
    final int requestId = limitedReconstructor.onRequest();

    // slowBlock is requested first but completes after otherBlock, which was requested later
    final SafeFuture<Optional<DataColumnSidecar>> first =
        limitedReconstructor.reconstructDataColumnSidecar(
            slowBlock.getSlotAndBlockRoot(), index, requestId);
    stubAsyncRunner.executeDueActionsRepeatedly();
    timeProvider.advanceTimeBySeconds(1);
    final SafeFuture<Optional<DataColumnSidecar>> other =
        limitedReconstructor.reconstructDataColumnSidecar(
            otherBlock.getSlotAndBlockRoot(), index, limitedReconstructor.onRequest());
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(other).isCompletedWithValueMatching(Optional::isPresent);
    timeProvider.advanceTimeBySeconds(1);
    slowBlockSidecars.complete(createFirstHalfSidecars(slowBlock));
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(first).isCompletedWithValueMatching(Optional::isPresent);

    // completing counts as a request, so the request's next column reuses it instead of a rebuild
    assertThat(
            limitedReconstructor.reconstructDataColumnSidecar(
                slowBlock.getSlotAndBlockRoot(), index.plus(1), requestId))
        .isCompletedWithValueMatching(Optional::isPresent);
    verify(chainDataClient).getDataColumnSidecars(eq(slowBlock.getSlotAndBlockRoot()), anyList());
  }

  @TestTemplate
  public void shouldRecordDurationOfHungReconstructionOnceItFinishes() {
    final List<Double> observedSeconds = new ArrayList<>();
    final StubMetricsSystem recordingMetricsSystem =
        new StubMetricsSystem() {
          @Override
          public Histogram createHistogram(
              final MetricCategory category,
              final String name,
              final String help,
              final double[] buckets) {
            return observedSeconds::add;
          }
        };
    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(timeProvider);
    final DataColumnSidecarArchiveReconstructorImpl hungReconstructor =
        new DataColumnSidecarArchiveReconstructorImpl(
            chainDataClient,
            stubAsyncRunner,
            timeoutAsyncRunner,
            () -> true,
            spec,
            0,
            sidecarArchivePrunableChannel,
            recordingMetricsSystem,
            timeProvider);
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final SafeFuture<List<DataColumnSidecar>> firstHalfSidecars = new SafeFuture<>();
    stubArchivedBlock(block, firstHalfSidecars);

    final SafeFuture<Optional<DataColumnSidecar>> hung =
        hungReconstructor.reconstructDataColumnSidecar(
            block.getSlotAndBlockRoot(),
            UInt64.valueOf(halfColumns),
            hungReconstructor.onRequest());
    stubAsyncRunner.executeDueActions();
    // the backstop fails the reconstruction before its work is done...
    timeProvider.advanceTimeBySeconds(61);
    timeoutAsyncRunner.executeDueActions();
    assertThat(hung).isCompletedWithValue(Optional.empty());
    assertThat(observedSeconds).isEmpty();

    // ...and its duration is still recorded once the work does finish
    timeProvider.advanceTimeBySeconds(4);
    firstHalfSidecars.complete(createFirstHalfSidecars(block));
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(observedSeconds).containsExactly(65.0);
  }

  @TestTemplate
  public void shouldWarnAboutUnexpectedFailureAtMostOncePerInterval() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("corrupted archive")));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));
    final UInt64 index = UInt64.valueOf(halfColumns);

    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      // every request retries the failed block, but only the first failure is logged at warn
      assertThat(
              reconstructor.reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), index, reconstructor.onRequest()))
          .isCompletedWithValue(Optional.empty());
      assertThat(
              reconstructor.reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), index, reconstructor.onRequest()))
          .isCompletedWithValue(Optional.empty());
      assertThat(logCaptor.getWarnLogs()).hasSize(1);
      // the warning is the first event logged and carries the stack trace
      assertThat(logCaptor.getThrowable(0)).isPresent();
      assertThat(logCaptor.getDebugLogs())
          .anyMatch(log -> log.startsWith("Reconstruction failed for"));

      // once the interval has passed, the next failure is logged at warn again
      timeProvider.advanceTimeBySeconds(61);
      assertThat(
              reconstructor.reconstructDataColumnSidecar(
                  block.getSlotAndBlockRoot(), index, reconstructor.onRequest()))
          .isCompletedWithValue(Optional.empty());
      assertThat(logCaptor.getWarnLogs()).hasSize(2);
    }
    assertThat(failures("error")).isEqualTo(3);
  }

  @TestTemplate
  public void shouldReturnEmptyWhenBlockIsNotAvailable() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecarProofs(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));
    when(chainDataClient.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(
            reconstructor.reconstructDataColumnSidecar(
                block.getSlotAndBlockRoot(),
                UInt64.valueOf(halfColumns),
                reconstructor.onRequest()))
        .isCompletedWithValue(Optional.empty());
    // nothing else is read for a block that cannot be loaded
    verify(chainDataClient, never()).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
    assertThat(failures("unavailable")).isEqualTo(1);
  }

  @TestTemplate
  public void shouldCountStorageQueryRejectionApartFromReconstructionQueueRejection() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    // the storage query queue shared with the other RPC handlers is full when reading the archive
    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.failedFuture(new QueueIsFullException()));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));

    assertThat(
            reconstructor.reconstructDataColumnSidecar(
                block.getSlotAndBlockRoot(),
                UInt64.valueOf(halfColumns),
                reconstructor.onRequest()))
        .isCompletedWithValue(Optional.empty());
    assertThat(failures("storage_rejected")).isEqualTo(1);
    assertThat(failures("rejected")).isZero();
  }

  @TestTemplate
  @SuppressWarnings("FutureReturnValueIgnored")
  public void shouldRetryReconstructionAfterEmptyResult() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final int requestId = reconstructor.onRequest();
    reconstructor.reconstructDataColumnSidecar(
        block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns), requestId);

    // an empty (failed) reconstruction is not cached, so a later request retries it
    final int newRequestId = reconstructor.onRequest();
    reconstructor.reconstructDataColumnSidecar(
        block.getSlotAndBlockRoot(), UInt64.valueOf(halfColumns), newRequestId);

    verify(chainDataClient, times(2)).getDataColumnSidecarProofs(block.getSlot());
    verify(chainDataClient, never()).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldReuseSuccessfulResultRegardlessOfAgeUntilPruned() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final UInt64 index = UInt64.valueOf(halfColumns);
    reconstructor
        .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), index, reconstructor.onRequest())
        .join();

    // past the 30s retention but not pruned yet: the deterministic result is reused, not rebuilt
    timeProvider.advanceTimeBySeconds(35);
    assertThat(
            reconstructor
                .reconstructDataColumnSidecar(
                    block.getSlotAndBlockRoot(), index, reconstructor.onRequest())
                .join())
        .isPresent();
    verify(chainDataClient, times(1)).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
  }

  @TestTemplate
  public void shouldPruneOnSlotOnlyOnceUnrequestedForRetention() {
    final SignedBeaconBlock block = createBlockWithBlobs(UInt64.ONE);
    final List<DataColumnSidecar> firstHalfSidecars = createFirstHalfSidecars(block);
    final List<List<KZGProof>> secondHalfProofs = createSecondHalfProofs(block);

    when(chainDataClient.getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(firstHalfSidecars));
    when(chainDataClient.getDataColumnSidecarProofs(any()))
        .thenReturn(SafeFuture.completedFuture(secondHalfProofs));

    final UInt64 index = UInt64.valueOf(halfColumns);
    reconstructor
        .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), index, reconstructor.onRequest())
        .join();

    // requested again after 25s, so 25s later it is only 25s since the last request: kept
    timeProvider.advanceTimeBySeconds(25);
    reconstructor
        .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), index, reconstructor.onRequest())
        .join();
    timeProvider.advanceTimeBySeconds(25);
    reconstructor.onSlot(UInt64.ONE);
    reconstructor
        .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), index, reconstructor.onRequest())
        .join();
    verify(chainDataClient, times(1)).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());

    // unrequested for 35s: the per-slot prune drops it and the next request reconstructs again
    timeProvider.advanceTimeBySeconds(35);
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(DataColumnSidecarArchiveReconstructorImpl.class, Level.DEBUG)) {
      reconstructor.onSlot(UInt64.valueOf(2));
      assertThat(logCaptor.getDebugLogs())
          .containsExactly(
              "Pruned 1 reconstructed results unrequested for over 30s and 0 failed "
                  + "reconstructions at slot 2");
    }
    reconstructor
        .reconstructDataColumnSidecar(block.getSlotAndBlockRoot(), index, reconstructor.onRequest())
        .join();
    verify(chainDataClient, times(2)).getDataColumnSidecars(any(SlotAndBlockRoot.class), anyList());
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
    reconstructor = newReconstructor(spec, () -> true, retentionEpochs);
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
  public void isSidecarPruned_falseWhenFinalizedCheckpointIsGenesis() {
    final Spec spiedSpec = spy(spec);
    doReturn(true).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    when(store.getFinalizedCheckpoint())
        .thenReturn(new Checkpoint(ZERO, dataStructureUtil.randomBytes32()));
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
    when(store.getFinalizedCheckpoint())
        .thenReturn(new Checkpoint(currentEpoch, dataStructureUtil.randomBytes32()));
    final DataColumnSidecarArchiveReconstructorImpl spiedReconstructor =
        newReconstructor(spiedSpec, () -> true, 0);

    assertThat(spiedReconstructor.isSidecarPruned(ZERO, UInt64.valueOf(halfColumns))).isTrue();
  }

  @TestTemplate
  public void isSidecarPruned_canServeExistingArchivesWithDefaultRetentionEpochs() {
    final Spec spiedSpec = spy(spec);
    doReturn(true).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    final UInt64 currentEpoch = spec.getCurrentEpoch(store);
    // Restoring default retention must not hide archives written with a shorter retention.
    when(store.getFinalizedCheckpoint())
        .thenReturn(new Checkpoint(currentEpoch, dataStructureUtil.randomBytes32()));
    final DataColumnSidecarArchiveReconstructorImpl defaultRetention =
        newReconstructor(spiedSpec, () -> true, Integer.MAX_VALUE);

    assertThat(defaultRetention.isSidecarPruned(ZERO, UInt64.valueOf(halfColumns))).isTrue();
  }

  @TestTemplate
  public void isSidecarPruned_falseWhenSlotAtOrAfterPruningBoundary() {
    final Spec spiedSpec = spy(spec);
    doReturn(true).when(spiedSpec).isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(any(), any());
    final UInt64 currentEpoch = spec.getCurrentEpoch(store);
    when(store.getFinalizedCheckpoint())
        .thenReturn(new Checkpoint(currentEpoch, dataStructureUtil.randomBytes32()));
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
        timeoutAsyncRunner,
        isSuperNodeSupplier,
        reconstructorSpec,
        retentionEpochs,
        sidecarArchivePrunableChannel,
        metricsSystem,
        timeProvider);
  }

  private DataColumnSidecarArchiveReconstructorImpl newReconstructor(
      final AsyncRunner reconstructionAsyncRunner) {
    return new DataColumnSidecarArchiveReconstructorImpl(
        chainDataClient,
        reconstructionAsyncRunner,
        timeoutAsyncRunner,
        () -> true,
        spec,
        0,
        sidecarArchivePrunableChannel,
        metricsSystem,
        timeProvider);
  }

  private DataColumnSidecarArchiveReconstructorImpl newReconstructor(
      final AsyncRunner reconstructionAsyncRunner,
      final int maxConcurrentReconstructions,
      final int maxQueuedReconstructions,
      final int maxRetainedResults) {
    return new DataColumnSidecarArchiveReconstructorImpl(
        chainDataClient,
        reconstructionAsyncRunner,
        timeoutAsyncRunner,
        () -> true,
        spec,
        0,
        sidecarArchivePrunableChannel,
        metricsSystem,
        timeProvider,
        maxConcurrentReconstructions,
        maxQueuedReconstructions,
        maxRetainedResults);
  }

  private void stubArchivedBlock(
      final SignedBeaconBlock block, final SafeFuture<List<DataColumnSidecar>> firstHalfSidecars) {
    when(chainDataClient.getDataColumnSidecars(eq(block.getSlotAndBlockRoot()), anyList()))
        .thenReturn(firstHalfSidecars);
    when(chainDataClient.getDataColumnSidecarProofs(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(createSecondHalfProofs(block)));
  }

  private String requestLog(
      final SignedBeaconBlock block,
      final UInt64 index,
      final int requestId,
      final String outcome) {
    return String.format(
        "Reconstruction requested for slot %s index %s blockRoot %s (requestId %s): %s",
        block.getSlot(), index, block.getRoot(), requestId, outcome);
  }

  private List<UInt64> firstHalfIndices() {
    return Stream.iterate(ZERO, UInt64::increment).limit(halfColumns).toList();
  }

  private List<KZGProof> randomKzgProofs(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> dataStructureUtil.randomKZGProof()).toList();
  }

  private SignedBeaconBlock createBlockWithBlobs(final UInt64 slot) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
    // a reconstruction loads its block by root when it starts
    when(chainDataClient.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    return block;
  }

  private long failures(final String reason) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.BEACON,
        "data_column_sidecar_archive_reconstruction_failures_total",
        reason);
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

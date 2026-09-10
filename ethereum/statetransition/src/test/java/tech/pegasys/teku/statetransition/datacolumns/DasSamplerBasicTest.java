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

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler.SamplingEligibilityStatus;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class DasSamplerBasicTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalFulu();
  private static final UInt64 SAMPLING_INDEX_0 = UInt64.ZERO;
  private static final UInt64 SAMPLING_INDEX_2 = UInt64.valueOf(2);
  private static final UInt64 SAMPLING_INDEX_5 = UInt64.valueOf(5);
  private static final Set<UInt64> SAMPLING_INDICES =
      Set.of(SAMPLING_INDEX_0, SAMPLING_INDEX_2, SAMPLING_INDEX_5);
  private static final int MAX_RECENTLY_SAMPLED_BLOCKS = 64;

  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(stubTimeProvider);
  private final RPCFetchDelayProvider rpcFetchDelayProvider = mock(RPCFetchDelayProvider.class);

  private RecentChainData recentChainData;
  private DataColumnSidecarCustody custody;
  private DataColumnSidecarRetriever retriever;
  private CurrentSlotProvider currentSlotProvider;
  private BlockImportChannel blockImportChannel;
  private CustodyGroupCountManager custodyGroupCountManager;
  private StubMetricsSystem metricsSystem;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, SPEC);

  private DasSamplerBasicImpl sampler;

  @BeforeEach
  public void setUp() {
    custodyGroupCountManager = mock(CustodyGroupCountManager.class);
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(SAMPLING_INDICES);
    recentChainData = mock(RecentChainData.class);
    custody = mock(DataColumnSidecarCustody.class);
    retriever = mock(DataColumnSidecarRetriever.class);
    currentSlotProvider = mock(CurrentSlotProvider.class);
    blockImportChannel = mock(BlockImportChannel.class);
    metricsSystem = new StubMetricsSystem();

    when(retriever.retrieve(any()))
        .thenReturn(SafeFuture.completedFuture(mock(DataColumnSidecar.class)));

    when(currentSlotProvider.getCurrentSlot()).thenReturn(UInt64.ZERO);
    when(custody.onNewValidatedDataColumnSidecar(any(), any())).thenReturn(SafeFuture.COMPLETE);

    sampler = createSampler(MAX_RECENTLY_SAMPLED_BLOCKS, metricsSystem);
  }

  private DasSamplerBasicImpl createSampler(
      final int maxRecentlySampledBlocks, final StubMetricsSystem metricsSystem) {
    return new DasSamplerBasicImpl(
        SPEC,
        asyncRunner,
        currentSlotProvider,
        rpcFetchDelayProvider,
        custody,
        retriever,
        custodyGroupCountManager,
        recentChainData,
        true,
        metricsSystem,
        maxRecentlySampledBlocks,
        blockImportChannel);
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldAddToTracker() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);

    final Set<UInt64> remainingColumns = Set.of(SAMPLING_INDEX_2, SAMPLING_INDEX_5);

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC);

    assertSamplerTracker(sidecar.getBeaconBlockRoot(), sidecar.getSlot(), remainingColumns);
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldScheduleRPCFetchWhenDelayIsNonZero() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);

    final Set<UInt64> remainingColumns = Set.of(SAMPLING_INDEX_2, SAMPLING_INDEX_5);

    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ofSeconds(1));

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC);

    assertSamplerTrackerHasRPCFetchScheduled(sidecar.getBeaconBlockRoot(), true);

    assertRPCFetchInMillis(
        sidecar.getSlot(), sidecar.getBeaconBlockRoot(), remainingColumns, 1_000);
    assertSamplerTrackerHasRPCFetchScheduled(sidecar.getBeaconBlockRoot(), false);
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldScheduleRPCFetchWhenDelayIsZero() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);

    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ZERO);

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC);

    assertThat(asyncRunner.countDelayedActions()).isZero();
    assertSamplerTrackerHasRPCFetchScheduled(sidecar.getBeaconBlockRoot(), false);
  }

  @Test
  void checkDataAvailability_shouldAddToTrackerAndReturnCompletionFuture() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<List<UInt64>> completionFuture =
        sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    assertThat(
            sampler
                .getRecentlySampledColumnsByRoot()
                .get(slotAndBlockRoot.getBlockRoot())
                .completionFuture())
        .isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldCallRetrieverForMissingColumnsAndCallCustodyOnRetrieval() {
    final SignedBeaconBlockHeader block = dataStructureUtil.randomSignedBeaconBlockHeader();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getMessage().getSlot(), block.getMessage().getRoot());

    final Map<UInt64, DataColumnSidecar> missingColumnSidecars =
        SAMPLING_INDICES.stream()
            .map(index -> dataStructureUtil.randomDataColumnSidecar(block, index))
            .collect(
                Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, Function.identity()));
    final Map<UInt64, SafeFuture<DataColumnSidecar>> futureSidecarsByIndex =
        missingColumnSidecars.values().stream()
            .map(DataColumnSidecar::getIndex)
            .map(index -> entry(index, new SafeFuture<DataColumnSidecar>()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    final DataColumnSidecar lateArrivedSidecar = missingColumnSidecars.get(SAMPLING_INDEX_0);
    final Set<UInt64> remainingColumnsIndices = Set.of(SAMPLING_INDEX_2, SAMPLING_INDEX_5);

    // prepare retriever mock to return futures when called
    doAnswer(
            invocation -> {
              final DataColumnSlotAndIdentifier id = invocation.getArgument(0);
              return futureSidecarsByIndex.get(id.columnIndex());
            })
        .when(retriever)
        .retrieve(any());

    when(rpcFetchDelayProvider.calculate(slotAndBlockRoot.getSlot()))
        .thenReturn(Duration.ofSeconds(1));

    // da check is requested
    sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);

    assertRPCFetchInMillis(
        slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), SAMPLING_INDICES, 1_000);

    // we receive one sidecar late, while retriever is still working
    sampler.onNewValidatedDataColumnSidecar(lateArrivedSidecar, RemoteOrigin.GOSSIP);

    // let the retriever complete all the futures
    futureSidecarsByIndex.forEach(
        (index, future) -> future.complete(missingColumnSidecars.get(index)));

    // verify RPC fetch schedule is reset
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), false);

    // verify we never called custody for the late sidecar arrival
    verify(custody, never()).onNewValidatedDataColumnSidecar(eq(lateArrivedSidecar), any());

    // verify we call custody for all the retrieved sidecars
    for (final UInt64 missingColumn : remainingColumnsIndices) {
      verify(custody)
          .onNewValidatedDataColumnSidecar(
              missingColumnSidecars.get(missingColumn), RemoteOrigin.RPC);
    }

    verifyNoMoreInteractions(retriever);
    verifyNoMoreInteractions(custody);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldSetRPCFetchedToFalseOnFailureAndRPCFetchesAgain() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    when(retriever.retrieve(any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("error")));

    final SafeFuture<List<UInt64>> completionFuture =
        sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    assertThat(
            sampler
                .getRecentlySampledColumnsByRoot()
                .get(slotAndBlockRoot.getBlockRoot())
                .completionFuture())
        .isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), false);

    verify(retriever, times(SAMPLING_INDICES.size())).retrieve(any());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<List<UInt64>> secondCompletionFuture =
        sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    verify(retriever, times(SAMPLING_INDICES.size() * 2)).retrieve(any());

    assertThat(secondCompletionFuture).isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldRPCFetchImmediatelyIfNotPreviouslyScheduled() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    when(rpcFetchDelayProvider.calculate(slotAndBlockRoot.getSlot())).thenReturn(Duration.ZERO);

    sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertRPCFetchInMillis(
        slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), SAMPLING_INDICES, 0);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldHandleMultipleTrackersFromMultipleEntryPoints() {
    final DataColumnSlotAndIdentifier source1 =
        new DataColumnSlotAndIdentifier(
            dataStructureUtil.randomSlot(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());
    final DataColumnSidecar source2 =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), dataStructureUtil.randomUInt64());
    final SlotAndBlockRoot source3 =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    sampler.onNewValidatedDataColumnSidecar(source1, RemoteOrigin.CUSTODY);
    sampler.onNewValidatedDataColumnSidecar(source2, RemoteOrigin.RPC);
    sampler.checkDataAvailability(source3.getSlot(), source3.getBlockRoot());

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsKey(source1.blockRoot())
        .containsKey(source2.getBeaconBlockRoot())
        .containsKey(source3.getBlockRoot());
  }

  @Test
  void flush_shouldForwardToRetriever() {
    sampler.flush();
    verify(retriever).flush();
  }

  @Test
  void checkSamplingEligibility_shouldReturnRequired() {

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ZERO, 1);

    final SamplingEligibilityStatus result =
        sampler.checkSamplingEligibility(block.getBeaconBlock().orElseThrow());

    assertEquals(SamplingEligibilityStatus.REQUIRED, result);
  }

  @Test
  void checkSamplingEligibility_shouldReturnNoBlobs() {

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ZERO, 0);

    final SamplingEligibilityStatus result =
        sampler.checkSamplingEligibility(block.getBeaconBlock().orElseThrow());

    assertEquals(SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS, result);
  }

  @Test
  void checkSamplingEligibility_shouldReturnOldEpoch() {

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ZERO, 0);

    when(currentSlotProvider.getCurrentSlot()).thenReturn(UInt64.MAX_VALUE);

    final SamplingEligibilityStatus result =
        sampler.checkSamplingEligibility(block.getBeaconBlock().orElseThrow());

    assertEquals(SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH, result);
  }

  @Test
  void onRemoveAllForBlock_shouldPruneTrackers() {
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot slotAndBlockRootAdded = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot slotAndBlockRootRemaining = dataStructureUtil.randomSlotAndBlockRoot();

    final DataColumnSamplingTracker completedTracker = mock(DataColumnSamplingTracker.class);
    when(completedTracker.completionFuture()).thenReturn(SafeFuture.completedFuture(null));
    when(completedTracker.blockRoot()).thenReturn(slotAndBlockRootRemaining.getBlockRoot());
    when(completedTracker.slot()).thenReturn(slotAndBlockRootRemaining.getSlot());

    final SafeFuture<List<UInt64>> completionFuture = new SafeFuture<>();
    final DataColumnSamplingTracker nonCompletedTracker = mock(DataColumnSamplingTracker.class);
    when(nonCompletedTracker.completionFuture()).thenReturn(completionFuture);
    when(nonCompletedTracker.blockRoot()).thenReturn(slotAndBlockRootAdded.getBlockRoot());
    when(nonCompletedTracker.slot()).thenReturn(slotAndBlockRootAdded.getSlot());

    sampler
        .getRecentlySampledColumnsByRoot()
        .put(slotAndBlockRootRemaining.getBlockRoot(), completedTracker);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(slotAndBlockRootAdded.getBlockRoot(), nonCompletedTracker);

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsValues(completedTracker, nonCompletedTracker);

    sampler.removeAllForBlock(slotAndBlockRootAdded);

    assertThat(sampler.getRecentlySampledColumnsByRoot()).containsValues(completedTracker);
    assertThat(nonCompletedTracker.completionFuture()).isCancelled();

    // Non-existing - doesn't throw
    sampler.removeAllForBlock(slotAndBlockRoot);
  }

  @Test
  void onSlot_shouldPruneTrackers() {
    final UInt64 finalizedEpoch = UInt64.valueOf(1);
    final Bytes32 importedBlockRoot = dataStructureUtil.randomBytes32();
    final UInt64 lastFinalizedSlot = UInt64.valueOf(8);
    final UInt64 firstNonFinalizedSlot = UInt64.valueOf(9);

    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(recentChainData.containsBlock(any())).thenReturn(false);
    when(recentChainData.containsBlock(importedBlockRoot)).thenReturn(true);

    final DataColumnSamplingTracker partiallyCompletedTrackerBeforeFinalized =
        mock(DataColumnSamplingTracker.class);
    when(partiallyCompletedTrackerBeforeFinalized.completionFuture())
        .thenReturn(SafeFuture.completedFuture(null));
    when(partiallyCompletedTrackerBeforeFinalized.fullySampled())
        .thenReturn(new AtomicBoolean(false));
    when(partiallyCompletedTrackerBeforeFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(partiallyCompletedTrackerBeforeFinalized.slot()).thenReturn(lastFinalizedSlot.decrement());

    final DataColumnSamplingTracker fullyCompletedTrackerBeforeFinalized =
        mock(DataColumnSamplingTracker.class);
    when(fullyCompletedTrackerBeforeFinalized.completionFuture())
        .thenReturn(SafeFuture.completedFuture(null));
    when(fullyCompletedTrackerBeforeFinalized.fullySampled()).thenReturn(new AtomicBoolean(true));
    when(fullyCompletedTrackerBeforeFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(fullyCompletedTrackerBeforeFinalized.slot()).thenReturn(lastFinalizedSlot.decrement());

    // But not imported yet!
    final DataColumnSamplingTracker fullyCompletedTrackerAfterFinalized =
        mock(DataColumnSamplingTracker.class);
    when(fullyCompletedTrackerAfterFinalized.completionFuture())
        .thenReturn(SafeFuture.completedFuture(null));
    when(fullyCompletedTrackerAfterFinalized.fullySampled()).thenReturn(new AtomicBoolean(true));
    when(fullyCompletedTrackerAfterFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(fullyCompletedTrackerAfterFinalized.slot()).thenReturn(lastFinalizedSlot.increment());

    final DataColumnSamplingTracker incompleteTrackerBeforeFinalized =
        mock(DataColumnSamplingTracker.class);
    final SafeFuture<List<UInt64>> incompleteTrackerFuture = new SafeFuture<>();
    when(incompleteTrackerBeforeFinalized.completionFuture()).thenReturn(incompleteTrackerFuture);
    when(incompleteTrackerBeforeFinalized.fullySampled()).thenReturn(new AtomicBoolean(false));
    when(incompleteTrackerBeforeFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(incompleteTrackerBeforeFinalized.slot()).thenReturn(lastFinalizedSlot);

    final SafeFuture<List<UInt64>> incompleteTrackerForImportedBlockFuture = new SafeFuture<>();
    final DataColumnSamplingTracker incompleteTrackerForImportedBlock =
        mock(DataColumnSamplingTracker.class);
    when(incompleteTrackerForImportedBlock.completionFuture())
        .thenReturn(incompleteTrackerForImportedBlockFuture);
    when(incompleteTrackerForImportedBlock.fullySampled()).thenReturn(new AtomicBoolean(false));
    when(incompleteTrackerForImportedBlock.slot()).thenReturn(firstNonFinalizedSlot);
    when(incompleteTrackerForImportedBlock.blockRoot()).thenReturn(importedBlockRoot);

    sampler
        .getRecentlySampledColumnsByRoot()
        .put(
            partiallyCompletedTrackerBeforeFinalized.blockRoot(),
            partiallyCompletedTrackerBeforeFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(
            fullyCompletedTrackerBeforeFinalized.blockRoot(), fullyCompletedTrackerBeforeFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(fullyCompletedTrackerAfterFinalized.blockRoot(), fullyCompletedTrackerAfterFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(incompleteTrackerBeforeFinalized.blockRoot(), incompleteTrackerBeforeFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(incompleteTrackerForImportedBlock.blockRoot(), incompleteTrackerForImportedBlock);

    sampler.onSlot(UInt64.valueOf(20));

    // DA check is completed but fetch is not yet completed for this one
    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsExactly(
            entry(
                partiallyCompletedTrackerBeforeFinalized.blockRoot(),
                partiallyCompletedTrackerBeforeFinalized),
            entry(
                fullyCompletedTrackerAfterFinalized.blockRoot(),
                fullyCompletedTrackerAfterFinalized));
    assertThat(incompleteTrackerBeforeFinalized.completionFuture()).isCompletedExceptionally();
    assertThat(incompleteTrackerForImportedBlock.completionFuture()).isCompletedExceptionally();
    assertThat(partiallyCompletedTrackerBeforeFinalized.completionFuture()).isCompleted();
    assertThat(fullyCompletedTrackerBeforeFinalized.completionFuture()).isCompleted();
  }

  @Test
  void onNewBlock_shouldNotCreateTrackerOrScheduleFetchWhenBlockHasNoBlobs() {
    final SignedBeaconBlock blockWithoutBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 0);

    when(rpcFetchDelayProvider.calculate(any())).thenReturn(Duration.ofSeconds(1));

    sampler.onNewBlock(blockWithoutBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(blockWithoutBlobs.getRoot());
    assertThat(asyncRunner.countDelayedActions()).isZero();
    verify(retriever, never()).retrieve(any());
  }

  @Test
  void onNewBlock_shouldCreateTrackerWhenBlockHasBlobs() {
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);

    sampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(sampler.getRecentlySampledColumnsByRoot()).containsKey(blockWithBlobs.getRoot());
    assertSamplerTracker(blockWithBlobs.getRoot(), blockWithBlobs.getSlot(), SAMPLING_INDICES);
  }

  @Test
  void onBlockImported_shouldRemoveTrackerWhenDataAvailabilityCheckedOnBlock() {
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    when(rpcFetchDelayProvider.calculate(blockWithBlobs.getSlot())).thenReturn(Duration.ZERO);

    sampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(sampler.getRecentlySampledColumnsByRoot()).containsKey(blockWithBlobs.getRoot());

    sampler.onBlockImported(blockWithBlobs);

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(blockWithBlobs.getRoot());
  }

  @Test
  void onBlockImported_shouldRetainTrackerWhenDataAvailabilityDeferredToExecutionPayload() {
    final DasSamplerBasicImpl gloasSampler = createGloasSampler();
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    when(rpcFetchDelayProvider.calculate(blockWithBlobs.getSlot())).thenReturn(Duration.ZERO);

    gloasSampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot())
        .containsKey(blockWithBlobs.getRoot());

    gloasSampler.onBlockImported(blockWithBlobs);

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot())
        .containsKey(blockWithBlobs.getRoot());

    gloasSampler.onExecutionPayloadImported(blockWithBlobs.getSlotAndBlockRoot());

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(blockWithBlobs.getRoot());
  }

  @Test
  void onSlot_shouldRetainGloasImportedBlockTrackerUntilExecutionPayloadImported() {
    final DasSamplerBasicImpl gloasSampler = createGloasSampler();
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    when(rpcFetchDelayProvider.calculate(blockWithBlobs.getSlot())).thenReturn(Duration.ZERO);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(recentChainData.containsBlock(blockWithBlobs.getRoot())).thenReturn(true);

    gloasSampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));
    final SafeFuture<List<UInt64>> completionFuture =
        gloasSampler
            .getRecentlySampledColumnsByRoot()
            .get(blockWithBlobs.getRoot())
            .completionFuture();

    gloasSampler.onSlot(UInt64.valueOf(2));

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot())
        .containsKey(blockWithBlobs.getRoot());
    assertThat(completionFuture).isNotDone();

    // Data availability is deferred to the payload, so the tracker is pruned now.
    gloasSampler.onExecutionPayloadImported(blockWithBlobs.getSlotAndBlockRoot());

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(blockWithBlobs.getRoot());
    assertThat(completionFuture).isCancelled();
  }

  @Test
  void enableBlockImportOnCompletion_shouldImportOnlyOnceWhenCalledMultipleTimes() {
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    when(rpcFetchDelayProvider.calculate(blockWithBlobs.getSlot())).thenReturn(Duration.ZERO);
    when(blockImportChannel.importBlock(blockWithBlobs))
        .thenReturn(
            SafeFuture.completedFuture(
                new BlockImportAndBroadcastValidationResults(
                    SafeFuture.completedFuture(BlockImportResult.successful(blockWithBlobs)))));

    sampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));

    sampler.enableBlockImportOnCompletion(blockWithBlobs);
    sampler.enableBlockImportOnCompletion(blockWithBlobs);

    verifyNoInteractions(blockImportChannel);

    SAMPLING_INDICES.forEach(
        columnIndex ->
            sampler.onNewValidatedDataColumnSidecar(
                new DataColumnSlotAndIdentifier(
                    blockWithBlobs.getSlot(), blockWithBlobs.getRoot(), columnIndex),
                RemoteOrigin.GOSSIP));

    verify(blockImportChannel, times(1)).importBlock(blockWithBlobs);
  }

  @Test
  void getBlock_shouldReturnEmptyWhenNoTrackerExists() {
    assertThat(sampler.getBlock(dataStructureUtil.randomBytes32())).isEmpty();
    assertThat(sampler.containsBlock(dataStructureUtil.randomBytes32())).isFalse();
  }

  @Test
  void getBlock_shouldReturnEmptyWhenTrackerHasNoBlockYet() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.GOSSIP);

    assertThat(sampler.getRecentlySampledColumnsByRoot()).containsKey(sidecar.getBeaconBlockRoot());
    assertThat(sampler.getBlock(sidecar.getBeaconBlockRoot())).isEmpty();
    assertThat(sampler.containsBlock(sidecar.getBeaconBlockRoot())).isFalse();
  }

  @Test
  void onNewBlock_shouldStoreBlockOnTrackerWhenBlockHasBlobs() {
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);

    sampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(sampler.containsBlock(blockWithBlobs.getRoot())).isTrue();
    assertThat(sampler.getBlock(blockWithBlobs.getRoot())).contains(blockWithBlobs);
  }

  @Test
  void onNewBlock_shouldNotStoreBlockWhenBlockHasNoBlobs() {
    final SignedBeaconBlock blockWithoutBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 0);

    sampler.onNewBlock(blockWithoutBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(sampler.containsBlock(blockWithoutBlobs.getRoot())).isFalse();
    assertThat(sampler.getBlock(blockWithoutBlobs.getRoot())).isEmpty();
  }

  @Test
  void getOrCreateTracker_shouldEvictOldestCompletedTrackerAtSoftLimit() {
    final DasSamplerBasicImpl smallSampler = createSampler(3, new StubMetricsSystem());

    final DataColumnSamplingTracker oldestCompleted =
        completedMockTracker(dataStructureUtil.randomBytes32(), 100L);
    final DataColumnSamplingTracker newerCompleted =
        completedMockTracker(dataStructureUtil.randomBytes32(), 200L);
    final DataColumnSamplingTracker incomplete =
        incompleteMockTracker(dataStructureUtil.randomBytes32(), 150L);

    smallSampler
        .getRecentlySampledColumnsByRoot()
        .put(oldestCompleted.blockRoot(), oldestCompleted);
    smallSampler.getRecentlySampledColumnsByRoot().put(newerCompleted.blockRoot(), newerCompleted);
    smallSampler.getRecentlySampledColumnsByRoot().put(incomplete.blockRoot(), incomplete);
    when(recentChainData.containsBlock(oldestCompleted.blockRoot())).thenReturn(true);
    when(recentChainData.containsBlock(newerCompleted.blockRoot())).thenReturn(true);

    // Trigger insertion of a new tracker, which calls makeRoomForNewTracker
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);
    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ofSeconds(1));

    smallSampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.GOSSIP);

    // softExcess = 4 - 3 + 1 = 2 → both completed evicted, incomplete and new remain
    assertThat(smallSampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(oldestCompleted.blockRoot())
        .doesNotContainKey(newerCompleted.blockRoot())
        .containsKey(incomplete.blockRoot())
        .containsKey(sidecar.getBeaconBlockRoot());
  }

  @Test
  void getOrCreateTracker_shouldPreferOldestCompletedWhenSoftEvicting() {
    final DasSamplerBasicImpl smallSampler = createSampler(3, new StubMetricsSystem());

    final DataColumnSamplingTracker oldestCompleted =
        completedMockTracker(dataStructureUtil.randomBytes32(), 100L);
    final DataColumnSamplingTracker newerCompleted =
        completedMockTracker(dataStructureUtil.randomBytes32(), 200L);

    smallSampler
        .getRecentlySampledColumnsByRoot()
        .put(oldestCompleted.blockRoot(), oldestCompleted);
    smallSampler.getRecentlySampledColumnsByRoot().put(newerCompleted.blockRoot(), newerCompleted);
    when(recentChainData.containsBlock(oldestCompleted.blockRoot())).thenReturn(true);
    when(recentChainData.containsBlock(newerCompleted.blockRoot())).thenReturn(true);

    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);
    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ofSeconds(1));

    smallSampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.GOSSIP);

    // size after insertion = 3 → softExcess = 1 → only the oldest completed is evicted
    assertThat(smallSampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(oldestCompleted.blockRoot())
        .containsKey(newerCompleted.blockRoot())
        .containsKey(sidecar.getBeaconBlockRoot());
  }

  @Test
  void getOrCreateTracker_shouldRetainCompletedTrackerAwaitingImportAtSoftLimit() {
    final DasSamplerBasicImpl smallSampler = createSampler(2, new StubMetricsSystem());
    final DataColumnSamplingTracker completedPendingImport =
        completedMockTracker(dataStructureUtil.randomBytes32(), 100L);
    smallSampler
        .getRecentlySampledColumnsByRoot()
        .put(completedPendingImport.blockRoot(), completedPendingImport);

    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);
    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ofSeconds(1));

    smallSampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.GOSSIP);

    assertThat(smallSampler.getRecentlySampledColumnsByRoot())
        .containsKey(completedPendingImport.blockRoot())
        .containsKey(sidecar.getBeaconBlockRoot());
  }

  @Test
  void getOrCreateTracker_shouldForceEvictOldestIncompleteAtHardLimit() {
    // hardLimit = maxRecentlySampledBlocks * 4 = 8
    final DasSamplerBasicImpl smallSampler = createSampler(2, new StubMetricsSystem());

    final List<DataColumnSamplingTracker> incompleteTrackers =
        IntStream.range(0, 8)
            .mapToObj(i -> incompleteMockTracker(dataStructureUtil.randomBytes32(), 1_000L + i))
            .toList();
    incompleteTrackers.forEach(
        t -> smallSampler.getRecentlySampledColumnsByRoot().put(t.blockRoot(), t));

    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDEX_0);
    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ofSeconds(1));

    smallSampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.GOSSIP);

    // After insertion: size = 9, hardLimit = 8 → 1 oldest incomplete force-evicted
    final DataColumnSamplingTracker oldestIncomplete = incompleteTrackers.getFirst();
    assertThat(smallSampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(oldestIncomplete.blockRoot())
        .containsKey(sidecar.getBeaconBlockRoot());
    assertThat(oldestIncomplete.completionFuture()).isCompletedExceptionally();
    // Newer incomplete trackers are preserved
    for (int i = 1; i < incompleteTrackers.size(); i++) {
      assertThat(smallSampler.getRecentlySampledColumnsByRoot())
          .containsKey(incompleteTrackers.get(i).blockRoot());
      assertThat(incompleteTrackers.get(i).completionFuture()).isNotDone();
    }
  }

  @Test
  void metrics_shouldExposeRecentlySampledBlocksGauge() {
    assertThat(
            metricsSystem
                .getGauge(TekuMetricCategory.BEACON, "das_recently_sampled_blocks_size")
                .getValue())
        .isZero();

    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    sampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));

    assertThat(
            metricsSystem
                .getGauge(TekuMetricCategory.BEACON, "das_recently_sampled_blocks_size")
                .getValue())
        .isEqualTo(1.0);
  }

  private DataColumnSamplingTracker completedMockTracker(
      final Bytes32 blockRoot, final long createdAtNanos) {
    final DataColumnSamplingTracker tracker = mock(DataColumnSamplingTracker.class);
    when(tracker.blockRoot()).thenReturn(blockRoot);
    when(tracker.slot()).thenReturn(UInt64.ONE);
    when(tracker.completionFuture()).thenReturn(SafeFuture.completedFuture(List.of()));
    when(tracker.createdAtNanos()).thenReturn(createdAtNanos);
    when(tracker.fullySampled()).thenReturn(new AtomicBoolean(true));
    return tracker;
  }

  private DataColumnSamplingTracker incompleteMockTracker(
      final Bytes32 blockRoot, final long createdAtNanos) {
    final DataColumnSamplingTracker tracker = mock(DataColumnSamplingTracker.class);
    when(tracker.blockRoot()).thenReturn(blockRoot);
    when(tracker.slot()).thenReturn(UInt64.ONE);
    when(tracker.completionFuture()).thenReturn(new SafeFuture<>());
    when(tracker.createdAtNanos()).thenReturn(createdAtNanos);
    when(tracker.fullySampled()).thenReturn(new AtomicBoolean(false));
    return tracker;
  }

  @Test
  void delayedRpcFetch_shouldSkipRemovedTracker() {
    final SignedBeaconBlock blockWithBlobs =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    when(rpcFetchDelayProvider.calculate(blockWithBlobs.getSlot()))
        .thenReturn(Duration.ofMillis(10));

    sampler.onNewBlock(blockWithBlobs, Optional.of(RemoteOrigin.GOSSIP));
    sampler.removeAllForBlock(blockWithBlobs.getSlotAndBlockRoot());

    stubTimeProvider.advanceTimeByMillis(10);
    asyncRunner.executeDueActions();

    assertThat(asyncRunner.countDelayedActions()).isZero();
    verify(retriever, never()).retrieve(any());
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldIgnoreWhenPreGloasBlockAlreadyImported() {
    final DataColumnSlotAndIdentifier columnId =
        new DataColumnSlotAndIdentifier(
            UInt64.ONE, dataStructureUtil.randomBytes32(), SAMPLING_INDEX_0);
    when(recentChainData.containsBlock(columnId.blockRoot())).thenReturn(true);

    sampler.onNewValidatedDataColumnSidecar(columnId, RemoteOrigin.GOSSIP);

    assertThat(sampler.getRecentlySampledColumnsByRoot()).doesNotContainKey(columnId.blockRoot());
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldTrackGloasBlockUntilPayloadImported() {
    final DasSamplerBasicImpl gloasSampler = createGloasSampler();
    final DataColumnSlotAndIdentifier columnId =
        new DataColumnSlotAndIdentifier(
            UInt64.ONE, dataStructureUtil.randomBytes32(), SAMPLING_INDEX_0);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.containsBlock(columnId.blockRoot())).thenReturn(true);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getExecutionPayloadIfAvailable(columnId.blockRoot())).thenReturn(Optional.empty());
    when(rpcFetchDelayProvider.calculate(columnId.slot())).thenReturn(Duration.ZERO);

    gloasSampler.onNewValidatedDataColumnSidecar(columnId, RemoteOrigin.GOSSIP);

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot()).containsKey(columnId.blockRoot());
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldIgnoreGloasBlockAfterPayloadImported() {
    final DasSamplerBasicImpl gloasSampler = createGloasSampler();
    final DataColumnSlotAndIdentifier columnId =
        new DataColumnSlotAndIdentifier(
            UInt64.ONE, dataStructureUtil.randomBytes32(), SAMPLING_INDEX_0);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.containsBlock(columnId.blockRoot())).thenReturn(true);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getExecutionPayloadIfAvailable(columnId.blockRoot()))
        .thenReturn(Optional.of(mock(SignedExecutionPayloadEnvelope.class)));

    gloasSampler.onNewValidatedDataColumnSidecar(columnId, RemoteOrigin.GOSSIP);

    assertThat(gloasSampler.getRecentlySampledColumnsByRoot())
        .doesNotContainKey(columnId.blockRoot());
  }

  private void assertSamplerTracker(
      final Bytes32 blockRoot, final UInt64 slot, final Set<UInt64> missingColumns) {
    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .hasEntrySatisfying(
            blockRoot,
            tracker -> {
              assertThat(tracker.missingColumns())
                  .containsExactlyInAnyOrderElementsOf(missingColumns);

              assertThat(tracker.getMissingColumnIdentifiers())
                  .containsExactlyInAnyOrderElementsOf(
                      missingColumns.stream()
                          .map(
                              columnIndex ->
                                  new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex))
                          .toList());
            });
  }

  private void assertSamplerTrackerHasRPCFetchScheduled(
      final Bytes32 blockRoot, final boolean expected) {
    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .hasEntrySatisfying(
            blockRoot,
            tracker -> assertThat(tracker.rpcFetchInProgress().get()).isEqualTo(expected));
  }

  private void assertRPCFetchInMillis(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final Set<UInt64> missingColumns,
      final int millisFromNow) {

    if (millisFromNow > 0) {
      // advance up to one millis from the due
      stubTimeProvider.advanceTimeByMillis(millisFromNow - 1);
      // verify scheduled but not due for execution
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
      asyncRunner.executeDueActions();
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);

      // then advance to the due
      stubTimeProvider.advanceTimeByMillis(1);

      // due time arrived
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
      asyncRunner.executeDueActions();
    }

    // no delayed actions, we expect fetch already triggered
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);

    // verify we call retrieve for all missing columns
    for (final UInt64 missingColumn : missingColumns) {
      verify(retriever).retrieve(new DataColumnSlotAndIdentifier(slot, blockRoot, missingColumn));
    }
  }

  @Test
  void getSlotForBlockRoot_returnsTrackerSlotForObservedColumn() {
    // Non-zero delay so onFirstSeen schedules (StubAsyncRunner won't auto-run) instead of fetching
    // now.
    when(rpcFetchDelayProvider.calculate(any())).thenReturn(Duration.ofSeconds(1));
    final UInt64 slot = UInt64.valueOf(123);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(recentChainData.containsBlock(blockRoot)).thenReturn(false);

    sampler.onNewValidatedDataColumnSidecar(
        new DataColumnSlotAndIdentifier(slot, blockRoot, SAMPLING_INDEX_0), RemoteOrigin.GOSSIP);

    assertThat(sampler.getSlotForBlockRoot(blockRoot)).contains(slot);
  }

  @Test
  void getSlotForBlockRoot_returnsEmptyForUnknownRoot() {
    assertThat(sampler.getSlotForBlockRoot(dataStructureUtil.randomBytes32())).isEmpty();
  }

  private DasSamplerBasicImpl createGloasSampler() {
    return new DasSamplerBasicImpl(
        TestSpecFactory.createMinimalGloas(),
        asyncRunner,
        currentSlotProvider,
        rpcFetchDelayProvider,
        custody,
        retriever,
        custodyGroupCountManager,
        recentChainData,
        true,
        new StubMetricsSystem(),
        MAX_RECENTLY_SAMPLED_BLOCKS,
        blockImportChannel);
  }
}

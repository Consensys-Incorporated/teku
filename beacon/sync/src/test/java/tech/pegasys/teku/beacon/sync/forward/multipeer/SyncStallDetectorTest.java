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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.SyncStallDetector.MAX_SECONDS_BETWEEN_IMPORTS;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.SyncStallDetector.MAX_SECONDS_BETWEEN_IMPORT_PROGRESS;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.SyncStallDetector.STALL_CHECK_INTERVAL;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.SyncSourceBatch;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncStallDetectorTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final InlineEventThread eventThread = new InlineEventThread();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final SyncController syncController = mock(SyncController.class);
  private final BatchSync sync = mock(BatchSync.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final SyncStallDetector detector =
      new SyncStallDetector(
          spec, eventThread, asyncRunner, timeProvider, syncController, sync, recentChainData);

  @BeforeEach
  void setUp() {
    assertThat(detector.start()).isDone();
    when(syncController.isSyncActive()).thenReturn(true);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ONE);
  }

  @Test
  void shouldNotAbortSyncWhenNoSyncIsInProgress() {
    // No sync in progress
    when(syncController.isSyncActive()).thenReturn(false);

    // Sync started long enough ago that it should have started importing something by now
    when(sync.getLastImportTimerStartPointSeconds())
        .thenReturn(timeProvider.getTimeInSeconds().minus(MAX_SECONDS_BETWEEN_IMPORTS));

    triggerStallCheck();

    verify(sync, never()).abort();
  }

  @Test
  void shouldAbortSyncWhenNoImportStartedInTime() {
    // Sync started long enough ago that it should have started importing something by now
    when(sync.getLastImportTimerStartPointSeconds())
        .thenReturn(timeProvider.getTimeInSeconds().minus(MAX_SECONDS_BETWEEN_IMPORTS));

    triggerStallCheck();

    verify(sync).abort();
  }

  @Test
  void shouldAbortSyncWhenImportStartedByNotMakingProgress() {
    // First import started in time
    when(sync.getLastImportTimerStartPointSeconds()).thenReturn(timeProvider.getTimeInSeconds());
    final Batch importingBatch = batchWithBlocks();
    when(sync.getImportingBatch()).thenReturn(Optional.of(importingBatch));
    withBlockNotImported(importingBatch, 0);

    // First check records that the sync started and which block it is up to
    triggerStallCheck();
    verify(sync, never()).abort();

    // Enough time passes before the first block is imported
    timeProvider.advanceTimeBySeconds(MAX_SECONDS_BETWEEN_IMPORT_PROGRESS);

    triggerStallCheck();
    verify(sync).abort();
  }

  @Test
  void shouldNotAbortSyncWhenBatchTakesALongTimeToImportButIsMakingProgress() {
    // First import started in time
    when(sync.getLastImportTimerStartPointSeconds()).thenReturn(timeProvider.getTimeInSeconds());
    final Batch importingBatch = batchWithBlocks();
    when(sync.getImportingBatch()).thenReturn(Optional.of(importingBatch));
    withBlockNotImported(importingBatch, 0);

    // First check records that the sync started and which block it is up to
    triggerStallCheck();
    verify(sync, never()).abort();

    // Enough time passes to trigger the stall but the first block has now been imported
    timeProvider.advanceTimeBySeconds(MAX_SECONDS_BETWEEN_IMPORT_PROGRESS);
    withBlockImported(importingBatch, 0);
    withBlockNotImported(importingBatch, 1);

    triggerStallCheck();
    verify(sync, never()).abort();
  }

  @Test
  void shouldAbortSyncWhenFirstBlockOfSecondImportedBatchNotImported() {
    // First import started in time
    when(sync.getLastImportTimerStartPointSeconds()).thenReturn(timeProvider.getTimeInSeconds());
    final Batch importingBatch = batchWithBlocks();
    when(sync.getImportingBatch()).thenReturn(Optional.of(importingBatch));
    withBlockNotImported(importingBatch, 0);

    // First check records that the sync started and which block it is up to
    triggerStallCheck();
    verify(sync, never()).abort();

    // Enough time passes to trigger the stall but the import completed and a new batch started
    timeProvider.advanceTimeBySeconds(MAX_SECONDS_BETWEEN_IMPORT_PROGRESS);
    withAllBlocksImported(importingBatch);
    final Batch secondImportingBatch = batchWithBlocks();
    when(sync.getImportingBatch()).thenReturn(Optional.of(secondImportingBatch));
    withBlockNotImported(secondImportingBatch, 0);
    triggerStallCheck();
    verify(sync, never()).abort();

    // Then time passes and no further progress is made
    timeProvider.advanceTimeBySeconds(MAX_SECONDS_BETWEEN_IMPORT_PROGRESS);

    // So the sync should be considered stalled
    triggerStallCheck();
    verify(sync).abort();
  }

  private void withBlockNotImported(final Batch importingBatch, final int blockIndex) {
    mockBlockImported(importingBatch, blockIndex, false);
  }

  private void withBlockImported(final Batch importingBatch, final int blockIndex) {
    mockBlockImported(importingBatch, blockIndex, true);
  }

  private void withAllBlocksImported(final Batch importingBatch) {
    importingBatch.getBlocks().forEach(block -> withBlockImported(block, true));
  }

  private void mockBlockImported(
      final Batch importingBatch, final int blockIndex, final boolean b) {
    final SignedBeaconBlock block = importingBatch.getBlocks().get(blockIndex);
    withBlockImported(block, b);
  }

  private void withBlockImported(final SignedBeaconBlock block, final boolean imported) {
    when(recentChainData.containsBlock(block.getRoot())).thenReturn(imported);
  }

  private void triggerStallCheck() {
    timeProvider.advanceTimeBySeconds(STALL_CHECK_INTERVAL.toSeconds());
    asyncRunner.executeDueActions();
  }

  private Batch batchWithBlocks() {
    final SyncSourceBatch batch = mock(SyncSourceBatch.class);
    when(batch.getBlocks())
        .thenReturn(
            List.of(
                dataStructureUtil.randomSignedBeaconBlock(100),
                dataStructureUtil.randomSignedBeaconBlock(101),
                dataStructureUtil.randomSignedBeaconBlock(104),
                dataStructureUtil.randomSignedBeaconBlock(105)));
    return batch;
  }
}

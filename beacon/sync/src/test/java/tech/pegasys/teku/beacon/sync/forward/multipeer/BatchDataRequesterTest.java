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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchAssert.assertThatBatch;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.StubBatchFactory;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChainTestUtil;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BatchDataRequesterTest {
  private static final UInt64 BATCH_SIZE = UInt64.valueOf(50);
  private static final int MAX_PENDING_BATCHES = 5;
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final InlineEventThread eventThread = new InlineEventThread();
  private final BatchChain batchChain = new BatchChain();
  private final StubBatchFactory batchFactory = new StubBatchFactory(eventThread, false);

  @SuppressWarnings("unchecked")
  private final Consumer<Batch> requestCompleteCallback = mock(Consumer.class);

  private final TargetChain targetChain =
      TargetChainTestUtil.chainWith(
          new SlotAndBlockRoot(UInt64.valueOf(500), dataStructureUtil.randomBytes32()));

  private final BatchDataRequester batchDataRequester =
      new BatchDataRequester(
          eventThread, batchChain, batchFactory, BATCH_SIZE, MAX_PENDING_BATCHES);

  @Test
  void shouldCreateNewBatchesWhenChainIsEmpty() {
    fillQueue(UInt64.valueOf(24));

    assertThat(batchFactory).hasSize(MAX_PENDING_BATCHES);
    assertThatBatch(batchFactory.get(0)).hasRange(25, 74);
    assertThatBatch(batchFactory.get(1)).hasRange(75, 124);
    assertThatBatch(batchFactory.get(2)).hasRange(125, 174);
    assertThatBatch(batchFactory.get(3)).hasRange(175, 224);
    assertThatBatch(batchFactory.get(4)).hasRange(225, 274);
    batchFactory.forEach(batch -> assertThatBatch(batch).isAwaitingBlocks());
  }

  @Test
  void shouldRequestAdditionalDataFromBatchesThatAreNotYetComplete() {
    // Block with some blocks, but not yet complete
    final Batch batch =
        batchFactory.createBatch(targetChain, UInt64.valueOf(6), UInt64.valueOf(10));
    batch.requestMoreBlocks(() -> {});
    batchFactory.receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(8));

    batchChain.add(batch);

    fillQueue(ZERO);

    // Should request additional blocks for the incomplete batch
    assertThatBatch(batch).isAwaitingBlocks();

    // And only create new batches up to the limit
    assertThat(batchChain.stream()).hasSize(MAX_PENDING_BATCHES);
  }

  @Test
  void shouldNotIncludeEmptyBatchesInThePendingBatchCount() {
    final Batch batch =
        batchFactory.createBatch(targetChain, UInt64.valueOf(6), UInt64.valueOf(10));
    batch.markComplete(); // Complete empty batch
    batchChain.add(batch);

    fillQueue(ZERO);

    // Shouldn't request more blocks for a complete batch
    assertThatBatch(batch).isNotAwaitingBlocks();

    // Exclude the empty batch from the pending batch count, so we can have one more than otherwise
    assertThat(batchChain.stream()).hasSize(MAX_PENDING_BATCHES + 1);
  }

  @Test
  void shouldIncludeNonEmptyIncompleteBatchesInThePendingBatchCount() {
    // Add a non-empty, complete batch
    final Batch batch =
        batchFactory.createBatch(targetChain, UInt64.valueOf(6), UInt64.valueOf(10));
    batch.requestMoreBlocks(() -> {});
    batchFactory.receiveBlocks(
        batch, dataStructureUtil.randomSignedBeaconBlock(batch.getLastSlot()));
    batchChain.add(batch);

    fillQueue(ZERO);

    // Shouldn't request more blocks for a complete batch
    assertThatBatch(batch).isNotAwaitingBlocks();

    // Complete batch is including in the pending count as it has blocks to import
    assertThat(batchChain.stream()).hasSize(MAX_PENDING_BATCHES);
  }

  @Test
  void shouldNotScheduleMoreBatchesWhenTargetSlotReached() {
    final Batch batch = batchFactory.createBatch(targetChain, UInt64.valueOf(440), BATCH_SIZE);
    batchChain.add(batch);

    fillQueue(ZERO);

    final List<Batch> batches = batchChain.stream().collect(toList());
    assertThat(batches).hasSize(2);
    assertThatBatch(batches.get(0)).hasRange(440, 489);
    assertThatBatch(batches.get(1)).hasRange(490, targetChain.getChainHead().getSlot().longValue());
  }

  @Test
  void shouldScheduleAdditionalBatchWhenThereIsOnlyOneBlockRemainingToFetch() {
    final UInt64 firstBatchStart = targetChain.getChainHead().getSlot().minus(BATCH_SIZE);
    final Batch batch = batchFactory.createBatch(targetChain, firstBatchStart, BATCH_SIZE);
    batchChain.add(batch);

    fillQueue(ZERO);

    final List<Batch> batches = batchChain.stream().collect(toList());
    assertThat(batches).hasSize(2);
    final long targetSlot = targetChain.getChainHead().getSlot().longValue();
    assertThatBatch(batches.get(0)).hasRange(firstBatchStart.intValue(), targetSlot - 1);
    assertThatBatch(batches.get(1)).hasRange(targetSlot, targetSlot);
  }

  private void fillQueue(final UInt64 commonAncestorSlot) {
    eventThread.execute(
        () ->
            batchDataRequester.fillRetrievingQueue(
                targetChain, commonAncestorSlot, requestCompleteCallback));
  }

  @Test
  void shouldReplaceBatchesFromOldChainsWithNoPeers() {
    final TargetChain oldTargetChain =
        TargetChainTestUtil.chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(500), dataStructureUtil.randomBytes32()));

    final Batch oldBatch1 = batchFactory.createBatch(oldTargetChain, UInt64.ZERO, BATCH_SIZE);
    final Batch oldBatch2 = batchFactory.createBatch(oldTargetChain, BATCH_SIZE, BATCH_SIZE);
    final Batch newBatch = batchFactory.createBatch(targetChain, BATCH_SIZE.times(2), BATCH_SIZE);
    batchChain.add(oldBatch1);
    batchChain.add(oldBatch2);
    batchChain.add(newBatch);

    fillQueue(ZERO);

    assertThat(batchChain).doesNotContain(oldBatch1, oldBatch2);

    // Check that the original two batches have been replaced by new ones from the new chain
    final List<Batch> batches = batchChain.stream().collect(toList());
    assertThat(batches).hasSizeGreaterThan(3);
    assertThatBatch(batches.get(0)).hasRange(0, 49);
    assertThatBatch(batches.get(0)).hasTargetChain(targetChain);
    assertThatBatch(batches.get(1)).hasRange(50, 99);
    assertThatBatch(batches.get(1)).hasTargetChain(targetChain);

    // Batch from new chain should not be replaced
    assertThatBatch(batches.get(2)).isSameAs(newBatch);
  }

  @Test
  void shouldNotReplaceCompleteBatchesFromOldChainsWithNoPeers() {
    final TargetChain oldTargetChain =
        TargetChainTestUtil.chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(500), dataStructureUtil.randomBytes32()));

    final Batch oldBatch1 = batchFactory.createBatch(oldTargetChain, UInt64.ZERO, BATCH_SIZE);
    oldBatch1.markComplete();
    final Batch oldBatch2 = batchFactory.createBatch(oldTargetChain, BATCH_SIZE, BATCH_SIZE);
    batchChain.add(oldBatch1);
    batchChain.add(oldBatch2);

    fillQueue(ZERO);

    assertThat(batchChain).doesNotContain(oldBatch2);

    final List<Batch> batches = batchChain.stream().collect(toList());
    assertThat(batches).hasSizeGreaterThan(2);
    // First batch was complete so shouldn't be replaced
    assertThatBatch(batches.get(0)).isSameAs(oldBatch1);

    // Second batch was incomplete so should be replaced
    assertThatBatch(batches.get(1)).hasTargetChain(targetChain);
  }
}

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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChainTestUtil;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BatchChainTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final TargetChain targetChain =
      TargetChainTestUtil.chainWith(new SlotAndBlockRoot(UInt64.MAX_VALUE, Bytes32.ZERO));
  private final StubBatchFactory batchFactory =
      new StubBatchFactory(new InlineEventThread(), false);
  private final BatchChain batchChain = new BatchChain();

  @Test
  void batchesBeforeExclusive_emptyWhenChainIsEmpty() {
    assertThat(batchChain.batchesBeforeExclusive(createBatch(100))).isEmpty();
  }

  @Test
  void batchesBeforeExclusive_emptyWhenBatchIsFirst() {
    final Batch batch = createBatch(1);
    batchChain.add(batch);
    batchChain.add(createBatch(2));
    batchChain.add(createBatch(3));
    assertThat(batchChain.batchesBeforeExclusive(batch)).isEmpty();
  }

  @Test
  void batchesBeforeExclusive_returnEarlierBatchesExcludingSpecifiedBatch() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(createBatch(4));
    assertThat(batchChain.batchesBeforeExclusive(batch3)).containsExactly(batch1, batch2);
  }

  @Test
  void batchesBeforeInclusive_emptyWhenChainIsEmpty() {
    assertThat(batchChain.batchesBeforeInclusive(createBatch(100))).isEmpty();
  }

  @Test
  void batchesBeforeInclusive_onlyBatchWhenBatchIsFirst() {
    final Batch batch = createBatch(1);
    batchChain.add(batch);
    batchChain.add(createBatch(2));
    batchChain.add(createBatch(3));
    assertThat(batchChain.batchesBeforeInclusive(batch)).containsExactly(batch);
  }

  @Test
  void batchesBeforeInclusive_returnEarlierBatchesIncludingSpecifiedBatch() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(createBatch(4));
    assertThat(batchChain.batchesBeforeInclusive(batch3)).containsExactly(batch1, batch2, batch3);
  }

  @Test
  void batchesAfterExclusive_emptyWhenChainIsEmpty() {
    assertThat(batchChain.batchesAfterExclusive(createBatch(100))).isEmpty();
  }

  @Test
  void batchesAfterExclusive_emptyWhenBatchIsLast() {
    final Batch batch = createBatch(3);
    batchChain.add(createBatch(1));
    batchChain.add(createBatch(2));
    batchChain.add(batch);
    assertThat(batchChain.batchesAfterExclusive(batch)).isEmpty();
  }

  @Test
  void batchesAfterExclusive_returnLaterBatchesExcludingSpecifiedBatch() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    assertThat(batchChain.batchesAfterExclusive(batch2)).containsExactly(batch3, batch4);
  }

  @Test
  void batchesAfterInclusive_emptyWhenChainIsEmpty() {
    assertThat(batchChain.batchesAfterInclusive(createBatch(100))).isEmpty();
  }

  @Test
  void batchesAfterInclusive_onlyBatchWhenBatchIsLast() {
    final Batch batch = createBatch(3);
    batchChain.add(createBatch(1));
    batchChain.add(createBatch(2));
    batchChain.add(batch);
    assertThat(batchChain.batchesAfterInclusive(batch)).containsExactly(batch);
  }

  @Test
  void batchesAfterInclusive_returnLaterBatchesIncludingSpecifiedBatch() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    assertThat(batchChain.batchesAfterInclusive(batch2)).containsExactly(batch2, batch3, batch4);
  }

  @Test
  void batchesBetweenInclusive_emptyWhenChainIsEmpty() {
    assertThat(batchChain.batchesBetweenInclusive(createBatch(10), createBatch(20))).isEmpty();
  }

  @Test
  void batchesBetweenInclusive_containsOnlyBatchesWhenNothingBetween() {
    final Batch from = createBatch(2);
    final Batch to = createBatch(3);
    batchChain.add(createBatch(1));
    batchChain.add(from);
    batchChain.add(to);
    batchChain.add(createBatch(1));
    assertThat(batchChain.batchesBetweenInclusive(from, to)).containsExactly(from, to);
  }

  @Test
  void batchesBetweenInclusive_containsBatchesBetweenAndSpecifiedBatches() {
    batchChain.add(createBatch(1));
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    final Batch batch5 = createBatch(5);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    batchChain.add(batch5);
    batchChain.add(createBatch(6));
    assertThat(batchChain.batchesBetweenInclusive(batch2, batch5))
        .containsExactly(batch2, batch3, batch4, batch5);
  }

  @Test
  void batchesBetweenExclusive_emptyWhenChainIsEmpty() {
    assertThat(batchChain.batchesBetweenExclusive(createBatch(10), createBatch(20))).isEmpty();
  }

  @Test
  void batchesBetweenExclusive_emptyWhenNothingBetween() {
    final Batch from = createBatch(2);
    final Batch to = createBatch(3);
    batchChain.add(createBatch(1));
    batchChain.add(from);
    batchChain.add(to);
    batchChain.add(createBatch(1));
    assertThat(batchChain.batchesBetweenExclusive(from, to)).isEmpty();
  }

  @Test
  void batchesBetweenExclusive_containsBatchesBetweenAndSpecifiedBatches() {
    batchChain.add(createBatch(1));
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    final Batch batch5 = createBatch(5);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    batchChain.add(batch5);
    batchChain.add(createBatch(6));
    assertThat(batchChain.batchesBetweenExclusive(batch2, batch5)).containsExactly(batch3, batch4);
  }

  @Test
  void batchesBetweenExclusiveStart_containsOnlyEndBatchWhenNothingBetween() {
    final Batch from = createBatch(2);
    final Batch to = createBatch(3);
    batchChain.add(createBatch(1));
    batchChain.add(from);
    batchChain.add(to);
    batchChain.add(createBatch(4));
    assertThat(batchChain.batchesBetweenExclusiveStart(from, to)).containsExactly(to);
  }

  @Test
  void batchesBetweenExclusiveStart_containsBatchesBetweenAndEndBatch() {
    batchChain.add(createBatch(1));
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    final Batch batch5 = createBatch(5);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    batchChain.add(batch5);
    batchChain.add(createBatch(6));
    assertThat(batchChain.batchesBetweenExclusiveStart(batch2, batch5))
        .containsExactly(batch3, batch4, batch5);
  }

  @Test
  void iterator_shouldIterateAllItems() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    assertThat(batchChain).containsExactly(batch1, batch2, batch3, batch4);
  }

  @Test
  void stream_shouldIncludeAllItems() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    final Batch batch4 = createBatch(4);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    batchChain.add(batch4);
    assertThat(batchChain.stream()).containsExactly(batch1, batch2, batch3, batch4);
  }

  @Test
  void last_shouldBeEmptyWhenEmpty() {
    assertThat(batchChain.last()).isEmpty();
  }

  @Test
  void last_shouldReturnLastItemWhenNotEmpty() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.last()).contains(batch3);
  }

  @Test
  void contains_shouldBeTrueWhenBatchIsInChain() {
    final Batch batch = createBatch(1);
    batchChain.add(batch);
    assertThat(batchChain.contains(batch)).isTrue();
  }

  @Test
  void contains_shouldBeFalseWhenBatchIsNotInChain() {
    final Batch batch = createBatch(1);
    batchChain.add(createBatch(2));
    assertThat(batchChain.contains(batch)).isFalse();
  }

  @Test
  void contains_shouldBeFalseWhenBatchIsNotInChainButDifferentInstanceIsWithSameStartSlot() {
    final Batch batch = createBatch(1);
    batchChain.add(createBatch(1));
    assertThat(batchChain.contains(batch)).isFalse();
  }

  @Test
  void isEmpty_shouldBeTrueWhenChainIsEmpty() {
    assertThat(batchChain.isEmpty()).isTrue();
  }

  @Test
  void isEmpty_shouldBeFalseWhenChainIsNotEmpty() {
    batchChain.add(createBatch(1));
    assertThat(batchChain.isEmpty()).isFalse();
  }

  @Test
  void previousNonEmptyBatch_shouldBeEmptyWhenChainIsEmpty() {
    assertThat(batchChain.previousNonEmptyBatch(createBatch(1))).isEmpty();
  }

  @Test
  void previousNonEmptyBatch_shouldBeEmptyWhenNoPreviousBatches() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.previousNonEmptyBatch(batch1)).isEmpty();
  }

  @Test
  void previousNonEmptyBatch_shouldBeEmptyWhenAllPreviousBatchesAreEmpty() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.previousNonEmptyBatch(batch3)).isEmpty();
  }

  @Test
  void previousNonEmptyBatch_shouldBePresentWhenThereIsAPreviousNonEmptyBatch() {
    final Batch batch1 = createNonEmptyBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createNonEmptyBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.previousNonEmptyBatch(batch3)).contains(batch1);
  }

  @Test
  void nextNonEmptyBatch_shouldBeEmptyWhenChainIsEmpty() {
    assertThat(batchChain.nextNonEmptyBatch(createBatch(1))).isEmpty();
  }

  @Test
  void nextNonEmptyBatch_shouldBeEmptyWhenNoLaterBatches() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.nextNonEmptyBatch(batch3)).isEmpty();
  }

  @Test
  void nextNonEmptyBatch_shouldBeEmptyWhenAllLaterBatchesAreEmpty() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.nextNonEmptyBatch(batch1)).isEmpty();
  }

  @Test
  void nextNonEmptyBatch_shouldBePresentWhenThereIsANextNonEmptyBatch() {
    final Batch batch1 = createNonEmptyBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createNonEmptyBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.nextNonEmptyBatch(batch1)).contains(batch3);
  }

  @Test
  void firstImportableBatch_shouldReturnEmptyWhenChainIsEmpty() {
    assertThat(batchChain.firstImportableBatch()).isEmpty();
  }

  @Test
  void firstImportableBatch_shouldReturnFirstConfirmedNonEmptyBatch() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createNonEmptyBatch(3);
    batch3.markFirstBlockConfirmed();
    batch3.markLastBlockConfirmed();
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.firstImportableBatch()).contains(batch3);
  }

  @Test
  void firstImportableBatch_shouldReturnEmptyWhenFirstNonEmptyBatchIsNotConfirmed() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createNonEmptyBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.firstImportableBatch()).isEmpty();
  }

  @Test
  void firstImportableBatch_shouldReturnEmptyWhenAllBatchesAreEmpty() {
    final Batch batch1 = createBatch(1);
    final Batch batch2 = createBatch(2);
    final Batch batch3 = createBatch(3);
    batchChain.add(batch1);
    batchChain.add(batch2);
    batchChain.add(batch3);
    assertThat(batchChain.firstImportableBatch()).isEmpty();
  }

  @Test
  void replace_shouldRemoveOldAndAddNewBatch() {
    final Batch oldBatch = createBatch(1);
    final Batch newBatch = createBatch(1);
    batchChain.add(oldBatch);
    batchChain.replace(oldBatch, newBatch);
    assertThat(batchChain.contains(oldBatch)).isFalse();
    assertThat(batchChain.contains(newBatch)).isTrue();
  }

  @Test
  void replace_shouldAddNewBatchIfOldIsNotInChain() {
    final Batch oldBatch = createBatch(1);
    final Batch newBatch = createBatch(1);
    batchChain.replace(oldBatch, newBatch);
    assertThat(batchChain.contains(oldBatch)).isFalse();
    assertThat(batchChain.contains(newBatch)).isTrue();
  }

  private Batch createNonEmptyBatch(final int startSlot) {
    final Batch batch =
        batchFactory.createBatch(targetChain, UInt64.valueOf(startSlot), UInt64.valueOf(startSlot));
    batch.requestMoreBlocks(() -> {});
    batchFactory.receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(2));
    return batch;
  }

  private Batch createBatch(final long startSlot) {
    return batchFactory.createBatch(
        targetChain, UInt64.valueOf(startSlot), UInt64.valueOf(startSlot));
  }
}

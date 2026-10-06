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

package tech.pegasys.teku.statetransition.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class FutureBlockPoolTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SettableLabelledGauge gauge = mock(SettableLabelledGauge.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final UInt64 currentSlot = UInt64.valueOf(5);
  private final Map<SignedBeaconBlock, Long> blockSizes = new HashMap<>();

  @Test
  void add_acceptsBlocksUpToTolerance() {
    final FutureBlockPool futureBlocks = create(16, 100);
    final SignedBeaconBlock block =
        block(currentSlot.plus(FutureBlockPool.FUTURE_SLOT_TOLERANCE), 10);

    assertThat(futureBlocks.add(block)).isTrue();
    assertThat(futureBlocks.contains(block)).isTrue();
    assertThat(futureBlocks.size()).isEqualTo(1);
    verify(gauge).set(1L, "blocks");
    assertResultCounts(1, 0, 0, 0);
  }

  @Test
  void add_rejectsBlocksBeyondTolerance() {
    final FutureBlockPool futureBlocks = create(16, 100);
    final SignedBeaconBlock block =
        block(currentSlot.plus(FutureBlockPool.FUTURE_SLOT_TOLERANCE).plus(1), 10);

    assertThat(futureBlocks.add(block)).isFalse();
    assertThat(futureBlocks.contains(block)).isFalse();
    assertThat(futureBlocks.size()).isZero();
    assertResultCounts(0, 0, 0, 1);
  }

  @Test
  void add_duplicateIsAcceptedButNotCountedTwice() {
    final FutureBlockPool futureBlocks = create(16, 100);
    final SignedBeaconBlock block = block(currentSlot.plus(1), 60);

    assertThat(futureBlocks.add(block)).isTrue();
    assertThat(futureBlocks.add(block)).isTrue();

    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(60);
    assertResultCounts(1, 0, 0, 0);
  }

  @Test
  void add_evictsOldestBlockAtSlotWhenSlotIsFull() {
    final FutureBlockPool futureBlocks = create(2, 100);
    final SignedBeaconBlock blockA = block(currentSlot.plus(1), 10);
    final SignedBeaconBlock blockB = block(currentSlot.plus(1), 10);
    final SignedBeaconBlock blockC = block(currentSlot.plus(1), 10);
    final SignedBeaconBlock otherSlotBlock = block(currentSlot.plus(2), 10);

    assertThat(futureBlocks.add(blockA)).isTrue();
    assertThat(futureBlocks.add(blockB)).isTrue();
    assertThat(futureBlocks.add(otherSlotBlock)).isTrue();
    assertThat(futureBlocks.add(blockC)).isTrue();

    assertThat(futureBlocks.contains(blockA)).isFalse();
    assertThat(futureBlocks.contains(blockB)).isTrue();
    assertThat(futureBlocks.contains(blockC)).isTrue();
    assertThat(futureBlocks.contains(otherSlotBlock)).isTrue();
    assertThat(futureBlocks.size()).isEqualTo(3);
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(30);
    assertResultCounts(4, 0, 1, 0);
  }

  @Test
  void add_rejectsBlockLargerThanLimit() {
    final FutureBlockPool futureBlocks = create(16, 100);

    assertThat(futureBlocks.add(block(currentSlot.plus(1), 101))).isFalse();
    assertThat(futureBlocks.size()).isZero();
    assertThat(futureBlocks.getTotalBytes()).isZero();
    assertResultCounts(0, 0, 0, 1);
  }

  @Test
  void add_evictsBlocksFromFurthestSlotFirst() {
    final FutureBlockPool futureBlocks = create(16, 100);
    final SignedBeaconBlock nextSlotBlock = block(currentSlot.plus(1), 40);
    final SignedBeaconBlock furthestSlotBlockA = block(currentSlot.plus(2), 30);
    final SignedBeaconBlock furthestSlotBlockB = block(currentSlot.plus(2), 30);
    assertThat(futureBlocks.add(nextSlotBlock)).isTrue();
    assertThat(futureBlocks.add(furthestSlotBlockA)).isTrue();
    assertThat(futureBlocks.add(furthestSlotBlockB)).isTrue();

    final SignedBeaconBlock newBlock = block(currentSlot.plus(1), 50);
    assertThat(futureBlocks.add(newBlock)).isTrue();

    assertThat(futureBlocks.contains(nextSlotBlock)).isTrue();
    assertThat(futureBlocks.contains(newBlock)).isTrue();
    assertThat(futureBlocks.contains(furthestSlotBlockA)).isFalse();
    assertThat(futureBlocks.contains(furthestSlotBlockB)).isFalse();
    assertThat(futureBlocks.size()).isEqualTo(2);
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(90);
    assertResultCounts(4, 0, 2, 0);
  }

  @Test
  void add_replacesOldestBlockAtFullSlotWhenAtSizeLimit() {
    final FutureBlockPool futureBlocks = create(2, 100);
    final SignedBeaconBlock oldestBlock = block(currentSlot.plus(1), 50);
    final SignedBeaconBlock otherBlock = block(currentSlot.plus(1), 50);
    assertThat(futureBlocks.add(oldestBlock)).isTrue();
    assertThat(futureBlocks.add(otherBlock)).isTrue();

    final SignedBeaconBlock newBlock = block(currentSlot.plus(1), 10);
    assertThat(futureBlocks.add(newBlock)).isTrue();

    assertThat(futureBlocks.contains(oldestBlock)).isFalse();
    assertThat(futureBlocks.contains(otherBlock)).isTrue();
    assertThat(futureBlocks.contains(newBlock)).isTrue();
    assertThat(futureBlocks.size()).isEqualTo(2);
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(60);
    assertResultCounts(3, 0, 1, 0);
  }

  @Test
  void add_combinesFullSlotAndFurthestSlotEvictionToMakeRoom() {
    final FutureBlockPool futureBlocks = create(2, 100);
    final SignedBeaconBlock oldestBlock = block(currentSlot.plus(1), 30);
    final SignedBeaconBlock otherBlock = block(currentSlot.plus(1), 30);
    final SignedBeaconBlock furthestSlotBlock = block(currentSlot.plus(2), 40);
    assertThat(futureBlocks.add(oldestBlock)).isTrue();
    assertThat(futureBlocks.add(otherBlock)).isTrue();
    assertThat(futureBlocks.add(furthestSlotBlock)).isTrue();

    final SignedBeaconBlock newBlock = block(currentSlot.plus(1), 60);
    assertThat(futureBlocks.add(newBlock)).isTrue();

    assertThat(futureBlocks.contains(oldestBlock)).isFalse();
    assertThat(futureBlocks.contains(furthestSlotBlock)).isFalse();
    assertThat(futureBlocks.contains(otherBlock)).isTrue();
    assertThat(futureBlocks.contains(newBlock)).isTrue();
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(90);
    assertResultCounts(4, 0, 2, 0);
  }

  @Test
  void add_rejectsBlockAtFullSlotWithoutEvictingWhenNotEnoughCapacityCanBeFreed() {
    final FutureBlockPool futureBlocks = create(2, 100);
    final SignedBeaconBlock oldestBlock = block(currentSlot.plus(1), 50);
    final SignedBeaconBlock otherBlock = block(currentSlot.plus(1), 50);
    assertThat(futureBlocks.add(oldestBlock)).isTrue();
    assertThat(futureBlocks.add(otherBlock)).isTrue();

    assertThat(futureBlocks.add(block(currentSlot.plus(1), 60))).isFalse();

    assertThat(futureBlocks.contains(oldestBlock)).isTrue();
    assertThat(futureBlocks.contains(otherBlock)).isTrue();
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(100);
    assertResultCounts(2, 0, 0, 1);
  }

  @Test
  void add_rejectsBlockWithoutEvictingWhenNotEnoughCapacityCanBeFreed() {
    final FutureBlockPool futureBlocks = create(16, 100);
    final SignedBeaconBlock nextSlotBlock = block(currentSlot.plus(1), 60);
    final SignedBeaconBlock furthestSlotBlock = block(currentSlot.plus(2), 30);
    assertThat(futureBlocks.add(nextSlotBlock)).isTrue();
    assertThat(futureBlocks.add(furthestSlotBlock)).isTrue();

    assertThat(futureBlocks.add(block(currentSlot.plus(2), 20))).isFalse();
    assertThat(futureBlocks.add(block(currentSlot.plus(1), 50))).isFalse();

    assertThat(futureBlocks.contains(nextSlotBlock)).isTrue();
    assertThat(futureBlocks.contains(furthestSlotBlock)).isTrue();
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(90);
    assertResultCounts(2, 0, 0, 2);
  }

  @Test
  void prune_returnsBlocksNoLongerInTheFutureAndReleasesCapacity() {
    final FutureBlockPool futureBlocks = create(16, 100);
    final SignedBeaconBlock nextSlotBlock = block(currentSlot.plus(1), 60);
    final SignedBeaconBlock laterBlock = block(currentSlot.plus(2), 40);
    assertThat(futureBlocks.add(nextSlotBlock)).isTrue();
    assertThat(futureBlocks.add(laterBlock)).isTrue();

    assertThat(futureBlocks.prune(currentSlot)).isEmpty();
    clearInvocations(gauge);
    assertThat(futureBlocks.prune(nextSlotBlock.getSlot())).containsExactly(nextSlotBlock);

    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.getTotalBytes()).isEqualTo(40);
    assertThat(futureBlocks.contains(laterBlock)).isTrue();
    verify(gauge).set(1L, "blocks");
    assertResultCounts(2, 1, 0, 0);
  }

  @Test
  void constructor_rejectsNonPositiveMaxBlocksPerSlot() {
    assertThatThrownBy(() -> new FutureBlockPool(0, 100, blockSizes::get, gauge, resultCounter()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Max future blocks per slot must be positive");
  }

  @Test
  void constructor_rejectsNegativeMaxTotalBytes() {
    assertThatThrownBy(() -> new FutureBlockPool(16, -1, blockSizes::get, gauge, resultCounter()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Max total future blocks bytes must not be negative");
  }

  private FutureBlockPool create(final int maxBlocksPerSlot, final long maxTotalBytes) {
    final FutureBlockPool futureBlocks =
        new FutureBlockPool(
            maxBlocksPerSlot, maxTotalBytes, blockSizes::get, gauge, resultCounter());
    futureBlocks.onSlot(currentSlot);
    return futureBlocks;
  }

  private LabelledMetric<Counter> resultCounter() {
    return FutureBlockPool.createResultCounter(metricsSystem);
  }

  private long resultCount(final String result) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.BEACON, "future_blocks_total", result);
  }

  private void assertResultCounts(
      final long queued, final long dequeued, final long evicted, final long dropped) {
    assertThat(resultCount("queued")).describedAs("queued").isEqualTo(queued);
    assertThat(resultCount("dequeued")).describedAs("dequeued").isEqualTo(dequeued);
    assertThat(resultCount("evicted")).describedAs("evicted").isEqualTo(evicted);
    assertThat(resultCount("dropped")).describedAs("dropped").isEqualTo(dropped);
  }

  private SignedBeaconBlock block(final UInt64 slot, final long size) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
    blockSizes.put(block, size);
    return block;
  }
}

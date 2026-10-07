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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.ParentExecutionPayloadDependency;
import tech.pegasys.teku.statetransition.execution.PendingExecutionPayloadBid;

public class PendingPoolTest {
  private static final String PAYLOAD_POOL = "blocks_waiting_for_parent_execution_payload";
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final UInt64 futureTolerance = UInt64.valueOf(2);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final int maxItems = 15;
  private final PendingPool<SignedBeaconBlock> pendingPool =
      new PoolFactory(metricsSystem)
          .createPendingPoolForBlocks(spec, historicalTolerance, futureTolerance, maxItems);
  private UInt64 currentSlot = historicalTolerance.times(2);
  private final List<Bytes32> requiredRootEvents = new ArrayList<>();
  private final List<Bytes32> requiredRootDroppedEvents = new ArrayList<>();

  @BeforeEach
  public void setup() {
    // Set up slot
    pendingPool.subscribeRequiredBlockRoot(requiredRootEvents::add);
    pendingPool.subscribeRequiredBlockRootDropped(requiredRootDroppedEvents::add);
    setSlot(currentSlot);
  }

  private void setSlot(final long slot) {
    setSlot(UInt64.valueOf(slot));
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    pendingPool.onSlot(slot);
  }

  @Test
  public void add_shouldDeferSubscribersCallToAvoidConcurrentModificationException() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    pendingPool.subscribeRequiredBlockRoot(
        blockRoot -> {
          if (blockRoot.equals(block.getParentRoot())) {
            pendingPool.add(block2);
          }
        });
    pendingPool.add(block);

    assertThat(pendingPool.contains(block2)).isTrue();
  }

  @Test
  public void add_blockForCurrentSlot() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParentRoot());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_historicalBlockWithinWindow() {
    final UInt64 slot = currentSlot.minus(historicalTolerance);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParentRoot());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_historicalBlockOutsideWindow() {
    final UInt64 slot = currentSlot.minus(historicalTolerance).minus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_futureBlockWithinWindow() {
    final UInt64 slot = currentSlot.plus(futureTolerance);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParentRoot());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_futureBlockOutsideWindow() {
    final UInt64 slot = currentSlot.plus(futureTolerance).plus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_nonFinalizedBlock() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    pendingPool.onNewFinalizedCheckpoint(checkpoint, false);

    final UInt64 slot = checkpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    setSlot(slot);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());

    pendingPool.add(block);
    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParentRoot());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_finalizedBlock() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    pendingPool.onNewFinalizedCheckpoint(checkpoint, false);
    final long slot = checkpoint.getEpochStartSlot(spec).longValue() + 10;
    setSlot(slot);

    final long blockSlot = checkpoint.getEpochStartSlot(spec).longValue();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(blockSlot);

    pendingPool.add(block);
    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_moreThanMaxItems() {
    for (int i = 0; i < maxItems * 2; i++) {
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
      pendingPool.add(block);

      final int expectedSize = Math.min(maxItems, i + 1);
      assertThat(pendingPool.contains(block)).isTrue();
      assertThat(pendingPool.size()).isEqualTo(expectedSize);
    }

    // Final sanity check
    assertThat(pendingPool.size()).isEqualTo(maxItems);
  }

  @Test
  public void add_dontDropItemsWhenNewItemsIgnored() {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    for (int i = 0; i < maxItems; i++) {
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
      pendingPool.add(block);
      blocks.add(block);

      final int expectedSize = Math.min(maxItems, i + 1);
      assertThat(pendingPool.contains(block)).isTrue();
      assertThat(pendingPool.size()).isEqualTo(expectedSize);
    }
    assertThat(pendingPool.size()).isEqualTo(maxItems);

    // Add some blocks that should be ignored
    SignedBeaconBlock ignoredBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.plus(futureTolerance).plus(1));
    pendingPool.add(ignoredBlock);
    ignoredBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.minus(historicalTolerance).minus(1));
    pendingPool.add(ignoredBlock);

    // All initial blocks should still be present
    assertThat(pendingPool.size()).isEqualTo(maxItems);
    blocks.forEach(b -> assertThat(pendingPool.contains(b)).isTrue());
  }

  @Test
  public void add_shouldDropOldestItemsWhenEnforcingLimits() {
    final UInt64 olderSlot = currentSlot;
    final UInt64 newerSlot = currentSlot.plus(1);
    SignedBeaconBlock block;

    // Add older block
    SignedBeaconBlock olderBlock = dataStructureUtil.randomSignedBeaconBlock(olderSlot);
    pendingPool.add(olderBlock);
    assertThat(pendingPool.contains(olderBlock)).isTrue();

    // Add max newer blocks
    for (int i = 0; i < maxItems; i++) {
      block = dataStructureUtil.randomSignedBeaconBlock(newerSlot);
      pendingPool.add(block);

      assertThat(pendingPool.contains(block)).isTrue();
      assertThat(pendingPool.size()).isLessThanOrEqualTo(maxItems);
    }

    // Older block should've been dropped
    assertThat(pendingPool.contains(olderBlock)).isFalse();

    // Add older block
    olderBlock = dataStructureUtil.randomSignedBeaconBlock(olderSlot);
    pendingPool.add(olderBlock);
    assertThat(pendingPool.contains(olderBlock)).isTrue();
    // Add newer block
    block = dataStructureUtil.randomSignedBeaconBlock(newerSlot);
    pendingPool.add(block);

    // Older block should be dropped when newer block is added
    assertThat(pendingPool.contains(olderBlock)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(maxItems);
  }

  @Test
  public void add_shouldDropOldestItemsWhenTotalWeightLimitExceeded() {
    final PendingPool<PendingTestItem> weightedPendingPool = createWeightedPendingPool(10);

    final PendingTestItem olderItem = createPendingTestItem(6, currentSlot);
    final PendingTestItem firstNewerItem = createPendingTestItem(4, currentSlot.plus(UInt64.ONE));
    final PendingTestItem secondNewerItem = createPendingTestItem(5, currentSlot.plus(UInt64.ONE));

    weightedPendingPool.add(olderItem);
    weightedPendingPool.add(firstNewerItem);
    weightedPendingPool.add(secondNewerItem);

    assertThat(weightedPendingPool.contains(olderItem)).isFalse();
    assertThat(weightedPendingPool.contains(firstNewerItem)).isTrue();
    assertThat(weightedPendingPool.contains(secondNewerItem)).isTrue();
    assertThat(weightedPendingPool.size()).isEqualTo(2);
    assertThat(weightedPendingPool.getTotalWeight()).isEqualTo(9);
  }

  @Test
  public void add_shouldIgnoreItemsThatExceedTotalWeightLimit() {
    final PendingPool<PendingTestItem> weightedPendingPool = createWeightedPendingPool(10);

    final PendingTestItem retainedItem = createPendingTestItem(4, currentSlot);
    final PendingTestItem tooLargeItem = createPendingTestItem(11, currentSlot.plus(UInt64.ONE));

    weightedPendingPool.add(retainedItem);
    weightedPendingPool.add(tooLargeItem);

    assertThat(weightedPendingPool.contains(retainedItem)).isTrue();
    assertThat(weightedPendingPool.contains(tooLargeItem)).isFalse();
    assertThat(weightedPendingPool.size()).isEqualTo(1);
    assertThat(weightedPendingPool.getTotalWeight()).isEqualTo(4);
  }

  @Test
  public void totalWeightGauge_shouldTrackAddsEvictionsAndRemovals() {
    final PendingPool<PendingTestItem> weightedPendingPool = createWeightedPendingPool(10);
    assertThat(weightedPoolBytes()).hasValue(0);

    final PendingTestItem olderItem = createPendingTestItem(6, currentSlot);
    final PendingTestItem newerItem = createPendingTestItem(4, currentSlot.plus(UInt64.ONE));
    weightedPendingPool.add(olderItem);
    weightedPendingPool.add(newerItem);
    assertThat(weightedPoolBytes()).hasValue(10);

    // evicts the older item to make room
    weightedPendingPool.add(createPendingTestItem(5, currentSlot.plus(UInt64.ONE)));
    assertThat(weightedPoolBytes()).hasValue(9);

    weightedPendingPool.remove(newerItem);
    assertThat(weightedPoolBytes()).hasValue(5);
  }

  @Test
  public void createPendingPoolForBlocks_shouldReportTotalSszBytes() {
    assertThat(pendingPoolBytes("blocks")).hasValue(0);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    pendingPool.add(block);
    assertThat(pendingPoolBytes("blocks")).hasValue(block.sszSerialize().size());

    pendingPool.remove(block);
    assertThat(pendingPoolBytes("blocks")).hasValue(0);
  }

  @Test
  public void createPendingBlockPool_shouldReportBytesForEachInnerPoolIndependently() {
    final StubMetricsSystem blockPoolMetricsSystem = new StubMetricsSystem();
    final PendingBlockPool pendingBlockPool =
        new PoolFactory(blockPoolMetricsSystem)
            .createPendingBlockPool(spec, historicalTolerance, futureTolerance, maxItems);
    pendingBlockPool.onSlot(currentSlot);
    final SignedBeaconBlock blockWaitingForParent =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final SignedBeaconBlock blockWaitingForPayload =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final long blockWaitingForParentSize = blockWaitingForParent.sszSerialize().size();
    final long blockWaitingForPayloadSize = blockWaitingForPayload.sszSerialize().size();

    assertThat(pendingPoolBytes(blockPoolMetricsSystem, "blocks")).hasValue(0);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, PAYLOAD_POOL)).hasValue(0);

    pendingBlockPool.addForMissingParent(blockWaitingForParent);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, "blocks"))
        .hasValue(blockWaitingForParentSize);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, PAYLOAD_POOL)).hasValue(0);

    pendingBlockPool.addForMissingParentExecutionPayload(
        blockWaitingForPayload,
        new ParentExecutionPayloadDependency(
            blockWaitingForPayload.getParentRoot(), dataStructureUtil.randomBytes32()));
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, "blocks"))
        .hasValue(blockWaitingForParentSize);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, PAYLOAD_POOL))
        .hasValue(blockWaitingForPayloadSize);

    pendingBlockPool.remove(blockWaitingForParent);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, "blocks")).hasValue(0);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, PAYLOAD_POOL))
        .hasValue(blockWaitingForPayloadSize);

    pendingBlockPool.remove(blockWaitingForPayload);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, "blocks")).hasValue(0);
    assertThat(pendingPoolBytes(blockPoolMetricsSystem, PAYLOAD_POOL)).hasValue(0);
  }

  @Test
  public void createPendingPoolForBlocks_shouldLimitTotalWeightFromGossipPayloadSize() {
    final int maxPayloadSize = 1024;
    final Spec specWithSmallGossipPayload =
        TestSpecFactory.createDefault(builder -> builder.maxPayloadSize(maxPayloadSize));

    final PendingPool<SignedBeaconBlock> blockPendingPool =
        new PoolFactory(new StubMetricsSystem())
            .createPendingPoolForBlocks(
                specWithSmallGossipPayload, historicalTolerance, futureTolerance, maxItems);

    assertThat(blockPendingPool.getMaxTotalWeight()).isEqualTo(maxPayloadSize * 4L);
  }

  @Test
  public void createPendingBlockPool_shouldUsePendingBlockByteLimit() {
    final int maxPayloadSize = 1024;
    final Spec specWithSmallGossipPayload =
        TestSpecFactory.createDefault(builder -> builder.maxPayloadSize(maxPayloadSize));
    final PoolFactory poolFactory = new PoolFactory(new StubMetricsSystem());
    final PendingPool<SignedBeaconBlock> blockPendingPool =
        poolFactory.createPendingPoolForBlocks(
            specWithSmallGossipPayload, historicalTolerance, futureTolerance, maxItems);

    final PendingBlockPool pendingBlockPool =
        poolFactory.createPendingBlockPool(
            specWithSmallGossipPayload, historicalTolerance, futureTolerance, maxItems);

    assertThat(pendingBlockPool.getBlocksWaitingForParent().getMaxTotalWeight())
        .isEqualTo(blockPendingPool.getMaxTotalWeight());
    assertThat(pendingBlockPool.getBlocksWaitingForParentExecutionPayload().getMaxTotalWeight())
        .isEqualTo(blockPendingPool.getMaxTotalWeight());
  }

  @Test
  public void createPendingPoolForExecutionPayloadBids_shouldConfigureBidFieldsAndCapacity() {
    final Spec gloasSpec = TestSpecFactory.createMainnetGloas();
    final DataStructureUtil gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid firstBid =
        gloasDataStructureUtil.randomSignedExecutionPayloadBid(
            gloasDataStructureUtil.randomExecutionPayloadBid(
                gloasDataStructureUtil.randomBytes32(),
                slot,
                gloasDataStructureUtil.randomUInt64(),
                gloasDataStructureUtil.randomUInt64(),
                UInt64.ZERO));
    final SignedExecutionPayloadBid secondBid =
        gloasDataStructureUtil.randomSignedExecutionPayloadBid(
            gloasDataStructureUtil.randomExecutionPayloadBid(
                firstBid.getMessage().getParentBlockHash(),
                slot,
                gloasDataStructureUtil.randomUInt64(),
                gloasDataStructureUtil.randomUInt64(),
                UInt64.ZERO));
    final SignedExecutionPayloadBid staleBid =
        gloasDataStructureUtil.randomSignedExecutionPayloadBid(
            gloasDataStructureUtil.randomExecutionPayloadBid(
                gloasDataStructureUtil.randomBytes32(),
                slot.minus(UInt64.ONE),
                gloasDataStructureUtil.randomUInt64(),
                gloasDataStructureUtil.randomUInt64(),
                UInt64.ZERO));
    final SignedExecutionPayloadBid farFutureBid =
        gloasDataStructureUtil.randomSignedExecutionPayloadBid(
            gloasDataStructureUtil.randomExecutionPayloadBid(
                gloasDataStructureUtil.randomBytes32(),
                slot.plus(2),
                gloasDataStructureUtil.randomUInt64(),
                gloasDataStructureUtil.randomUInt64(),
                UInt64.ZERO));
    final PendingPool<PendingExecutionPayloadBid> pendingBidPool =
        new PoolFactory(metricsSystem).createPendingPoolForExecutionPayloadBids(gloasSpec, 1);
    pendingBidPool.onSlot(slot);

    final PendingExecutionPayloadBid firstPendingBid =
        new PendingExecutionPayloadBid(firstBid, Optional.empty());
    pendingBidPool.add(firstPendingBid);

    assertThat(pendingBidPool.contains(firstBid.hashTreeRoot())).isTrue();
    assertThat(
            pendingBidPool.getItemsDependingOn(firstBid.getMessage().getParentBlockRoot(), false))
        .containsExactly(firstPendingBid);

    pendingBidPool.add(new PendingExecutionPayloadBid(secondBid, Optional.empty()));
    pendingBidPool.add(new PendingExecutionPayloadBid(staleBid, Optional.empty()));
    pendingBidPool.add(new PendingExecutionPayloadBid(farFutureBid, Optional.empty()));

    assertThat(pendingBidPool.size()).isEqualTo(1);
    assertThat(pendingBidPool.contains(secondBid.hashTreeRoot())).isTrue();
    assertThat(pendingBidPool.contains(staleBid.hashTreeRoot())).isFalse();
    assertThat(pendingBidPool.contains(farFutureBid.hashTreeRoot())).isFalse();
  }

  private Checkpoint finalizedCheckpoint(final SignedBeaconBlock block) {
    final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot()).plus(UInt64.ONE);
    final Bytes32 root = block.getMessage().hashTreeRoot();

    return new Checkpoint(epoch, root);
  }

  private OptionalDouble weightedPoolBytes() {
    return metricsSystem
        .getLabelledGauge(TekuMetricCategory.BEACON, "weighted_pending_pool_bytes")
        .getValue("weighted_items");
  }

  private OptionalDouble pendingPoolBytes(final String type) {
    return pendingPoolBytes(metricsSystem, type);
  }

  private static OptionalDouble pendingPoolBytes(
      final StubMetricsSystem metricsSystem, final String type) {
    return metricsSystem
        .getLabelledGauge(TekuMetricCategory.BEACON, "pending_pool_bytes")
        .getValue(type);
  }

  private PendingPool<PendingTestItem> createWeightedPendingPool(final long maxTotalWeight) {
    final PendingPool<PendingTestItem> weightedPendingPool =
        new PendingPool<>(
            SettableLabelledGauge.create(
                metricsSystem,
                TekuMetricCategory.BEACON,
                "weighted_pending_pool_size",
                "Number of items in weighted pending pool",
                "type"),
            "weighted_items",
            spec,
            historicalTolerance,
            futureTolerance,
            maxItems,
            maxTotalWeight,
            PendingTestItem::weight,
            Optional.of(
                SettableLabelledGauge.create(
                    metricsSystem,
                    TekuMetricCategory.BEACON,
                    "weighted_pending_pool_bytes",
                    "Total weight of items in weighted pending pool",
                    "type")),
            PendingTestItem::root,
            PendingTestItem::requiredRoots,
            PendingTestItem::slot);
    weightedPendingPool.onSlot(currentSlot);
    return weightedPendingPool;
  }

  private PendingTestItem createPendingTestItem(final long weight, final UInt64 slot) {
    return new PendingTestItem(
        dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32(), slot, weight);
  }

  private record PendingTestItem(Bytes32 root, Bytes32 requiredRoot, UInt64 slot, long weight) {
    private Set<Bytes32> requiredRoots() {
      return Set.of(requiredRoot);
    }
  }

  @Test
  public void add_duplicateBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final Bytes32 parentRoot = block.getParentRoot();
    pendingPool.add(block);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(parentRoot);
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_siblingBlocks() {
    final SignedBeaconBlock blockA =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final Bytes32 parentRoot = blockA.getParentRoot();
    final SignedBeaconBlock blockB =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), parentRoot);
    pendingPool.add(blockA);
    pendingPool.add(blockB);

    assertThat(pendingPool.contains(blockA)).isTrue();
    assertThat(pendingPool.contains(blockB)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(2);
    assertThat(pendingPool.getItemsDependingOn(blockA.getParentRoot(), false))
        .containsExactlyInAnyOrder(blockA, blockB);
    assertThat(requiredRootEvents).containsExactly(parentRoot);
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void remove_existingBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    pendingPool.add(block);
    pendingPool.remove(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false)).isEmpty();
    assertThat(requiredRootEvents).containsExactly(block.getParentRoot());
    assertThat(requiredRootDroppedEvents).containsExactly(block.getParentRoot());
  }

  @Test
  public void remove_unknownBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    pendingPool.remove(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParentRoot(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void remove_siblingBlock() {
    final SignedBeaconBlock blockA =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final Bytes32 parentRoot = blockA.getParentRoot();
    final SignedBeaconBlock blockB =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), parentRoot);
    pendingPool.add(blockA);
    pendingPool.add(blockB);
    pendingPool.remove(blockA);

    assertThat(pendingPool.contains(blockB)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(blockA.getParentRoot(), false))
        .containsExactlyInAnyOrder(blockB);
    assertThat(requiredRootEvents).containsExactly(parentRoot);
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void removeItemsMatching_removesAndReturnsOnlyMatchingItems() {
    final SignedBeaconBlock matchingBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final SignedBeaconBlock otherBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.plus(UInt64.ONE).longValue());
    pendingPool.add(matchingBlock);
    pendingPool.add(otherBlock);

    final List<SignedBeaconBlock> removedItems =
        pendingPool.removeItemsMatching(block -> block.getSlot().equals(currentSlot));

    assertThat(removedItems).containsExactly(matchingBlock);
    assertThat(pendingPool.contains(matchingBlock)).isFalse();
    assertThat(pendingPool.contains(otherBlock)).isTrue();
  }

  @Test
  public void removeItemsDependingOn_removesAndReturnsOnlyDependentItems() {
    final Bytes32 requiredRoot = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock firstDependentBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), requiredRoot);
    final SignedBeaconBlock secondDependentBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), requiredRoot);
    final SignedBeaconBlock independentBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    pendingPool.add(firstDependentBlock);
    pendingPool.add(secondDependentBlock);
    pendingPool.add(independentBlock);

    final List<SignedBeaconBlock> removedItems = pendingPool.removeItemsDependingOn(requiredRoot);

    assertThat(removedItems).containsExactlyInAnyOrder(firstDependentBlock, secondDependentBlock);
    assertThat(pendingPool.contains(firstDependentBlock)).isFalse();
    assertThat(pendingPool.contains(secondDependentBlock)).isFalse();
    assertThat(pendingPool.contains(independentBlock)).isTrue();
  }

  @Test
  void getAllRequiredBlockRoots_shouldExcludeRootsForAlreadyPendingItems() {
    final SignedBeaconBlock blockA =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final Bytes32 parentRoot = blockA.getParentRoot();
    final SignedBeaconBlock blockB =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), parentRoot);
    // Should not be included as blockB is already present
    final SignedBeaconBlock blockC =
        dataStructureUtil.randomSignedBeaconBlock(
            currentSlot.longValue() + 1, blockB.getParentRoot());
    final SignedBeaconBlock blockD =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() + 1);

    pendingPool.add(blockA);
    pendingPool.add(blockB);
    pendingPool.add(blockC);
    pendingPool.add(blockD);
    assertThat(pendingPool.size()).isEqualTo(4);

    assertThat(pendingPool.getAllRequiredBlockRoots())
        .containsExactlyInAnyOrder(parentRoot, blockD.getParentRoot());
  }

  @Test
  public void getItemsDependingOn_includeIndirect() {
    final int chainDepth = 2;
    final int descendentChainCount = 2;
    final List<SignedBeaconBlock> directDescendents = new ArrayList<>();
    final List<SignedBeaconBlock> indirectDescendents = new ArrayList<>();

    final Bytes32 commonAncestorRoot = dataStructureUtil.randomBytes32();
    for (int chainIndex = 0; chainIndex < descendentChainCount; chainIndex++) {
      Bytes32 parentRoot = commonAncestorRoot;
      for (int depth = 0; depth < chainDepth; depth++) {
        final long slot = currentSlot.longValue() + 1 + depth;
        final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot, parentRoot);
        final List<SignedBeaconBlock> blockSet =
            depth == 0 ? directDescendents : indirectDescendents;
        blockSet.add(block);

        parentRoot = block.getMessage().hashTreeRoot();
      }
    }

    indirectDescendents.forEach(pendingPool::add);
    directDescendents.forEach(pendingPool::add);

    List<SignedBeaconBlock> dependentBlocks =
        pendingPool.getItemsDependingOn(commonAncestorRoot, true);
    assertThat(dependentBlocks.size())
        .isEqualTo(directDescendents.size() + indirectDescendents.size());
    assertThat(dependentBlocks).containsAll(directDescendents);
    assertThat(dependentBlocks).containsAll(indirectDescendents);
  }

  @Test
  public void getItemsDependingOn_directOnly() {
    final int chainDepth = 2;
    final int descendentChainCount = 2;
    final List<SignedBeaconBlock> directDescendents = new ArrayList<>();
    final List<SignedBeaconBlock> indirectDescendents = new ArrayList<>();

    final Bytes32 commonAncestorRoot = dataStructureUtil.randomBytes32();
    for (int chainIndex = 0; chainIndex < descendentChainCount; chainIndex++) {
      Bytes32 parentRoot = commonAncestorRoot;
      for (int depth = 0; depth < chainDepth; depth++) {
        final long slot = currentSlot.longValue() + 1 + depth;
        final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot, parentRoot);
        final List<SignedBeaconBlock> blockSet =
            depth == 0 ? directDescendents : indirectDescendents;
        blockSet.add(block);

        parentRoot = block.getMessage().hashTreeRoot();
      }
    }

    indirectDescendents.forEach(pendingPool::add);
    directDescendents.forEach(pendingPool::add);

    List<SignedBeaconBlock> dependentBlocks =
        pendingPool.getItemsDependingOn(commonAncestorRoot, false);
    assertThat(dependentBlocks).containsExactlyInAnyOrderElementsOf(directDescendents);
  }

  @Test
  public void prune_finalizedBlocks() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    final long finalizedSlot = checkpoint.getEpochStartSlot(spec).longValue();
    setSlot(finalizedSlot);

    // Add a bunch of blocks
    List<SignedBeaconBlock> nonFinalBlocks =
        List.of(dataStructureUtil.randomSignedBeaconBlock(finalizedSlot + 1));
    List<SignedBeaconBlock> finalizedBlocks =
        List.of(
            dataStructureUtil.randomSignedBeaconBlock(finalizedSlot),
            dataStructureUtil.randomSignedBeaconBlock(finalizedSlot - 1));
    List<SignedBeaconBlock> allBlocks = new ArrayList<>();
    allBlocks.addAll(nonFinalBlocks);
    allBlocks.addAll(finalizedBlocks);
    nonFinalBlocks.forEach(pendingPool::add);
    finalizedBlocks.forEach(pendingPool::add);

    // Check that all blocks are in the collection
    assertThat(pendingPool.size()).isEqualTo(finalizedBlocks.size() + nonFinalBlocks.size());
    for (SignedBeaconBlock block : allBlocks) {
      assertThat(pendingPool.contains(block)).isTrue();
    }

    // Update finalized checkpoint and prune
    pendingPool.onNewFinalizedCheckpoint(checkpoint, false);
    pendingPool.prune();

    // Check that all final blocks have been pruned
    assertThat(pendingPool.size()).isEqualTo(nonFinalBlocks.size());
    for (SignedBeaconBlock block : nonFinalBlocks) {
      assertThat(pendingPool.contains(block)).isTrue();
    }
  }

  @Test
  public void onSlot_prunesOldAndFinalizedBlocks() {
    // We prune on PRUNE_SLOT every epoch everything
    // older than (current - historicalTolerance) or finalized
    // Let's put some blocks around (pruneSlot - historicalTolerance)
    final UInt64 startSlot =
        AbstractIgnoringFutureHistoricalSlot.PRUNE_SLOT
            .plus(spec.getGenesisSpec().getSlotsPerEpoch())
            .minus(historicalTolerance);
    setSlot(startSlot);

    // Interleave blocks to keep and blocks to prune
    final List<SignedBeaconBlock> blocksToPrune = new ArrayList<>();
    final List<SignedBeaconBlock> blocksToKeep = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final SignedBeaconBlock toPrune =
          dataStructureUtil.randomSignedBeaconBlock(startSlot.longValue() - 1L);
      final SignedBeaconBlock toKeep =
          dataStructureUtil.randomSignedBeaconBlock(startSlot.longValue());

      blocksToPrune.add(toPrune);
      blocksToKeep.add(toKeep);
      pendingPool.add(toPrune);
      pendingPool.add(toKeep);
    }

    blocksToKeep.forEach(b -> assertThat(pendingPool.contains(b)).isTrue());
    blocksToPrune.forEach(b -> assertThat(pendingPool.contains(b)).isTrue());

    UInt64 newSlot = startSlot;
    for (int i = 0; i < historicalTolerance.intValue() - 1; i++) {
      newSlot = newSlot.plus(UInt64.ONE);
      pendingPool.onSlot(newSlot);
      blocksToKeep.forEach(b -> assertThat(pendingPool.contains(b)).isTrue());
      blocksToPrune.forEach(b -> assertThat(pendingPool.contains(b)).isTrue());
    }

    // Next slot should prune blocksToPrune
    pendingPool.onSlot(newSlot.plus(UInt64.ONE));

    blocksToKeep.forEach(b -> assertThat(pendingPool.contains(b)).isTrue());
    blocksToPrune.forEach(b -> assertThat(pendingPool.contains(b)).isFalse());

    // Make all finalized
    pendingPool.onNewFinalizedCheckpoint(dataStructureUtil.randomCheckpoint(1), false);
    pendingPool.onSlot(newSlot.plus(UInt64.ONE));
    blocksToKeep.forEach(b -> assertThat(pendingPool.contains(b)).isFalse());
  }
}

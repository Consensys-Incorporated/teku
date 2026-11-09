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

package tech.pegasys.teku.storage.protoarray;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;

abstract class AbstractBlockMetadataStoreTest {

  protected final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final Checkpoint genesisCheckpoint =
      new Checkpoint(SpecConfig.GENESIS_EPOCH, genesis.getRoot());

  @Test
  void contains_shouldOnlyContainBlocksThatExist() {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    assertThat(store.contains(genesis.getRoot())).isTrue();
    assertThat(store.contains(Bytes32.ZERO)).isFalse();
  }

  @Test
  void contains_shouldContainAddedBlocks() {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    final BlockAndCheckpoints block1 =
        BlockAndCheckpoints.fromBlockAndState(spec, chainBuilder.generateBlockAtSlot(1));
    final BlockAndCheckpoints block2 =
        BlockAndCheckpoints.fromBlockAndState(spec, chainBuilder.generateBlockAtSlot(2));
    final BlockAndCheckpoints block3 =
        BlockAndCheckpoints.fromBlockAndState(spec, chainBuilder.generateBlockAtSlot(3));

    store.applyUpdate(
        List.of(block1, block2, block3), emptyMap(), emptySet(), emptyMap(), genesisCheckpoint);

    chainBuilder
        .streamBlocksAndStates()
        .forEach(block -> assertThat(store.contains(block.getRoot())).isTrue());
  }

  @Test
  void contains_shouldNotContainRemovedBlocks() {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    final BlockAndCheckpoints block1 =
        BlockAndCheckpoints.fromBlockAndState(spec, chainBuilder.generateBlockAtSlot(1));
    final BlockAndCheckpoints block2 =
        BlockAndCheckpoints.fromBlockAndState(spec, chainBuilder.generateBlockAtSlot(2));
    final BlockAndCheckpoints block3 =
        BlockAndCheckpoints.fromBlockAndState(spec, chainBuilder.generateBlockAtSlot(3));

    store.applyUpdate(
        List.of(block1, block2, block3),
        emptyMap(),
        emptySet(),
        Map.of(genesis.getRoot(), UInt64.ZERO, block1.getRoot(), block1.getSlot()),
        new Checkpoint(UInt64.ONE, block2.getRoot()));

    assertThat(store.contains(genesis.getRoot())).isFalse();
    assertThat(store.contains(block1.getRoot())).isFalse();
    assertThat(store.contains(block2.getRoot())).isTrue();
    assertThat(store.contains(block3.getRoot())).isTrue();
  }

  @Test
  void processAllBeaconBlocksInOrder_shouldVisitAllBlocksInSingleChain() {
    final int lastSlot = 10;
    chainBuilder.generateBlocksUpToSlot(lastSlot);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    final AtomicInteger expectedNextSlot = new AtomicInteger(0);
    store.processAllBeaconBlocksInOrder(
        (childRoot, slot, parentRoot) -> {
          assertThat(slot).isEqualTo(UInt64.valueOf(expectedNextSlot.getAndIncrement()));
          final SignedBeaconBlock block = chainBuilder.getBlockAtSlot(slot);
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());
        });
    // We should have processed the block in the last slot and now be expected the one after
    assertThat(expectedNextSlot).hasValue(lastSlot + 1);
  }

  @Test
  void processAllBeaconBlocksInOrder_shouldVisitParentBlocksBeforeChildBlocksInForks() {
    // First chain has all blocks up to 10
    // Fork chain has 0-5, skips 6 and then has 7-10
    chainBuilder.generateBlocksUpToSlot(5);
    final ChainBuilder forkBuilder = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(10);
    forkBuilder.generateBlockAtSlot(7);
    forkBuilder.generateBlocksUpToSlot(10);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    store.applyUpdate(
        forkBuilder
            .streamBlocksAndStates()
            .map(blockAndState -> BlockAndCheckpoints.fromBlockAndState(spec, blockAndState))
            .collect(toList()),
        emptyMap(),
        emptySet(),
        emptyMap(),
        genesisCheckpoint);

    final Set<Bytes32> seenBlocks = new HashSet<>();

    store.processAllBeaconBlocksInOrder(
        (childRoot, slot, parentRoot) -> {
          assertThat(seenBlocks).doesNotContain(childRoot);
          if (!seenBlocks.isEmpty()) {
            // First block won't have visited the parent
            assertThat(seenBlocks).contains(parentRoot);
          }

          seenBlocks.add(childRoot);

          final SignedBeaconBlock block =
              chainBuilder
                  .getBlock(childRoot)
                  .or(() -> forkBuilder.getBlock(childRoot))
                  .orElseThrow();
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());
        });
    // Check every block was seen
    final Set<Bytes32> expectedSeenRoots =
        Stream.concat(chainBuilder.streamBlocksAndStates(), forkBuilder.streamBlocksAndStates())
            .map(StateAndBlockSummary::getRoot)
            .collect(toSet());

    assertThat(seenBlocks).containsExactlyInAnyOrderElementsOf(expectedSeenRoots);
  }

  @Test
  void processBeaconBlockChain_shouldWalkUpSpecifiedChain() {
    // First chain has all blocks up to 10
    // Fork chain has 0-5, skips 6 and then has 7-10
    chainBuilder.generateBlocksUpToSlot(5);
    final ChainBuilder forkBuilder = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(10);
    forkBuilder.generateBlockAtSlot(7);
    forkBuilder.generateBlocksUpToSlot(10);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);
    store.applyUpdate(
        forkBuilder
            .streamBlocksAndStates()
            .map(blockAndState -> BlockAndCheckpoints.fromBlockAndState(spec, blockAndState))
            .collect(toList()),
        emptyMap(),
        emptySet(),
        emptyMap(),
        genesisCheckpoint);

    verifyHashesInChain(
        store,
        forkBuilder,
        forkBuilder.getLatestBlockAndState().getRoot(),
        forkBuilder.streamBlocksAndStates());
    verifyHashesInChain(
        store,
        chainBuilder,
        chainBuilder.getLatestBlockAndState().getRoot(),
        chainBuilder.streamBlocksAndStates());

    // And check we can start from part way along the chain
    verifyHashesInChain(
        store,
        chainBuilder,
        chainBuilder.getBlockAtSlot(6).getRoot(),
        chainBuilder.streamBlocksAndStates(0, 6));
  }

  @Test
  void processBeaconBlockChainWhile_shouldStopProcessingWhenProcessorReturnsFalse() {
    chainBuilder.generateBlocksUpToSlot(10);
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder);

    // And check we can start from part way along the chain
    final Bytes32 headRoot = chainBuilder.getBlockAtSlot(6).getRoot();
    final Stream<SignedBlockAndState> expectedBlocks = chainBuilder.streamBlocksAndStates(3, 6);

    final Set<Bytes32> seenBlocks = new HashSet<>();

    final AtomicReference<Bytes32> expectedBlock = new AtomicReference<>(headRoot);
    store.processBeaconBlockChainWhile(
        headRoot,
        (childRoot, slot, parentRoot) -> {
          assertThat(seenBlocks).doesNotContain(childRoot);
          seenBlocks.add(childRoot);

          final SignedBeaconBlock block = chainBuilder.getBlock(expectedBlock.get()).orElseThrow();
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());

          expectedBlock.set(block.getParentRoot());
          return !slot.equals(UInt64.valueOf(3));
        });
    // Check all fork blocks were seen
    final Set<Bytes32> expectedSeenRoots =
        expectedBlocks.map(StateAndBlockSummary::getRoot).collect(toSet());

    assertThat(seenBlocks).containsExactlyInAnyOrderElementsOf(expectedSeenRoots);
  }

  @Test
  void findCommonAncestor_atGenesisBlock() {
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);
    assertCommonAncestorFound(
        genesis.getRoot(), block2.getRoot(), new SlotAndBlockRoot(UInt64.ZERO, genesis.getRoot()));
  }

  @Test
  void findCommonAncestor_headBlockEmptyVsFull() {
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);

    assertCommonAncestorFound(
        block1.getRoot(), block2.getRoot(), new SlotAndBlockRoot(UInt64.ONE, block1.getRoot()));
  }

  @Test
  void findCommonAncestor_differingChains() {
    chainBuilder.generateBlockAtSlot(2);
    final ChainBuilder fork = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(4);
    final SignedBlockAndState chainHead = chainBuilder.generateBlockAtSlot(5);

    // Fork skips slot 3 so the chains are different
    fork.generateBlockAtSlot(4);
    fork.generateBlocksUpToSlot(6);
    final SignedBlockAndState forkHead = fork.generateBlockAtSlot(7);
    final SignedBeaconBlock commonBlock = chainBuilder.getBlockAtSlot(UInt64.valueOf(2));

    assertCommonAncestorFound(
        chainHead.getRoot(),
        forkHead.getRoot(),
        new SlotAndBlockRoot(commonBlock.getSlot(), commonBlock.getRoot()),
        fork);
  }

  @Test
  void findCommonAncestor_commonAncestorNotInProtoArray() {
    final ChainBuilder fork = chainBuilder.fork();

    final SignedBlockAndState chainHead =
        chainBuilder.generateBlockAtSlot(spec.getSlotsPerHistoricalRoot(fork.getLatestSlot()) + 2);

    // Fork skips slot 1 so the chains are different
    fork.generateBlockAtSlot(1);
    final SignedBlockAndState forkHead =
        fork.generateBlockAtSlot(spec.getSlotsPerHistoricalRoot(fork.getLatestSlot()) + 2);

    // We don't add the fork to protoarray, much like it was invalidated by the finalized checkpoint
    assertCommonAncestorFound(chainHead.getRoot(), forkHead.getRoot(), Optional.empty());
  }

  private void assertCommonAncestorFound(
      final Bytes32 root1,
      final Bytes32 root2,
      final SlotAndBlockRoot expectedCommonAncestor,
      final ChainBuilder... forks) {
    assertCommonAncestorFound(root1, root2, Optional.of(expectedCommonAncestor), forks);
  }

  private void assertCommonAncestorFound(
      final Bytes32 root1,
      final Bytes32 root2,
      final Optional<SlotAndBlockRoot> expectedCommonAncestor,
      final ChainBuilder... forks) {
    final BlockMetadataStore store = createBlockMetadataStore(chainBuilder, forks);
    final Optional<SlotAndBlockRoot> slotAndBlockRootChainA =
        store.findCommonAncestor(root1, root2);
    final Optional<SlotAndBlockRoot> slotAndBlockRootChainB =
        store.findCommonAncestor(root2, root1);
    assertThat(slotAndBlockRootChainA).isEqualTo(expectedCommonAncestor);
    assertThat(slotAndBlockRootChainB).isEqualTo(expectedCommonAncestor);
  }

  private void verifyHashesInChain(
      final BlockMetadataStore store,
      final ChainBuilder chain,
      final Bytes32 headRoot,
      final Stream<SignedBlockAndState> expectedBlocks) {
    final Set<Bytes32> seenBlocks = new HashSet<>();

    final AtomicReference<Bytes32> expectedBlock = new AtomicReference<>(headRoot);
    store.processBeaconBlockChain(
        headRoot,
        (childRoot, slot, parentRoot) -> {
          assertThat(seenBlocks).doesNotContain(childRoot);
          seenBlocks.add(childRoot);

          final SignedBeaconBlock block = chain.getBlock(expectedBlock.get()).orElseThrow();
          assertThat(childRoot).isEqualTo(block.getRoot());
          assertThat(slot).isEqualTo(block.getSlot());
          assertThat(parentRoot).isEqualTo(block.getParentRoot());

          expectedBlock.set(block.getParentRoot());
        });
    // Check all fork blocks were seen
    final Set<Bytes32> expectedSeenRoots =
        expectedBlocks.map(StateAndBlockSummary::getRoot).collect(toSet());

    assertThat(seenBlocks).containsExactlyInAnyOrderElementsOf(expectedSeenRoots);
  }

  protected abstract BlockMetadataStore createBlockMetadataStore(
      ChainBuilder chainBuilder, ChainBuilder... additionalBuilders);
}

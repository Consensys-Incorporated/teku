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

package tech.pegasys.teku.dataproviders.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class StateGeneratorTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);

  @Test
  public void regenerateStateForBlock_missingBaseBlock() {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final Map<Bytes32, SignedBeaconBlock> blockMap =
        chainBuilder
            .streamBlocksAndStates()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
    final SignedBlockAndState lastBlockAndState = chainBuilder.getLatestBlockAndState();

    final HashTree tree =
        HashTree.builder().rootHash(genesis.getRoot()).blocks(blockMap.values()).build();
    // Create block provider that is missing the base (genesis) block
    blockMap.remove(genesis.getRoot());
    final BlockProvider blockProvider = BlockProvider.fromMap(blockMap);

    final StateGenerator generator = StateGenerator.create(spec, tree, genesis, blockProvider);
    final SafeFuture<StateAndBlockSummary> result =
        generator.regenerateStateForBlock(lastBlockAndState.getRoot());
    assertThat(result).isCompletedWithValue(lastBlockAndState);
  }

  @Test
  public void regenerateStateForBlock_regenerateBaseStateWhenBlockIsMissing() {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final Map<Bytes32, SignedBeaconBlock> blockMap =
        chainBuilder
            .streamBlocksAndStates()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));

    final HashTree tree =
        HashTree.builder().rootHash(genesis.getRoot()).blocks(blockMap.values()).build();
    // Create block provider that is missing the base (genesis) block
    blockMap.remove(genesis.getRoot());
    final BlockProvider blockProvider = BlockProvider.fromMap(blockMap);

    final StateGenerator generator = StateGenerator.create(spec, tree, genesis, blockProvider);
    final SafeFuture<StateAndBlockSummary> result =
        generator.regenerateStateForBlock(genesis.getRoot());
    final StateAndBlockSummary expected = StateAndBlockSummary.create(genesis.getState());
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void regenerateStateForBlock_failOnMissingBlocks() {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SafeFuture<StateAndBlockSummary> result =
              generator.regenerateStateForBlock(missingBlock.getRoot());
          assertThatThrownBy(result::get)
              .hasCauseInstanceOf(IllegalStateException.class)
              .hasMessageContaining(
                  "Failed to retrieve 1 / 2 blocks building on state at slot 0: "
                      + missingBlock.getRoot());
        });
  }

  @Test
  public void regenerateStateForBlock_blockPastTargetIsMissing() {
    testGeneratorWithMissingBlock(
        (generator, missingBlock) -> {
          SignedBlockAndState target =
              chainBuilder.getBlockAndStateAtSlot(missingBlock.getSlot().minus(UInt64.ONE));
          SafeFuture<StateAndBlockSummary> result =
              generator.regenerateStateForBlock(target.getRoot());
          assertThat(result).isCompletedWithValue(target);
        });
  }

  private void testGeneratorWithMissingBlock(
      final BiConsumer<StateGenerator, SignedBeaconBlock> processor) {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(5);
    final Map<Bytes32, SignedBeaconBlock> blockMap =
        chainBuilder
            .streamBlocksAndStates()
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));

    final HashTree tree =
        HashTree.builder().rootHash(genesis.getRoot()).blocks(blockMap.values()).build();
    // Create block provider that is missing some blocks
    final SignedBeaconBlock missingBlock = chainBuilder.getBlockAtSlot(genesis.getSlot().plus(2));
    blockMap.remove(missingBlock.getRoot());
    final BlockProvider blockProvider = BlockProvider.fromMap(blockMap);

    final StateGenerator generator = StateGenerator.create(spec, tree, genesis, blockProvider);
    processor.accept(generator, missingBlock);
  }

  public static Stream<Arguments> getParameters() {
    Stream.Builder<Arguments> builder = Stream.builder();

    final IntList stateCacheSizes = IntList.of(0, 1, 100);
    final IntList blockBatchSizes = IntList.of(1, 5, 1000);
    for (int stateCacheSize : stateCacheSizes) {
      for (int blockBatchSize : blockBatchSizes) {
        builder.add(Arguments.of(stateCacheSize, blockBatchSize));
      }
    }

    return builder.build();
  }
}

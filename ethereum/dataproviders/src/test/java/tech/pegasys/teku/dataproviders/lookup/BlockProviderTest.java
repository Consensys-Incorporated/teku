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

package tech.pegasys.teku.dataproviders.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class BlockProviderTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @Test
  void withKnownBlocks_withEmptyProvider() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(2);
    final Map<Bytes32, SignedBeaconBlock> knownBlocks =
        chainBuilder
            .streamBlocksAndStates()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider provider = BlockProvider.withKnownBlocks(BlockProvider.NOOP, knownBlocks);
    for (Bytes32 root : knownBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(knownBlocks.get(root));
    }
  }

  @Test
  void withKnownBlocks_withNonEmptyProvider() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);

    final Map<Bytes32, SignedBeaconBlock> knownBlocks =
        chainBuilder
            .streamBlocksAndStates(0, 5)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> otherBlocks =
        chainBuilder
            .streamBlocksAndStates(6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider origProvider = BlockProvider.fromMap(otherBlocks);
    final BlockProvider provider = BlockProvider.withKnownBlocks(origProvider, knownBlocks);

    // Pull known blocks
    for (Bytes32 root : knownBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(knownBlocks.get(root));
    }
    // Pull blocks from original provider
    for (Bytes32 root : otherBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(otherBlocks.get(root));
    }
  }

  @Test
  void combined() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);

    final Map<Bytes32, SignedBeaconBlock> allBlocks =
        chainBuilder
            .streamBlocksAndStates()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> setA =
        chainBuilder
            .streamBlocksAndStates(0, 3)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> setB =
        chainBuilder
            .streamBlocksAndStates(4, 6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> setC =
        chainBuilder
            .streamBlocksAndStates(7, 10)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider provider =
        BlockProvider.combined(
            BlockProvider.fromMap(setA), BlockProvider.fromMap(setB), BlockProvider.fromMap(setC));

    // Check all blocks are available
    for (Bytes32 root : allBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(allBlocks.get(root));
    }
  }

  @Test
  void fromDynamicMap() {
    chainBuilder.generateGenesis();
    SignedBlockAndState blockA = chainBuilder.generateNextBlock();
    SignedBlockAndState blockB = chainBuilder.generateNextBlock();

    final AtomicReference<Map<Bytes32, SignedBeaconBlock>> mapSupplier =
        new AtomicReference<>(Collections.emptyMap());
    final BlockProvider provider = BlockProvider.fromDynamicMap(mapSupplier::get);

    assertThat(provider.getBlock(blockA.getRoot())).isCompletedWithValue(Optional.empty());
    assertThat(provider.getBlock(blockB.getRoot())).isCompletedWithValue(Optional.empty());

    mapSupplier.set(Map.of(blockA.getRoot(), blockA.getBlock()));

    assertThat(provider.getBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock()));
    assertThat(provider.getBlock(blockB.getRoot())).isCompletedWithValue(Optional.empty());

    mapSupplier.set(Map.of(blockB.getRoot(), blockB.getBlock()));

    assertThat(provider.getBlock(blockA.getRoot())).isCompletedWithValue(Optional.empty());
    assertThat(provider.getBlock(blockB.getRoot()))
        .isCompletedWithValue(Optional.of(blockB.getBlock()));
  }
}

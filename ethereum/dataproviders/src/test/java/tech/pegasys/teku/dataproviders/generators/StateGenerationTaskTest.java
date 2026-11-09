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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

class StateGenerationTaskTest {

  private static final int REPLAY_TOLERANCE_TO_AVOID_LOADING_IN_EPOCHS = 0;
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private TrackingBlockProvider blockProvider;

  @BeforeEach
  void setUp() {
    chainBuilder.generateGenesis();
    blockProvider = new TrackingBlockProvider(getBlockProvider());
  }

  @Test
  void performTask_shouldRegenerateState() {
    chainBuilder.generateBlocksUpToSlot(3);
    final StateGenerationTask task = createTask(0, 3);
    final SafeFuture<Optional<StateAndBlockSummary>> result = task.performTask();
    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(chainBuilder.getBlockAndStateAtSlot(3));
    assertRequestedBlockRangeInclusive(1, 3);
  }

  @Test
  void performTask_shouldLoadStateFromLatestEpochBoundary() {
    chainBuilder.generateBlocksUpToSlot(5);
    final SignedBeaconBlock epochBoundaryBlock = chainBuilder.getBlockAtSlot(3);
    final Optional<SlotAndBlockRoot> epochBoundaryRoot =
        Optional.of(
            new SlotAndBlockRoot(epochBoundaryBlock.getSlot(), epochBoundaryBlock.getRoot()));
    final StateGenerationTask task = createTask(1, 5, epochBoundaryRoot);
    final SafeFuture<Optional<StateAndBlockSummary>> result = task.performTask();

    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(chainBuilder.getBlockAndStateAtSlot(5));
    assertRequestedBlockRangeInclusive(4, 5);
  }

  @Test
  void rebase_shouldStartFromMoreRecentState() {
    chainBuilder.generateBlocksUpToSlot(5);

    final StateGenerationTask originalTask = createTask(0, 5);

    final StateGenerationTask rebasedTask =
        originalTask.rebase(chainBuilder.getBlockAndStateAtSlot(4));
    final SafeFuture<Optional<StateAndBlockSummary>> result = rebasedTask.performTask();
    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(chainBuilder.getBlockAndStateAtSlot(5));
    assertRequestedBlockRangeInclusive(5, 5);
  }

  @Test
  void streamIntermediateSteps_shouldStreamParentBlocksUpToInitialStartingPoint() {
    chainBuilder.generateBlocksUpToSlot(5);

    final List<Bytes32> expectedIntermediateSteps =
        chainBuilder
            .streamBlocksAndStates(3, 4)
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    Collections.reverse(expectedIntermediateSteps);

    final StateGenerationTask task = createTask(3, 5);
    final Stream<Bytes32> intermediateSteps = task.streamIntermediateSteps();
    assertThat(intermediateSteps).containsExactlyElementsOf(expectedIntermediateSteps);
  }

  private void assertRequestedBlockRangeInclusive(final int fromSlot, final int toSlot) {
    assertThat(blockProvider.getRequestedBlocks())
        .containsExactlyInAnyOrderElementsOf(
            chainBuilder
                .streamBlocksAndStates(fromSlot, toSlot)
                .map(SignedBlockAndState::getRoot)
                .collect(Collectors.toSet()));
  }

  private StateGenerationTask createTask(final int startSlot, final int endSlot) {
    return createTask(startSlot, endSlot, Optional.empty());
  }

  private StateGenerationTask createTask(
      final int startSlot, final int endSlot, final Optional<SlotAndBlockRoot> epochBoundaryRoot) {
    final SignedBlockAndState startBlockAndState = chainBuilder.getBlockAndStateAtSlot(startSlot);
    final SignedBeaconBlock endBlock = chainBuilder.getBlockAtSlot(endSlot);
    final HashTree.Builder treeBuilder =
        HashTree.builder()
            .block(startBlockAndState.getBlock())
            .rootHash(startBlockAndState.getRoot());
    SignedBeaconBlock block = endBlock;
    while (block.getSlot().isGreaterThan(startBlockAndState.getSlot())) {
      treeBuilder.block(block);
      block = chainBuilder.getBlock(block.getParentRoot()).orElseThrow();
    }
    final HashTree tree = treeBuilder.build();
    return new StateGenerationTask(
        spec,
        endBlock.getRoot(),
        tree,
        blockProvider,
        new StateRegenerationBaseSelector(
            spec,
            epochBoundaryRoot,
            () ->
                Optional.of(
                    new BlockRootAndState(
                        startBlockAndState.getRoot(), startBlockAndState.getState())),
            getStateAndBlockProvider(),
            Optional.empty(),
            REPLAY_TOLERANCE_TO_AVOID_LOADING_IN_EPOCHS));
  }

  private static class TrackingBlockProvider implements BlockProvider {
    private final Set<Bytes32> requestedBlocks = new HashSet<>();
    private final BlockProvider delegate;

    private TrackingBlockProvider(final BlockProvider delegate) {
      this.delegate = delegate;
    }

    @Override
    public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getBlocks(final Set<Bytes32> blockRoots) {
      requestedBlocks.addAll(blockRoots);
      return delegate.getBlocks(blockRoots);
    }

    public Set<Bytes32> getRequestedBlocks() {
      return requestedBlocks;
    }
  }

  public BlockProvider getBlockProvider() {
    return BlockProvider.fromDynamicMap(
        () ->
            chainBuilder
                .streamBlocksAndStates()
                .collect(
                    Collectors.toMap(
                        StateAndBlockSummary::getRoot, SignedBlockAndState::getBlock)));
  }

  public StateAndBlockSummaryProvider getStateAndBlockProvider() {
    return blockRoot ->
        SafeFuture.completedFuture(chainBuilder.getBlockAndState(blockRoot).map(a -> a));
  }
}

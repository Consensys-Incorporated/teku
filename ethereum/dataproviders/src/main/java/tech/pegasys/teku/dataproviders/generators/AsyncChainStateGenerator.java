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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class AsyncChainStateGenerator {
  private static final Logger LOG = LogManager.getLogger();
  public static final int DEFAULT_BLOCK_BATCH_SIZE = 250;

  private final Spec spec;
  private final HashTree blockTree;
  private final BlockProvider blockProvider;
  private final StateProvider stateProvider;
  private final int blockBatchSize;

  private AsyncChainStateGenerator(
      final Spec spec,
      final HashTree blockTree,
      final BlockProvider blockProvider,
      final StateProvider stateProvider,
      final int blockBatchSize) {
    this.spec = spec;
    this.blockTree = blockTree;
    this.blockProvider = blockProvider;
    this.stateProvider = stateProvider;
    this.blockBatchSize = blockBatchSize;
  }

  public static AsyncChainStateGenerator create(
      final Spec spec,
      final HashTree blockTree,
      final BlockProvider blockProvider,
      final StateProvider stateProvider) {
    return new AsyncChainStateGenerator(
        spec, blockTree, blockProvider, stateProvider, DEFAULT_BLOCK_BATCH_SIZE);
  }

  public SafeFuture<StateAndBlockSummary> generateTargetState(final Bytes32 targetRoot) {
    if (!blockTree.contains(targetRoot)) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException("Target root is unknown: " + targetRoot));
    }

    final SafeFuture<StateAndBlockSummary> lastBlockAndState = new SafeFuture<>();
    generateStates(
            targetRoot,
            (stateAndBlock) -> {
              if (stateAndBlock.getRoot().equals(targetRoot)) {
                lastBlockAndState.complete(stateAndBlock);
              }
            })
        .finish(
            // Make sure future is completed
            () ->
                lastBlockAndState.completeExceptionally(
                    new IllegalStateException("Failed to generate state for " + targetRoot)),
            lastBlockAndState::completeExceptionally);

    return lastBlockAndState;
  }

  private SafeFuture<?> generateStates(final Bytes32 targetRoot, final StateHandler handler) {
    return SafeFuture.of(
        () -> {
          // Build chain from target root to the first ancestor with a known state
          final AtomicReference<BeaconState> baseState = new AtomicReference<>(null);
          final AtomicReference<Bytes32> baseBlockRoot = new AtomicReference<>(null);
          final List<Bytes32> chain =
              blockTree.collectChainRoots(
                  targetRoot,
                  (currentRoot) -> {
                    stateProvider
                        .getState(currentRoot)
                        .ifPresent(
                            newValue -> {
                              baseState.set(newValue);
                              baseBlockRoot.set(currentRoot);
                            });
                    return baseState.get() == null;
                  });

          if (baseState.get() == null) {
            throw new IllegalArgumentException("Unable to find base state to build on");
          }

          if (chain.isEmpty()) {
            throw new IllegalStateException("Failed to retrieve chain");
          }

          // Short-circuit if we found the target state
          if (baseBlockRoot.get().equals(targetRoot)) {
            handler.handle(StateAndBlockSummary.create(baseState.get()));
            return SafeFuture.completedFuture(baseState.get());
          }

          // Remove the base block since we don't need it to regenerate our target state
          chain.remove(baseBlockRoot.get());

          LOG.debug(
              "Regenerate state at {}, processing {} blocks on top of slot {} (root: {})",
              targetRoot,
              chain.size(),
              baseState.get().getSlot(),
              chain.getFirst());

          // Process chain in batches
          final List<List<Bytes32>> blockBatches = Lists.partition(chain, blockBatchSize);
          // Request and process each batch of blocks in order
          SafeFuture<BeaconState> future =
              processBlockBatch(blockBatches.get(0), baseState.get(), handler);
          for (int i = 1; i < blockBatches.size(); i++) {
            final List<Bytes32> blockBatch = blockBatches.get(i);
            future = future.thenCompose(state -> processBlockBatch(blockBatch, state, handler));
          }
          return future;
        });
  }

  private SafeFuture<BeaconState> processBlockBatch(
      final List<Bytes32> blockRoots, final BeaconState startState, final StateHandler handler) {
    checkArgument(startState != null, "Must provide start state");
    LOG.debug("Retrieve and process {} blocks", blockRoots.size());
    return blockProvider
        .getBlocks(blockRoots)
        .thenApply(
            blocks -> {
              final List<SignedBeaconBlock> chainBlocks =
                  blockRoots.stream().map(blocks::get).filter(Objects::nonNull).toList();
              if (chainBlocks.size() < blockRoots.size()) {
                final String missingBlocks =
                    blockRoots.stream()
                        .filter(root -> !blocks.containsKey(root))
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                final int missingCount = blockRoots.size() - chainBlocks.size();
                throw new IllegalStateException(
                    String.format(
                        "Failed to retrieve %d / %d blocks building on state at slot %s: %s",
                        missingCount, blockRoots.size(), startState.getSlot(), missingBlocks));
              }

              final ChainStateGenerator chainStateGenerator =
                  ChainStateGenerator.create(spec, chainBlocks, startState, true);
              final AtomicReference<BeaconState> lastState = new AtomicReference<>(null);
              chainStateGenerator.generateStates(
                  stateAndBlock -> {
                    lastState.set(stateAndBlock.getState());
                    handler.handle(stateAndBlock);
                  });

              return lastState.get();
            });
  }
}

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

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.generators.CachingTaskQueue.CacheableTask;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;

public class StateGenerationTask implements CacheableTask<Bytes32, StateAndBlockSummary> {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final HashTree tree;
  private final BlockProvider blockProvider;
  private final Bytes32 blockRoot;
  private final StateRegenerationBaseSelector baseSelector;

  public StateGenerationTask(
      final Spec spec,
      final Bytes32 blockRoot,
      final HashTree tree,
      final BlockProvider blockProvider,
      final StateRegenerationBaseSelector baseSelector) {
    this.spec = spec;
    this.tree = tree;
    this.blockProvider = blockProvider;
    this.blockRoot = blockRoot;
    this.baseSelector = baseSelector;
  }

  @Override
  public Bytes32 getKey() {
    return blockRoot;
  }

  @Override
  public Stream<Bytes32> streamIntermediateSteps() {
    final Bytes32 rootParent = tree.getParent(tree.getRootHash()).orElseThrow();
    return Stream.iterate(
            tree.getParent(blockRoot),
            Optional::isPresent,
            current -> current.flatMap(tree::getParent))
        .flatMap(Optional::stream)
        // The root's parent is in the tree but is worse that the current starting point so exclude
        .filter(ancestorRoot -> !ancestorRoot.equals(rootParent));
  }

  @Override
  public StateGenerationTask rebase(final StateAndBlockSummary newBaseBlockAndState) {
    final Bytes32 newBaseRoot = newBaseBlockAndState.getRoot();
    if (!tree.contains(newBaseRoot)) {
      LOG.warn(
          "Attempting to rebase a task for {} onto a starting state that is not a required ancestor ({} at slot {})",
          blockRoot,
          newBaseRoot,
          newBaseBlockAndState.getSlot());
      return this;
    }
    return new StateGenerationTask(
        spec,
        blockRoot,
        tree,
        blockProvider,
        baseSelector.withRebasedStartingPoint(newBaseBlockAndState));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> performTask() {
    return baseSelector.getBestBase().thenCompose(this::regenerateState);
  }

  protected SafeFuture<Optional<StateAndBlockSummary>> regenerateState(
      final Optional<StateAndBlockSummary> maybeBase) {
    if (maybeBase.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final StateAndBlockSummary base = maybeBase.get();
    return StateGenerator.create(
            spec, tree.withRoot(base.getRoot()).block(base).build(), base, blockProvider)
        .regenerateStateForBlock(blockRoot)
        .thenApply(Optional::of);
  }
}

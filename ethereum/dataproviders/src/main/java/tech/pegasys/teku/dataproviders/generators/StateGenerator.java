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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateGenerator {
  public static final int DEFAULT_STATE_CACHE_SIZE = 100;
  private static final Logger LOG = LogManager.getLogger();

  private final HashTree blockTree;
  private final AsyncChainStateGenerator chainStateGenerator;

  private StateGenerator(
      final HashTree blockTree,
      final AsyncChainStateGenerator chainStateGenerator,
      final StateCache stateCache) {
    checkArgument(
        stateCache.containsKnownState(blockTree.getRootHash()), "Root state must be available");

    this.blockTree = blockTree;
    this.chainStateGenerator = chainStateGenerator;
  }

  public static StateGenerator create(
      final Spec spec,
      final HashTree blockTree,
      final StateAndBlockSummary rootBlockAndState,
      final BlockProvider blockProvider) {
    return create(spec, blockTree, rootBlockAndState, blockProvider, Collections.emptyMap());
  }

  public static StateGenerator create(
      final Spec spec,
      final HashTree blockTree,
      final StateAndBlockSummary rootBlockAndState,
      final BlockProvider blockProvider,
      final Map<Bytes32, BeaconState> knownStates) {
    return create(
        spec, blockTree, rootBlockAndState, blockProvider, knownStates, DEFAULT_STATE_CACHE_SIZE);
  }

  public static StateGenerator create(
      final Spec spec,
      final HashTree blockTree,
      final StateAndBlockSummary rootBlockAndState,
      final BlockProvider blockProvider,
      final Map<Bytes32, BeaconState> knownStates,
      final int stateCacheSize) {
    checkArgument(
        rootBlockAndState.getRoot().equals(blockTree.getRootHash()),
        "Provided root block must match the root of the provided block tree");

    final Map<Bytes32, BeaconState> availableStates = new HashMap<>(knownStates);
    availableStates.put(rootBlockAndState.getRoot(), rootBlockAndState.getState());
    final StateCache stateCache = new StateCache(stateCacheSize, availableStates);

    final AsyncChainStateGenerator chainStateGenerator =
        AsyncChainStateGenerator.create(spec, blockTree, blockProvider, stateCache::get);
    return new StateGenerator(blockTree, chainStateGenerator, stateCache);
  }

  public SafeFuture<StateAndBlockSummary> regenerateStateForBlock(final Bytes32 blockRoot) {
    final int blockCount = blockTree.size() - 1;
    LOG.debug("Regenerate state for block {} by replaying {} blocks", blockRoot, blockCount);
    final long startTime = System.currentTimeMillis();

    return chainStateGenerator
        .generateTargetState(blockRoot)
        .thenPeek(
            result ->
                LOG.info(
                    "Completed regeneration of state for block {} at slot {} by replaying {} blocks. Took {}ms",
                    blockRoot,
                    result.getSlot(),
                    blockCount,
                    System.currentTimeMillis() - startTime));
  }
}

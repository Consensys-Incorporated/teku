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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.generator.ChainProperties;
import tech.pegasys.teku.storage.server.StateStorageMode;

public class PruningModeCombinedChainDataClientTest extends AbstractCombinedChainDataClientTest {

  @Override
  protected StateStorageMode getStorageMode() {
    return StateStorageMode.PRUNE;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getStateBySlotParameters")
  public <T> void queryBySlot_shouldReturnEmptyResponseForHistoricalState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(historicalBlock.getSlot()).isLessThan(finalizedBlock.getSlot());

    final UInt64 querySlot = historicalBlock.getSlot();
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.empty();
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void getBlockAndStateInEffectAtSlot_missingState() throws Exception {
    chainUpdater.initializeGenesis();

    final SignedBlockAndState targetBlock = chainBuilder.generateNextBlock();
    chainUpdater.saveBlock(targetBlock);

    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();
    // Sanity check
    assertThat(bestBlock.getSlot()).isGreaterThan(targetBlock.getSlot());
    // Finalize best block so that state is pruned
    final UInt64 finalizedEpoch =
        new ChainProperties(spec).computeBestEpochFinalizableAtSlot(bestBlock.getSlot());
    chainUpdater.finalizeEpoch(finalizedEpoch);

    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(targetBlock.getSlot());
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getCheckpointStateAtEpoch_historicalCheckpointWithSkippedBoundarySlot() {
    final UInt64 finalizedEpoch = UInt64.valueOf(3);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    final UInt64 nextEpoch = finalizedEpoch.plus(UInt64.ONE);

    chainUpdater.initializeGenesis();
    // Setup chain at epoch to be queried
    chainUpdater.advanceChain(finalizedSlot.minus(UInt64.ONE));
    chainUpdater.finalizeEpoch(finalizedEpoch);
    // Bury queried epoch behind another finalized epoch
    chainUpdater.advanceChain(spec.computeStartSlotAtEpoch(nextEpoch));
    chainUpdater.finalizeEpoch(nextEpoch);
    chainUpdater.addNewBestBlock();

    final SafeFuture<Optional<CheckpointState>> actual =
        client.getCheckpointStateAtEpoch(finalizedEpoch);
    assertThatSafeFuture(actual).isCompletedWithEmptyOptional();
  }
}

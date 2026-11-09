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

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.stategenerator.CheckpointStateGenerator;
import tech.pegasys.teku.storage.server.StateStorageMode;

public class ArchiveModeCombinedChainDataClientTest extends AbstractCombinedChainDataClientTest {
  @Override
  protected StateStorageMode getStorageMode() {
    return StateStorageMode.ARCHIVE;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveHistoricalState(
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
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(historicalBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveHistoricalStateInEffectAtSkippedSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    final UInt64 skippedSlot = historicalBlock.getSlot().plus(UInt64.ONE);
    chainUpdater.advanceChain(skippedSlot.plus(UInt64.ONE));
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(skippedSlot).isLessThan(finalizedBlock.getSlot());

    final UInt64 querySlot = skippedSlot;
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(historicalBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void getStateByStateRoot_shouldReturnFinalizedState()
      throws ExecutionException, InterruptedException {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain();
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    Optional<BeaconState> result =
        client.getStateByStateRoot(finalizedBlock.getState().hashTreeRoot()).get();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(finalizedBlock.getState());
  }

  @Test
  public void getStateByStateRoot_shouldReturnFinalizedStateAtSkippedSlot()
      throws ExecutionException, InterruptedException {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    final UInt64 skippedSlot = historicalBlock.getSlot().plus(UInt64.ONE);
    chainUpdater.advanceChain(skippedSlot.plus(UInt64.ONE));
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();
    final BeaconState skippedSlotState = client.getStateAtSlotExact(skippedSlot).join().get();

    // Sanity check
    assertThat(skippedSlot).isLessThan(finalizedBlock.getSlot());
    assertThat(skippedSlot).isEqualTo(skippedSlotState.getSlot());

    Optional<BeaconState> result =
        client.getStateByStateRoot(skippedSlotState.hashTreeRoot()).get();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(skippedSlotState);
  }

  @Test
  public void getCheckpointStateAtEpoch_historicalCheckpoint() {
    final UInt64 finalizedEpoch = UInt64.valueOf(3);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    // Setup chain at epoch to be queried
    final SignedBlockAndState checkpointBlockAndState = chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    final Checkpoint checkpoint = new Checkpoint(finalizedEpoch, checkpointBlockAndState.getRoot());
    final CheckpointState expected =
        CheckpointStateGenerator.generate(spec, checkpoint, checkpointBlockAndState);

    final SafeFuture<Optional<CheckpointState>> actual =
        client.getCheckpointStateAtEpoch(finalizedEpoch);
    assertThat(actual).isCompletedWithValue(Optional.of(expected));
  }

  @Test
  public void getCheckpointStateAtEpoch_historicalCheckpointWithSkippedBoundarySlot() {
    final UInt64 finalizedEpoch = UInt64.valueOf(3);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    final UInt64 nextEpoch = finalizedEpoch.plus(UInt64.ONE);

    chainUpdater.initializeGenesis();
    // Setup chain at epoch to be queried
    final SignedBlockAndState checkpointBlockAndState =
        chainUpdater.advanceChain(finalizedSlot.minus(UInt64.ONE));
    chainUpdater.finalizeEpoch(finalizedEpoch);
    // Bury queried epoch behind another finalized epoch
    chainUpdater.advanceChain(spec.computeStartSlotAtEpoch(nextEpoch));
    chainUpdater.finalizeEpoch(nextEpoch);
    chainUpdater.addNewBestBlock();

    final Checkpoint checkpoint = new Checkpoint(finalizedEpoch, checkpointBlockAndState.getRoot());
    final CheckpointState expected =
        CheckpointStateGenerator.generate(spec, checkpoint, checkpointBlockAndState);

    final SafeFuture<Optional<CheckpointState>> actual =
        client.getCheckpointStateAtEpoch(finalizedEpoch);
    assertThat(actual).isCompletedWithValue(Optional.of(expected));
  }
}

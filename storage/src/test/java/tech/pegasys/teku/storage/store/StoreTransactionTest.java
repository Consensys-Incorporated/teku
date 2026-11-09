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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class StoreTransactionTest extends AbstractStoreTest {

  @Test
  public void setTimeMillis_failsWhenValueIsOlderThanCurrentTime() {
    final UpdatableStore store = createGenesisStore();

    // Make sure time is non-zero
    setTime(store, store.getTimeInMillis().plus(10));

    final UInt64 invalidTime = store.getTimeInMillis().minus(1);
    assertThatThrownBy(() -> setTime(store, invalidTime))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            String.format(
                "Cannot revert time (millis) from %s to %s", store.getTimeInMillis(), invalidTime));
  }

  @Test
  public void setTimeMillis_doesNotOverwriteNewerValue() {
    final UpdatableStore store = createGenesisStore();

    // Make sure time is non-zero
    setTime(store, store.getTimeInMillis().plus(10));

    final StoreTransaction txA = store.startTransaction(storageUpdateChannel);
    final UInt64 timeA = store.getTimeInMillis().plus(1);

    final UInt64 timeB = timeA.plus(1);
    setTime(store, timeB);
    assertThat(store.getTimeInMillis()).isEqualTo(timeB);

    // Commit tx, time should not be updated
    assertThat(txA.commit()).isCompleted();
    assertThat(store.getTimeInMillis()).isEqualTo(timeB);
  }

  @Test
  public void setTimeMillis_updatesSeconds() {
    final UpdatableStore store = createGenesisStore();

    // given
    setTime(store, UInt64.valueOf(1300));

    // then seconds should be updated
    assertThat(store.getTimeSeconds()).isEqualTo(UInt64.ONE);

    // one more test
    setTime(store, UInt64.valueOf(3750));

    assertThat(store.getTimeSeconds()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  public void getLatestFinalized_fromUnderlyingStore() {
    final UpdatableStore store = createGenesisStore();
    final AnchorPoint expected = store.getLatestFinalized();

    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    assertThat(tx.getLatestFinalized()).isEqualTo(expected);
  }

  @Test
  public void getLatestFinalized_withNewFinalizedCheckpoint_blockInUnderlyingStore() {
    final UpdatableStore store = createGenesisStore();

    // Create some blocks that we can finalize
    final UInt64 epoch = UInt64.ONE;
    final SignedBlockAndState finalizedBlock =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(epoch));
    final Checkpoint finalizedCheckpoint = new Checkpoint(epoch, finalizedBlock.getRoot());

    // Save the finalized block
    addBlock(store, finalizedBlock);

    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.getLatestFinalized().getRoot()).isEqualTo(finalizedBlock.getRoot());
  }

  @Test
  public void getLatestFinalized_withNewFinalizedCheckpoint_blockAddedToTx() {
    final UpdatableStore store = createGenesisStore();

    // Create some blocks that we can finalize
    final UInt64 epoch = UInt64.ONE;
    final SignedBlockAndState finalizedBlock =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(epoch));
    final Checkpoint finalizedCheckpoint = new Checkpoint(epoch, finalizedBlock.getRoot());

    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(finalizedBlock, spec.calculateBlockCheckpoints(finalizedBlock.getState()));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.getLatestFinalized().getRoot()).isEqualTo(finalizedBlock.getRoot());
  }

  @Test
  public void retrieveSignedBlock_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          final SignedBeaconBlock expectedBlock = blockAndState.getBlock();
          SafeFuture<Optional<SignedBeaconBlock>> result = tx.retrieveSignedBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage("Expected block %s to be available", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveSignedBlock_fromTx() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));

    SafeFuture<Optional<SignedBeaconBlock>> result =
        tx.retrieveSignedBlock(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getBlock()));
  }

  @Test
  public void retrieveBlock_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          final BeaconBlock expectedBlock = blockAndState.getBlock().getMessage();
          SafeFuture<Optional<BeaconBlock>> result = tx.retrieveBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage("Expected block %s to be available", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveBlock_fromTx() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));

    SafeFuture<Optional<BeaconBlock>> result = tx.retrieveBlock(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getBlock().getMessage()));
  }

  @Test
  public void retrieveBlockAndState_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<SignedBlockAndState>> result = tx.retrieveBlockAndState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage(
                  "Expected block and state at %s to be available", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState));
        });
  }

  @Test
  public void retrieveBlockAndState_fromTx() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));

    SafeFuture<Optional<SignedBlockAndState>> result =
        tx.retrieveBlockAndState(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState));
  }

  @Test
  public void retrieveBlockState_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<BeaconState>> result = tx.retrieveBlockState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage("Expected state at %s to be available", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState.getState()));
        });
  }

  @Test
  public void retrieveBlockState_fromTx() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));

    SafeFuture<Optional<BeaconState>> result = tx.retrieveBlockState(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getState()));
  }

  @Test
  public void retrieveCheckpointState_fromUnderlyingStore_withLimitedCache() {
    processCheckpointsWithLimitedCache(
        (store, checkpointState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          SafeFuture<Optional<BeaconState>> result =
              tx.retrieveCheckpointState(checkpointState.getCheckpoint());
          assertThat(result)
              .withFailMessage(
                  "Expected checkpoint state for checkpoint %s", checkpointState.getCheckpoint())
              .isCompletedWithValue(Optional.of(checkpointState.getState()));
        });
  }

  @Test
  public void getCheckpointState_fromBlockInTx() {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch = UInt64.ONE;
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(epochStartSlot);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    final UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));

    final SafeFuture<Optional<BeaconState>> result = tx.retrieveCheckpointState(checkpoint);
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getState()));
  }

  @Test
  public void retrieveFinalizedCheckpointAndState_finalizedBlockInMemory() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    tx.putBlockAndState(
        finalizedBlockAndState, spec.calculateBlockCheckpoints(finalizedBlockAndState.getState()));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    final SafeFuture<CheckpointState> result = tx.retrieveFinalizedCheckpointAndState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getCheckpoint()).isEqualTo(finalizedCheckpoint);
    assertThat(safeJoin(result).getRoot()).isEqualTo(finalizedBlockAndState.getRoot());
    assertThat(safeJoin(result).getState()).isNotEqualTo(finalizedBlockAndState.getState());
    assertThat(safeJoin(result).getState().getSlot())
        .isEqualTo(finalizedBlockAndState.getSlot().plus(1));
  }

  @Test
  public void retrieveFinalizedCheckpointAndState_finalizedBlockInStore() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final StoreTransaction blockTx = store.startTransaction(new StubStorageUpdateChannel());
    blockTx.putBlockAndState(
        finalizedBlockAndState, spec.calculateBlockCheckpoints(finalizedBlockAndState.getState()));
    assertThat(blockTx.commit()).isCompleted();

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    final SafeFuture<CheckpointState> result = tx.retrieveFinalizedCheckpointAndState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getCheckpoint()).isEqualTo(finalizedCheckpoint);
    assertThat(safeJoin(result).getRoot()).isEqualTo(finalizedBlockAndState.getRoot());
    assertThat(safeJoin(result).getState()).isNotEqualTo(finalizedBlockAndState.getState());
    assertThat(safeJoin(result).getState().getSlot())
        .isEqualTo(finalizedBlockAndState.getSlot().plus(1));
  }

  @Test
  public void retrieveFinalizedCheckpointAndState_pullFromStore() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final StoreTransaction finalizingTx = store.startTransaction(new StubStorageUpdateChannel());
    finalizingTx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    finalizingTx.putBlockAndState(
        finalizedBlockAndState, spec.calculateBlockCheckpoints(finalizedBlockAndState.getState()));
    assertThat(finalizingTx.commit()).isCompleted();

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());

    final SafeFuture<CheckpointState> result = tx.retrieveFinalizedCheckpointAndState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getCheckpoint()).isEqualTo(finalizedCheckpoint);
    assertThat(safeJoin(result).getRoot()).isEqualTo(finalizedBlockAndState.getRoot());
    assertThat(safeJoin(result).getState()).isNotEqualTo(finalizedBlockAndState.getState());
    assertThat(safeJoin(result).getState().getSlot())
        .isEqualTo(finalizedBlockAndState.getSlot().plus(1));
  }

  @Test
  public void retrieveFinalizedCheckpointAndState_finalizedCheckpointPruned() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final SignedBlockAndState newerFinalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) * 2L);
    final Checkpoint newerFinalizedCheckpoint =
        new Checkpoint(UInt64.valueOf(2), newerFinalizedBlockAndState.getRoot());

    // Save blocks
    final StoreTransaction blockTx = store.startTransaction(new StubStorageUpdateChannel());
    blockTx.putBlockAndState(
        finalizedBlockAndState, spec.calculateBlockCheckpoints(finalizedBlockAndState.getState()));
    blockTx.putBlockAndState(
        newerFinalizedBlockAndState,
        spec.calculateBlockCheckpoints(newerFinalizedBlockAndState.getState()));
    assertThat(blockTx.commit()).isCompleted();

    // Start tx finalizing epoch 1
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    // Finalize epoch 2
    final StoreTransaction otherTx = store.startTransaction(new StubStorageUpdateChannel());
    otherTx.putBlockAndState(
        newerFinalizedBlockAndState,
        spec.calculateBlockCheckpoints(newerFinalizedBlockAndState.getState()));
    otherTx.setFinalizedCheckpoint(newerFinalizedCheckpoint, false);
    assertThat(otherTx.commit()).isCompleted();

    // Check response from tx1 for finalized value
    final SafeFuture<CheckpointState> result = tx.retrieveFinalizedCheckpointAndState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getCheckpoint()).isEqualTo(newerFinalizedCheckpoint);
    assertThat(safeJoin(result).getRoot()).isEqualTo(newerFinalizedBlockAndState.getRoot());
    assertThat(safeJoin(result).getState()).isEqualTo(newerFinalizedBlockAndState.getState());
  }

  @Test
  public void getOrderedBlockRoots_withNewBlocks() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesis = chainBuilder.getBlockAndStateAtSlot(GENESIS_SLOT);

    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState forkBlock2 = fork.generateBlockAtSlot(2);

    final SignedBlockAndState mainChainBlock1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState mainChainBlock3 = chainBuilder.generateBlockAtSlot(3);
    final SignedBlockAndState mainChainBlock4 = chainBuilder.generateBlockAtSlot(4);

    addBlocks(store, List.of(mainChainBlock1, mainChainBlock3, mainChainBlock4));
    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);

    // Initially we should get existing block hashes
    assertThat(tx.getOrderedBlockRoots())
        .containsExactly(
            genesis.getRoot(),
            mainChainBlock1.getRoot(),
            mainChainBlock3.getRoot(),
            mainChainBlock4.getRoot());

    // Added block should be included
    tx.putBlockAndState(forkBlock2, spec.calculateBlockCheckpoints(forkBlock2.getState()));

    // Children are ordered by slot
    assertThat(tx.getOrderedBlockRoots())
        .containsExactly(
            genesis.getRoot(),
            mainChainBlock1.getRoot(),
            forkBlock2.getRoot(),
            mainChainBlock3.getRoot(),
            mainChainBlock4.getRoot());
  }

  private void setTime(final UpdatableStore store, final UInt64 newTime) {
    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.setTimeMillis(newTime);
    assertThat(tx.commit()).isCompleted();
  }
}

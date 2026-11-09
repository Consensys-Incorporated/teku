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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;

class StoreTransactionUpdatesFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @Test
  void hotBlocksShouldNotContainPrunedRoots() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);

    final UpdatableStore store =
        StoreBuilder.create()
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(roots -> SafeFuture.completedFuture(Collections.emptyMap()))
            .earliestBlobSidecarSlotProvider(EarliestBlobSidecarSlotProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .anchor(Optional.empty())
            .genesisTime(genesis.getState().getGenesisTime())
            .time(genesis.getState().getGenesisTime())
            .latestFinalized(AnchorPoint.create(spec, genesisCheckpoint, genesis))
            .justifiedCheckpoint(genesisCheckpoint)
            .bestJustifiedCheckpoint(genesisCheckpoint)
            .blockInformation(
                Map.of(
                    genesis.getRoot(),
                    new StoredBlockMetadata(
                        genesis.getSlot(),
                        genesis.getRoot(),
                        genesis.getParentRoot(),
                        genesis.getStateRoot(),
                        genesis.getExecutionBlockNumber(),
                        genesis.getExecutionBlockHash(),
                        Optional.of(spec.calculateBlockCheckpoints(genesis.getState())))))
            .storeConfig(StoreConfig.createDefault())
            .votes(Collections.emptyMap())
            .latestCanonicalBlockRoot(Optional.empty())
            .build();

    // Generate blocks for multiple epochs to trigger finalization
    final UInt64 epoch3Slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);

    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    // Start a transaction and add all blocks
    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));

    // Set finalized checkpoint to epoch 2
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(2));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    tx.commit().join();

    // Verify we captured a storage update
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    final StorageUpdate storageUpdate = captor.getValue();

    // Get the finalized block
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.getBlockAndState(finalizedCheckpoint.getRoot()).orElseThrow();

    // Collect all block roots that should have been pruned (blocks at or before finalized slot,
    // except the finalized block itself)
    final UInt64 finalizedSlot = finalizedBlockAndState.getSlot();
    final List<Bytes32> rootsThatShouldBePruned =
        chainBuilder
            .streamBlocksAndStates(1, finalizedSlot.longValue())
            .map(SignedBlockAndState::getRoot)
            .filter(root -> !root.equals(finalizedBlockAndState.getRoot()))
            .toList();

    // Verify that the pruned roots are really pruned
    assertThat(rootsThatShouldBePruned).isNotEmpty();
    for (final Bytes32 prunedRoot : rootsThatShouldBePruned) {
      assertThat(storageUpdate.getHotBlocks())
          .describedAs("Hot blocks should not contain pruned root %s", prunedRoot)
          .doesNotContainKey(prunedRoot);
    }

    // Also verify the pruned roots are actually recorded in the deleted hot blocks
    assertThat(storageUpdate.getDeletedHotBlocks().keySet()).containsAll(rootsThatShouldBePruned);
  }
}

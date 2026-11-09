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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;

public class StoreTransactionGloasTest extends AbstractStoreTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalGloas();
  }

  @Override
  protected List<BLSKeyPair> getValidatorKeys() {
    return new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 8);
  }

  @Test
  public void commit_shouldAddBlindedExecutionPayloadsToStorageUpdate() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadEnvelope executionPayload =
        chainBuilder.getExecutionPayloadAtSlot(blockAndState.getSlot()).orElseThrow();
    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    tx.putExecutionPayload(executionPayload, false);

    assertThat(tx.commit()).isCompleted();
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    assertThat(captor.getValue().getBlindedExecutionPayloads())
        .containsEntry(blockAndState.getRoot(), executionPayload.blind(spec));
  }

  @Test
  public void commit_shouldAddAllBlindedExecutionPayloadsToStorageUpdate() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState firstBlockAndState = chainBuilder.generateNextBlock();
    final SignedBlockAndState secondBlockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadEnvelope firstExecutionPayload =
        chainBuilder.getExecutionPayloadAtSlot(firstBlockAndState.getSlot()).orElseThrow();
    final SignedExecutionPayloadEnvelope secondExecutionPayload =
        chainBuilder.getExecutionPayloadAtSlot(secondBlockAndState.getSlot()).orElseThrow();
    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);
    tx.putBlockAndState(
        firstBlockAndState, spec.calculateBlockCheckpoints(firstBlockAndState.getState()));
    tx.putExecutionPayload(firstExecutionPayload, false);
    tx.putBlockAndState(
        secondBlockAndState, spec.calculateBlockCheckpoints(secondBlockAndState.getState()));
    tx.putExecutionPayload(secondExecutionPayload, false);

    assertThat(tx.commit()).isCompleted();
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    assertThat(captor.getValue().getBlindedExecutionPayloads())
        .containsOnlyKeys(firstBlockAndState.getRoot(), secondBlockAndState.getRoot());
  }

  @Test
  public void commit_shouldRetainBlindedEnvelopesForBlocksFinalizedInSameTransaction() {
    final List<BLSKeyPair> keys = BLSKeyGenerator.generateKeyPairs(16);
    final ChainBuilder gloasChainBuilder = ChainBuilder.create(spec, keys);
    final SignedBlockAndState genesis = gloasChainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = gloasChainBuilder.getCurrentCheckpointForEpoch(0);

    final UpdatableStore store =
        StoreBuilder.create()
            .asyncRunner(SyncAsyncRunner.SYNC_RUNNER)
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

    // Generate blocks spanning 2 epochs so we can finalize epoch 1
    final UInt64 epoch2Slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(2));
    gloasChainBuilder.generateBlocksUpToSlot(epoch2Slot);

    final StorageUpdateChannel channel = mock(StorageUpdateChannel.class);
    when(channel.onStorageUpdate(any())).thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final UpdatableStore.StoreTransaction tx = store.startTransaction(channel);

    // Add all blocks and their execution payloads in the same transaction
    gloasChainBuilder
        .streamBlocksAndStates(1, gloasChainBuilder.getLatestSlot().longValue())
        .forEach(
            blockAndState -> {
              tx.putBlockAndState(
                  blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
              gloasChainBuilder
                  .getExecutionPayloadAtSlot(blockAndState.getSlot())
                  .ifPresent(payload -> tx.putExecutionPayload(payload, false));
            });

    // Finalize at epoch 1 — blocks before this checkpoint will be pruned from hot
    final Checkpoint finalizedCheckpoint =
        gloasChainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(1));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);

    assertThat(tx.commit()).isCompleted();
    final ArgumentCaptor<StorageUpdate> captor = ArgumentCaptor.forClass(StorageUpdate.class);
    verify(channel).onStorageUpdate(captor.capture());
    final StorageUpdate storageUpdate = captor.getValue();

    // Finalized blocks were pruned from hot, but their envelopes must still be present
    final SignedBlockAndState finalizedBlockAndState =
        gloasChainBuilder.getBlockAndState(finalizedCheckpoint.getRoot()).orElseThrow();
    gloasChainBuilder
        .streamBlocksAndStates(1, finalizedBlockAndState.getSlot().longValue())
        .forEach(
            blockAndState ->
                assertThat(storageUpdate.getBlindedExecutionPayloads())
                    .describedAs(
                        "Envelope for finalized block at slot %s should be present",
                        blockAndState.getSlot())
                    .containsKey(blockAndState.getRoot()));
  }

  @Test
  public void retrieveSignedExecutionPayload_fromHotStore() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadEnvelope executionPayload =
        chainBuilder.getExecutionPayloadAtSlot(blockAndState.getSlot()).orElseThrow();

    final UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    tx.putExecutionPayload(executionPayload, false);
    assertThat(tx.commit()).isCompleted();

    assertThat(store.retrieveSignedExecutionPayload(blockAndState.getRoot()))
        .isCompletedWithValue(Optional.of(executionPayload));
  }

  @Test
  public void retrieveSignedExecutionPayload_fromExternalProvider() {
    final StoreBuilder storeBuilder = createStoreBuilder(defaultStoreConfig);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadEnvelope envelope =
        chainBuilder.getExecutionPayloadAtSlot(blockAndState.getSlot()).orElseThrow();
    final Bytes32 blockRoot = envelope.getBeaconBlockRoot();

    final UpdatableStore store =
        storeBuilder
            .executionPayloadProvider(
                roots ->
                    SafeFuture.completedFuture(
                        roots.contains(blockRoot) ? Map.of(blockRoot, envelope) : Map.of()))
            .build();

    // Load the block into forkChoiceStrategy so containsBlock(blockRoot) returns true, but
    // skip putExecutionPayload so retrieval must fall through to the external provider.
    final UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    assertThat(tx.commit()).isCompleted();

    assertThat(store.retrieveSignedExecutionPayload(blockRoot))
        .isCompletedWithValue(Optional.of(envelope));
  }

  @Test
  public void retrieveSignedExecutionPayload_returnsEmptyWhenNotFound() {
    final UpdatableStore store = createGenesisStore();

    assertThat(store.retrieveSignedExecutionPayload(dataStructureUtil.randomBytes32()))
        .isCompletedWithValue(Optional.empty());
  }
}

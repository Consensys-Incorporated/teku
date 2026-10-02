/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.FinalizedChainData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.protoarray.ExecutionPayloadUpdate;

class StoreTransactionUpdates {
  private final StoreTransaction tx;

  private final Optional<FinalizedChainData> finalizedChainData;
  private final Map<Bytes32, BlockAndCheckpoints> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> hotBlockAndStates;
  // A subset of hot states to be persisted to disk
  private final Map<Bytes32, BeaconState> hotStatesToPersist;
  private final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars;
  private final Optional<UInt64> maybeEarliestBlobSidecarSlot;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Map<Bytes32, UInt64> prunedHotBlockRoots;
  private final Optional<Bytes32> maybeUnsatisfiedInclusionListBlockRoot;
  private final boolean optimisticTransitionBlockRootSet;
  private final Optional<Bytes32> optimisticTransitionBlockRoot;
  private final Optional<Bytes32> latestCanonicalBlockRoot;
  private final Optional<UInt64> custodyGroupCount;
  private final boolean blobSidecarsEnabled;
  private final boolean dataColumnSidecarsEnabled;
  private final boolean executionPayloadEnvelopesEnabled;
  private final Map<Bytes32, ExecutionPayloadUpdate> hotExecutionPayloads;
  private final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads;

  StoreTransactionUpdates(
      final StoreTransaction tx,
      final Optional<FinalizedChainData> finalizedChainData,
      final Map<Bytes32, BlockAndCheckpoints> hotBlocks,
      final Map<Bytes32, SignedBlockAndState> hotBlockAndStates,
      final Map<Bytes32, BeaconState> hotStatesToPersist,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot,
      final Map<Bytes32, UInt64> prunedHotBlockRoots,
      final Map<Bytes32, SlotAndBlockRoot> stateRoots,
      final Optional<Bytes32> maybeUnsatisfiedInclusionListBlockRoot,
      final boolean optimisticTransitionBlockRootSet,
      final Optional<Bytes32> optimisticTransitionBlockRoot,
      final Optional<Bytes32> latestCanonicalBlockRoot,
      final Optional<UInt64> custodyGroupCount,
      final boolean blobSidecarsEnabled,
      final boolean dataColumnSidecarsEnabled,
      final boolean executionPayloadEnvelopesEnabled,
      final Map<Bytes32, ExecutionPayloadUpdate> hotExecutionPayloads,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads) {
    checkNotNull(tx, "Transaction is required");
    checkNotNull(finalizedChainData, "Finalized data is required");
    checkNotNull(hotBlocks, "Hot blocks are required");
    checkNotNull(hotBlockAndStates, "Hot block states are required");
    checkNotNull(hotStatesToPersist, "Hot states to persist are required");
    checkNotNull(blobSidecars, "BlobSidecars are required");
    checkNotNull(maybeEarliestBlobSidecarSlot, "Hot maybe earliest blobSidecar slot is required");
    checkNotNull(prunedHotBlockRoots, "Pruned roots are required");
    checkNotNull(stateRoots, "State roots are required");
    checkNotNull(
        maybeUnsatisfiedInclusionListBlockRoot, "Unsatisfied inclusion lists are required");
    checkNotNull(optimisticTransitionBlockRoot, "Optimistic transition block root is required");
    checkNotNull(latestCanonicalBlockRoot, "Latest canonical block root is required");
    checkNotNull(custodyGroupCount, "Current custody group count is required");
    checkNotNull(hotExecutionPayloads, "Hot execution payloads are required");
    checkNotNull(blindedExecutionPayloads, "Blinded execution payloads are required");

    this.tx = tx;
    this.finalizedChainData = finalizedChainData;
    this.hotBlocks = hotBlocks;
    this.hotBlockAndStates = hotBlockAndStates;
    this.hotStatesToPersist = hotStatesToPersist;
    this.blobSidecars = blobSidecars;
    this.maybeEarliestBlobSidecarSlot = maybeEarliestBlobSidecarSlot;
    this.prunedHotBlockRoots = prunedHotBlockRoots;
    this.stateRoots = stateRoots;
    this.maybeUnsatisfiedInclusionListBlockRoot = maybeUnsatisfiedInclusionListBlockRoot;
    this.optimisticTransitionBlockRootSet = optimisticTransitionBlockRootSet;
    this.optimisticTransitionBlockRoot = optimisticTransitionBlockRoot;
    this.latestCanonicalBlockRoot = latestCanonicalBlockRoot;
    this.custodyGroupCount = custodyGroupCount;
    this.blobSidecarsEnabled = blobSidecarsEnabled;
    this.dataColumnSidecarsEnabled = dataColumnSidecarsEnabled;
    this.executionPayloadEnvelopesEnabled = executionPayloadEnvelopesEnabled;
    this.hotExecutionPayloads = hotExecutionPayloads;
    this.blindedExecutionPayloads = blindedExecutionPayloads;
  }

  public StorageUpdate createStorageUpdate() {
    return new StorageUpdate(
        tx.genesisTime,
        finalizedChainData,
        tx.justifiedCheckpoint,
        tx.bestJustifiedCheckpoint,
        hotBlocks,
        hotStatesToPersist,
        blindedExecutionPayloads,
        maybeUnsatisfiedInclusionListBlockRoot,
        blobSidecars,
        maybeEarliestBlobSidecarSlot,
        prunedHotBlockRoots,
        stateRoots,
        optimisticTransitionBlockRootSet,
        optimisticTransitionBlockRoot,
        latestCanonicalBlockRoot,
        custodyGroupCount,
        blobSidecarsEnabled,
        dataColumnSidecarsEnabled,
        executionPayloadEnvelopesEnabled);
  }

  public void applyToStore(final Store store, final UpdateResult updateResult) {
    // Add new data
    tx.timeMillis.ifPresent(store::cacheTimeMillis);
    tx.genesisTime.ifPresent(store::cacheGenesisTime);
    tx.justifiedCheckpoint.ifPresent(store::updateJustifiedCheckpoint);
    tx.bestJustifiedCheckpoint.ifPresent(store::updateBestJustifiedCheckpoint);
    tx.getCustodyGroupCount().ifPresent(store::updateCustodyGroupCount);
    store.cacheBlocks(hotBlocks.values());
    store.cacheBlockStates(Maps.transformValues(hotBlockAndStates, this::blockAndStateAsSummary));
    store.cacheBlobSidecars(blobSidecars);
    maybeUnsatisfiedInclusionListBlockRoot.ifPresent(store::cacheUnsatisfiedInclusionListBlock);
    if (optimisticTransitionBlockRootSet) {
      store.cacheFinalizedOptimisticTransitionPayload(
          updateResult.getFinalizedOptimisticTransitionPayload());
    }

    // Update finalized data
    finalizedChainData.ifPresent(
        finalizedData -> store.updateFinalizedAnchor(finalizedData.getLatestFinalized()));

    // Prune blocks, execution payloads and states
    prunedHotBlockRoots
        .keySet()
        .forEach(
            blockRoot -> {
              store.removeBlockAndState(blockRoot);
              store.removeExecutionPayload(blockRoot);
            });

    store.cleanupCheckpointStates(
        slotAndBlockRoot -> prunedHotBlockRoots.containsKey(slotAndBlockRoot.getBlockRoot()));

    if (tx.proposerBoostRootSet) {
      store.cacheProposerBoostRoot(tx.proposerBoostRoot);
    }

    store.cacheExecutionPayloads(
        Maps.transformValues(hotExecutionPayloads, ExecutionPayloadUpdate::executionPayload));

    final Optional<BlockAndCheckpoints> finalizedExecutionPayloadBoundaryBlock =
        finalizedChainData.flatMap(FinalizedChainData::getFinalizedExecutionPayloadBoundaryBlock);
    store
        .getForkChoiceStrategy()
        .applyUpdate(
            hotBlocks.values(),
            hotExecutionPayloads,
            tx.pulledUpBlockCheckpoints,
            prunedHotBlockRoots,
            store.getFinalizedCheckpoint(),
            finalizedExecutionPayloadBoundaryBlock);
  }

  private StateAndBlockSummary blockAndStateAsSummary(final SignedBlockAndState blockAndState) {
    return blockAndState;
  }
}

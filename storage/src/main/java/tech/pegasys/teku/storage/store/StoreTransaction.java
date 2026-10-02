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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.dataproviders.generators.StateAtSlotTask.AsyncStateProvider.fromBlockAndState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.generators.StateAtSlotTask;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteSnapshot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.protoarray.ExecutionPayloadUpdate;

class StoreTransaction implements UpdatableStore.StoreTransaction {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Store store;
  private final ReadWriteLock lock;
  private final StorageUpdateChannel storageUpdateChannel;

  Optional<UInt64> timeMillis = Optional.empty();
  Optional<UInt64> genesisTime = Optional.empty();
  Optional<Checkpoint> justifiedCheckpoint = Optional.empty();
  Optional<Checkpoint> finalizedCheckpoint = Optional.empty();
  boolean finalizedCheckpointOptimistic = false;
  Optional<Checkpoint> bestJustifiedCheckpoint = Optional.empty();
  Optional<Bytes32> proposerBoostRoot = Optional.empty();
  boolean proposerBoostRootSet = false;
  boolean clearFinalizedOptimisticTransitionPayload = false;
  Map<Bytes32, SlotAndBlockRoot> stateRoots = new HashMap<>();
  Set<Bytes32> pulledUpBlockCheckpoints = new HashSet<>();
  Map<Bytes32, TransactionBlockData> blockData = new HashMap<>();
  Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars = new HashMap<>();
  Optional<Bytes32> maybeUnsatisfiedInclusionListBlockRoot = Optional.empty();
  Optional<UInt64> maybeEarliestBlobSidecarTransactionSlot = Optional.empty();
  Optional<Bytes32> maybeLatestCanonicalBlockRoot = Optional.empty();
  Optional<UInt64> maybeCustodyGroupCount = Optional.empty();
  Map<Bytes32, ExecutionPayloadUpdate> executionPayloadData = new HashMap<>();

  private final UpdatableStore.StoreUpdateHandler updateHandler;

  StoreTransaction(
      final Spec spec,
      final Store store,
      final ReadWriteLock lock,
      final StorageUpdateChannel storageUpdateChannel,
      final UpdatableStore.StoreUpdateHandler updateHandler) {
    this.spec = spec;
    this.store = store;
    this.lock = lock;
    this.storageUpdateChannel = storageUpdateChannel;
    this.updateHandler = updateHandler;
  }

  @Override
  public void putBlockAndState(
      final SignedBeaconBlock block,
      final BeaconState state,
      final BlockCheckpoints blockCheckpoints,
      final Optional<List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    blockData.put(block.getRoot(), new TransactionBlockData(block, state, blockCheckpoints));
    blobSidecars.ifPresent(
        sidecars -> this.blobSidecars.put(block.getSlotAndBlockRoot(), sidecars));
    if (needToUpdateEarliestBlobSidecarSlot(maybeEarliestBlobSidecarSlot)) {
      this.maybeEarliestBlobSidecarTransactionSlot = maybeEarliestBlobSidecarSlot;
    }
    putStateRoot(state.hashTreeRoot(), block.getSlotAndBlockRoot());
  }

  @Override
  public void putExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    executionPayloadData.put(
        executionPayload.getBeaconBlockRoot(),
        new ExecutionPayloadUpdate(executionPayload, executionOptimistic));
  }

  @Override
  public void putUnsatisfiedInclusionListBlock(final Bytes32 blockRoot) {
    this.maybeUnsatisfiedInclusionListBlockRoot = Optional.of(blockRoot);
  }

  private boolean needToUpdateEarliestBlobSidecarSlot(
      final Optional<UInt64> maybeNewEarliestBlobSidecarSlot) {
    // New value is absent - false
    if (maybeNewEarliestBlobSidecarSlot.isEmpty()) {
      return false;
    }
    // New value is present, old is absent - true
    if (maybeEarliestBlobSidecarTransactionSlot.isEmpty()) {
      return true;
    }
    // New value is smaller than an old one - true
    final UInt64 newEarliestBlobSidecarSlot = maybeNewEarliestBlobSidecarSlot.get();
    return newEarliestBlobSidecarSlot.isLessThan(maybeEarliestBlobSidecarTransactionSlot.get());
  }

  @Override
  public void putStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
    stateRoots.put(stateRoot, slotAndBlockRoot);
  }

  @Override
  public void pullUpBlockCheckpoints(final Bytes32 blockRoot) {
    pulledUpBlockCheckpoints.add(blockRoot);
  }

  @Override
  public void setTimeMillis(final UInt64 timeMillis) {
    final UInt64 storeTimeMillis = store.getTimeInMillis();
    checkArgument(
        timeMillis.isGreaterThanOrEqualTo(storeTimeMillis),
        "Cannot revert time (millis) from %s to %s",
        storeTimeMillis,
        timeMillis);
    this.timeMillis = Optional.of(timeMillis);
  }

  @Override
  public void setGenesisTime(final UInt64 genesisTime) {
    this.genesisTime = Optional.of(genesisTime);
  }

  @Override
  public void setJustifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    this.justifiedCheckpoint = Optional.of(justifiedCheckpoint);
  }

  @Override
  public void setFinalizedCheckpoint(
      final Checkpoint finalizedCheckpoint, final boolean fromOptimisticBlock) {
    this.finalizedCheckpoint = Optional.of(finalizedCheckpoint);
    this.finalizedCheckpointOptimistic = fromOptimisticBlock;
  }

  @Override
  public void setBestJustifiedCheckpoint(final Checkpoint bestJustifiedCheckpoint) {
    this.bestJustifiedCheckpoint = Optional.of(bestJustifiedCheckpoint);
  }

  @Override
  public void setProposerBoostRoot(final Bytes32 boostedBlockRoot) {
    proposerBoostRoot = Optional.of(boostedBlockRoot);
    proposerBoostRootSet = true;
  }

  @Override
  public void setLatestCanonicalBlockRoot(final Bytes32 latestCanonicalBlockRoot) {
    maybeLatestCanonicalBlockRoot = Optional.of(latestCanonicalBlockRoot);
  }

  @Override
  public void setCustodyGroupCount(final UInt64 custodyGroupCount) {
    maybeCustodyGroupCount = Optional.of(custodyGroupCount);
  }

  @Override
  public void removeProposerBoostRoot() {
    proposerBoostRoot = Optional.empty();
    proposerBoostRootSet = true;
  }

  @Override
  public void removeFinalizedOptimisticTransitionPayload() {
    this.clearFinalizedOptimisticTransitionPayload = true;
  }

  @CheckReturnValue
  @Override
  public SafeFuture<Void> commit() {
    return retrieveLatestFinalized()
        .thenCompose(
            latestFinalized -> {
              final StoreTransactionUpdates updates;
              // Lock so that we have a consistent view while calculating our updates
              final Lock writeLock = lock.writeLock();
              writeLock.lock();
              try {
                updates = StoreTransactionUpdatesFactory.create(spec, store, this, latestFinalized);
              } finally {
                writeLock.unlock();
              }

              final StorageUpdate storageUpdate = updates.createStorageUpdate();
              final SafeFuture<UpdateResult> storageResult =
                  storageUpdate.isEmpty()
                      ? UpdateResult.EMPTY_FUTURE
                      : storageUpdateChannel.onStorageUpdate(storageUpdate);

              if (!storageUpdate.isFinalizedOptimisticTransitionBlockRootSet()) {
                storageResult.finishError(LOG);
                applyToStore(updates, UpdateResult.EMPTY);
                return SafeFuture.COMPLETE;
              } else {
                return storageResult.thenAccept(
                    updateResult -> applyToStore(updates, updateResult));
              }
            });
  }

  private void applyToStore(
      final StoreTransactionUpdates updates, final UpdateResult updateResult) {
    final Lock writeLock = lock.writeLock();
    // Propagate changes to Store
    writeLock.lock();
    try {
      // Add new data
      updates.applyToStore(store, updateResult);
    } finally {
      writeLock.unlock();
    }

    // Signal back changes to the handler
    finalizedCheckpoint.ifPresent(
        checkpoint ->
            updateHandler.onNewFinalizedCheckpoint(checkpoint, finalizedCheckpointOptimistic));
  }

  @Override
  public void commit(final Runnable onSuccess, final String errorMessage) {
    commit(onSuccess, err -> LOG.error(errorMessage, err));
  }

  @Override
  public UInt64 getTimeInMillis() {
    return timeMillis.orElseGet(store::getTimeInMillis);
  }

  @Override
  public UInt64 getGenesisTime() {
    return genesisTime.orElseGet(store::getGenesisTime);
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return store.getInitialCheckpoint();
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint.orElseGet(store::getJustifiedCheckpoint);
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint.orElseGet(store::getFinalizedCheckpoint);
  }

  @Override
  public Optional<UInt64> getCustodyGroupCount() {
    return maybeCustodyGroupCount;
  }

  @Override
  public VoteTracker getVote(final UInt64 validatorIndex) {
    return store.getVote(validatorIndex);
  }

  /**
   * Intentionally delegates to the committed store: uncommitted vote changes staged in this
   * transaction are excluded so the fast confirmation rule sees only committed vote state.
   */
  @Override
  public VoteSnapshot getVoteSnapshot() {
    return store.getVoteSnapshot();
  }

  @Override
  public AnchorPoint getLatestFinalized() {
    if (finalizedCheckpoint.isPresent()) {
      // Ideally we wouldn't join here - but seems not worth making this API async since we're
      // unlikely to call this on tx objects
      final SignedBlockAndState finalizedBlockAndState =
          retrieveBlockAndState(finalizedCheckpoint.get().getRoot()).join().orElseThrow();
      return AnchorPoint.create(spec, finalizedCheckpoint.get(), finalizedBlockAndState);
    }
    return store.getLatestFinalized();
  }

  @Override
  public Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload() {
    return store.getFinalizedOptimisticTransitionPayload();
  }

  private SafeFuture<AnchorPoint> retrieveLatestFinalized() {
    if (finalizedCheckpoint.isPresent()) {
      final Checkpoint finalizedCheckpoint = this.finalizedCheckpoint.get();
      return retrieveBlockAndState(this.finalizedCheckpoint.get().getRoot())
          .thenApply(
              blockAndState ->
                  AnchorPoint.create(
                      spec,
                      finalizedCheckpoint,
                      blockAndState.orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Missing latest finalized block and state"))));
    }
    return SafeFuture.completedFuture(store.getLatestFinalized());
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    return getLatestFinalized().getBlockSlot();
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint.orElseGet(store::getBestJustifiedCheckpoint);
  }

  @Override
  public Optional<Bytes32> getProposerBoostRoot() {
    return proposerBoostRootSet ? proposerBoostRoot : store.getProposerBoostRoot();
  }

  @Override
  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return store.getForkChoiceStrategy();
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {
    return blockData.containsKey(blockRoot) || store.containsBlock(blockRoot);
  }

  @Override
  public Collection<Bytes32> getOrderedBlockRoots() {
    if (this.blockData.isEmpty()) {
      return store.getOrderedBlockRoots();
    }

    lock.readLock().lock();
    try {
      final NavigableMap<UInt64, Bytes32> blockRootsBySlot = new TreeMap<>();
      store
          .getForkChoiceStrategy()
          .processAllBeaconBlocksInOrder((root, slot, parent) -> blockRootsBySlot.put(slot, root));
      this.blockData
          .values()
          .forEach(
              blockAndState ->
                  blockRootsBySlot.put(blockAndState.getSlot(), blockAndState.getRoot()));
      return blockRootsBySlot.values();
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockData.get(blockRoot))
        .map(SignedBlockAndState::getState)
        .or(() -> store.getBlockStateIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(final Bytes32 blockRoot) {
    if (blockData.containsKey(blockRoot)) {
      return SafeFuture.completedFuture(
          Optional.of(blockData.get(blockRoot)).map(SignedBlockAndState::getBlock));
    }
    return store.retrieveSignedBlock(blockRoot);
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(final Bytes32 blockRoot) {
    if (blockData.containsKey(blockRoot)) {
      final TransactionBlockData result = blockData.get(blockRoot);
      return SafeFuture.completedFuture(Optional.of(result.toSignedBlockAndState()));
    }
    return store.retrieveBlockAndState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(
      final Bytes32 blockRoot) {
    if (blockData.containsKey(blockRoot)) {
      final TransactionBlockData result = blockData.get(blockRoot);
      return SafeFuture.completedFuture(Optional.of(result.toSignedBlockAndState()));
    }
    return store.retrieveStateAndBlockSummary(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(final Bytes32 blockRoot) {
    if (blockData.containsKey(blockRoot)) {
      return SafeFuture.completedFuture(
          Optional.of(blockData.get(blockRoot)).map(SignedBlockAndState::getState));
    }
    return store.retrieveBlockState(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(
      final SlotAndBlockRoot slotAndBlockRoot) {
    SignedBlockAndState inMemoryBlockState = blockData.get(slotAndBlockRoot.getBlockRoot());
    if (inMemoryBlockState != null) {
      // Not executing the task via the task queue to avoid caching the result before the tx is
      // committed
      return new StateAtSlotTask(spec, slotAndBlockRoot, fromBlockAndState(inMemoryBlockState))
          .performTask();
    }
    return store.retrieveBlockState(slotAndBlockRoot);
  }

  @Override
  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>> retrieveSignedExecutionPayload(
      final Bytes32 blockRoot) {
    if (executionPayloadData.containsKey(blockRoot)) {
      return SafeFuture.completedFuture(
          Optional.of(executionPayloadData.get(blockRoot))
              .map(ExecutionPayloadUpdate::executionPayload));
    }
    return store.retrieveSignedExecutionPayload(blockRoot);
  }

  @Override
  public SafeFuture<Optional<SignedBlindedExecutionPayloadEnvelope>>
      retrieveSignedBlindedExecutionPayload(final Bytes32 blockRoot) {
    if (executionPayloadData.containsKey(blockRoot)) {
      return SafeFuture.completedFuture(
          Optional.of(executionPayloadData.get(blockRoot))
              .map(
                  executionPayloadUpdate -> executionPayloadUpdate.executionPayload().blind(spec)));
    }
    return store.retrieveSignedBlindedExecutionPayload(blockRoot);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(final Checkpoint checkpoint) {
    SignedBlockAndState inMemoryCheckpointBlockState = blockData.get(checkpoint.getRoot());
    if (inMemoryCheckpointBlockState != null) {
      // Not executing the task via the task queue to avoid caching the result before the tx is
      // committed
      return new StateAtSlotTask(
              spec,
              checkpoint.toSlotAndBlockRoot(spec),
              fromBlockAndState(inMemoryCheckpointBlockState))
          .performTask();
    }
    return store.retrieveCheckpointState(checkpoint);
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    if (this.finalizedCheckpoint.isEmpty()) {
      return store.retrieveFinalizedCheckpointAndState();
    }

    final Checkpoint finalizedCheckpoint = getFinalizedCheckpoint();
    final SafeFuture<Optional<BeaconState>> stateFuture =
        retrieveCheckpointState(finalizedCheckpoint);
    final SafeFuture<Optional<SignedBeaconBlock>> blockFuture =
        retrieveSignedBlock(finalizedCheckpoint.getRoot());

    return SafeFuture.allOf(stateFuture, blockFuture)
        .thenCompose(
            (__) -> {
              final Optional<BeaconState> state = stateFuture.join();
              final Optional<SignedBeaconBlock> block = blockFuture.join();
              if (state.isEmpty() || block.isEmpty()) {
                return store.retrieveFinalizedCheckpointAndState();
              } else {
                return SafeFuture.completedFuture(
                    CheckpointState.create(spec, finalizedCheckpoint, block.get(), state.get()));
              }
            });
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    return store.retrieveCheckpointState(checkpoint, latestStateAtEpoch);
  }

  @Override
  public UInt64 getReorgThreshold() {
    return store.getReorgThreshold();
  }

  @Override
  public UInt64 getParentThreshold() {
    return store.getParentThreshold();
  }

  @Override
  public void computeBalanceThresholds(final BeaconState justifiedState) {
    store.computeBalanceThresholds(justifiedState);
  }

  @Override
  public Optional<Boolean> isFfgCompetitive(final Bytes32 headRoot, final Bytes32 parentRoot) {
    return store.isFfgCompetitive(headRoot, parentRoot);
  }

  @Override
  public boolean satisfiesInclusionList(final Bytes32 blockRoot) {
    return store.satisfiesInclusionList(blockRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockData.get(blockRoot))
        .map(SignedBlockAndState::getBlock)
        .or(() -> store.getBlockIfAvailable(blockRoot));
  }

  @Override
  public Optional<BeaconState> getJustifiedStateIfAvailable() {
    if (justifiedCheckpoint.isEmpty()) {
      return store.getJustifiedStateIfAvailable();
    }
    return store.getCheckpointStateIfAvailable(justifiedCheckpoint.get());
  }

  @Override
  public Optional<BeaconState> getCheckpointStateIfAvailable(final Checkpoint checkpoint) {
    return store.getCheckpointStateIfAvailable(checkpoint);
  }

  @Override
  public Optional<SignedExecutionPayloadEnvelope> getExecutionPayloadIfAvailable(
      final Bytes32 blockRoot) {
    return Optional.ofNullable(executionPayloadData.get(blockRoot))
        .map(ExecutionPayloadUpdate::executionPayload)
        .or(() -> store.getExecutionPayloadIfAvailable(blockRoot));
  }

  @Override
  public Optional<List<BlobSidecar>> getBlobSidecarsIfAvailable(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return Optional.ofNullable(blobSidecars.get(slotAndBlockRoot))
        .or(() -> store.getBlobSidecarsIfAvailable(slotAndBlockRoot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> retrieveEarliestBlobSidecarSlot() {
    // we look up it in store first because if something is there, tx data is irrelevant
    return store
        .retrieveEarliestBlobSidecarSlot()
        .thenApply(
            storeEarliestBlobSidecarSlot -> {
              if (storeEarliestBlobSidecarSlot.isEmpty()) {
                return maybeEarliestBlobSidecarTransactionSlot;
              } else {
                return storeEarliestBlobSidecarSlot;
              }
            });
  }
}

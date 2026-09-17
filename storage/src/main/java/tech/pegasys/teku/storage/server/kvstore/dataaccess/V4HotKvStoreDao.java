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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StoredLightClientUpdate;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.HotUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;

public class V4HotKvStoreDao {
  // Persistent data
  private final KvStoreAccessor db;
  private final SchemaHotAdapter schema;

  public V4HotKvStoreDao(final KvStoreAccessor db, final SchemaHotAdapter schema) {
    this.db = db;
    this.schema = schema;
  }

  public Optional<UInt64> getGenesisTime() {
    return db.get(schema.getVariableGenesisTime());
  }

  public Optional<Checkpoint> getAnchor() {
    return db.get(schema.getVariableAnchorCheckpoint());
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(schema.getVariableJustifiedCheckpoint());
  }

  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(schema.getVariableBestJustifiedCheckpoint());
  }

  public Optional<Bytes32> getLatestCanonicalBlockRoot() {
    return db.get(schema.getVariableLatestCanonicalBlockRoot());
  }

  public Optional<UInt64> getCustodyGroupCount() {
    return db.get(schema.getVariableCustodyGroupCount());
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(schema.getVariableFinalizedCheckpoint());
  }

  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(schema.getColumnHotBlocksByRoot(), root);
  }

  public Optional<Bytes> getHotBlockRaw(final Bytes32 root) {
    return db.getRaw(schema.getColumnHotBlocksByRoot(), root);
  }

  public Optional<BlockCheckpoints> getHotBlockCheckpointEpochs(final Bytes32 root) {
    return db.get(schema.getColumnHotBlockCheckpointEpochsByRoot(), root);
  }

  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return db.get(schema.getColumnHotStatesByRoot(), root);
  }

  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return db.stream(schema.getColumnHotBlocksByRoot()).map(ColumnEntry::getValue);
  }

  @MustBeClosed
  public Stream<ColumnEntry<UInt64, StoredLightClientUpdate>> streamBestLightClientUpdates() {
    return db.stream(schema.getLightClientUpdatesByPeriod());
  }

  @MustBeClosed
  public Stream<UInt64> streamBestLightClientUpdatePeriods() {
    return db.streamKeys(schema.getLightClientUpdatesByPeriod());
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz() {
    return streamRawColumn(schema.getColumnHotBlocksByRoot()).map(entry -> entry);
  }

  public Optional<BeaconState> getLatestFinalizedState() {
    return db.get(schema.getVariableLatestFinalizedState());
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return db.get(schema.getVariableWeakSubjectivityCheckpoint());
  }

  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    try (Stream<ColumnEntry<Bytes32, SlotAndBlockRoot>> stream =
        db.stream(schema.getColumnStateRootToSlotAndBlockRoot())) {
      return stream
          .filter((column) -> column.getValue().getSlot().compareTo(slot) < 0)
          .map(ColumnEntry::getKey)
          .toList();
    }
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnStateRootToSlotAndBlockRoot(), stateRoot);
  }

  public Map<UInt64, VoteTracker> getVotes() {
    return db.getAll(schema.getColumnVotes());
  }

  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return db.stream(schema.getColumnDepositsFromBlockEvents()).map(ColumnEntry::getValue);
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints() {
    return db.stream(schema.getColumnHotBlockCheckpointEpochsByRoot()).map(entry -> entry);
  }

  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return db.get(schema.getVariableMinGenesisTimeBlock());
  }

  @MustBeClosed
  public V4HotUpdater hotUpdater() {
    return new V4HotUpdater(db, schema);
  }

  public <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var) {
    return db.getRaw(var);
  }

  @MustBeClosed
  public <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(
      final KvStoreColumn<K, V> kvStoreColumn) {
    return db.streamRaw(kvStoreColumn);
  }

  public Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot() {
    return db.get(schema.getVariableFinalizedDepositSnapshot());
  }

  public void close() throws Exception {
    db.close();
  }

  public void compact() {
    db.compact();
  }

  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return schema.getColumnMap();
  }

  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return schema.getVariableMap();
  }

  public <V, K> Optional<Bytes> getRaw(final KvStoreColumn<K, V> kvStoreColumn, final K key) {
    return db.getRaw(kvStoreColumn, key);
  }

  public Map<String, Long> getColumnCounts(final Optional<String> maybeColumnNameFilter) {
    final Map<String, Long> columnCounts = new LinkedHashMap<>();
    schema
        .getColumnMap()
        .forEach(
            (k, v) -> {
              if (maybeColumnNameFilter.isEmpty()
                  || k.contains(maybeColumnNameFilter.get().toUpperCase(Locale.ROOT))) {
                columnCounts.put(k, db.size(v));
              }
            });

    return columnCounts;
  }

  static class V4HotUpdater implements HotUpdater {

    private final KvStoreTransaction transaction;
    private final SchemaHotAdapter schema;

    V4HotUpdater(final KvStoreAccessor db, final SchemaHotAdapter schema) {
      this.transaction = db.startTransaction();
      this.schema = schema;
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
      transaction.put(schema.getVariableGenesisTime(), genesisTime);
    }

    @Override
    public void setAnchor(final Checkpoint anchor) {
      transaction.put(schema.getVariableAnchorCheckpoint(), anchor);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableJustifiedCheckpoint(), checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableBestJustifiedCheckpoint(), checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableFinalizedCheckpoint(), checkpoint);
    }

    @Override
    public void setLatestCanonicalBlockRoot(final Bytes32 canonicalBlockRoot) {
      transaction.put(schema.getVariableLatestCanonicalBlockRoot(), canonicalBlockRoot);
    }

    @Override
    public void setCustodyGroupCount(final UInt64 custodyGroupCount) {
      transaction.put(schema.getVariableCustodyGroupCount(), custodyGroupCount);
    }

    @Override
    public void addBestLightClientUpdate(
        final UInt64 period, final StoredLightClientUpdate update) {
      transaction.put(schema.getLightClientUpdatesByPeriod(), period, update);
    }

    @Override
    public void removeBestLightClientUpdate(final UInt64 period) {
      transaction.delete(schema.getLightClientUpdatesByPeriod(), period);
    }

    @Override
    public void setWeakSubjectivityCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableWeakSubjectivityCheckpoint(), checkpoint);
    }

    @Override
    public void clearWeakSubjectivityCheckpoint() {
      transaction.delete(schema.getVariableWeakSubjectivityCheckpoint());
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(schema.getVariableLatestFinalizedState(), state);
    }

    @Override
    public void addHotBlock(final BlockAndCheckpoints block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(schema.getColumnHotBlocksByRoot(), blockRoot, block.getBlock());
      addHotBlockCheckpointEpochs(blockRoot, block.getBlockCheckpoints());
    }

    private void addHotBlockCheckpointEpochs(
        final Bytes32 blockRoot, final BlockCheckpoints blockCheckpoints) {
      transaction.put(
          schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot, blockCheckpoints);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(schema.getColumnHotStatesByRoot(), blockRoot, state);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      stateRootToSlotAndBlockRootMap.forEach(
          (stateRoot, slotAndBlockRoot) ->
              transaction.put(
                  schema.getColumnStateRootToSlotAndBlockRoot(), stateRoot, slotAndBlockRoot));
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      stateRoots.forEach(
          (root) -> transaction.delete(schema.getColumnStateRootToSlotAndBlockRoot(), root));
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(schema.getColumnVotes(), validatorIndex, vote));
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotBlocksByRoot(), blockRoot);
      transaction.delete(schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot);
      deleteHotState(blockRoot);
    }

    @Override
    public void deleteHotBlockOnly(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotBlocksByRoot(), blockRoot);
    }

    @Override
    public void deleteHotState(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotStatesByRoot(), blockRoot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      transaction.put(schema.getVariableMinGenesisTimeBlock(), event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      transaction.put(schema.getColumnDepositsFromBlockEvents(), event.getBlockNumber(), event);
    }

    @Override
    public void removeDepositsFromBlockEvent(final UInt64 blockNumber) {
      transaction.delete(schema.getColumnDepositsFromBlockEvents(), blockNumber);
    }

    @Override
    public void setFinalizedDepositSnapshot(final DepositTreeSnapshot finalizedDepositSnapshot) {
      transaction.put(schema.getVariableFinalizedDepositSnapshot(), finalizedDepositSnapshot);
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      close();
    }

    @Override
    public void cancel() {
      transaction.rollback();
      close();
    }

    @Override
    public void close() {
      transaction.close();
    }
  }
}

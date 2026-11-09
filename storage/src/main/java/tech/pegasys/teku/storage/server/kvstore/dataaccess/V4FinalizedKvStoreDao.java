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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import static tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.MAX_BLOCK_ROOT;
import static tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.MIN_BLOCK_ROOT;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;

public class V4FinalizedKvStoreDao {
  private final KvStoreAccessor db;
  private final SchemaFinalizedSnapshotStateAdapter schema;
  private final V4FinalizedStateStorageLogic<SchemaFinalizedSnapshotStateAdapter> stateStorageLogic;

  public V4FinalizedKvStoreDao(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor db,
      final SchemaFinalizedSnapshotStateAdapter schema,
      final V4FinalizedStateStorageLogic<SchemaFinalizedSnapshotStateAdapter> stateStorageLogic) {
    this.db = db;
    this.schema = schema;
    this.stateStorageLogic = stateStorageLogic;
  }

  public void close() throws Exception {
    db.close();
  }

  public void compact() {
    db.compact();
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {

    return db.get(schema.getColumnFinalizedBlocksBySlot(), slot);
  }

  public Optional<UInt64> getEarliestFinalizedBlockSlot() {
    return db.get(schema.getVariableEarliestBlockSlot())
        .or(
            () ->
                db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getKey));
  }

  public Optional<UInt64> getEarliestFinalizedStateSlot() {
    return stateStorageLogic.getEarliestAvailableFinalizedStateSlot(db, schema);
  }

  public Optional<SignedBeaconBlock> getEarliestFinalizedBlock() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getValue);
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {

    return db.getFloorEntry(schema.getColumnFinalizedBlocksBySlot(), slot)
        .map(ColumnEntry::getValue);
  }

  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    Optional<Set<Bytes32>> maybeRoots = db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
    return maybeRoots.stream()
        .flatMap(Collection::stream)
        .flatMap(root -> db.get(schema.getColumnNonCanonicalBlocksByRoot(), root).stream())
        .toList();
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return stateStorageLogic.getLatestAvailableFinalizedState(db, schema, maxSlot);
  }

  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedBlocksBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getValue);
  }

  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), blockRoot);
  }

  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
  }

  public Optional<Bytes32> getLatestCanonicalBlockRoot() {
    return db.get(schema.getVariableLatestCanonicalBlockRoot());
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(
      final Bytes32 stateRoot) {
    Optional<UInt64> maybeSlot = db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
    return maybeSlot.flatMap(
        slot ->
            getFinalizedBlockAtSlot(slot)
                .map(block -> new SlotAndBlockRoot(slot, block.getRoot())));
  }

  public Optional<UInt64> getOptimisticTransitionBlockSlot() {
    return db.get(schema.getOptimisticTransitionBlockSlot());
  }

  public Optional<SignedBeaconBlock> getNonCanonicalBlock(final Bytes32 root) {
    return db.get(schema.getColumnNonCanonicalBlocksByRoot(), root);
  }

  public Optional<Bytes> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return db.get(schema.getColumnBlobSidecarBySlotRootBlobIndex(), key);
  }

  public Optional<Bytes> getNonCanonicalBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return db.get(schema.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex(), key);
  }

  @MustBeClosed
  public Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.streamKeys(
        schema.getColumnBlobSidecarBySlotRootBlobIndex(),
        new SlotAndBlockRootAndBlobIndex(startSlot, MIN_BLOCK_ROOT, UInt64.ZERO),
        new SlotAndBlockRootAndBlobIndex(endSlot, MAX_BLOCK_ROOT, UInt64.MAX_VALUE));
  }

  @MustBeClosed
  public Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.streamKeys(
        schema.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex(),
        new SlotAndBlockRootAndBlobIndex(startSlot, MIN_BLOCK_ROOT, UInt64.ZERO),
        new SlotAndBlockRootAndBlobIndex(endSlot, MAX_BLOCK_ROOT, UInt64.MAX_VALUE));
  }

  @MustBeClosed
  public Stream<Bytes> streamBlobSidecars(final SlotAndBlockRoot slotAndBlockRoot) {
    return db.stream(
            schema.getColumnBlobSidecarBySlotRootBlobIndex(),
            new SlotAndBlockRootAndBlobIndex(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.ZERO),
            new SlotAndBlockRootAndBlobIndex(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.MAX_VALUE))
        .map(ColumnEntry::getValue);
  }

  public List<SlotAndBlockRootAndBlobIndex> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    try (final Stream<SlotAndBlockRootAndBlobIndex> streamKeys =
        db.streamKeys(
            schema.getColumnBlobSidecarBySlotRootBlobIndex(),
            new SlotAndBlockRootAndBlobIndex(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.ZERO),
            new SlotAndBlockRootAndBlobIndex(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.MAX_VALUE))) {
      return streamKeys.toList();
    }
  }

  public List<SlotAndBlockRootAndBlobIndex> getNonCanonicalBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    try (final Stream<SlotAndBlockRootAndBlobIndex> streamKeys =
        db.streamKeys(
            schema.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex(),
            new SlotAndBlockRootAndBlobIndex(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.ZERO),
            new SlotAndBlockRootAndBlobIndex(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.MAX_VALUE))) {
      return streamKeys.toList();
    }
  }

  public Optional<UInt64> getEarliestBlobSidecarSlot() {
    return db.get(schema.getVariableEarliestBlobSidecarSlot());
  }

  public Optional<UInt64> getFirstCustodyIncompleteSlot() {
    return db.get(schema.getVariableFirstCustodyIncompleteSlot());
  }

  public Optional<Bytes> getSidecar(final DataColumnSlotAndIdentifier identifier) {
    return db.get(schema.getColumnSidecarByColumnSlotAndIdentifier(), identifier);
  }

  public Optional<Bytes> getNonCanonicalSidecar(final DataColumnSlotAndIdentifier identifier) {
    return db.get(schema.getColumnNonCanonicalSidecarByColumnSlotAndIdentifier(), identifier);
  }

  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.streamKeys(
        schema.getColumnSidecarByColumnSlotAndIdentifier(),
        new DataColumnSlotAndIdentifier(startSlot, MIN_BLOCK_ROOT, UInt64.ZERO),
        new DataColumnSlotAndIdentifier(endSlot, MAX_BLOCK_ROOT, UInt64.MAX_VALUE));
  }

  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamNonCanonicalDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.streamKeys(
        schema.getColumnNonCanonicalSidecarByColumnSlotAndIdentifier(),
        new DataColumnSlotAndIdentifier(startSlot, MIN_BLOCK_ROOT, UInt64.ZERO),
        new DataColumnSlotAndIdentifier(endSlot, MAX_BLOCK_ROOT, UInt64.MAX_VALUE));
  }

  public List<DataColumnSlotAndIdentifier> getDataColumnIdentifiers(
      final SlotAndBlockRoot slotAndBlockRoot) {
    try (final Stream<DataColumnSlotAndIdentifier> identifierStream =
        db.streamKeys(
            schema.getColumnSidecarByColumnSlotAndIdentifier(),
            new DataColumnSlotAndIdentifier(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.ZERO),
            new DataColumnSlotAndIdentifier(
                slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), UInt64.MAX_VALUE))) {
      return identifierStream.toList();
    }
  }

  public Optional<UInt64> getEarliestAvailableDataColumnSlot() {
    return db.get(schema.getVariableEarliestAvailableDataColumnSlot());
  }

  public Optional<UInt64> getLastDataColumnSidecarPrunedSlot() {
    return db.get(schema.getVariableLastDataColumnSidecarPrunedSlot());
  }

  public Optional<UInt64> getLastDataColumnSidecarsProofsSlot() {
    return db.getLastKey(schema.getColumnDataColumnSidecarsProofsBySlot());
  }

  public Optional<List<List<KZGProof>>> getDataColumnSidecarProofs(final UInt64 slot) {
    return db.get(schema.getColumnDataColumnSidecarsProofsBySlot(), slot);
  }

  public <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var) {
    return db.getRaw(var);
  }

  @MustBeClosed
  public <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(
      final KvStoreColumn<K, V> kvStoreColumn) {
    return db.streamRaw(kvStoreColumn);
  }

  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), root)
        .flatMap(this::getFinalizedBlockAtSlot);
  }

  public Optional<SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloadEnvelope(
      final Bytes32 root) {
    return db.get(schema.getColumnBlindedExecutionPayloadEnvelopesByRoot(), root);
  }

  @MustBeClosed
  public V4FinalizedUpdater finalizedUpdater() {
    return new V4FinalizedKvStoreDao.V4FinalizedUpdater(db, schema, stateStorageLogic.updater());
  }

  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return schema.getColumnMap();
  }

  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return schema.getVariableMap();
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots() {
    return db.stream(schema.getColumnSlotsByFinalizedStateRoot()).map(entry -> entry);
  }

  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> getFinalizedBlockRoots() {
    return db.stream(schema.getColumnSlotsByFinalizedRoot()).map(entry -> entry);
  }

  public Set<Bytes32> getNonCanonicalBlockRootsAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnNonCanonicalRootsBySlot(), slot).orElseGet(HashSet::new);
  }

  public <V, K> Optional<Bytes> getRaw(final KvStoreColumn<K, V> kvStoreColumn, final K key) {
    return db.getRaw(kvStoreColumn, key);
  }

  public Map<String, Long> getColumnCounts(final Optional<String> maybeColumnFilter) {
    final Map<String, Long> columnCounts = new HashMap<>();
    schema
        .getColumnMap()
        .forEach(
            (k, v) -> {
              if (maybeColumnFilter.isEmpty()
                  || k.contains(maybeColumnFilter.get().toUpperCase(Locale.ROOT))) {
                columnCounts.put(k, db.size(v));
              }
            });
    return columnCounts;
  }

  public long getBlobSidecarColumnCount() {
    final KvStoreColumn<?, ?> column =
        schema.getColumnMap().get("BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX");
    return db.size(column);
  }

  public long getSidecarColumnCount() {
    final KvStoreColumn<?, ?> column =
        schema.getColumnMap().get("SIDECAR_BY_COLUMN_SLOT_AND_IDENTIFIER");
    return db.size(column);
  }

  public long getNonCanonicalBlobSidecarColumnCount() {
    final KvStoreColumn<?, ?> column =
        schema
            .getColumnMap()
            .get("NON_CANONICAL_BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX");
    return db.size(column);
  }

  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedStatesBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getKey);
  }

  static class V4FinalizedUpdater implements FinalizedUpdater {
    private final KvStoreTransaction transaction;
    private final KvStoreAccessor db;
    private final SchemaFinalizedSnapshotStateAdapter schema;
    private final V4FinalizedStateStorageLogic.FinalizedStateUpdater<
            SchemaFinalizedSnapshotStateAdapter>
        stateStorageUpdater;

    V4FinalizedUpdater(
        final KvStoreAccessor db,
        final SchemaFinalizedSnapshotStateAdapter schema,
        final V4FinalizedStateStorageLogic.FinalizedStateUpdater<
                SchemaFinalizedSnapshotStateAdapter>
            stateStorageUpdater) {
      this.transaction = db.startTransaction();
      this.db = db;
      this.schema = schema;
      this.stateStorageUpdater = stateStorageUpdater;
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnSlotsByFinalizedRoot(), block.getRoot(), block.getSlot());
      transaction.put(schema.getColumnFinalizedBlocksBySlot(), block.getSlot(), block);
    }

    @Override
    public void addFinalizedBlockRaw(
        final UInt64 slot, final Bytes32 blockRoot, final Bytes blockBytes) {
      transaction.put(schema.getColumnSlotsByFinalizedRoot(), blockRoot, slot);
      final KvStoreColumn<UInt64, SignedBeaconBlock> columnFinalizedBlocksBySlot =
          schema.getColumnFinalizedBlocksBySlot();
      transaction.putRaw(
          columnFinalizedBlocksBySlot,
          Bytes.wrap(columnFinalizedBlocksBySlot.getKeySerializer().serialize(slot)),
          blockBytes);
    }

    @Override
    public void addBlindedExecutionPayloadEnvelope(
        final Bytes32 blockRoot,
        final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope) {
      transaction.put(
          schema.getColumnBlindedExecutionPayloadEnvelopesByRoot(),
          blockRoot,
          signedBlindedExecutionPayloadEnvelope);
    }

    @Override
    public void addNonCanonicalBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnNonCanonicalBlocksByRoot(), block.getRoot(), block);
    }

    @Override
    public void deleteFinalizedBlock(final UInt64 slot, final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnFinalizedBlocksBySlot(), slot);
      transaction.delete(schema.getColumnSlotsByFinalizedRoot(), blockRoot);
      deleteBlindedExecutionPayloadEnvelope(blockRoot);
    }

    @Override
    public void deleteBlindedExecutionPayloadEnvelope(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnBlindedExecutionPayloadEnvelopesByRoot(), blockRoot);
    }

    @Override
    public void deleteNonCanonicalBlockOnly(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnNonCanonicalBlocksByRoot(), blockRoot);
    }

    @Override
    public void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots) {
      Optional<Set<Bytes32>> maybeRoots = db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
      final Set<Bytes32> roots = maybeRoots.orElse(new HashSet<>());
      if (roots.addAll(blockRoots)) {
        transaction.put(schema.getColumnNonCanonicalRootsBySlot(), slot, roots);
      }
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      stateStorageUpdater.addFinalizedState(db, transaction, schema, state);
    }

    @Override
    public void deleteFinalizedState(final UInt64 slot) {
      transaction.delete(schema.getColumnFinalizedStatesBySlot(), slot);
    }

    @Override
    public void addReconstructedFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      stateStorageUpdater.addReconstructedFinalizedState(db, transaction, schema, state);
    }

    @Override
    public void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot) {
      transaction.put(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot, slot);
    }

    @Override
    public void deleteFinalizedStateRoot(final Bytes32 stateRoot) {
      transaction.delete(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
    }

    @Override
    public void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot) {
      if (transitionBlockSlot.isPresent()) {
        transaction.put(schema.getOptimisticTransitionBlockSlot(), transitionBlockSlot.get());
      } else {
        transaction.delete(schema.getOptimisticTransitionBlockSlot());
      }
    }

    @Override
    public void addBlobSidecar(final BlobSidecar blobSidecar) {
      transaction.put(
          schema.getColumnBlobSidecarBySlotRootBlobIndex(),
          new SlotAndBlockRootAndBlobIndex(
              blobSidecar.getSlot(), blobSidecar.getBlockRoot(), blobSidecar.getIndex()),
          blobSidecar.sszSerialize());
    }

    @Override
    public void addNonCanonicalBlobSidecar(final BlobSidecar blobSidecar) {
      transaction.put(
          schema.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex(),
          new SlotAndBlockRootAndBlobIndex(
              blobSidecar.getSlot(), blobSidecar.getBlockRoot(), blobSidecar.getIndex()),
          blobSidecar.sszSerialize());
    }

    @Override
    public void addNonCanonicalBlobSidecarRaw(
        final Bytes blobSidecarBytes, final SlotAndBlockRootAndBlobIndex key) {
      final KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes> column =
          schema.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex();
      transaction.putRaw(
          column, Bytes.wrap(column.getKeySerializer().serialize(key)), blobSidecarBytes);
    }

    @Override
    public void removeBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
      transaction.delete(schema.getColumnBlobSidecarBySlotRootBlobIndex(), key);
    }

    @Override
    public void removeNonCanonicalBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
      transaction.delete(schema.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex(), key);
    }

    @Override
    public void setEarliestBlobSidecarSlot(final UInt64 slot) {
      transaction.put(schema.getVariableEarliestBlobSidecarSlot(), slot);
    }

    @Override
    public void setEarliestAvailableDataColumnSlot(final UInt64 slot) {
      transaction.put(schema.getVariableEarliestAvailableDataColumnSlot(), slot);
    }

    @Override
    public void setLastDataColumnSidecarPrunedSlot(final UInt64 slot) {
      transaction.put(schema.getVariableLastDataColumnSidecarPrunedSlot(), slot);
    }

    @Override
    public void setEarliestBlockSlot(final UInt64 slot) {
      transaction.put(schema.getVariableEarliestBlockSlot(), slot);
    }

    @Override
    public void deleteEarliestBlockSlot() {
      transaction.delete(schema.getVariableEarliestBlockSlot());
    }

    @Override
    public void setFirstCustodyIncompleteSlot(final UInt64 slot) {
      transaction.put(schema.getVariableFirstCustodyIncompleteSlot(), slot);
    }

    @Override
    public void addSidecar(final DataColumnSidecar sidecar) {
      transaction.put(
          schema.getColumnSidecarByColumnSlotAndIdentifier(),
          new DataColumnSlotAndIdentifier(
              sidecar.getSlot(), sidecar.getBeaconBlockRoot(), sidecar.getIndex()),
          sidecar.sszSerialize());
    }

    @Override
    public void addNonCanonicalSidecar(final DataColumnSidecar sidecar) {
      transaction.put(
          schema.getColumnNonCanonicalSidecarByColumnSlotAndIdentifier(),
          new DataColumnSlotAndIdentifier(
              sidecar.getSlot(), sidecar.getBeaconBlockRoot(), sidecar.getIndex()),
          sidecar.sszSerialize());
    }

    @Override
    public void removeSidecar(final DataColumnSlotAndIdentifier identifier) {
      transaction.delete(schema.getColumnSidecarByColumnSlotAndIdentifier(), identifier);
    }

    @Override
    public void removeNonCanonicalSidecar(final DataColumnSlotAndIdentifier identifier) {
      transaction.delete(
          schema.getColumnNonCanonicalSidecarByColumnSlotAndIdentifier(), identifier);
    }

    @Override
    public void addDataColumnSidecarsProofs(
        final UInt64 slot, final List<List<KZGProof>> kzgProofs) {
      transaction.put(schema.getColumnDataColumnSidecarsProofsBySlot(), slot, kzgProofs);
    }

    @Override
    public void removeDataColumnSidecarsProofs(final UInt64 slot) {
      transaction.delete(schema.getColumnDataColumnSidecarsProofsBySlot(), slot);
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      stateStorageUpdater.commit();
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

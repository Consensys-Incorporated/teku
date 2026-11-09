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

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotState;

public class V4FinalizedStateSnapshotStorageLogic<S extends SchemaFinalizedSnapshotState>
    implements V4FinalizedStateStorageLogic<S> {

  private final UInt64 stateStorageFrequency;

  public V4FinalizedStateSnapshotStorageLogic(final long stateStorageFrequency) {
    this.stateStorageFrequency = UInt64.valueOf(stateStorageFrequency);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(
      final KvStoreAccessor db, final SchemaFinalizedSnapshotState schema, final UInt64 maxSlot) {
    return db.getFloorEntry(schema.getColumnFinalizedStatesBySlot(), maxSlot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public Optional<UInt64> getEarliestAvailableFinalizedStateSlot(
      final KvStoreAccessor db, final S schema) {
    return db.getFirstEntry(schema.getColumnFinalizedStatesBySlot()).map(ColumnEntry::getKey);
  }

  @Override
  public FinalizedStateUpdater<S> updater() {
    return new FinalizedStateSnapshotUpdater<>(stateStorageFrequency);
  }

  @Override
  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(
      final KvStoreAccessor db,
      final SchemaFinalizedSnapshotState schema,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedStatesBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getKey);
  }

  private static class FinalizedStateSnapshotUpdater<S extends SchemaFinalizedSnapshotState>
      implements V4FinalizedStateStorageLogic.FinalizedStateUpdater<S> {

    private final UInt64 stateStorageFrequency;
    private Optional<UInt64> lastStateStoredSlot = Optional.empty();
    private boolean loadedLastStoreState = false;

    private Optional<UInt64> lastReconstructedStateStoredSlot = Optional.empty();
    private boolean loadedLastReconstructedStoreState = false;

    private FinalizedStateSnapshotUpdater(final UInt64 stateStorageFrequency) {
      this.stateStorageFrequency = stateStorageFrequency;
    }

    @Override
    public void addFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final SchemaFinalizedSnapshotState schema,
        final BeaconState state) {
      if (!loadedLastStoreState) {
        lastStateStoredSlot = db.getLastKey(schema.getColumnFinalizedStatesBySlot());
        loadedLastStoreState = true;
      }
      if (lastStateStoredSlot.isPresent()) {
        UInt64 nextStorageSlot = lastStateStoredSlot.get().plus(stateStorageFrequency);
        if (state.getSlot().compareTo(nextStorageSlot) >= 0) {
          addFinalizedState(transaction, schema, state);
        }
      } else {
        addFinalizedState(transaction, schema, state);
      }
    }

    @Override
    public void addReconstructedFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final S schema,
        final BeaconState state) {
      if (!loadedLastReconstructedStoreState) {
        lastReconstructedStateStoredSlot =
            db.getFloorEntry(schema.getColumnFinalizedStatesBySlot(), state.getSlot())
                .map(ColumnEntry::getKey);
        loadedLastReconstructedStoreState = true;
      }
      if (lastReconstructedStateStoredSlot.isPresent()) {
        UInt64 nextStorageSlot = lastReconstructedStateStoredSlot.get().plus(stateStorageFrequency);
        if (state.getSlot().compareTo(nextStorageSlot) >= 0) {
          addReconstructedFinalizedState(transaction, schema, state);
        }
      } else {
        addReconstructedFinalizedState(transaction, schema, state);
      }
    }

    @Override
    public void deleteFinalizedState(
        final KvStoreTransaction transaction, final S schema, final UInt64 slot) {
      transaction.delete(schema.getColumnFinalizedStatesBySlot(), slot);
    }

    @Override
    public void commit() {}

    private void addFinalizedState(
        final KvStoreTransaction transaction,
        final SchemaFinalizedSnapshotState schema,
        final BeaconState state) {
      transaction.put(schema.getColumnFinalizedStatesBySlot(), state.getSlot(), state);
      lastStateStoredSlot = Optional.of(state.getSlot());
    }

    private void addReconstructedFinalizedState(
        final KvStoreTransaction transaction,
        final SchemaFinalizedSnapshotState schema,
        final BeaconState state) {
      transaction.put(schema.getColumnFinalizedStatesBySlot(), state.getSlot(), state);
      lastReconstructedStateStoredSlot = Optional.of(state.getSlot());
    }
  }
}

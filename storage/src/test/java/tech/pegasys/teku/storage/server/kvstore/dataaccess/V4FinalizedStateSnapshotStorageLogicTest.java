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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic.FinalizedStateUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;

class V4FinalizedStateSnapshotStorageLogicTest {

  public static final int STATE_STORAGE_FREQUENCY = 100;

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final KvStoreAccessor db = mock(KvStoreAccessor.class);
  private final KvStoreTransaction transaction = mock(KvStoreTransaction.class);
  private final SchemaFinalizedSnapshotStateAdapter schema =
      V6SchemaCombinedSnapshot.createV6(spec).asSchemaFinalized();

  private final V4FinalizedStateSnapshotStorageLogic<SchemaFinalizedSnapshotStateAdapter> logic =
      new V4FinalizedStateSnapshotStorageLogic<>(STATE_STORAGE_FREQUENCY);

  @Test
  void getLatestAvailableFinalizedState_shouldGetFloorEntry() {
    final UInt64 maxSlot = UInt64.valueOf(2038);
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.ONE);
    when(db.getFloorEntry(schema.getColumnFinalizedStatesBySlot(), maxSlot))
        .thenReturn(Optional.of(ColumnEntry.create(UInt64.ONE, state)));

    assertThat(logic.getLatestAvailableFinalizedState(db, schema, maxSlot)).contains(state);
  }

  @Test
  void updater_shouldNotStoreFirstStateIfItIsTooCloseToLastStoredState() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(120));
    when(db.getLastKey(schema.getColumnFinalizedStatesBySlot()))
        .thenReturn(Optional.of(UInt64.valueOf(100)));

    final FinalizedStateUpdater<SchemaFinalizedSnapshotStateAdapter> updater = logic.updater();
    updater.addFinalizedState(db, transaction, schema, state);

    verifyNoInteractions(transaction);
  }

  @Test
  void updater_shouldStoreFirstStateIfItIsFarEnoughAfterLastStoredState() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(120));
    when(db.getLastKey(schema.getColumnFinalizedStatesBySlot()))
        .thenReturn(Optional.of(state.getSlot().minus(STATE_STORAGE_FREQUENCY)));

    final FinalizedStateUpdater<SchemaFinalizedSnapshotStateAdapter> updater = logic.updater();
    updater.addFinalizedState(db, transaction, schema, state);

    verify(transaction).put(schema.getColumnFinalizedStatesBySlot(), state.getSlot(), state);
  }

  @Test
  void updater_shouldStoreFirstStateIfNoPreviousStateStored() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(120));
    when(db.getLastKey(schema.getColumnFinalizedStatesBySlot())).thenReturn(Optional.empty());

    final FinalizedStateUpdater<SchemaFinalizedSnapshotStateAdapter> updater = logic.updater();
    updater.addFinalizedState(db, transaction, schema, state);

    verify(transaction).put(schema.getColumnFinalizedStatesBySlot(), state.getSlot(), state);
  }

  @Test
  void updater_shouldStoreSubsequentStateIfFarEnoughAfterPreviousState() {
    final BeaconState state1 = dataStructureUtil.randomBeaconState(UInt64.valueOf(120));
    final BeaconState state2 =
        dataStructureUtil.randomBeaconState(state1.getSlot().plus(STATE_STORAGE_FREQUENCY));
    when(db.getLastKey(schema.getColumnFinalizedStatesBySlot())).thenReturn(Optional.empty());

    final FinalizedStateUpdater<SchemaFinalizedSnapshotStateAdapter> updater = logic.updater();
    // Store first state
    updater.addFinalizedState(db, transaction, schema, state1);
    verify(transaction).put(schema.getColumnFinalizedStatesBySlot(), state1.getSlot(), state1);

    // Store second state
    updater.addFinalizedState(db, transaction, schema, state2);
    verify(transaction).put(schema.getColumnFinalizedStatesBySlot(), state2.getSlot(), state2);
  }

  @Test
  void updater_shouldNotStoreSubsequentStateIfNotFarEnoughAfterPreviousState() {
    final BeaconState state1 = dataStructureUtil.randomBeaconState(UInt64.valueOf(120));
    final BeaconState state2 =
        dataStructureUtil.randomBeaconState(state1.getSlot().plus(STATE_STORAGE_FREQUENCY - 1));
    when(db.getLastKey(schema.getColumnFinalizedStatesBySlot())).thenReturn(Optional.empty());

    final FinalizedStateUpdater<SchemaFinalizedSnapshotStateAdapter> updater = logic.updater();
    // Store first state
    updater.addFinalizedState(db, transaction, schema, state1);
    verify(transaction).put(schema.getColumnFinalizedStatesBySlot(), state1.getSlot(), state1);

    // Store second state
    updater.addFinalizedState(db, transaction, schema, state2);
    verifyNoMoreInteractions(transaction);
  }

  @Test
  void updater_shouldStoreReconstructedStateAfterFinalizedState() {
    final FinalizedStateUpdater<SchemaFinalizedSnapshotStateAdapter> updater = logic.updater();

    // Store finalized state
    final KvStoreTransaction transaction = mock(KvStoreTransaction.class);
    final BeaconState finalizedState = dataStructureUtil.randomBeaconState(UInt64.valueOf(20));

    updater.addFinalizedState(db, transaction, schema, finalizedState);
    verify(transaction)
        .put(schema.getColumnFinalizedStatesBySlot(), finalizedState.getSlot(), finalizedState);

    // Store reconstructed finalized state
    final KvStoreTransaction reconstructTransaction = mock(KvStoreTransaction.class);
    final BeaconState reconstructedFinalizedState =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(5));

    updater.addReconstructedFinalizedState(
        db, reconstructTransaction, schema, reconstructedFinalizedState);
    verify(reconstructTransaction)
        .put(
            schema.getColumnFinalizedStatesBySlot(),
            reconstructedFinalizedState.getSlot(),
            reconstructedFinalizedState);

    // Store finalized state before latest
    final KvStoreTransaction invalidTransaction = mock(KvStoreTransaction.class);
    final BeaconState invalidFinalizedState =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(11));

    updater.addFinalizedState(db, invalidTransaction, schema, invalidFinalizedState);
    verifyNoInteractions(invalidTransaction);
  }
}

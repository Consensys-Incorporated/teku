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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Collections;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.MockKvStoreInstance;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic.FinalizedStateUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;

class V4FinalizedStateTreeStorageLogicTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final V6SchemaCombinedTreeState schema = new V6SchemaCombinedTreeState(spec);
  private final KvStoreAccessor db =
      MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());

  private final V4FinalizedStateTreeStorageLogic logic =
      new V4FinalizedStateTreeStorageLogic(new NoOpMetricsSystem(), spec, 1000);

  @Test
  void shouldRoundTripState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    storeState(state);
    assertStateReloads(state);
  }

  @Test
  void shouldGetMostRecentStateBeforeRequestedSlot() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    storeState(state);
    assertStateReloads(state, state.getSlot().plus(10));
  }

  @Test
  void shouldStoreAndLoadMultipleStates() {
    final BeaconState state1 = dataStructureUtil.randomBeaconState(UInt64.valueOf(3));
    final BeaconState state2 = dataStructureUtil.randomBeaconState(UInt64.valueOf(5));
    final BeaconState state3 = dataStructureUtil.randomBeaconState(UInt64.valueOf(7));
    final BeaconState state4 = dataStructureUtil.randomBeaconState(UInt64.valueOf(10));
    try (final KvStoreTransaction transaction = db.startTransaction()) {
      final FinalizedStateUpdater<SchemaCombinedTreeState> updater = logic.updater();
      updater.addFinalizedState(db, transaction, schema, state1);
      updater.addFinalizedState(db, transaction, schema, state2);
      updater.addFinalizedState(db, transaction, schema, state3);
      updater.addFinalizedState(db, transaction, schema, state4);
      transaction.commit();
    }

    assertStateReloads(state1);
    assertStateReloads(state2);
    assertStateReloads(state3);
    assertStateReloads(state4);
  }

  private void assertStateReloads(final BeaconState state) {
    assertStateReloads(state, state.getSlot());
  }

  private void assertStateReloads(final BeaconState expectedState, final UInt64 slot) {
    final Optional<BeaconState> loadedState =
        logic.getLatestAvailableFinalizedState(db, schema, slot);
    assertThat(loadedState).contains(expectedState);
  }

  @Test
  void shouldRoundTripGloasState() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasUtil = new DataStructureUtil(gloasSpec);
    final V6SchemaCombinedTreeState gloasSchema = new V6SchemaCombinedTreeState(gloasSpec);
    final KvStoreAccessor gloasDb =
        MockKvStoreInstance.createEmpty(gloasSchema.getAllColumns(), gloasSchema.getAllVariables());
    final V4FinalizedStateTreeStorageLogic gloasLogic =
        new V4FinalizedStateTreeStorageLogic(new NoOpMetricsSystem(), gloasSpec, 1000);

    final BeaconState state = gloasUtil.randomBeaconState();
    try (final KvStoreTransaction transaction = gloasDb.startTransaction()) {
      gloasLogic.updater().addFinalizedState(gloasDb, transaction, gloasSchema, state);
      transaction.commit();
    }
    final Optional<BeaconState> loaded =
        gloasLogic.getLatestAvailableFinalizedState(gloasDb, gloasSchema, state.getSlot());
    assertThat(loaded).contains(state);
  }

  @Test
  void shouldRoundTripGloasStateWithZeroParticipation() {
    // Regression: genesis-like states have all-zero participation lists (32 validators, all
    // flags=0)
    // The progressive data tree for a 1-chunk all-zero list has the same hash as ZERO_TREES[1],
    // causing loadProgressiveSpine to return LeafNode.EMPTY_LEAF instead of a BranchNode.
    // Accessing any element then fails with "Invalid root index: 2".
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasUtil = new DataStructureUtil(gloasSpec);
    final V6SchemaCombinedTreeState gloasSchema = new V6SchemaCombinedTreeState(gloasSpec);
    final KvStoreAccessor gloasDb =
        MockKvStoreInstance.createEmpty(gloasSchema.getAllColumns(), gloasSchema.getAllVariables());
    final V4FinalizedStateTreeStorageLogic gloasLogic =
        new V4FinalizedStateTreeStorageLogic(new NoOpMetricsSystem(), gloasSpec, 1000);

    final BeaconState randomState = gloasUtil.randomBeaconState();
    final int validatorCount = randomState.getValidators().size();
    final BeaconState state =
        randomState.updated(
            mutable -> {
              final MutableBeaconStateGloas mutableGloas =
                  MutableBeaconStateGloas.required(mutable);
              final var zeros = Collections.nCopies(validatorCount, SszByte.of((byte) 0));
              mutableGloas.setPreviousEpochParticipation(
                  mutableGloas
                      .getPreviousEpochParticipation()
                      .getSchema()
                      .createFromElements(zeros));
              mutableGloas.setCurrentEpochParticipation(
                  mutableGloas
                      .getCurrentEpochParticipation()
                      .getSchema()
                      .createFromElements(zeros));
            });

    try (final KvStoreTransaction transaction = gloasDb.startTransaction()) {
      gloasLogic.updater().addFinalizedState(gloasDb, transaction, gloasSchema, state);
      transaction.commit();
    }
    final Optional<BeaconState> loaded =
        gloasLogic.getLatestAvailableFinalizedState(gloasDb, gloasSchema, state.getSlot());
    assertThat(loaded).contains(state);

    // Verify elements are actually accessible (not just hash-equal), which was the failure mode:
    // accessing participation.get(0) on a loaded Gloas state with all-zero participation threw
    // "Invalid root index: 2" because loadProgressiveSpine returned LeafNode.EMPTY_LEAF
    // when the progressive data tree hash collides with ZERO_TREES[1].
    final BeaconStateGloas loadedGloas = BeaconStateGloas.required(loaded.get());
    assertThatCode(() -> loadedGloas.getPreviousEpochParticipation().get(0))
        .doesNotThrowAnyException();
    assertThatCode(() -> loadedGloas.getCurrentEpochParticipation().get(0))
        .doesNotThrowAnyException();
    assertThat(loadedGloas.getPreviousEpochParticipation().get(0).get()).isEqualTo((byte) 0);
    assertThat(loadedGloas.getCurrentEpochParticipation().get(0).get()).isEqualTo((byte) 0);
  }

  private void storeState(final BeaconState state) {
    try (final KvStoreTransaction transaction = db.startTransaction()) {
      logic.updater().addFinalizedState(db, transaction, schema, state);
      transaction.commit();
    }
  }
}

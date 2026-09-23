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

package tech.pegasys.teku.statetransition.lightclient;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.api.StoredLightClientUpdate;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * Covers the wiring from {@link LightClientServerService} through {@link LightClientUpdateStore}
 * into real storage, for the two paths a devnet does not reach: a reorg orphaning the block an
 * update was built from, and finalization pruning periods beyond the retention window.
 *
 * <p>{@link LightClientUpdateStoreTest} checks the same transitions against a mock channel; this
 * asserts the rows actually reach and leave the database.
 */
class LightClientUpdatePersistenceTest {

  private static final BiPredicate<UInt64, Bytes32> CANONICAL = (slot, root) -> true;
  private static final BiPredicate<UInt64, Bytes32> ORPHANED = (slot, root) -> false;

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  private LightClientUpdateStore store;

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    store = new LightClientUpdateStore(spec, storageSystem.chainStorage());
  }

  @Test
  void reorg_shouldRemoveOrphanedUpdatesFromStorage() {
    store.addUpdate(updateAtPeriod(1), dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(storedPeriods()).containsExactly(UInt64.ONE);

    // the signature block is no longer on the canonical chain
    chainHeadUpdatedWithReorg(ORPHANED);

    assertThat(storedPeriods()).isEmpty();
  }

  @Test
  void reorg_shouldKeepUpdatesThatAreStillCanonical() {
    store.addUpdate(updateAtPeriod(1), dataStructureUtil.randomBytes32(), CANONICAL);

    chainHeadUpdatedWithReorg(CANONICAL);

    assertThat(storedPeriods()).containsExactly(UInt64.ONE);
  }

  @Test
  void finalization_shouldPruneStorageBeyondTheRetentionWindow() {
    final UInt64 finalizedPeriod =
        UInt64.valueOf(LightClientServerService.MAX_RETAINED_PERIODS + 2);
    final UInt64 retainedPeriod = finalizedPeriod.minus(1);
    store.addUpdate(updateAtPeriod(1), dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(
        updateAtPeriod(retainedPeriod.longValue()), dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(storedPeriods()).containsExactly(UInt64.ONE, retainedPeriod);

    serviceWith(CANONICAL)
        .onNewFinalizedCheckpoint(
            new Checkpoint(epochOfPeriod(finalizedPeriod), dataStructureUtil.randomBytes32()),
            false);

    // the cutoff is finalizedPeriod - MAX_RETAINED_PERIODS, so period 1 goes and the recent one
    // stays
    assertThat(storedPeriods()).containsExactly(retainedPeriod);
  }

  private void chainHeadUpdatedWithReorg(final BiPredicate<UInt64, Bytes32> isCanonical) {
    serviceWith(isCanonical)
        .chainHeadUpdated(
            UInt64.ONE,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false,
            false,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Optional.empty(),
            ReorgContext.of(
                dataStructureUtil.randomBytes32(),
                UInt64.ONE,
                dataStructureUtil.randomBytes32(),
                UInt64.ZERO,
                dataStructureUtil.randomBytes32()));
  }

  private LightClientServerService serviceWith(final BiPredicate<UInt64, Bytes32> isCanonical) {
    return new LightClientServerService(
        spec,
        store,
        root -> completedFuture(Optional.empty()),
        root -> completedFuture(Optional.empty()),
        isCanonical);
  }

  private List<UInt64> storedPeriods() {
    return safeJoin(storageSystem.chainStorage().getBestLightClientUpdates()).stream()
        .map(StoredLightClientUpdate::period)
        .sorted()
        .toList();
  }

  private LightClientUpdate updateAtPeriod(final long period) {
    return dataStructureUtil
        .createRandomLightClientUpdateBuilder(periodOneStartSlot().times(period))
        .build();
  }

  private UInt64 epochOfPeriod(final UInt64 period) {
    return spec.computeEpochAtSlot(periodOneStartSlot().times(period));
  }

  private UInt64 periodOneStartSlot() {
    return spec.computeStartSlotAtEpoch(
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO));
  }
}

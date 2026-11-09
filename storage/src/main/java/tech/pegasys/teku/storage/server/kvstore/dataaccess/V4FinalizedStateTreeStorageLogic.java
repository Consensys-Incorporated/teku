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
import java.util.Set;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;

public class V4FinalizedStateTreeStorageLogic
    implements V4FinalizedStateStorageLogic<SchemaCombinedTreeState> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_BRANCH_LEVELS_SKIPPED = 5;
  private final LabelledMetric<Counter> branchNodeStoredCounter;
  private final Counter statesStoredCounter;
  private final Set<Bytes32> knownStoredBranchesCache;
  private final Spec spec;
  private final Counter leafNodeStoredCounter;

  public V4FinalizedStateTreeStorageLogic(
      final MetricsSystem metricsSystem, final Spec spec, final int maxKnownNodeCacheSize) {
    this.spec = spec;
    this.knownStoredBranchesCache = LimitedSet.createSynchronizedLRU(maxKnownNodeCacheSize);
    this.branchNodeStoredCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.STORAGE_FINALIZED_DB,
            "state_branch_nodes_total",
            "Number of finalized state tree branch nodes stored vs skipped",
            "type");
    this.leafNodeStoredCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.STORAGE_FINALIZED_DB,
            "state_leaf_nodes_total",
            "Number of finalized state tree leaf nodes stored");
    statesStoredCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.STORAGE_FINALIZED_DB,
            "states_stored_total",
            "Number of finalized states stored");
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(
      final KvStoreAccessor db, final SchemaCombinedTreeState dbSchema, final UInt64 maxSlot) {
    return db.getFloorEntry(dbSchema.getColumnFinalizedStateRootsBySlot(), maxSlot)
        .map(
            entry ->
                spec.atSlot(entry.getKey())
                    .getSchemaDefinitions()
                    .getBeaconStateSchema()
                    .load(
                        new KvStoreTreeNodeSource(db, dbSchema),
                        entry.getValue(),
                        GIndexUtil.SELF_G_INDEX));
  }

  @Override
  public Optional<UInt64> getEarliestAvailableFinalizedStateSlot(
      final KvStoreAccessor db, final SchemaCombinedTreeState dbSchema) {
    return db.getFirstEntry(dbSchema.getColumnFinalizedStateRootsBySlot()).map(ColumnEntry::getKey);
  }

  @Override
  public FinalizedStateUpdater<SchemaCombinedTreeState> updater() {
    return new StateTreeUpdater(
        knownStoredBranchesCache,
        branchNodeStoredCounter,
        statesStoredCounter,
        leafNodeStoredCounter);
  }

  @Override
  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(
      final KvStoreAccessor db,
      final SchemaCombinedTreeState schema,
      final UInt64 startSlot,
      final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedStateRootsBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getKey);
  }

  private static class StateTreeUpdater implements FinalizedStateUpdater<SchemaCombinedTreeState> {

    private final Set<Bytes32> knownStoredBranchesCache;
    private final LabelledMetric<Counter> branchNodeStoredCounter;
    private final Counter statesStoredCounter;
    private final Counter leafNodeStoredCounter;
    private TreeNodeStore nodeStore;
    private int statesStored = 0;

    private StateTreeUpdater(
        final Set<Bytes32> knownStoredBranchesCache,
        final LabelledMetric<Counter> branchNodeStoredCounter,
        final Counter statesStoredCounter,
        final Counter leafNodeStoredCounter) {
      this.knownStoredBranchesCache = knownStoredBranchesCache;
      this.branchNodeStoredCounter = branchNodeStoredCounter;
      this.statesStoredCounter = statesStoredCounter;
      this.leafNodeStoredCounter = leafNodeStoredCounter;
    }

    @Override
    public void addFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final SchemaCombinedTreeState schema,
        final BeaconState state) {
      if (nodeStore == null) {
        nodeStore = new KvStoreTreeNodeStore(knownStoredBranchesCache, db, transaction, schema);
      }
      transaction.put(
          schema.getColumnFinalizedStateRootsBySlot(), state.getSlot(), state.hashTreeRoot());
      final int branchesBefore = nodeStore.getStoredBranchNodeCount();
      final int leavesBefore = nodeStore.getStoredLeafNodeCount();
      state
          .getSchema()
          .storeBackingNodes(
              nodeStore,
              MAX_BRANCH_LEVELS_SKIPPED,
              GIndexUtil.SELF_G_INDEX,
              state.getBackingNode());
      statesStored++;
      LOG.debug(
          "Wrote finalized state delta slot={} root={} branchNodes={} skipped={} leafNodes={}",
          state.getSlot(),
          state.hashTreeRoot(),
          nodeStore.getStoredBranchNodeCount() - branchesBefore,
          nodeStore.getSkippedBranchNodeCount(),
          nodeStore.getStoredLeafNodeCount() - leavesBefore);
    }

    @Override
    public void addReconstructedFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final SchemaCombinedTreeState schema,
        final BeaconState state) {
      addFinalizedState(db, transaction, schema, state);
    }

    @Override
    public void deleteFinalizedState(
        final KvStoreTransaction transaction,
        final SchemaCombinedTreeState schema,
        final UInt64 slot) {
      throw new RuntimeException("Tree mode does not support deleting finalized states");
    }

    @Override
    public void commit() {
      if (nodeStore != null) {
        knownStoredBranchesCache.addAll(nodeStore.getStoredBranchRoots());
        branchNodeStoredCounter.labels("stored").inc(nodeStore.getStoredBranchNodeCount());
        branchNodeStoredCounter.labels("skipped").inc(nodeStore.getSkippedBranchNodeCount());
        leafNodeStoredCounter.inc(nodeStore.getStoredLeafNodeCount());
        statesStoredCounter.inc(statesStored);
      }
    }
  }
}

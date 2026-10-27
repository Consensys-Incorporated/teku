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

package tech.pegasys.teku.storage.server.kvstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.FinalizedChainData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.KvStoreDatabase.DataColumnSidecarType;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.HotUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;

class KvStoreDatabaseTest {

  // Data column sidecar pruning is forward (oldest-first): each run removes the oldest up-to-limit
  // distinct populated slots at or before the cutoff, so the earliest retained slot keeps advancing
  // and no gap can form when sidecars are stored faster than they can be pruned. For canonical
  // sidecars the scan resumes from the persisted prune watermark so consecutive runs - and runs
  // after a restart - continue from where the previous run stopped instead of re-scanning
  // already-pruned slots.

  private final NavigableSet<UInt64> populatedSlots = new TreeSet<>();
  private final List<SlotRange> streamedRanges = new ArrayList<>();
  private final List<UInt64> removedSlots = new ArrayList<>();
  private final AtomicInteger commits = new AtomicInteger();

  // Stands in for the persisted LAST_SLOT_DATA_COLUMN_PRUNED variable.
  private Optional<UInt64> prunedWatermark = Optional.empty();

  // Orphaned execution payload detection runs on every finalization once Gloas is in the fork
  // schedule. Before the fork, and for pre-Gloas parents at the fork boundary, no block body may
  // be read from the database: nothing can have an execution payload envelope.

  private final UInt64 gloasForkEpoch = UInt64.valueOf(1000);
  private final Spec gloasScheduledSpec =
      TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch);
  private final DataStructureUtil gloasDataStructureUtil =
      new DataStructureUtil(gloasScheduledSpec);
  private final KvStoreCombinedDao gloasDao = mock(KvStoreCombinedDao.class);
  private final KvStoreDatabase gloasDatabase =
      new KvStoreDatabase(gloasDao, StateStorageMode.PRUNE, false, gloasScheduledSpec);

  @Test
  void findOrphanedExecutionPayloadRoots_shouldNotReadBlocksWhenFinalizedEpochIsBeforeGloas() {
    final Bytes32 childRoot = gloasDataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = gloasDataStructureUtil.randomBytes32();

    final Set<Bytes32> orphanedRoots =
        gloasDatabase.findOrphanedExecutionPayloadRoots(
            Optional.of(new Checkpoint(gloasForkEpoch.minus(1), childRoot)),
            Map.of(childRoot, parentRoot),
            Map.of(),
            Map.of());

    assertThat(orphanedRoots).isEmpty();
    verify(gloasDao, never()).getHotBlock(any());
    verify(gloasDao, never()).getFinalizedBlock(any());
  }

  @Test
  void findOrphanedExecutionPayloadRoots_shouldNotReadPreGloasParentsAtForkBoundary() {
    final Bytes32 childRoot = gloasDataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = gloasDataStructureUtil.randomBytes32();
    final UInt64 preGloasSlot = gloasScheduledSpec.computeStartSlotAtEpoch(gloasForkEpoch).minus(1);

    final Set<Bytes32> orphanedRoots =
        gloasDatabase.findOrphanedExecutionPayloadRoots(
            Optional.of(new Checkpoint(gloasForkEpoch, childRoot)),
            Map.of(childRoot, parentRoot),
            Map.of(),
            // the pruned hot blocks carry the parent slot, so no block has to be read
            Map.of(parentRoot, preGloasSlot));

    assertThat(orphanedRoots).isEmpty();
    verify(gloasDao, never()).getHotBlock(any());
    verify(gloasDao, never()).getFinalizedBlock(any());
  }

  @Test
  void findOrphanedExecutionPayloadRoots_shouldReadEachFinalizedBlockOnceWalkingTheChain() {
    // Gloas from genesis: every finalized block may have an envelope
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder chainBuilder = ChainBuilder.create(gloasSpec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(2);
    // The block at slot 3 builds on the EMPTY variant of the block at slot 2
    chainBuilder.generateBlockAtSlot(
        3, chainBuilder.createBlockOptionsBuildingOnEmptyParent(UInt64.valueOf(3)));
    chainBuilder.generateBlockAtSlot(4);
    final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
    when(dao.getHotBlock(any()))
        .thenAnswer(invocation -> chainBuilder.getBlock(invocation.getArgument(0)));
    final KvStoreDatabase database =
        new KvStoreDatabase(dao, StateStorageMode.PRUNE, false, gloasSpec);
    final Map<Bytes32, Bytes32> finalizedChildToParent = new HashMap<>();
    chainBuilder
        .streamBlocksAndStates(1, 4)
        .forEach(block -> finalizedChildToParent.put(block.getRoot(), block.getParentRoot()));

    final Set<Bytes32> orphanedRoots =
        database.findOrphanedExecutionPayloadRoots(
            Optional.of(new Checkpoint(UInt64.ZERO, chainBuilder.getBlockAtSlot(4).getRoot())),
            finalizedChildToParent,
            Map.of(),
            Map.of());

    // The genesis block is EMPTY too: the block at slot 1 builds on the EL genesis block
    assertThat(orphanedRoots)
        .containsExactlyInAnyOrder(genesis.getRoot(), chainBuilder.getBlockAtSlot(2).getRoot());
    // Each block of the chain, down to the parent of the oldest finalized block, is read once
    chainBuilder
        .streamBlocksAndStates(1, 4)
        .forEach(block -> verify(dao, times(1)).getHotBlock(block.getRoot()));
    verify(dao, times(1)).getHotBlock(genesis.getRoot());
    verify(dao, never()).getFinalizedBlock(any());
  }

  @Test
  void update_shouldWriteAndDeleteEnvelopesInTheSameTransactionAsTheFinalizedBlockInPruneMode() {
    assertEnvelopesAreUpdatedInTheSameTransactionAsTheFinalizedBlock(StateStorageMode.PRUNE);
  }

  @Test
  void update_shouldWriteAndDeleteEnvelopesInTheSameTransactionAsTheFinalizedBlockInArchiveMode() {
    assertEnvelopesAreUpdatedInTheSameTransactionAsTheFinalizedBlock(StateStorageMode.ARCHIVE);
  }

  private void assertEnvelopesAreUpdatedInTheSameTransactionAsTheFinalizedBlock(
      final StateStorageMode storageMode) {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ChainBuilder chainBuilder = ChainBuilder.create(gloasSpec);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final SignedBlockAndState parent = chainBuilder.generateBlockAtSlot(1);
    // The child builds on the EMPTY variant of the parent, so the parent's envelope is orphaned
    final SignedBlockAndState child =
        chainBuilder.generateBlockAtSlot(
            2, chainBuilder.createBlockOptionsBuildingOnEmptyParent(UInt64.valueOf(2)));
    final SignedBlindedExecutionPayloadEnvelope parentEnvelope =
        chainBuilder.getExecutionPayloadAtSlot(parent.getSlot()).orElseThrow().blind(gloasSpec);
    final SignedBlindedExecutionPayloadEnvelope childEnvelope =
        chainBuilder.getExecutionPayloadAtSlot(child.getSlot()).orElseThrow().blind(gloasSpec);
    final Checkpoint anchor = new Checkpoint(UInt64.ZERO, genesis.getRoot());
    final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
    when(dao.getAnchor()).thenReturn(Optional.of(anchor));
    when(dao.getFinalizedCheckpoint()).thenReturn(Optional.of(anchor));
    when(dao.getHotBlock(any())).thenReturn(Optional.empty());
    when(dao.getHotBlock(genesis.getRoot())).thenReturn(Optional.of(genesis.getBlock()));
    when(dao.getHotBlockAsSsz(any())).thenReturn(Optional.empty());
    when(dao.getHotState(any())).thenReturn(Optional.empty());
    when(dao.getFinalizedBlock(any())).thenReturn(Optional.empty());
    final FinalizedUpdater finalizedUpdater = mock(FinalizedUpdater.class);
    final HotUpdater hotUpdater = mock(HotUpdater.class);
    final KvStoreDatabase database =
        new KvStoreDatabase(dao, storageMode, false, gloasSpec) {
          @Override
          protected FinalizedUpdater finalizedUpdater() {
            return finalizedUpdater;
          }

          @Override
          protected HotUpdater hotUpdater() {
            return hotUpdater;
          }
        };
    final FinalizedChainData finalizedChainData =
        FinalizedChainData.builder()
            .latestFinalized(AnchorPoint.fromInitialBlockAndState(gloasSpec, child))
            .finalizedChildAndParent(parent.getRoot(), parent.getParentRoot())
            // The previously finalized block is part of every finalization, as the base of the
            // finalized chain
            .finalizedChildAndParent(genesis.getRoot(), genesis.getParentRoot())
            .finalizedBlock(parent.getBlock())
            .build();

    // Both envelopes arrive with the finalization of their blocks
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.of(finalizedChainData),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(parent.getRoot(), parentEnvelope, child.getRoot(), childEnvelope),
            Map.of(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            true));

    // The orphaned envelope is deleted, and the chain's envelope written, by the transaction that
    // writes the respective block, before it commits; no separate envelope transaction is needed
    final InOrder parentOrder = inOrder(finalizedUpdater);
    parentOrder.verify(finalizedUpdater).addFinalizedBlock(parent.getBlock());
    parentOrder.verify(finalizedUpdater).deleteBlindedExecutionPayloadEnvelope(parent.getRoot());
    parentOrder.verify(finalizedUpdater).commit();
    final InOrder childOrder = inOrder(finalizedUpdater);
    childOrder.verify(finalizedUpdater).addFinalizedBlock(child.getBlock());
    childOrder
        .verify(finalizedUpdater)
        .addBlindedExecutionPayloadEnvelope(child.getRoot(), childEnvelope);
    childOrder.verify(finalizedUpdater).commit();
    verify(finalizedUpdater, never())
        .addBlindedExecutionPayloadEnvelope(eq(parent.getRoot()), any());
    verify(finalizedUpdater, times(1)).commit();
  }

  @Test
  void findOrphanedExecutionPayloadRoots_shouldReadGloasParents() {
    final Bytes32 childRoot = gloasDataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = gloasDataStructureUtil.randomBytes32();
    final UInt64 gloasSlot = gloasScheduledSpec.computeStartSlotAtEpoch(gloasForkEpoch);
    when(gloasDao.getHotBlock(any())).thenReturn(Optional.empty());
    when(gloasDao.getFinalizedBlock(any())).thenReturn(Optional.empty());

    gloasDatabase.findOrphanedExecutionPayloadRoots(
        Optional.of(new Checkpoint(gloasForkEpoch.plus(1), childRoot)),
        Map.of(childRoot, parentRoot),
        Map.of(),
        Map.of(parentRoot, gloasSlot));

    verify(gloasDao).getHotBlock(parentRoot);
  }

  @Test
  void prunesOldestDistinctSlotsInASingleCommit() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 28, 29, 30);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots).containsExactly(UInt64.valueOf(28), UInt64.valueOf(29));
    assertThat(commits).hasValue(1);
    // The watermark records the newest pruned slot (29).
    assertThat(prunedWatermark).contains(UInt64.valueOf(29));
  }

  @Test
  void prunesOnlyUpToTheLimitDistinctSlots() {
    final KvStoreDatabase database = database(UInt64.ZERO, 10, 11, 12, 13, 14);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);

    // Only the oldest 2 slots (10 and 11) are pruned.
    assertThat(removedSlots).containsExactly(UInt64.valueOf(10), UInt64.valueOf(11));
    assertThat(commits).hasValue(1);
    assertThat(prunedWatermark).contains(UInt64.valueOf(11));
  }

  @Test
  void prunesDistinctSlotsAcrossGaps() {
    // Only slots 1 and 100 are populated; with a limit of 2 both must be pruned even though there
    // are no slots in between them.
    final KvStoreDatabase database = database(UInt64.ZERO, 1, 100);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(100), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots).containsExactly(UInt64.ONE, UInt64.valueOf(100));
    assertThat(commits).hasValue(1);
    assertThat(prunedWatermark).contains(UInt64.valueOf(100));
  }

  @Test
  void stopsWhenFewerThanLimitDistinctSlotsAreAvailable() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 10, 11, 12);

    database.pruneDataColumnSidecars(10, UInt64.valueOf(12), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots)
        .containsExactly(UInt64.valueOf(10), UInt64.valueOf(11), UInt64.valueOf(12));
    assertThat(commits).hasValue(1);
    assertThat(prunedWatermark).contains(UInt64.valueOf(12));
  }

  @Test
  void coldStartScanBeginsAtFirstSupportedSlot() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 28, 29, 30);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    // With no persisted watermark, the forward scan starts at the first slot with data column
    // sidecar support.
    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(10), UInt64.valueOf(30)));
  }

  @Test
  void watermarkAdvancesSoConsecutiveRunsDoNotRescanPrunedSlots() {
    final KvStoreDatabase database = database(UInt64.ZERO, 10, 11, 12, 13, 14);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);
    database.pruneDataColumnSidecars(2, UInt64.valueOf(14), DataColumnSidecarType.CANONICAL);

    // Run 1 prunes 10,11 (watermark -> 11) then run 2 resumes from 12, never re-scanning from 0.
    assertThat(removedSlots)
        .containsExactly(
            UInt64.valueOf(10), UInt64.valueOf(11), UInt64.valueOf(12), UInt64.valueOf(13));
    assertThat(prunedWatermark).contains(UInt64.valueOf(13));
    assertThat(streamedRanges)
        .containsExactly(
            new SlotRange(UInt64.ZERO, UInt64.valueOf(14)),
            new SlotRange(UInt64.valueOf(12), UInt64.valueOf(14)));
    assertThat(commits).hasValue(2);
  }

  @Test
  void nonCanonicalScanIgnoresCanonicalWatermark() {
    final KvStoreDatabase database = database(UInt64.valueOf(10), 28, 29, 30);
    // A canonical watermark must not influence the non-canonical scan, which always starts at the
    // first supported slot.
    prunedWatermark = Optional.of(UInt64.valueOf(29));

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.NON_CANONICAL);

    assertThat(streamedRanges)
        .containsExactly(new SlotRange(UInt64.valueOf(10), UInt64.valueOf(30)));
    assertThat(removedSlots).containsExactly(UInt64.valueOf(28), UInt64.valueOf(29));
    // Non-canonical pruning leaves the canonical watermark untouched.
    assertThat(prunedWatermark).contains(UInt64.valueOf(29));
  }

  @Test
  void doesNothingWhenNoSidecarsAtOrBeforeCutoff() {
    final KvStoreDatabase database = database(UInt64.valueOf(10));

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(removedSlots).isEmpty();
    assertThat(commits).hasValue(0);
    assertThat(prunedWatermark).isEmpty();
  }

  @Test
  void doesNothingWhenFirstSupportedSlotIsAfterCutoff() {
    final KvStoreDatabase database = database(UInt64.valueOf(40), 50);

    database.pruneDataColumnSidecars(2, UInt64.valueOf(30), DataColumnSidecarType.CANONICAL);

    assertThat(streamedRanges).isEmpty();
    assertThat(removedSlots).isEmpty();
    assertThat(commits).hasValue(0);
    assertThat(prunedWatermark).isEmpty();
  }

  @Test
  void streamBestLightClientUpdatesSkipsUnreadableRows() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final V6SchemaCombinedSnapshot schema = V6SchemaCombinedSnapshot.createV6(spec);
    final MockKvStoreInstance db =
        MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());
    final Database database =
        KvStoreDatabase.createWithStateSnapshots(
            db, schema, StateStorageMode.PRUNE, 1, false, spec, new StubMetricsSystem());
    final LightClientUpdate update0 = dataStructureUtil.randomLightClientUpdate(UInt64.ZERO);
    final LightClientUpdate update2 = dataStructureUtil.randomLightClientUpdate(UInt64.valueOf(2));
    database.storeBestLightClientUpdate(UInt64.ZERO, update0, Bytes32.ZERO);
    database.storeBestLightClientUpdate(UInt64.valueOf(2), update2, Bytes32.ZERO);

    final KvStoreColumn<UInt64, LightClientUpdate> column =
        schema.getBestLightClientUpdatesByPeriod();
    try (final KvStoreTransaction transaction = db.startTransaction()) {
      transaction.putRaw(
          column,
          Bytes.wrap(column.getKeySerializer().serialize(UInt64.ONE)),
          Bytes.fromHexString("0xdeadbeef"));
      transaction.commit();
    }

    try (final Stream<Map.Entry<UInt64, LightClientUpdate>> updates =
        database.streamBestLightClientUpdates()) {
      assertThat(updates.toList())
          .extracting(Map.Entry::getKey, Map.Entry::getValue)
          .containsExactly(tuple(UInt64.ZERO, update0), tuple(UInt64.valueOf(2), update2));
    }
  }

  private KvStoreDatabase database(final UInt64 firstFuluSlot, final long... slots) {
    for (final long slot : slots) {
      populatedSlots.add(UInt64.valueOf(slot));
    }
    final Spec spec = mock(Spec.class);
    when(spec.computeFirstSlotWithDataColumnSidecarSupport())
        .thenReturn(Optional.of(firstFuluSlot));
    final KvStoreCombinedDao dao = mock(KvStoreCombinedDao.class);
    when(dao.getLastDataColumnSidecarPrunedSlot()).thenAnswer(__ -> prunedWatermark);
    return new KvStoreDatabase(dao, StateStorageMode.PRUNE, false, spec) {

      @Override
      protected FinalizedUpdater finalizedUpdater() {
        return countingUpdater();
      }

      @Override
      public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
          final UInt64 firstSlot, final UInt64 lastSlot) {
        return streamPopulated(firstSlot, lastSlot);
      }

      @Override
      public Stream<DataColumnSlotAndIdentifier> streamNonCanonicalDataColumnIdentifiers(
          final UInt64 firstSlot, final UInt64 lastSlot) {
        return streamPopulated(firstSlot, lastSlot);
      }
    };
  }

  private Stream<DataColumnSlotAndIdentifier> streamPopulated(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    streamedRanges.add(new SlotRange(firstSlot, lastSlot));
    return populatedSlots.subSet(firstSlot, true, lastSlot, true).stream()
        .map(slot -> identifier(slot.longValue(), 0));
  }

  private FinalizedUpdater countingUpdater() {
    final FinalizedUpdater updater = mock(FinalizedUpdater.class);
    doAnswer(this::recordRemovedSlot).when(updater).removeSidecar(any());
    doAnswer(this::recordRemovedSlot).when(updater).removeNonCanonicalSidecar(any());
    doAnswer(
            (final InvocationOnMock invocation) -> {
              prunedWatermark = Optional.of(invocation.getArgument(0));
              return null;
            })
        .when(updater)
        .setLastDataColumnSidecarPrunedSlot(any());
    doAnswer(
            (final InvocationOnMock __) -> {
              commits.incrementAndGet();
              return null;
            })
        .when(updater)
        .commit();
    return updater;
  }

  private Void recordRemovedSlot(final InvocationOnMock invocation) {
    final DataColumnSlotAndIdentifier key = invocation.getArgument(0);
    removedSlots.add(key.slot());
    populatedSlots.remove(key.slot());
    return null;
  }

  private static DataColumnSlotAndIdentifier identifier(final long slot, final long columnIndex) {
    return new DataColumnSlotAndIdentifier(
        UInt64.valueOf(slot), Bytes32.ZERO, UInt64.valueOf(columnIndex));
  }

  private record SlotRange(UInt64 firstSlot, UInt64 lastSlot) {}
}

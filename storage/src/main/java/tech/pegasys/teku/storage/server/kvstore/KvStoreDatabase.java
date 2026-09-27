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

package tech.pegasys.teku.storage.server.kvstore;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.groupingBy;
import static tech.pegasys.teku.infrastructure.logging.DbLogger.DB_LOGGER;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.GloasForkChoiceRebuildData;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.CombinedKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.CombinedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.HotUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoAdapter;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateSnapshotStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateTreeStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4HotKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombined;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedSnapshotState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.state.StateRootRecorder;

public class KvStoreDatabase implements Database {

  protected static final int TX_BATCH_SIZE = 500;
  protected static final int BLOBS_TX_BATCH_SIZE = 100;
  private static final Logger LOG = LogManager.getLogger();
  protected final Spec spec;
  protected final boolean storeNonCanonicalBlocks;
  @VisibleForTesting final KvStoreCombinedDao dao;
  private final StateStorageMode stateStorageMode;

  enum DataColumnSidecarType {
    CANONICAL("canonical"),
    NON_CANONICAL("non-canonical");

    private final String displayName;

    DataColumnSidecarType(final String displayName) {
      this.displayName = displayName;
    }

    String displayName() {
      return displayName;
    }
  }

  KvStoreDatabase(
      final KvStoreCombinedDao dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    this.dao = dao;
    checkNotNull(spec);
    this.stateStorageMode = stateStorageMode;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.spec = spec;
  }

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final SchemaHotAdapter schemaHot,
      final SchemaFinalizedSnapshotStateAdapter schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaFinalizedSnapshotStateAdapter>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    final V4HotKvStoreDao hotDao = new V4HotKvStoreDao(hotDb, schemaHot);
    final KvStoreCombinedDaoAdapter dao =
        new KvStoreCombinedDaoAdapter(
            hotDao,
            new V4FinalizedKvStoreDao(
                metricsSystem, finalizedDb, schemaFinalized, finalizedStateStorageLogic));
    return new KvStoreDatabase(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  public static Database createWithStateSnapshots(
      final KvStoreAccessor db,
      final SchemaCombinedSnapshotState schema,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final MetricsSystem metricsSystem) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaCombinedSnapshotState>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    return create(
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        spec,
        finalizedStateStorageLogic,
        metricsSystem);
  }

  public static Database createWithStateTree(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor db,
      final SchemaCombinedTreeState schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Spec spec) {
    final V4FinalizedStateTreeStorageLogic finalizedStateStorageLogic =
        new V4FinalizedStateTreeStorageLogic(metricsSystem, spec, maxKnownNodeCacheSize);
    return create(
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        spec,
        finalizedStateStorageLogic,
        metricsSystem);
  }

  private static <S extends SchemaCombined> KvStoreDatabase create(
      final KvStoreAccessor db,
      final S schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final V4FinalizedStateStorageLogic<S> finalizedStateStorageLogic,
      final MetricsSystem metricsSystem) {
    final CombinedKvStoreDao<S> dao =
        new CombinedKvStoreDao<>(db, schema, finalizedStateStorageLogic, metricsSystem);
    return new KvStoreDatabase(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  @MustBeClosed
  protected CombinedUpdater combinedUpdater() {
    return dao.combinedUpdater();
  }

  @MustBeClosed
  protected HotUpdater hotUpdater() {
    return dao.hotUpdater();
  }

  @MustBeClosed
  protected FinalizedUpdater finalizedUpdater() {
    return dao.finalizedUpdater();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return dao.getSlotForFinalizedBlockRoot(blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return dao.getSlotForFinalizedStateRoot(stateRoot);
  }

  @Override
  public Optional<Bytes32> getLatestCanonicalBlockRoot() {
    return dao.getLatestCanonicalBlockRoot();
  }

  @Override
  public Optional<UInt64> getCustodyGroupCount() {
    return dao.getCustodyGroupCount();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return dao.getEarliestFinalizedBlockSlot();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return dao.getEarliestFinalizedBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getLastAvailableFinalizedBlock() {
    return dao.getFinalizedCheckpoint()
        .flatMap(
            checkpoint -> getFinalizedBlock(checkpoint.toSlotAndBlockRoot(spec).getBlockRoot()));
  }

  @Override
  public Optional<Bytes32> getFinalizedBlockRootBySlot(final UInt64 slot) {
    return dao.getFinalizedBlockAtSlot(slot).map(SignedBeaconBlock::getRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return dao.getHotBlock(root)
        .or(() -> dao.getFinalizedBlock(root))
        .or(() -> dao.getNonCanonicalBlock(root));
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> dao.getHotBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return dao.getHotBlock(blockRoot);
  }

  @Override
  public Optional<SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloadEnvelope(
      final Bytes32 blockRoot) {
    return dao.getBlindedExecutionPayloadEnvelope(blockRoot);
  }

  @Override
  public Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloadEnvelopes(
      final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> dao.getBlindedExecutionPayloadEnvelope(root).stream())
        .collect(
            Collectors.toMap(
                SignedBlindedExecutionPayloadEnvelope::getBeaconBlockRoot,
                Function.identity(),
                (existing, duplicate) -> {
                  throw new IllegalStateException(
                      "Duplicate blinded execution payload envelope in DB for block root "
                          + existing.getBeaconBlockRoot());
                }));
  }

  @Override
  public Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz() {
    return dao.streamHotBlocksAsSsz();
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamFinalizedBlocks(startSlot, endSlot);
  }

  protected Map<Bytes32, StoredBlockMetadata> buildHotBlockMetadata() {
    final Map<Bytes32, StoredBlockMetadata> blockInformation = new HashMap<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = dao.streamHotBlocks()) {
      hotBlocks.forEach(
          b -> {
            final Optional<BlockCheckpoints> checkpointEpochs =
                dao.getHotBlockCheckpointEpochs(b.getRoot());
            final Optional<ExecutionPayload> executionPayload =
                b.getMessage().getBody().getOptionalExecutionPayload();
            final Optional<GloasForkChoiceRebuildData> gloasForkChoiceRebuildData =
                StoredBlockMetadata.extractGloasForkChoiceRebuildData(
                    b, dao.getBlindedExecutionPayloadEnvelope(b.getRoot()));
            blockInformation.put(
                b.getRoot(),
                new StoredBlockMetadata(
                    b.getSlot(),
                    b.getRoot(),
                    b.getParentRoot(),
                    b.getStateRoot(),
                    executionPayload.map(ExecutionPayload::getBlockNumber),
                    executionPayload.map(ExecutionPayload::getBlockHash),
                    executionPayload.map(ExecutionPayload::getGasLimit),
                    checkpointEpochs,
                    gloasForkChoiceRebuildData));
          });
    }
    return blockInformation;
  }

  protected void storeAnchorStateAndBlock(
      final CombinedUpdater updater, final BeaconState anchorState, final SignedBeaconBlock block) {
    updater.addHotBlock(
        new BlockAndCheckpoints(
            block,
            new BlockCheckpoints(
                anchorState.getCurrentJustifiedCheckpoint(),
                anchorState.getFinalizedCheckpoint(),
                anchorState.getCurrentJustifiedCheckpoint(),
                anchorState.getFinalizedCheckpoint())));
    // Save to cold storage
    updater.addFinalizedBlock(block);
  }

  @Override
  public Optional<SignedBeaconBlock> getNonCanonicalBlockByRoot(final Bytes32 blockRoot) {
    return dao.getNonCanonicalBlock(blockRoot);
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return dao.getNonCanonicalBlocksAtSlot(slot);
  }

  protected void storeFinalizedBlocksToDao(
      final Collection<SignedBeaconBlock> blocks,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> finalizedBlobSidecarsBySlot,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Optional<UInt64> maybeEarliestBlobSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      blocks.forEach(
          block -> {
            updater.addFinalizedBlock(block);
            final Optional<SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayload =
                Optional.ofNullable(blindedExecutionPayloads.get(block.getRoot()));
            blindedExecutionPayload.ifPresent(
                envelope -> updater.addBlindedExecutionPayloadEnvelope(block.getRoot(), envelope));
            // If there is no slot in BlobSidecar's map it means we are pre-Deneb or not in
            // availability period
            if (!finalizedBlobSidecarsBySlot.containsKey(block.getSlotAndBlockRoot())) {
              return;
            }
            finalizedBlobSidecarsBySlot
                .get(block.getSlotAndBlockRoot())
                .forEach(updater::addBlobSidecar);
          });

      needToUpdateEarliestBlockSlot(blocks.stream().findFirst())
          .ifPresent(updater::setEarliestBlockSlot);
      needToUpdateEarliestBlobSidecarSlot(maybeEarliestBlobSidecar)
          .ifPresent(updater::setEarliestBlobSidecarSlot);
      updater.commit();
    }
  }

  private Optional<UInt64> needToUpdateEarliestBlockSlot(
      final Optional<SignedBeaconBlock> maybeNewEarliestBlockSlot) {
    // New value is absent - not updating
    if (maybeNewEarliestBlockSlot.isEmpty()) {
      return Optional.empty();
    }
    // New value is present, value from DB is absent - updating
    final Optional<UInt64> maybeEarliestFinalizedBlockSlotDb = dao.getEarliestFinalizedBlockSlot();
    if (maybeEarliestFinalizedBlockSlotDb.isEmpty()) {
      return maybeNewEarliestBlockSlot.map(SignedBeaconBlock::getSlot);
    }
    // New value is smaller than value from DB - updating
    final UInt64 newEarliestBlockSlot = maybeNewEarliestBlockSlot.get().getSlot();
    if (newEarliestBlockSlot.isLessThan(maybeEarliestFinalizedBlockSlotDb.get())) {
      return maybeNewEarliestBlockSlot.map(SignedBeaconBlock::getSlot);
    } else {
      return Optional.empty();
    }
  }

  private Optional<UInt64> needToUpdateEarliestBlobSidecarSlot(
      final Optional<UInt64> maybeNewEarliestBlobSidecarSlot) {
    // New value is absent - not updating
    if (maybeNewEarliestBlobSidecarSlot.isEmpty()) {
      return Optional.empty();
    }
    // New value is present, value from DB is absent - updating
    final Optional<UInt64> maybeEarliestFinalizedBlockSlotDb = dao.getEarliestFinalizedBlockSlot();
    if (maybeEarliestFinalizedBlockSlotDb.isEmpty()) {
      return maybeNewEarliestBlobSidecarSlot;
    }
    // New value is smaller than value from DB - updating
    final UInt64 newEarliestBlobSidecarSlot = maybeNewEarliestBlobSidecarSlot.get();
    if (newEarliestBlobSidecarSlot.isLessThan(maybeEarliestFinalizedBlockSlotDb.get())) {
      return maybeNewEarliestBlobSidecarSlot;
    } else {
      return Optional.empty();
    }
  }

  protected Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return dao.getHotBlock(root);
  }

  @Override
  public Map<String, Long> getColumnCounts(final Optional<String> maybeColumnFilter) {
    return dao.getColumnCounts(maybeColumnFilter);
  }

  @Override
  public Map<String, Optional<String>> getVariables() {
    return dao.getVariables();
  }

  @Override
  public long getBlobSidecarColumnCount() {
    return dao.getBlobSidecarColumnCount();
  }

  @Override
  public long getSidecarColumnCount() {
    return dao.getSidecarColumnCount();
  }

  @Override
  public long getNonCanonicalBlobSidecarColumnCount() {
    return dao.getNonCanonicalBlobSidecarColumnCount();
  }

  @Override
  public void deleteHotBlocks(final Set<Bytes32> blockRootsToDelete) {
    try (final HotUpdater updater = hotUpdater()) {
      blockRootsToDelete.forEach(updater::deleteHotBlock);
      updater.commit();
    }
  }

  @Override
  public UInt64 pruneFinalizedBlocks(
      final UInt64 lastSlotToPrune, final int pruneLimit, final UInt64 checkpointInitialSlot) {
    final Optional<UInt64> earliestFinalizedBlockSlot = dao.getEarliestFinalizedBlockSlot();
    LOG.debug(
        "Earliest block slot stored is {}",
        () -> earliestFinalizedBlockSlot.map(UInt64::toString).orElse("EMPTY"));
    if (earliestFinalizedBlockSlot.isEmpty()) {
      return lastSlotToPrune;
    }
    return pruneToBlock(
        lastSlotToPrune, earliestFinalizedBlockSlot.get(), pruneLimit, checkpointInitialSlot);
  }

  private UInt64 pruneToBlock(
      final UInt64 lastSlotToPrune,
      final UInt64 earliestFinalizedBlockSlot,
      final int pruneLimit,
      final UInt64 checkpointInitialSlot) {
    final List<Pair<UInt64, Bytes32>> blocksToPrune;
    final Optional<UInt64> earliestSlotAvailableAfterPrune;
    LOG.debug("Pruning finalized blocks to slot {} (included)", lastSlotToPrune);
    if (lastSlotToPrune.isLessThanOrEqualTo(earliestFinalizedBlockSlot)) {
      LOG.debug(
          "Last slot to prune {} was lower than the earliest finalized block slot in the database {}",
          lastSlotToPrune,
          earliestFinalizedBlockSlot);
      return lastSlotToPrune;
    }
    try (final Stream<SignedBeaconBlock> stream =
        dao.streamFinalizedBlocks(earliestFinalizedBlockSlot, lastSlotToPrune)) {
      // get an extra block to set earliest finalized block slot available after pruning runs
      // ensuring it is an existing block in the DB
      blocksToPrune =
          stream.limit(pruneLimit).map(block -> Pair.of(block.getSlot(), block.getRoot())).toList();
    }

    if (blocksToPrune.isEmpty()) {
      LOG.debug("No finalized blocks to prune up to {} slot", lastSlotToPrune);
      return lastSlotToPrune;
    }

    try (final Stream<SignedBeaconBlock> stream =
        dao.streamFinalizedBlocks(earliestFinalizedBlockSlot, checkpointInitialSlot)) {

      earliestSlotAvailableAfterPrune =
          stream
              .map(SignedBeaconBlock::getSlot)
              .filter(slot -> slot.isGreaterThan(blocksToPrune.getLast().getLeft()))
              .findFirst();
    }

    final UInt64 lastPrunedBlockSlot = blocksToPrune.getLast().getKey();
    LOG.debug(
        "Pruning {} finalized blocks, last block slot is {}",
        blocksToPrune.size(),
        lastPrunedBlockSlot);
    deleteFinalizedBlocks(blocksToPrune, earliestSlotAvailableAfterPrune);

    return blocksToPrune.size() < pruneLimit ? lastSlotToPrune : lastPrunedBlockSlot;
  }

  private void deleteFinalizedBlocks(
      final List<Pair<UInt64, Bytes32>> blocksToPrune,
      final Optional<UInt64> earliestSlotAvailableAfterPrune) {
    if (blocksToPrune.size() > 0) {
      if (blocksToPrune.size() < 20) {
        LOG.debug(
            "Received blocks ({}) to delete",
            () -> blocksToPrune.stream().map(Pair::getLeft).toList());
      } else {
        LOG.debug("Received {} finalized blocks to delete", blocksToPrune.size());
      }

      try (final FinalizedUpdater updater = finalizedUpdater()) {
        blocksToPrune.forEach(
            pair -> updater.deleteFinalizedBlock(pair.getLeft(), pair.getRight()));
        earliestSlotAvailableAfterPrune.ifPresentOrElse(
            updater::setEarliestBlockSlot, updater::deleteEarliestBlockSlot);
        updater.commit();
      }
    }
  }

  @Override
  public Optional<UInt64> pruneFinalizedStates(
      final Optional<UInt64> lastPrunedSlot, final UInt64 lastSlotToPrune, final long pruneLimit) {
    final Optional<UInt64> earliestFinalizedStateSlot;

    if (lastPrunedSlot.isEmpty()) {
      earliestFinalizedStateSlot = dao.getEarliestFinalizedStateSlot();
    } else {
      earliestFinalizedStateSlot = lastPrunedSlot;
    }

    LOG.debug(
        "Earliest finalized state stored is for slot {}",
        () ->
            earliestFinalizedStateSlot.isEmpty()
                ? "EMPTY"
                : earliestFinalizedStateSlot.get().toString());
    return earliestFinalizedStateSlot
        .map(uInt64 -> pruneFinalizedStateForSlots(uInt64, lastSlotToPrune, pruneLimit))
        .or(() -> Optional.of(lastSlotToPrune));
  }

  private UInt64 pruneFinalizedStateForSlots(
      final UInt64 earliestFinalizedStateSlot,
      final UInt64 lastSlotToPrune,
      final long pruneLimit) {
    final List<Pair<UInt64, Optional<Bytes32>>> slotsToPruneStateFor;

    try (final Stream<UInt64> stream =
        dao.streamFinalizedStateSlots(earliestFinalizedStateSlot, lastSlotToPrune)) {
      slotsToPruneStateFor =
          stream
              .limit(pruneLimit)
              .map(
                  slot ->
                      Pair.of(
                          slot,
                          dao.getFinalizedBlockAtSlot(slot).map(SignedBeaconBlock::getStateRoot)))
              .toList();
    }
    if (slotsToPruneStateFor.isEmpty()) {
      LOG.debug("No finalized state to prune up to {} slot", lastSlotToPrune);
      return lastSlotToPrune;
    }

    final UInt64 lastPrunedSlot = slotsToPruneStateFor.getLast().getLeft();

    deleteFinalizedStatesForSlot(slotsToPruneStateFor);

    return slotsToPruneStateFor.size() < pruneLimit ? lastSlotToPrune : lastPrunedSlot;
  }

  private void deleteFinalizedStatesForSlot(
      final List<Pair<UInt64, Optional<Bytes32>>> slotsToPruneStateFor) {
    if (!slotsToPruneStateFor.isEmpty()) {
      if (slotsToPruneStateFor.size() < 20) {
        LOG.debug(
            "Received finalized slots ({}) to delete state",
            () -> slotsToPruneStateFor.stream().map(Pair::getLeft).toList());
      } else {
        LOG.debug("Received {} finalized slots to delete state", slotsToPruneStateFor.size());
      }

      try (final FinalizedUpdater updater = finalizedUpdater()) {
        slotsToPruneStateFor.forEach(
            pair -> {
              updater.deleteFinalizedState(pair.getLeft());
              pair.getRight().ifPresent(updater::deleteFinalizedStateRoot);
            });

        updater.commit();
      } catch (Exception e) {
        LOG.error("Failed to prune finalized states", e);
      }
    }
  }

  protected void updateHotBlocks(
      final HotUpdater updater,
      final Map<Bytes32, BlockAndCheckpoints> addedBlocks,
      final Set<Bytes32> deletedHotBlockRoots) {
    updater.addHotBlocks(addedBlocks);
    deletedHotBlockRoots.forEach(updater::deleteHotBlock);
  }

  protected void addFinalizedBlock(final SignedBeaconBlock block, final FinalizedUpdater updater) {
    updater.addFinalizedBlock(block);
  }

  protected void storeNonCanonicalBlocks(
      final Set<Bytes32> blockRoots, final Map<Bytes32, Bytes32> finalizedChildToParentMap) {
    if (storeNonCanonicalBlocks) {
      final Set<SignedBeaconBlock> nonCanonicalBlocks =
          blockRoots.stream()
              .filter(root -> !finalizedChildToParentMap.containsKey(root))
              .flatMap(root -> getHotBlock(root).stream())
              .collect(Collectors.toSet());
      int i = 0;
      final Iterator<SignedBeaconBlock> it = nonCanonicalBlocks.iterator();
      while (it.hasNext()) {
        final Map<UInt64, Set<Bytes32>> nonCanonicalRootsBySlotBuffer = new HashMap<>();
        final int start = i;
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          while (it.hasNext() && (i - start) < TX_BATCH_SIZE) {
            final SignedBeaconBlock block = it.next();
            LOG.debug("Non canonical block {}:{}", block.getRoot().toHexString(), block.getSlot());
            updater.addNonCanonicalBlock(block);
            nonCanonicalRootsBySlotBuffer
                .computeIfAbsent(block.getSlot(), dao::getNonCanonicalBlockRootsAtSlot)
                .add(block.getRoot());
            i++;
          }
          nonCanonicalRootsBySlotBuffer.forEach(updater::addNonCanonicalRootAtSlot);
          updater.commit();
        }
      }
    }
  }

  @Override
  public void storeInitialAnchor(final AnchorPoint initialAnchor) {
    try (final CombinedUpdater updater = combinedUpdater()) {
      // We should only have a single block / state / checkpoint at anchorpoint initialization
      final Checkpoint anchorCheckpoint = initialAnchor.getCheckpoint();
      final Bytes32 anchorRoot = anchorCheckpoint.getRoot();
      final BeaconState anchorState = initialAnchor.getState();
      final Optional<SignedBeaconBlock> anchorBlock = initialAnchor.getSignedBeaconBlock();

      updater.setAnchor(initialAnchor.getCheckpoint());
      updater.setGenesisTime(anchorState.getGenesisTime());
      updater.setJustifiedCheckpoint(anchorCheckpoint);
      updater.setBestJustifiedCheckpoint(anchorCheckpoint);
      updater.setFinalizedCheckpoint(anchorCheckpoint);
      updater.setLatestFinalizedState(anchorState);

      // We need to store the anchor block in both hot and cold storage so that on restart
      // we're guaranteed to have at least one block / state to load into RecentChainData.
      anchorBlock.ifPresent(
          block -> {
            // Save to hot storage
            storeAnchorStateAndBlock(updater, anchorState, block);
          });

      putFinalizedState(updater, anchorRoot, anchorState);
      if (initialAnchor.isGenesis()
          && spec.atSlot(initialAnchor.getSlot())
              .getMilestone()
              .isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
        updater.setEarliestBlockSlot(initialAnchor.getSlot());
        updater.setEarliestBlobSidecarSlot(initialAnchor.getSlot());
      }

      updater.commit();
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    Optional<SlotAndBlockRoot> maybeSlotAndBlockRoot =
        dao.getSlotAndBlockRootFromStateRoot(stateRoot);
    if (maybeSlotAndBlockRoot.isPresent()) {
      return maybeSlotAndBlockRoot;
    }
    return dao.getSlotAndBlockRootForFinalizedStateRoot(stateRoot);
  }

  @Override
  public UpdateResult update(final StorageUpdate event) {
    if (event.isEmpty()) {
      return UpdateResult.EMPTY;
    }
    return doUpdate(event);
  }

  public void ingestDatabase(
      final KvStoreDatabase kvStoreDatabase, final int batchSize, final Consumer<String> logger) {
    dao.ingest(kvStoreDatabase.dao, batchSize, logger);
  }

  @Override
  public void storeFinalizedBlocks(
      final Collection<SignedBeaconBlock> blocks,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    if (blocks.isEmpty()) {
      return;
    }

    // Sort blocks and verify that they are contiguous with the oldestBlock
    final List<SignedBeaconBlock> sorted =
        blocks.stream()
            .sorted(Comparator.comparing(SignedBeaconBlock::getSlot).reversed())
            .toList();

    // The new block should be just prior to our earliest block if available, and otherwise should
    // match our latest finalized block
    Bytes32 expectedRoot =
        getEarliestAvailableBlock()
            .map(SignedBeaconBlock::getParentRoot)
            .orElseGet(() -> this.getLatestFinalizedBlockSummary().getRoot());
    for (SignedBeaconBlock block : sorted) {
      if (!block.getRoot().equals(expectedRoot)) {
        throw new IllegalArgumentException(
            "Blocks must be contiguous with the earliest known block.");
      }
      expectedRoot = block.getParentRoot();
    }

    storeFinalizedBlocksToDao(
        blocks, blobSidecarsBySlot, blindedExecutionPayloads, maybeEarliestBlobSidecarSlot);
  }

  @Override
  public void updateWeakSubjectivityState(final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final HotUpdater updater = hotUpdater()) {
      Optional<Checkpoint> checkpoint = weakSubjectivityUpdate.getWeakSubjectivityCheckpoint();
      checkpoint.ifPresentOrElse(
          updater::setWeakSubjectivityCheckpoint, updater::clearWeakSubjectivityCheckpoint);
      updater.commit();
    }
  }

  @Override
  public void storeReconstructedFinalizedState(final BeaconState state, final Bytes32 blockRoot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addReconstructedFinalizedState(blockRoot, state);
      handleAddFinalizedStateRoot(state, updater);
    }
  }

  private void handleAddFinalizedStateRoot(
      final BeaconState state, final FinalizedUpdater updater) {
    final Optional<BeaconState> maybeLastState =
        getLatestAvailableFinalizedState(state.getSlot().minusMinZero(ONE));
    maybeLastState.ifPresentOrElse(
        lastState -> {
          final StateRootRecorder recorder =
              new StateRootRecorder(
                  lastState.getSlot().increment(), updater::addFinalizedStateRoot, spec);
          recorder.acceptNextState(state);
        },
        () -> updater.addFinalizedStateRoot(state.hashTreeRoot(), state.getSlot()));

    updater.commit();
  }

  @Override
  public Optional<OnDiskStoreData> createMemoryStore() {
    return createMemoryStore(() -> Instant.now().getEpochSecond());
  }

  @VisibleForTesting
  Optional<OnDiskStoreData> createMemoryStore(final Supplier<Long> timeSupplier) {
    Optional<UInt64> maybeGenesisTime = dao.getGenesisTime();
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UInt64 genesisTime = maybeGenesisTime.get();
    final Optional<Checkpoint> maybeAnchor = dao.getAnchor();
    final Checkpoint justifiedCheckpoint = dao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = dao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = dao.getBestJustifiedCheckpoint().orElseThrow();
    final BeaconState finalizedState = dao.getLatestFinalizedState().orElseThrow();
    final Optional<Bytes32> latestCanonicalBlockRoot = dao.getLatestCanonicalBlockRoot();
    final Optional<UInt64> custodyGroupCount = dao.getCustodyGroupCount();

    final Map<UInt64, VoteTracker> votes = dao.getVotes();

    // Build map with block information
    final Map<Bytes32, StoredBlockMetadata> blockInformation = buildHotBlockMetadata();
    // If anchor block is missing, try to pull block info from the anchor state
    final boolean shouldIncludeAnchorBlock =
        maybeAnchor.isPresent()
            && finalizedCheckpoint
                .getEpochStartSlot(spec)
                .equals(maybeAnchor.get().getEpochStartSlot(spec));
    if (shouldIncludeAnchorBlock && !blockInformation.containsKey(maybeAnchor.get().getRoot())) {
      final Checkpoint anchor = maybeAnchor.orElseThrow();
      final StateAndBlockSummary latestFinalized = StateAndBlockSummary.create(finalizedState);
      if (!latestFinalized.getRoot().equals(anchor.getRoot())) {
        throw new IllegalStateException("Anchor state (" + anchor + ") is unavailable");
      }
      blockInformation.put(
          anchor.getRoot(), StoredBlockMetadata.fromBlockAndState(spec, latestFinalized));
    }
    enrichFinalizedGloasBlockMetadata(blockInformation, finalizedCheckpoint.getRoot());

    final Optional<SignedBeaconBlock> finalizedBlock =
        getFinalizedBlock(finalizedCheckpoint.getRoot());
    final AnchorPoint latestFinalized =
        AnchorPoint.create(spec, finalizedCheckpoint, finalizedState, finalizedBlock);
    final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload =
        dao.getOptimisticTransitionBlockSlot()
            .flatMap(this::getFinalizedBlockAtSlot)
            .flatMap(SlotAndExecutionPayloadSummary::fromBlock);

    // Make sure time is set to a reasonable value in the case where we start up before genesis when
    // the clock time would be prior to genesis
    final long clockTime = timeSupplier.get();
    final UInt64 slotTime = spec.computeTimeAtSlot(finalizedState.getSlot(), genesisTime);
    final UInt64 time = slotTime.max(clockTime);

    return Optional.of(
        new OnDiskStoreData(
            time,
            maybeAnchor,
            genesisTime,
            latestFinalized,
            finalizedOptimisticTransitionPayload,
            justifiedCheckpoint,
            bestJustifiedCheckpoint,
            blockInformation,
            votes,
            latestCanonicalBlockRoot,
            custodyGroupCount));
  }

  private void enrichFinalizedGloasBlockMetadata(
      final Map<Bytes32, StoredBlockMetadata> blockInformation, final Bytes32 finalizedBlockRoot) {
    blockInformation.computeIfPresent(
        finalizedBlockRoot,
        (blockRoot, blockMetadata) ->
            blockMetadata
                .getGloasForkChoiceRebuildData()
                .flatMap(
                    rebuildData ->
                        resolveExecutionPayloadContext(
                            blockMetadata.getParentRoot(), rebuildData.payloadParentBlockHash()))
                .map(
                    executionContext ->
                        new StoredBlockMetadata(
                            blockMetadata.getBlockSlot(),
                            blockMetadata.getBlockRoot(),
                            blockMetadata.getParentRoot(),
                            blockMetadata.getStateRoot(),
                            Optional.of(executionContext.blockNumber()),
                            blockMetadata.getExecutionBlockHash(),
                            Optional.of(executionContext.gasLimit()),
                            blockMetadata.getCheckpointEpochs(),
                            blockMetadata.getGloasForkChoiceRebuildData()))
                .orElse(blockMetadata));
  }

  private Optional<ExecutionPayloadContext> resolveExecutionPayloadContext(
      final Bytes32 blockRoot, final Bytes32 executionBlockHash) {
    final Set<Bytes32> visitedBlockRoots = new HashSet<>();
    Bytes32 currentBlockRoot = blockRoot;
    while (visitedBlockRoots.add(currentBlockRoot)) {
      final Optional<SignedBeaconBlock> maybeBlock = getSignedBlock(currentBlockRoot);
      if (maybeBlock.isEmpty()) {
        return Optional.empty();
      }
      final SignedBeaconBlock block = maybeBlock.orElseThrow();

      final Optional<ExecutionPayloadSummary> maybeExecutionPayload =
          block.getMessage().getBody().getOptionalExecutionPayloadSummary();
      if (maybeExecutionPayload.isPresent()) {
        final ExecutionPayloadSummary executionPayload = maybeExecutionPayload.orElseThrow();
        if (!executionPayload.getBlockHash().equals(executionBlockHash)) {
          return Optional.empty();
        }
        return createExecutionPayloadContext(
            executionPayload.getBlockNumber(), executionPayload.getGasLimit());
      }

      final Optional<GloasForkChoiceRebuildData> maybeRebuildData =
          StoredBlockMetadata.extractGloasForkChoiceRebuildData(
              block, dao.getBlindedExecutionPayloadEnvelope(currentBlockRoot));
      if (maybeRebuildData.isEmpty()) {
        return Optional.empty();
      }
      final GloasForkChoiceRebuildData rebuildData = maybeRebuildData.orElseThrow();
      if (rebuildData.payloadBlockHash().equals(executionBlockHash)) {
        return rebuildData
            .payloadBlockNumber()
            .flatMap(
                blockNumber ->
                    rebuildData
                        .payloadGasLimit()
                        .flatMap(gasLimit -> createExecutionPayloadContext(blockNumber, gasLimit)));
      }
      if (!rebuildData.payloadParentBlockHash().equals(executionBlockHash)) {
        return Optional.empty();
      }
      currentBlockRoot = block.getParentRoot();
    }
    return Optional.empty();
  }

  private Optional<ExecutionPayloadContext> createExecutionPayloadContext(
      final UInt64 blockNumber, final UInt64 gasLimit) {
    return gasLimit.isZero()
        ? Optional.empty()
        : Optional.of(new ExecutionPayloadContext(blockNumber, gasLimit));
  }

  private record ExecutionPayloadContext(UInt64 blockNumber, UInt64 gasLimit) {}

  @Override
  public WeakSubjectivityState getWeakSubjectivityState() {
    return WeakSubjectivityState.create(dao.getWeakSubjectivityCheckpoint());
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return dao.getVotes();
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return dao.getFinalizedCheckpoint();
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return dao.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots() {
    return dao.getFinalizedStateRoots();
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return dao.getHotState(root);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints() {
    return dao.streamBlockCheckpoints();
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    return dao.getStateRootsBeforeSlot(slot);
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.addHotStateRoots(stateRootToSlotAndBlockRootMap);
      updater.commit();
    }
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return dao.getGenesisTime();
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.pruneHotStateRoots(stateRoots);
      updater.commit();
    }
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return dao.getMinGenesisTimeBlock();
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return dao.streamDepositsFromBlocks();
  }

  @Override
  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamFinalizedStateSlots(startSlot, endSlot);
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.addMinGenesisTimeBlock(event);
      updater.commit();
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.addDepositsFromBlockEvent(event);
      updater.commit();
    }
  }

  @Override
  public void removeDepositsFromBlockEvents(final List<UInt64> blockNumbers) {
    try (final HotUpdater updater = hotUpdater()) {
      blockNumbers.forEach(updater::removeDepositsFromBlockEvent);
      updater.commit();
    }
  }

  @Override
  public void storeBlobSidecar(final BlobSidecar blobSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addBlobSidecar(blobSidecar);
      updater.commit();
    }
  }

  @Override
  public void storeNonCanonicalBlobSidecar(final BlobSidecar blobSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addNonCanonicalBlobSidecar(blobSidecar);
      updater.commit();
    }
  }

  @Override
  public Optional<BlobSidecar> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    final Optional<Bytes> maybePayload = dao.getBlobSidecar(key);
    return maybePayload.map(payload -> spec.deserializeBlobSidecar(payload, key.getSlot()));
  }

  @Override
  public Optional<BlobSidecar> getNonCanonicalBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    final Optional<Bytes> maybePayload = dao.getNonCanonicalBlobSidecar(key);
    return maybePayload.map(payload -> spec.deserializeBlobSidecar(payload, key.getSlot()));
  }

  @Override
  public boolean pruneOldestBlobSidecars(
      final UInt64 lastSlotToPrune,
      final int pruneLimit,
      final BlobSidecarsArchiver blobSidecarsArchiver) {
    final Optional<UInt64> earliestBlobSidecarSlot = getEarliestBlobSidecarSlot();
    if (earliestBlobSidecarSlot.isPresent()
        && earliestBlobSidecarSlot.get().isGreaterThan(lastSlotToPrune)) {
      return false;
    }
    try (final Stream<SlotAndBlockRootAndBlobIndex> prunableBlobKeys =
        streamBlobSidecarKeys(earliestBlobSidecarSlot.orElse(UInt64.ZERO), lastSlotToPrune)) {
      return pruneBlobSidecars(pruneLimit, prunableBlobKeys, blobSidecarsArchiver, false);
    }
  }

  @Override
  public boolean pruneOldestNonCanonicalBlobSidecars(
      final UInt64 lastSlotToPrune,
      final int pruneLimit,
      final BlobSidecarsArchiver blobSidecarsArchiver) {
    final Optional<UInt64> earliestBlobSidecarSlot = getEarliestBlobSidecarSlot();
    if (earliestBlobSidecarSlot.isPresent()
        && earliestBlobSidecarSlot.get().isGreaterThan(lastSlotToPrune)) {
      return false;
    }
    try (final Stream<SlotAndBlockRootAndBlobIndex> prunableNoncanonicalBlobKeys =
        streamNonCanonicalBlobSidecarKeys(
            earliestBlobSidecarSlot.orElse(UInt64.ZERO), lastSlotToPrune)) {
      return pruneBlobSidecars(
          pruneLimit, prunableNoncanonicalBlobKeys, blobSidecarsArchiver, true);
    }
  }

  private boolean pruneBlobSidecars(
      final int pruneLimit,
      final Stream<SlotAndBlockRootAndBlobIndex> prunableBlobKeys,
      final BlobSidecarsArchiver blobSidecarsArchiver,
      final boolean nonCanonicalBlobSidecars) {

    int pruned = 0;
    Optional<UInt64> earliestBlobSidecarSlot = Optional.empty();

    // Group the BlobSidecars by slot. Potential for higher memory usage
    // if it hasn't been pruned in a while
    final Map<UInt64, List<SlotAndBlockRootAndBlobIndex>> prunableMap =
        prunableBlobKeys.collect(groupingBy(SlotAndBlockRootAndBlobIndex::getSlot));

    // pruneLimit is the number of slots to prune, not the number of BlobSidecars
    final List<UInt64> slots = prunableMap.keySet().stream().sorted().limit(pruneLimit).toList();
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      for (final UInt64 slot : slots) {
        final List<SlotAndBlockRootAndBlobIndex> keys = prunableMap.get(slot);

        // Retrieve the BlobSidecars for archiving.
        final List<BlobSidecar> blobSidecars =
            keys.stream()
                .map(
                    nonCanonicalBlobSidecars
                        ? this::getNonCanonicalBlobSidecar
                        : this::getBlobSidecar)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        // Just warn if we failed to find all the BlobSidecars.
        if (keys.size() != blobSidecars.size()) {
          LOG.warn("Failed to retrieve BlobSidecars for keys: {}", keys);
        }

        if (!keys.isEmpty()) {
          final SlotAndBlockRoot slotAndBlockRoot = keys.getFirst().getSlotAndBlockRoot();
          blobSidecarsArchiver.archive(slotAndBlockRoot, blobSidecars);
        }

        // Remove the BlobSidecars from the database.
        for (final SlotAndBlockRootAndBlobIndex key : keys) {
          if (nonCanonicalBlobSidecars) {
            updater.removeNonCanonicalBlobSidecar(key);
          } else {
            updater.removeBlobSidecar(key);
            earliestBlobSidecarSlot = Optional.of(slot.plus(1));
          }
        }

        ++pruned;
      }

      if (!nonCanonicalBlobSidecars) {
        earliestBlobSidecarSlot.ifPresent(updater::setEarliestBlobSidecarSlot);
      }
      updater.commit();
    }
    LOG.debug("Pruned {} BlobSidecars", pruned);
    // `pruned` will be greater when we reach pruneLimit not on the latest BlobSidecar in a slot
    return pruned >= pruneLimit;
  }

  @MustBeClosed
  @Override
  public Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamBlobSidecarKeys(startSlot, endSlot);
  }

  @MustBeClosed
  @Override
  public Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamNonCanonicalBlobSidecarKeys(startSlot, endSlot);
  }

  @MustBeClosed
  @Override
  public Stream<BlobSidecar> streamBlobSidecars(final SlotAndBlockRoot slotAndBlockRoot) {
    return dao.streamBlobSidecars(slotAndBlockRoot)
        .map(payload -> spec.deserializeBlobSidecar(payload, slotAndBlockRoot.getSlot()));
  }

  @Override
  public List<SlotAndBlockRootAndBlobIndex> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final List<SlotAndBlockRootAndBlobIndex> blobSidecarKeys =
        dao.getBlobSidecarKeys(slotAndBlockRoot);
    if (blobSidecarKeys.isEmpty()) {
      return dao.getNonCanonicalBlobSidecarKeys(slotAndBlockRoot);
    } else {
      return blobSidecarKeys;
    }
  }

  @Override
  public Optional<UInt64> getEarliestBlobSidecarSlot() {
    return dao.getEarliestBlobSidecarSlot();
  }

  @Override
  public void storeVotes(final Map<UInt64, VoteTracker> votes) {
    try (final HotUpdater hotUpdater = hotUpdater()) {
      hotUpdater.addVotes(votes);
      hotUpdater.commit();
    }
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return dao.getAnchor();
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return dao.getJustifiedCheckpoint();
  }

  @Override
  public Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot() {
    return dao.getFinalizedDepositSnapshot();
  }

  @Override
  public void setFinalizedDepositSnapshot(final DepositTreeSnapshot finalizedDepositSnapshot) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.setFinalizedDepositSnapshot(finalizedDepositSnapshot);
      updater.commit();
    }
  }

  @Override
  public Optional<UInt64> getFirstCustodyIncompleteSlot() {
    return dao.getFirstCustodyIncompleteSlot();
  }

  @Override
  public Optional<DataColumnSidecar> getSidecar(final DataColumnSlotAndIdentifier identifier) {
    final Optional<Bytes> maybePayload = dao.getSidecar(identifier);
    return maybePayload.map(payload -> spec.deserializeSidecar(payload, identifier.slot()));
  }

  @Override
  public Optional<DataColumnSidecar> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    final Optional<Bytes> maybePayload = dao.getNonCanonicalSidecar(identifier);
    return maybePayload.map(payload -> spec.deserializeSidecar(payload, identifier.slot()));
  }

  @Override
  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    return dao.streamDataColumnIdentifiers(firstSlot, lastSlot);
  }

  @Override
  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamNonCanonicalDataColumnIdentifiers(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    return dao.streamNonCanonicalDataColumnIdentifiers(firstSlot, lastSlot);
  }

  @Override
  public Optional<UInt64> getEarliestDataColumnSidecarSlot() {
    final Optional<UInt64> maybeFirstDataColumnSidecarSlot = getFirstDataColumnSidecarSlot();
    if (maybeFirstDataColumnSidecarSlot.isEmpty()) {
      return Optional.empty();
    }
    // Start the scan just above the last pruned slot rather than at the first slot with data column
    // sidecar support. Everything up to and including the watermark has been deleted, so seeking to
    // watermark + 1 lands directly on live data instead of crossing the band of deletion tombstones
    // left below it - a forward scan over those tombstones can take minutes on a backlogged node.
    final UInt64 fromSlot =
        dao.getLastDataColumnSidecarPrunedSlot()
            .map(lastPruned -> lastPruned.plus(1))
            .orElse(maybeFirstDataColumnSidecarSlot.get());
    try (final Stream<DataColumnSlotAndIdentifier> identifiers =
        streamDataColumnIdentifiers(fromSlot, UInt64.MAX_VALUE)) {
      return identifiers.findFirst().map(DataColumnSlotAndIdentifier::slot);
    }
  }

  @Override
  public Optional<UInt64> getEarliestAvailableDataColumnSlot() {
    return dao.getEarliestAvailableDataColumnSlot();
  }

  @Override
  public void setEarliestAvailableDataColumnSlot(final UInt64 slot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.setEarliestAvailableDataColumnSlot(slot);
      updater.commit();
    }
  }

  @Override
  public Optional<UInt64> getLastDataColumnSidecarsProofsSlot() {
    return dao.getLastDataColumnSidecarsProofsSlot();
  }

  @Override
  public Optional<List<List<KZGProof>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return dao.getDataColumnSidecarsProofs(slot);
  }

  @Override
  public void setFirstCustodyIncompleteSlot(final UInt64 slot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.setFirstCustodyIncompleteSlot(slot);
      updater.commit();
    }
  }

  @Override
  public void addSidecar(final DataColumnSidecar sidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addSidecar(sidecar);
      updater.commit();
    }
  }

  @Override
  public void addNonCanonicalSidecar(final DataColumnSidecar sidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addNonCanonicalSidecar(sidecar);
      updater.commit();
    }
  }

  @Override
  public void pruneAllSidecars(final UInt64 tillSlotInclusive, final int pruneLimit) {
    final long startTime = System.currentTimeMillis();
    LOG.debug(
        "Pruning data column sidecars up to slot {}, limit {}", tillSlotInclusive, pruneLimit);

    pruneDataColumnSidecars(pruneLimit, tillSlotInclusive, DataColumnSidecarType.CANONICAL);
    pruneDataColumnSidecars(pruneLimit, tillSlotInclusive, DataColumnSidecarType.NON_CANONICAL);

    LOG.debug(
        "Data column sidecars pruning completed in {} ms", System.currentTimeMillis() - startTime);
  }

  @Override
  public void archiveSidecarsProofs(final UInt64 startSlot, final UInt64 tillSlotInclusive) {
    // Extension columns (indices >= NUMBER_OF_COLUMNS / 2) are the reconstructable half whose
    // proofs we retain while dropping the columns themselves.
    final int halfColumns =
        spec.getNumberOfDataColumns()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Cannot archive data column sidecar proofs before the Fulu milestone"))
            / 2;
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnSidecars =
        streamDataColumnIdentifiers(startSlot, tillSlotInclusive)) {

      int archivedSlots = 0;

      final Map<UInt64, List<DataColumnSlotAndIdentifier>> archiveMap = new HashMap<>();

      dataColumnSidecars
          // we need only extension
          .filter(identifier -> identifier.columnIndex().isGreaterThanOrEqualTo(halfColumns))
          .forEach(
              item -> archiveMap.computeIfAbsent(item.slot(), k -> new ArrayList<>()).add(item));

      final List<UInt64> slots = archiveMap.keySet().stream().sorted().toList();

      if (!slots.isEmpty()) {
        LOG.debug(
            "Archiving data column sidecars to proofs from slots {} to {}",
            slots.getFirst(),
            slots.getLast());
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          for (final UInt64 slot : slots) {
            final List<DataColumnSlotAndIdentifier> keys =
                archiveMap.get(slot).stream().sorted().toList();
            final List<DataColumnSidecar> sidecars = new ArrayList<>();

            for (final DataColumnSlotAndIdentifier key : keys) {
              final Optional<Bytes> sidecar = dao.getSidecar(key);
              // surprise, let's skip this slot
              if (sidecar.isEmpty()) {
                break;
              }
              sidecars.add(spec.deserializeSidecar(sidecar.get(), key.slot()));
            }

            // remove sidecars only if we have required 1/2
            if (sidecars.size() == halfColumns) {
              final List<List<KZGProof>> proofs =
                  sidecars.stream()
                      .map(
                          sidecar ->
                              sidecar.getKzgProofs().stream()
                                  .map(SszKZGProof::getKZGProof)
                                  .toList())
                      .toList();
              updater.addDataColumnSidecarsProofs(slot, proofs);
              for (final DataColumnSlotAndIdentifier key : keys) {
                updater.removeSidecar(key);
              }
              ++archivedSlots;
              LOG.trace(
                  "Pruned {} extension data column sidecars, keeping their proofs, at slot {}",
                  keys.size(),
                  slot);
            }
          }
          updater.commit();
        }
        LOG.debug("Archived data column sidecars to proofs across {} slots", archivedSlots);
      }
    }
  }

  /**
   * Prunes data column sidecars oldest-first: each run removes the oldest (up to) {@code
   * pruneSlotLimit} distinct populated slots at or before the cutoff, in a single committed
   * transaction. Pruning oldest-first keeps the earliest retained slot advancing and prevents a gap
   * from forming when sidecars are stored faster than they can be pruned.
   *
   * <p>For canonical sidecars the forward scan resumes from the persisted prune watermark ({@link
   * KvStoreCombinedDao#getLastDataColumnSidecarPrunedSlot()}) rather than the fixed first slot with
   * data column sidecar support, so the opening seek lands directly on live data instead of
   * crossing the deletion tombstones left below it - including across restarts. The watermark is
   * advanced atomically with the deletions. Non-canonical sidecars are sparse, so they always scan
   * from the first supported slot. Gaps between populated slots are skipped, so the batch always
   * spans {@code pruneSlotLimit} populated slots regardless of how far apart they are.
   */
  void pruneDataColumnSidecars(
      final int pruneSlotLimit,
      final UInt64 tillSlotInclusive,
      final DataColumnSidecarType sidecarType) {

    final long startTime = System.currentTimeMillis();

    if (pruneSlotLimit <= 0) {
      return;
    }

    final Optional<UInt64> maybeFirstDataColumnSidecarSlot = getFirstDataColumnSidecarSlot();
    if (maybeFirstDataColumnSidecarSlot.isEmpty()) {
      return;
    }

    final UInt64 firstSupportedSlot = maybeFirstDataColumnSidecarSlot.get();
    final UInt64 fromSlot =
        sidecarType == DataColumnSidecarType.CANONICAL
            ? dao.getLastDataColumnSidecarPrunedSlot()
                .map(lastPruned -> lastPruned.plus(1))
                .orElse(firstSupportedSlot)
            : firstSupportedSlot;
    if (fromSlot.isGreaterThan(tillSlotInclusive)) {
      return;
    }

    final DataColumnSidecarsToPrune toPrune =
        collectOldestSidecarsToPrune(pruneSlotLimit, fromSlot, tillSlotInclusive, sidecarType);

    if (toPrune.keys().isEmpty()) {
      LOG.debug("No {} data column sidecars to prune", sidecarType.displayName());
      return;
    }

    try (final FinalizedUpdater updater = finalizedUpdater()) {
      for (final DataColumnSlotAndIdentifier key : toPrune.keys()) {
        removeDataColumnSidecar(sidecarType, updater, key);
      }
      // Persist the canonical prune watermark atomically with the deletions so the next run (and
      // any
      // run after a restart) can resume above the tombstones rather than rescanning from the first
      // supported slot.
      if (sidecarType == DataColumnSidecarType.CANONICAL) {
        updater.setLastDataColumnSidecarPrunedSlot(toPrune.keys().getLast().slot());
        final List<UInt64> prunedSlots =
            toPrune.keys().stream().map(DataColumnSlotAndIdentifier::slot).distinct().toList();
        LOG.debug(
            "Removing archived data column sidecar proofs for {} pruned canonical slots ({}..{})",
            prunedSlots.size(),
            prunedSlots.getFirst(),
            prunedSlots.getLast());
        prunedSlots.forEach(updater::removeDataColumnSidecarsProofs);
      }
      updater.commit();
    }

    if (toPrune.distinctSlots() >= pruneSlotLimit) {
      LOG.debug(
          "{} data column sidecars pruning reached the limit of {}",
          sidecarType.displayName(),
          pruneSlotLimit);
    }
    LOG.debug(
        "Pruned {} {} data column sidecars across {} slots ({},{}) in {} ms",
        toPrune.keys().size(),
        sidecarType.displayName(),
        toPrune.distinctSlots(),
        toPrune.keys().getFirst().slot(),
        toPrune.keys().getLast().slot(),
        System.currentTimeMillis() - startTime);
  }

  /**
   * Collects the keys for the oldest (up to) {@code pruneSlotLimit} distinct populated slots in
   * {@code [fromSlot, tillSlotInclusive]} in a single forward scan, stopping as soon as the limit
   * is reached. The stream is consumed lazily, so the iterator only advances as far as the slots
   * being pruned.
   */
  private DataColumnSidecarsToPrune collectOldestSidecarsToPrune(
      final int pruneSlotLimit,
      final UInt64 fromSlot,
      final UInt64 tillSlotInclusive,
      final DataColumnSidecarType sidecarType) {
    final List<DataColumnSlotAndIdentifier> keys = new ArrayList<>();
    int distinctSlots = 0;
    try (final Stream<DataColumnSlotAndIdentifier> forwardStream =
        streamDataColumnIdentifiersForward(sidecarType, fromSlot, tillSlotInclusive)) {
      final Iterator<DataColumnSlotAndIdentifier> iterator = forwardStream.iterator();
      UInt64 currentSlot = null;
      while (iterator.hasNext()) {
        final DataColumnSlotAndIdentifier key = iterator.next();
        if (!key.slot().equals(currentSlot)) {
          if (distinctSlots == pruneSlotLimit) {
            break;
          }
          currentSlot = key.slot();
          ++distinctSlots;
        }
        keys.add(key);
      }
    }
    return new DataColumnSidecarsToPrune(keys, distinctSlots);
  }

  @MustBeClosed
  private Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiersForward(
      final DataColumnSidecarType sidecarType, final UInt64 fromSlot, final UInt64 toSlot) {
    if (sidecarType == DataColumnSidecarType.CANONICAL) {
      return streamDataColumnIdentifiers(fromSlot, toSlot);
    }
    return streamNonCanonicalDataColumnIdentifiers(fromSlot, toSlot);
  }

  private record DataColumnSidecarsToPrune(
      List<DataColumnSlotAndIdentifier> keys, int distinctSlots) {}

  private void removeDataColumnSidecar(
      final DataColumnSidecarType sidecarType,
      final FinalizedUpdater updater,
      final DataColumnSlotAndIdentifier key) {
    switch (sidecarType) {
      case CANONICAL -> updater.removeSidecar(key);
      case NON_CANONICAL -> updater.removeNonCanonicalSidecar(key);
    }
  }

  private Optional<UInt64> getFirstDataColumnSidecarSlot() {
    return spec.computeFirstSlotWithDataColumnSidecarSupport();
  }

  @Override
  public void compactStorage() {
    dao.compact();
  }

  @Override
  public void close() throws Exception {
    dao.close();
  }

  private UpdateResult doUpdate(final StorageUpdate update) {
    LOG.trace("Applying finalized updates");
    long startTime = System.currentTimeMillis();
    // Update finalized blocks and states
    final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticExecutionPayload =
        updateFinalizedData(
            update.getFinalizedChildToParentMap(),
            update.getFinalizedBlocks(),
            update.getFinalizedStates(),
            update.getDeletedHotBlocks(),
            update.isFinalizedOptimisticTransitionBlockRootSet(),
            update.getOptimisticTransitionBlockRoot());

    if (update.isExecutionPayloadEnvelopesEnabled()) {
      final Set<Bytes32> nonCanonicalPrunedRoots =
          update.getDeletedHotBlocks().keySet().stream()
              .filter(root -> !update.getFinalizedChildToParentMap().containsKey(root))
              .collect(Collectors.toSet());
      updateBlindedExecutionPayloads(update.getBlindedExecutionPayloads(), nonCanonicalPrunedRoots);
    }

    if (update.isBlobSidecarsEnabled()) {
      removeNonCanonicalBlobSidecars(
          update.getDeletedHotBlocks(), update.getFinalizedChildToParentMap());

      updateBlobSidecarData(
          update.getEarliestBlobSidecarSlot(),
          update.getBlobSidecars().values().stream().flatMap(Collection::stream));
    }
    if (update.isSidecarsEnabled()) {
      removeNonCanonicalSidecars(
          update.getDeletedHotBlocks(), update.getFinalizedChildToParentMap());
    }
    long finalizedDataUpdatedTime = System.currentTimeMillis();

    LOG.trace("Applying hot updates");
    final long latestFinalizedStateUpdateStartTime;
    final long latestFinalizedStateUpdateEndTime;
    try (final HotUpdater updater = hotUpdater()) {
      // Store new hot data
      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update
          .getFinalizedCheckpoint()
          .ifPresent(
              checkpoint -> {
                updater.setFinalizedCheckpoint(checkpoint);
                final int slotsPerEpoch = spec.slotsPerEpoch(checkpoint.getEpoch());
                final UInt64 finalizedSlot = checkpoint.getEpochStartSlot(spec).plus(slotsPerEpoch);
                updater.pruneHotStateRoots(dao.getStateRootsBeforeSlot(finalizedSlot));
                updater.deleteHotState(checkpoint.getRoot());
              });

      update.getLatestCanonicalBlockRoot().ifPresent(updater::setLatestCanonicalBlockRoot);
      update.getCustodyGroupCount().ifPresent(updater::setCustodyGroupCount);
      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);
      latestFinalizedStateUpdateStartTime = System.currentTimeMillis();
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);
      latestFinalizedStateUpdateEndTime = System.currentTimeMillis();

      updateHotBlocks(updater, update.getHotBlocks(), update.getDeletedHotBlocks().keySet());
      updater.addHotStates(update.getHotStates());

      if (update.getStateRoots().size() > 0) {
        updater.addHotStateRoots(update.getStateRoots());
      }

      // Delete finalized data from hot db

      LOG.trace("Committing hot db changes");
      updater.commit();
    }

    long endTime = System.currentTimeMillis();
    DB_LOGGER.onDbOpAlertThreshold(
        "KvStoreDatabase::doUpdate",
        () ->
            String.format(
                "Finalized data updated time: %d ms - Hot data updated time: %d ms of which latest finalized state updated time: %d ms",
                finalizedDataUpdatedTime - startTime,
                endTime - finalizedDataUpdatedTime,
                latestFinalizedStateUpdateEndTime - latestFinalizedStateUpdateStartTime),
        startTime,
        endTime);
    LOG.trace("Update complete");
    return new UpdateResult(finalizedOptimisticExecutionPayload);
  }

  private void updateBlobSidecarData(
      final Optional<UInt64> getEarliestBlobSidecarSlot, final Stream<BlobSidecar> blobSidecars) {
    LOG.trace("Applying blob sidecar updates");

    try (final FinalizedUpdater updater = finalizedUpdater()) {
      // add blob sidecars
      blobSidecars.forEach(updater::addBlobSidecar);

      // update the earliest blob sidecar slot if needed
      getEarliestBlobSidecarSlot.ifPresent(
          earliestSlot -> {
            if (dao.getEarliestBlobSidecarSlot().isEmpty()) {
              updater.setEarliestBlobSidecarSlot(earliestSlot);
            }
          });

      LOG.trace("Committing blob sidecar updates");
      updater.commit();
    }
  }

  private Optional<SlotAndExecutionPayloadSummary> updateFinalizedData(
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final Map<Bytes32, UInt64> deletedHotBlocksRootsWithSlot,
      final boolean isFinalizedOptimisticBlockRootSet,
      final Optional<Bytes32> finalizedOptimisticTransitionBlockRoot) {
    if (finalizedChildToParentMap.isEmpty()) {
      // Nothing to do
      return Optional.empty();
    }

    final Optional<SlotAndExecutionPayloadSummary> optimisticTransitionPayload =
        updateFinalizedOptimisticTransitionBlock(
            isFinalizedOptimisticBlockRootSet, finalizedOptimisticTransitionBlockRoot);
    if (stateStorageMode.storesFinalizedStates()) {
      updateFinalizedDataArchiveMode(finalizedChildToParentMap, finalizedBlocks, finalizedStates);
    } else {
      updateFinalizedDataPruneMode(finalizedChildToParentMap, finalizedBlocks);
    }

    storeNonCanonicalBlocks(deletedHotBlocksRootsWithSlot.keySet(), finalizedChildToParentMap);

    return optimisticTransitionPayload;
  }

  private Optional<SlotAndExecutionPayloadSummary> updateFinalizedOptimisticTransitionBlock(
      final boolean isFinalizedOptimisticBlockRootSet,
      final Optional<Bytes32> finalizedOptimisticTransitionBlockRoot) {
    if (isFinalizedOptimisticBlockRootSet) {
      final Optional<SignedBeaconBlock> transitionBlock =
          finalizedOptimisticTransitionBlockRoot.flatMap(this::getHotBlock);
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        updater.setOptimisticTransitionBlockSlot(transitionBlock.map(SignedBeaconBlock::getSlot));
        updater.commit();
      }
      return transitionBlock.flatMap(SlotAndExecutionPayloadSummary::fromBlock);
    } else {
      return Optional.empty();
    }
  }

  private void removeNonCanonicalBlobSidecars(
      final Map<Bytes32, UInt64> deletedHotBlocks,
      final Map<Bytes32, Bytes32> finalizedChildToParentMap) {

    final Set<SlotAndBlockRoot> nonCanonicalBlocks =
        deletedHotBlocks.entrySet().stream()
            .filter(entry -> !finalizedChildToParentMap.containsKey(entry.getKey()))
            .map(entry -> new SlotAndBlockRoot(entry.getValue(), entry.getKey()))
            .collect(Collectors.toSet());

    if (storeNonCanonicalBlocks) {
      final Iterator<SlotAndBlockRoot> nonCanonicalBlocksIterator = nonCanonicalBlocks.iterator();
      int index = 0;
      while (nonCanonicalBlocksIterator.hasNext()) {
        final int start = index;
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          while (nonCanonicalBlocksIterator.hasNext() && (index - start) < BLOBS_TX_BATCH_SIZE) {
            dao.getBlobSidecarKeys(nonCanonicalBlocksIterator.next())
                .forEach(
                    key -> {
                      dao.getBlobSidecar(key)
                          .ifPresent(
                              blobSidecarBytes -> {
                                updater.addNonCanonicalBlobSidecarRaw(blobSidecarBytes, key);
                                updater.removeBlobSidecar(key);
                              });
                    });
            index++;
          }
          updater.commit();
        }
      }
    } else {
      LOG.trace("Removing blob sidecars for non-canonical blocks");
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        for (final SlotAndBlockRoot slotAndBlockRoot : nonCanonicalBlocks) {
          dao.getBlobSidecarKeys(slotAndBlockRoot)
              .forEach(
                  key -> {
                    LOG.trace("Removing blobSidecar with index {} for non-canonical block", key);
                    updater.removeBlobSidecar(key);
                  });
        }
        updater.commit();
      }
    }
  }

  private void removeNonCanonicalSidecars(
      final Map<Bytes32, UInt64> deletedHotBlocks,
      final Map<Bytes32, Bytes32> finalizedChildToParentMap) {

    final Set<SlotAndBlockRoot> nonCanonicalBlocks =
        deletedHotBlocks.entrySet().stream()
            .filter(entry -> !finalizedChildToParentMap.containsKey(entry.getKey()))
            .map(entry -> new SlotAndBlockRoot(entry.getValue(), entry.getKey()))
            .collect(Collectors.toSet());

    if (storeNonCanonicalBlocks) {
      final Iterator<SlotAndBlockRoot> nonCanonicalBlocksIterator = nonCanonicalBlocks.iterator();
      int index = 0;
      while (nonCanonicalBlocksIterator.hasNext()) {
        final int start = index;
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          while (nonCanonicalBlocksIterator.hasNext() && (index - start) < BLOBS_TX_BATCH_SIZE) {
            dao.getDataColumnIdentifiers(nonCanonicalBlocksIterator.next())
                .forEach(
                    key -> {
                      dao.getSidecar(key)
                          .ifPresent(
                              sidecarBytes -> {
                                DataColumnSidecar sideCar =
                                    spec.deserializeSidecar(sidecarBytes, key.slot());
                                updater.addNonCanonicalSidecar(sideCar);
                                LOG.trace(
                                    "Moving non-canonical sidecar with identifier {} to non-canonical sidecars table",
                                    key);
                                updater.removeSidecar(key);
                              });
                    });
            index++;
          }
          updater.commit();
        }
      }
    } else {
      LOG.trace("Removing sidecars for non-canonical blocks");
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        for (final SlotAndBlockRoot slotAndBlockRoot : nonCanonicalBlocks) {
          dao.getDataColumnIdentifiers(slotAndBlockRoot)
              .forEach(
                  key -> {
                    LOG.trace("Removing sidecar with identifier {} for non-canonical block", key);
                    updater.removeSidecar(key);
                  });
        }
        updater.commit();
      }
    }
  }

  private void updateFinalizedDataArchiveMode(
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {

    final Optional<Checkpoint> initialCheckpoint = dao.getAnchor();
    final Optional<Bytes32> initialBlockRoot = initialCheckpoint.map(Checkpoint::getRoot);
    // Get previously finalized block to build on top of
    final BeaconBlockSummary baseBlock = getLatestFinalizedBlockOrSummary();

    final List<Bytes32> finalizedRoots =
        HashTree.builder()
            .rootHash(baseBlock.getRoot())
            .childAndParentRoots(finalizedChildToParentMap)
            .build()
            .preOrderStream()
            .toList();

    int i = 0;
    UInt64 lastSlot = baseBlock.getSlot();
    while (i < finalizedRoots.size()) {
      final int start = i;
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final StateRootRecorder recorder =
            new StateRootRecorder(lastSlot, updater::addFinalizedStateRoot, spec);

        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 blockRoot = finalizedRoots.get(i);

          final Optional<UInt64> maybeBlockSlot =
              addFinalizedBlock(blockRoot, finalizedBlocks, updater, initialBlockRoot);

          Optional.ofNullable(finalizedStates.get(blockRoot))
              .or(() -> getHotState(blockRoot))
              .ifPresent(
                  state -> {
                    updater.addFinalizedState(blockRoot, state);
                    recorder.acceptNextState(state);
                  });

          lastSlot =
              maybeBlockSlot.orElseGet(
                  () -> initialCheckpoint.orElseThrow().getEpochStartSlot(spec));
          i++;
        }
        updater.commit();
        if (i >= TX_BATCH_SIZE) {
          STATUS_LOG.recordedFinalizedBlocks(i, finalizedRoots.size());
        }
      }
    }
  }

  private void updateFinalizedDataPruneMode(
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks) {
    final Optional<Bytes32> initialBlockRoot = dao.getAnchor().map(Checkpoint::getRoot);

    final List<Bytes32> finalizedRoots = new ArrayList<>(finalizedChildToParentMap.keySet());
    int i = 0;
    while (i < finalizedRoots.size()) {
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final int start = i;
        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 root = finalizedRoots.get(i);

          addFinalizedBlock(root, finalizedBlocks, updater, initialBlockRoot);

          i++;
        }
        updater.commit();
        if (i >= TX_BATCH_SIZE) {
          STATUS_LOG.recordedFinalizedBlocks(i, finalizedRoots.size());
        }
      }
    }
  }

  private Optional<UInt64> addFinalizedBlock(
      final Bytes32 blockRoot,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final FinalizedUpdater updater,
      final Optional<Bytes32> initialBlockRoot) {
    final SignedBeaconBlock block = finalizedBlocks.get(blockRoot);
    if (block != null) {
      addFinalizedBlock(block, updater);
      return Optional.of(block.getSlot());
    }

    final Optional<Bytes> rawBlock = dao.getHotBlockAsSsz(blockRoot);
    if (rawBlock.isPresent()) {
      final UInt64 slot = BeaconBlockInvariants.extractSignedBlockContainerSlot(rawBlock.get());
      updater.addFinalizedBlockRaw(slot, blockRoot, rawBlock.get());
      return Optional.of(slot);
    }

    // If block is missing and doesn't match the initial anchor, throw
    if (initialBlockRoot.filter(r -> r.equals(blockRoot)).isEmpty()) {
      throw new IllegalStateException("Missing finalized block");
    }

    return Optional.empty();
  }

  private void updateBlindedExecutionPayloads(
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Set<Bytes32> prunedBlockRoots) {
    if (blindedExecutionPayloads.isEmpty() && prunedBlockRoots.isEmpty()) {
      return;
    }
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      blindedExecutionPayloads.forEach(updater::addBlindedExecutionPayloadEnvelope);
      prunedBlockRoots.forEach(updater::deleteBlindedExecutionPayloadEnvelope);
      updater.commit();
    }
  }

  private BeaconBlockSummary getLatestFinalizedBlockOrSummary() {
    final Bytes32 baseBlockRoot = dao.getFinalizedCheckpoint().orElseThrow().getRoot();
    return getFinalizedBlock(baseBlockRoot)
        .<BeaconBlockSummary>map(a -> a)
        .orElseGet(this::getLatestFinalizedBlockSummary);
  }

  private BeaconBlockSummary getLatestFinalizedBlockSummary() {
    final Optional<BeaconBlockSummary> finalizedBlock =
        dao.getLatestFinalizedState().map(BeaconBlockHeader::fromState);
    return finalizedBlock.orElseThrow(
        () -> new IllegalStateException("Unable to reconstruct latest finalized block summary"));
  }

  private void putFinalizedState(
      final FinalizedUpdater updater, final Bytes32 blockRoot, final BeaconState state) {
    if (stateStorageMode.storesFinalizedStates()) {
      updater.addFinalizedState(blockRoot, state);
      updater.addFinalizedStateRoot(state.hashTreeRoot(), state.getSlot());
    }
  }
}

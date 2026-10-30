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

package tech.pegasys.teku.storage.server;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.ChainStorageFacade;
import tech.pegasys.teku.storage.api.LightClientUpdateChannel;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StoredLightClientUpdate;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreDeserializationException;
import tech.pegasys.teku.storage.server.state.FinalizedStateCache;

public class ChainStorage
    implements StorageUpdateChannel,
        StorageQueryChannel,
        VoteUpdateChannel,
        SidecarUpdateChannel,
        LightClientUpdateChannel,
        ChainStorageFacade {
  private static final Logger LOG = LogManager.getLogger();

  private final Database database;
  private final FinalizedStateCache finalizedStateCache;
  private final StateStorageMode dataStorageMode;
  private final BlobSidecarsArchiver blobSidecarsArchiver;
  private final Consumer<UInt64> sidecarStoredListener;
  private final Spec spec;

  private Optional<OnDiskStoreData> cachedStoreData = Optional.empty();

  private ChainStorage(
      final Database database,
      final FinalizedStateCache finalizedStateCache,
      final StateStorageMode dataStorageMode,
      final BlobSidecarsArchiver blobSidecarsArchiver,
      final Consumer<UInt64> sidecarStoredListener,
      final Spec spec) {
    this.database = database;
    this.finalizedStateCache = finalizedStateCache;
    this.dataStorageMode = dataStorageMode;
    this.blobSidecarsArchiver = blobSidecarsArchiver;
    this.sidecarStoredListener = sidecarStoredListener;
    this.spec = spec;
  }

  public static ChainStorage create(
      final Database database,
      final Spec spec,
      final StateStorageMode dataStorageMode,
      final int stateRebuildTimeoutSeconds,
      final BlobSidecarsArchiver blobSidecarsArchiver,
      final Consumer<UInt64> sidecarStoredListener) {
    final int finalizedStateCacheSize = spec.getSlotsPerEpoch(SpecConfig.GENESIS_EPOCH) * 3;
    return new ChainStorage(
        database,
        new FinalizedStateCache(
            spec, database, finalizedStateCacheSize, true, stateRebuildTimeoutSeconds),
        dataStorageMode,
        blobSidecarsArchiver,
        sidecarStoredListener,
        spec);
  }

  private synchronized Optional<OnDiskStoreData> getStore() {
    if (cachedStoreData.isEmpty()) {
      // Create store from database
      cachedStoreData = database.createMemoryStore();
    }

    return cachedStoreData;
  }

  private synchronized void handleStoreUpdate() {
    cachedStoreData = Optional.empty();
  }

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    if (database == null) {
      return SafeFuture.failedFuture(new IllegalStateException("Database not initialized yet"));
    }

    return SafeFuture.completedFuture(getStore());
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return SafeFuture.of(database::getWeakSubjectivityState);
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    return SafeFuture.of(
        () -> {
          final UpdateResult updateResult = database.update(event);
          handleStoreUpdate();
          return updateResult;
        });
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(
      final Collection<SignedBeaconBlock> finalizedBlocks,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    return SafeFuture.fromRunnable(
        () ->
            database.storeFinalizedBlocks(
                finalizedBlocks,
                blobSidecarsBySlot,
                blindedExecutionPayloads,
                maybeEarliestBlobSidecarSlot));
  }

  @Override
  public SafeFuture<Void> onReconstructedFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    return SafeFuture.fromRunnable(
        () -> database.storeReconstructedFinalizedState(finalizedState, blockRoot));
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    database.storeInitialAnchor(initialAnchor);
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return SafeFuture.fromRunnable(
        () -> database.updateWeakSubjectivityState(weakSubjectivityUpdate));
  }

  @Override
  public SafeFuture<Void> onFinalizedDepositSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    return SafeFuture.fromRunnable(() -> database.setFinalizedDepositSnapshot(depositTreeSnapshot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return SafeFuture.of(database::getEarliestAvailableBlockSlot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return SafeFuture.of(database::getEarliestAvailableBlock);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSignedBlock(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getHotState(blockRoot)
                .flatMap(
                    s -> database.getHotBlock(blockRoot).map(b -> new SignedBlockAndState(b, s))));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getHotState(blockRoot)
                .map(
                    state -> {
                      final BeaconBlockSummary block =
                          database
                              .getHotBlock(blockRoot)
                              .map(b -> (BeaconBlockSummary) b)
                              .orElseGet(() -> BeaconBlockHeader.fromState(state));
                      return StateAndBlockSummary.create(block, state);
                    }));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return SafeFuture.of(() -> database.getHotBlocks(blockRoots));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>>
      getBlindedExecutionPayloadEnvelopesByBlockRoot(final Set<Bytes32> blockRoots) {
    return SafeFuture.of(() -> database.getBlindedExecutionPayloadEnvelopes(blockRoots));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getBlobSidecarsBySlotAndBlockRoot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<BlobSidecar> blobSidecarStream =
              database.streamBlobSidecars(slotAndBlockRoot)) {
            return blobSidecarStream.toList();
          }
        });
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return SafeFuture.of(() -> database.getSlotAndBlockRootFromStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    if (dataStorageMode.storesFinalizedStates()) {
      return SafeFuture.of(() -> getLatestFinalizedStateAtSlotSync(slot));
    }
    LOG.debug("Not storing archive states - not fetching finalized state at slot {}", slot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(final UInt64 slot) {
    if (dataStorageMode.storesFinalizedStates()) {
      return SafeFuture.of(() -> getLatestAvailableFinalizedStateSync(slot));
    }
    LOG.debug(
        "Not storing archive states - not fetching latest available finalized state at slot {}",
        slot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSlotForFinalizedBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    if (dataStorageMode.storesFinalizedStates()) {
      return getFinalizedSlotByBlockRoot(blockRoot)
          .thenApply(slot -> slot.flatMap(this::getLatestFinalizedStateAtSlotSync));
    }
    LOG.debug("Not storing archive states - not fetching finalized state at root {}", blockRoot);
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return SafeFuture.of(() -> database.getSlotForFinalizedStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<Bytes32>> getLatestCanonicalBlockRoot() {
    return SafeFuture.of(database::getLatestCanonicalBlockRoot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getCustodyGroupCount() {
    return SafeFuture.of(database::getCustodyGroupCount);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getNonCanonicalBlockByRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getNonCanonicalBlockByRoot(blockRoot));
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    return SafeFuture.of(() -> database.getNonCanonicalBlocksAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return SafeFuture.of(database::getAnchor);
  }

  private Optional<BeaconState> getLatestFinalizedStateAtSlotSync(final UInt64 slot) {
    return finalizedStateCache.getFinalizedState(slot);
  }

  private Optional<BeaconState> getLatestAvailableFinalizedStateSync(final UInt64 slot) {
    return database.getLatestAvailableFinalizedState(slot);
  }

  @Override
  public void onVotesUpdated(final Map<UInt64, VoteTracker> votes) {
    database.storeVotes(votes);
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return SafeFuture.of(database::getFinalizedDepositSnapshot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return SafeFuture.of(database::getEarliestBlobSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.of(() -> database.getBlobSidecar(key));
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getNonCanonicalBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.of(() -> database.getNonCanonicalBlobSidecar(key));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecars =
              database.streamBlobSidecarKeys(slot)) {
            return blobSidecars.toList();
          }
        });
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getAllBlobSidecarKeys(final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<SlotAndBlockRootAndBlobIndex> canonicalBlobSidecars =
                  database.streamBlobSidecarKeys(slot);
              final Stream<SlotAndBlockRootAndBlobIndex> nonCanonicalBlobSidecars =
                  database.streamNonCanonicalBlobSidecarKeys(slot)) {
            return Stream.concat(canonicalBlobSidecars, nonCanonicalBlobSidecars).toList();
          }
        });
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot, final long limit) {
    return SafeFuture.of(
        () -> {
          try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecars =
              database.streamBlobSidecarKeys(startSlot, endSlot)) {
            return blobSidecars.limit(limit).toList();
          }
        });
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.of(() -> database.getBlobSidecarKeys(slotAndBlockRoot));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.of(() -> blobSidecarsArchiver.retrieve(slotAndBlockRoot).orElse(List.of()));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(final UInt64 slot) {
    return SafeFuture.of(() -> blobSidecarsArchiver.retrieve(slot).orElse(List.of()));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return SafeFuture.of(database::getFirstCustodyIncompleteSlot);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return SafeFuture.of(() -> database.getSidecar(identifier));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return SafeFuture.of(() -> database.getNonCanonicalSidecar(identifier));
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> getSidecars(
      final List<DataColumnSlotAndIdentifier> identifiers) {
    return SafeFuture.of(
        () -> identifiers.stream().map(database::getSidecar).flatMap(Optional::stream).toList());
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
              database.streamDataColumnIdentifiers(slot)) {
            return dataColumnIdentifiersStream.toList();
          }
        });
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getCustodyDataColumnIdentifiers(
      final UInt64 slot) {
    return getDataColumnIdentifiers(slot)
        .thenApply(identifiers -> includeArchivedCustodyIdentifiers(slot, identifiers));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getCustodyDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<DataColumnSlotAndIdentifier> identifiers =
              database.streamDataColumnIdentifiers(startSlot, endSlot)) {
            final Map<UInt64, List<DataColumnSlotAndIdentifier>> identifiersBySlot =
                identifiers.collect(
                    Collectors.groupingBy(
                        DataColumnSlotAndIdentifier::slot, TreeMap::new, Collectors.toList()));
            return identifiersBySlot.entrySet().stream()
                .flatMap(
                    entry ->
                        includeArchivedCustodyIdentifiers(entry.getKey(), entry.getValue())
                            .stream())
                .toList();
          }
        });
  }

  private List<DataColumnSlotAndIdentifier> includeArchivedCustodyIdentifiers(
      final UInt64 slot, final List<DataColumnSlotAndIdentifier> identifiers) {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElse(0);
    final int halfColumns = numberOfColumns / 2;
    if (numberOfColumns == 0 || identifiers.size() < halfColumns) {
      return identifiers;
    }
    if (hasCompletePhysicalColumns(identifiers, numberOfColumns)) {
      return identifiers;
    }
    // Read proofs after identifiers so concurrent archival cannot hide availability.
    // Avoid loading the finalized block entirely when no archive exists.
    if (!hasCompleteSidecarArchive(slot, halfColumns)) {
      return identifiers;
    }
    final Optional<Bytes32> canonicalRoot =
        identifiers.stream()
            .map(DataColumnSlotAndIdentifier::blockRoot)
            .distinct()
            .filter(
                root -> database.getSlotForFinalizedBlockRoot(root).map(slot::equals).orElse(false))
            .findFirst();
    if (canonicalRoot.isEmpty()) {
      return identifiers;
    }
    return expandCanonicalCustodyIdentifiers(
        slot, canonicalRoot.get(), identifiers, numberOfColumns);
  }

  private boolean hasCompleteSidecarArchive(final UInt64 slot, final int halfColumns) {
    try {
      return database
          .getDataColumnSidecarsProofs(slot)
          .map(proofs -> proofs.size() == halfColumns)
          .orElse(false);
    } catch (final KvStoreDeserializationException error) {
      LOG.warn("Ignoring malformed archived proofs for custody at slot {}", slot, error);
      return false;
    }
  }

  private boolean hasCompletePhysicalColumns(
      final List<DataColumnSlotAndIdentifier> identifiers, final int numberOfColumns) {
    // Database identifiers are unique. A complete set for one root needs no archive.
    return identifiers.size() == numberOfColumns
        && identifiers.stream()
            .allMatch(
                identifier ->
                    identifier.blockRoot().equals(identifiers.getFirst().blockRoot())
                        && identifier.columnIndex().isLessThan(numberOfColumns));
  }

  private List<DataColumnSlotAndIdentifier> expandCanonicalCustodyIdentifiers(
      final UInt64 slot,
      final Bytes32 canonicalRoot,
      final List<DataColumnSlotAndIdentifier> identifiers,
      final int numberOfColumns) {
    final int halfColumns = numberOfColumns / 2;
    final Set<DataColumnSlotAndIdentifier> available = new HashSet<>(identifiers);
    for (int index = 0; index < halfColumns; index++) {
      if (!available.contains(
          new DataColumnSlotAndIdentifier(slot, canonicalRoot, UInt64.valueOf(index)))) {
        return identifiers;
      }
    }
    for (int index = halfColumns; index < numberOfColumns; index++) {
      available.add(new DataColumnSlotAndIdentifier(slot, canonicalRoot, UInt64.valueOf(index)));
    }
    return available.stream().sorted().toList();
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getNonCanonicalDataColumnIdentifiers(
      final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
              database.streamNonCanonicalDataColumnIdentifiers(slot)) {
            return dataColumnIdentifiersStream.toList();
          }
        });
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot, final UInt64 limit) {
    return SafeFuture.of(
        () -> {
          try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
              database
                  .streamDataColumnIdentifiers(startSlot, endSlot)
                  .limit(
                      limit.isGreaterThan(Long.MAX_VALUE) ? Long.MAX_VALUE : limit.longValue())) {
            return dataColumnIdentifiersStream.toList();
          }
        });
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestDataColumnSidecarSlot() {
    return SafeFuture.of(database::getEarliestDataColumnSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<List<List<KZGProof>>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return SafeFuture.of(() -> database.getDataColumnSidecarsProofs(slot));
  }

  @Override
  public SafeFuture<Void> onFirstCustodyIncompleteSlot(final UInt64 slot) {
    return SafeFuture.fromRunnable(() -> database.setFirstCustodyIncompleteSlot(slot));
  }

  @Override
  public SafeFuture<Void> onEarliestAvailableDataColumnSlot(final UInt64 slot) {
    return SafeFuture.fromRunnable(() -> database.setEarliestAvailableDataColumnSlot(slot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot() {
    return SafeFuture.of(database::getEarliestAvailableDataColumnSlot);
  }

  @Override
  public SafeFuture<Void> onNewSidecar(final DataColumnSidecar sidecar) {
    return SafeFuture.fromRunnable(() -> database.addSidecar(sidecar))
        .thenRun(() -> sidecarStoredListener.accept(sidecar.getSlot()));
  }

  @Override
  public SafeFuture<Void> onNewBestLightClientUpdate(
      final UInt64 period, final LightClientUpdate update, final Bytes32 signatureBlockRoot) {
    return SafeFuture.fromRunnable(
        () -> database.storeBestLightClientUpdate(period, update, signatureBlockRoot));
  }

  @Override
  public SafeFuture<Void> onRemoveBestLightClientUpdates(final Collection<UInt64> periods) {
    return SafeFuture.fromRunnable(() -> database.removeBestLightClientUpdates(periods));
  }

  @Override
  public SafeFuture<List<StoredLightClientUpdate>> getBestLightClientUpdates() {
    return SafeFuture.of(
        () -> {
          try (final Stream<Map.Entry<UInt64, LightClientUpdate>> updates =
              database.streamBestLightClientUpdates()) {
            return updates
                .flatMap(
                    entry ->
                        database
                            .getBestLightClientUpdateSignatureBlockRoot(entry.getKey())
                            .map(
                                root ->
                                    new StoredLightClientUpdate(
                                        entry.getKey(), entry.getValue(), root))
                            .stream())
                .toList();
          }
        });
  }

  @Override
  public SafeFuture<Void> onPruneBestLightClientUpdatesBefore(final UInt64 period) {
    return SafeFuture.fromRunnable(() -> database.pruneBestLightClientUpdatesBefore(period));
  }

  @Override
  public SafeFuture<Void> onNewLightClientBootstrap(final LightClientBootstrap bootstrap) {
    return SafeFuture.fromRunnable(() -> database.storeLightClientBootstrap(bootstrap));
  }

  @Override
  public SafeFuture<Optional<LightClientBootstrap>> getLightClientBootstrap(final UInt64 slot) {
    return SafeFuture.of(() -> database.getLightClientBootstrap(slot));
  }
}

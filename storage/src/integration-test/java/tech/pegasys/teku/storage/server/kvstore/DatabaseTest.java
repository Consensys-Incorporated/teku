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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.storage.store.StoreAssertions.assertStoresMatch;

import com.google.common.collect.Streams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.generator.ChainProperties;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.FinalizedChainData;
import tech.pegasys.teku.storage.api.GloasForkChoiceRebuildData;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.archive.filesystem.FileSystemBlobSidecarsArchiver;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseContext;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.TestDatabaseContext;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.HotUpdater;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreAssertions;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

@TestDatabaseContext
@Execution(ExecutionMode.CONCURRENT)
public class DatabaseTest {
  private static final Logger LOG = LogManager.getLogger();
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);

  protected Spec spec;
  DataStructureUtil dataStructureUtil;
  private ChainBuilder chainBuilder;
  private ChainProperties chainProperties;
  private final List<File> tmpDirectories = new ArrayList<>();
  private final UInt64 genesisTime = UInt64.valueOf(100);
  private AnchorPoint genesisAnchor;
  private SignedBlockAndState genesisBlockAndState;
  private SignedBlockAndState checkpoint1BlockAndState;
  private SignedBlockAndState checkpoint2BlockAndState;
  private SignedBlockAndState checkpoint3BlockAndState;
  private Checkpoint genesisCheckpoint;
  private Checkpoint checkpoint1;
  private Checkpoint checkpoint2;
  private Checkpoint checkpoint3;
  private StateStorageMode storageMode;
  private StorageSystem storageSystem;
  private Database database;
  private FileSystemBlobSidecarsArchiver blobSidecarsArchiver;
  private RecentChainData recentChainData;
  private UpdatableStore store;
  private final List<StorageSystem> storageSystems = new ArrayList<>();

  @BeforeEach
  public void setup(final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
  }

  private void setupWithSpec(final Spec spec) throws IOException {
    setupWithSpec(spec, VALIDATOR_KEYS);
  }

  private void setupWithSpec(final Spec spec, final List<BLSKeyPair> validatorKeys)
      throws IOException {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.chainBuilder = ChainBuilder.create(spec, validatorKeys);
    this.chainProperties = new ChainProperties(spec);
    final Path blobsArchive = Files.createTempDirectory("blobs");
    tmpDirectories.add(blobsArchive.toFile());
    this.blobSidecarsArchiver = new FileSystemBlobSidecarsArchiver(spec, blobsArchive);
    genesisBlockAndState = chainBuilder.generateGenesis(genesisTime, true);
    genesisCheckpoint = getCheckpointForBlock(genesisBlockAndState.getBlock());
    genesisAnchor = AnchorPoint.fromGenesisState(spec, genesisBlockAndState.getState());
  }

  private void initialize(final DatabaseContext context, final StateStorageMode storageMode)
      throws IOException {
    final StoreConfig config = StoreConfig.builder().stateCacheSize(32).build();
    createStorageSystem(context, storageMode, config, false);

    // Initialize genesis store
    initGenesis();
  }

  private void initialize(final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.ARCHIVE);
  }

  @AfterEach
  public void reset() throws Exception {
    for (StorageSystem storageSystem : storageSystems) {
      storageSystem.close();
    }
    for (File tmpDirectory : tmpDirectories) {
      MoreFiles.deleteRecursively(tmpDirectory.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    }
    tmpDirectories.clear();
  }

  private void restartStorage() {
    final StorageSystem storage = storageSystem.restarted(storageMode);
    setDefaultStorage(storage);
  }

  @TestTemplate
  public void createMemoryStoreFromEmptyDatabase(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);
    assertThat(database.createMemoryStore()).isEmpty();
  }

  @TestTemplate
  public void shouldRecreateOriginalGenesisStore(final DatabaseContext context) throws IOException {
    initialize(context);
    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
  }

  @TestTemplate
  public void updateWeakSubjectivityState_setValue(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    assertThat(database.getWeakSubjectivityState().getCheckpoint()).isEmpty();

    final WeakSubjectivityUpdate update =
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(checkpoint);
    database.updateWeakSubjectivityState(update);

    assertThat(database.getWeakSubjectivityState().getCheckpoint()).contains(checkpoint);
  }

  @TestTemplate
  public void verifyBlobsLifecycle(final DatabaseContext context) throws IOException {
    initialize(context);

    // no blobs, earliest slot 0, because of genesis Deneb
    assertThat(database.getEarliestBlobSidecarSlot()).contains(ZERO);

    final BlobSidecar block1Sidecar0 =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);
    final SignedBeaconBlock blockAt2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final BlobSidecar block2Sidecar0 = dataStructureUtil.randomBlobSidecarForBlock(blockAt2, 0);
    final BlobSidecar block2Sidecar1 = dataStructureUtil.randomBlobSidecarForBlock(blockAt2, 1);
    final BlobSidecar block3Sidecar0 =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(3), 0);
    final BlobSidecar block5Sidecar0 =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(5), 0);
    final BlobSidecar blobSidecarNotAdded = dataStructureUtil.randomBlobSidecar();

    // add blobs out of order
    database.storeBlobSidecar(block2Sidecar0);
    database.storeBlobSidecar(block1Sidecar0);
    database.storeBlobSidecar(block2Sidecar1);
    database.storeBlobSidecar(block5Sidecar0);
    database.storeBlobSidecar(block3Sidecar0);

    assertThat(database.getBlobSidecarColumnCount()).isEqualTo(5L);
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.of(block1Sidecar0.getSlot()),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            false));
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.of(block3Sidecar0.getSlot()),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            false));
    // Will not be overridden from Database interface, only initial set
    assertThat(database.getEarliestBlobSidecarSlot()).contains(ZERO);

    // all added blobs must be there
    List.of(block1Sidecar0, block2Sidecar0, block2Sidecar1, block3Sidecar0, block5Sidecar0)
        .forEach(
            blobSidecar ->
                assertThat(database.getBlobSidecar(blobSidecarToKey(blobSidecar)))
                    .contains(blobSidecar));

    // non added blobs must not be there
    assertThat(database.getBlobSidecar(blobSidecarToKey(blobSidecarNotAdded))).isEmpty();

    // all blobs must be streamed ordered by slot
    assertBlobSidecarKeys(
        block1Sidecar0.getSlot(),
        block5Sidecar0.getSlot(),
        blobSidecarToKey(block1Sidecar0),
        blobSidecarToKey(block2Sidecar0),
        blobSidecarToKey(block2Sidecar1),
        blobSidecarToKey(block3Sidecar0),
        blobSidecarToKey(block5Sidecar0));
    assertBlobSidecars(
        Map.of(
            block1Sidecar0.getSlot(),
            List.of(block1Sidecar0),
            block2Sidecar0.getSlot(),
            List.of(block2Sidecar0, block2Sidecar1),
            block3Sidecar0.getSlot(),
            List.of(block3Sidecar0),
            block5Sidecar0.getSlot(),
            List.of(block5Sidecar0)));

    // let's prune with limit to 1
    assertThat(database.pruneOldestBlobSidecars(UInt64.MAX_VALUE, 1, blobSidecarsArchiver))
        .isTrue();
    assertBlobSidecarKeys(
        block2Sidecar0.getSlot(),
        block5Sidecar0.getSlot(),
        blobSidecarToKey(block2Sidecar0),
        blobSidecarToKey(block2Sidecar1),
        blobSidecarToKey(block3Sidecar0),
        blobSidecarToKey(block5Sidecar0));
    assertBlobSidecars(
        Map.of(
            block2Sidecar0.getSlot(), List.of(block2Sidecar0, block2Sidecar1),
            block3Sidecar0.getSlot(), List.of(block3Sidecar0),
            block5Sidecar0.getSlot(), List.of(block5Sidecar0)));
    assertThat(database.getEarliestBlobSidecarSlot()).contains(UInt64.valueOf(2));
    assertThat(database.getBlobSidecarColumnCount()).isEqualTo(4L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block1Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block2Sidecar0)).doesNotExist();

    // let's prune up to slot 1 (nothing will be pruned)
    assertThat(database.pruneOldestBlobSidecars(ONE, 10, blobSidecarsArchiver)).isFalse();
    assertBlobSidecarKeys(
        block2Sidecar0.getSlot(),
        block5Sidecar0.getSlot(),
        blobSidecarToKey(block2Sidecar0),
        blobSidecarToKey(block2Sidecar1),
        blobSidecarToKey(block3Sidecar0),
        blobSidecarToKey(block5Sidecar0));
    assertBlobSidecars(
        Map.of(
            block2Sidecar0.getSlot(), List.of(block2Sidecar0, block2Sidecar1),
            block3Sidecar0.getSlot(), List.of(block3Sidecar0),
            block5Sidecar0.getSlot(), List.of(block5Sidecar0)));
    assertThat(database.getEarliestBlobSidecarSlot()).contains(UInt64.valueOf(2));
    assertThat(database.getBlobSidecarColumnCount()).isEqualTo(4L);

    // let's prune all from slot 4 excluded
    assertThat(database.pruneOldestBlobSidecars(UInt64.valueOf(3), 10, blobSidecarsArchiver))
        .isFalse();
    assertBlobSidecarKeys(
        block1Sidecar0.getSlot(), block5Sidecar0.getSlot(), blobSidecarToKey(block5Sidecar0));
    assertBlobSidecars(Map.of(block5Sidecar0.getSlot(), List.of(block5Sidecar0)));
    assertThat(database.getEarliestBlobSidecarSlot()).contains(UInt64.valueOf(4));
    assertThat(database.getBlobSidecarColumnCount()).isEqualTo(1L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block2Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block3Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block5Sidecar0)).doesNotExist();

    // let's prune all
    assertThat(database.pruneOldestBlobSidecars(UInt64.valueOf(5), 1, blobSidecarsArchiver))
        .isTrue();
    // all empty now
    assertBlobSidecarKeys(ZERO, UInt64.valueOf(10));
    assertThat(database.getEarliestBlobSidecarSlot()).contains(UInt64.valueOf(6));
    assertThat(database.getBlobSidecarColumnCount()).isEqualTo(0L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block5Sidecar0)).exists();
  }

  private Path getSlotBlobsArchiveFile(final BlobSidecar blobSidecar) {
    return blobSidecarsArchiver.resolveArchivePath(
        blobSidecar.getSlotAndBlockRoot().getBlockRoot());
  }

  @TestTemplate
  public void verifyNonCanonicalBlobsLifecycle(final DatabaseContext context) throws IOException {
    initialize(context);

    final BlobSidecar block1Sidecar0 =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final BlobSidecar block2Sidecar0 = dataStructureUtil.randomBlobSidecarForBlock(block2, 0);
    final BlobSidecar block2Sidecar1 = dataStructureUtil.randomBlobSidecarForBlock(block2, 1);
    final BlobSidecar block3Sidecar0 =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(3), 0);
    final BlobSidecar block5Sidecar0 =
        dataStructureUtil.randomBlobSidecarForBlock(
            dataStructureUtil.randomSignedBeaconBlock(5), 0);
    final BlobSidecar blobSidecarNotAdded = dataStructureUtil.randomBlobSidecar();

    // add non-canonical blobs out of order
    database.storeNonCanonicalBlobSidecar(block2Sidecar0);
    database.storeNonCanonicalBlobSidecar(block1Sidecar0);
    database.storeNonCanonicalBlobSidecar(block2Sidecar1);
    database.storeNonCanonicalBlobSidecar(block5Sidecar0);
    database.storeNonCanonicalBlobSidecar(block3Sidecar0);

    assertThat(database.getNonCanonicalBlobSidecarColumnCount()).isEqualTo(5L);

    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.empty(),
            Map.of(block1Sidecar0.getBlockRoot(), block1Sidecar0.getSlot()),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            false));
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.empty(),
            Map.of(block3Sidecar0.getBlockRoot(), block3Sidecar0.getSlot()),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            false));

    // check all non-canonical blobs are present
    List.of(block1Sidecar0, block2Sidecar0, block2Sidecar1, block3Sidecar0, block5Sidecar0)
        .forEach(
            blobSidecar ->
                assertThat(database.getNonCanonicalBlobSidecar(blobSidecarToKey(blobSidecar)))
                    .contains(blobSidecar));

    // check blobSidecarNotAdded is not present
    assertThat(database.getNonCanonicalBlobSidecar(blobSidecarToKey(blobSidecarNotAdded)))
        .isEmpty();

    // all non-canonical blobs must be streamed ordered by slot
    assertNonCanonicalBlobSidecarKeys(
        block1Sidecar0.getSlot(),
        block5Sidecar0.getSlot(),
        blobSidecarToKey(block1Sidecar0),
        blobSidecarToKey(block2Sidecar0),
        blobSidecarToKey(block2Sidecar1),
        blobSidecarToKey(block3Sidecar0),
        blobSidecarToKey(block5Sidecar0));
    assertNonCanonicalBlobSidecars(
        Map.of(
            block1Sidecar0.getSlot(),
            List.of(block1Sidecar0),
            block2Sidecar0.getSlot(),
            List.of(block2Sidecar0, block2Sidecar1),
            block3Sidecar0.getSlot(),
            List.of(block3Sidecar0),
            block5Sidecar0.getSlot(),
            List.of(block5Sidecar0)));

    // Pruning with a prune limit set to 1: Only blobSidecar1 will be pruned
    assertThat(
            database.pruneOldestNonCanonicalBlobSidecars(UInt64.MAX_VALUE, 1, blobSidecarsArchiver))
        .isTrue();
    assertNonCanonicalBlobSidecarKeys(
        block2Sidecar0.getSlot(),
        block5Sidecar0.getSlot(),
        blobSidecarToKey(block2Sidecar0),
        blobSidecarToKey(block2Sidecar1),
        blobSidecarToKey(block3Sidecar0),
        blobSidecarToKey(block5Sidecar0));
    assertNonCanonicalBlobSidecars(
        Map.of(
            block2Sidecar0.getSlot(), List.of(block2Sidecar0, block2Sidecar1),
            block3Sidecar0.getSlot(), List.of(block3Sidecar0),
            block5Sidecar0.getSlot(), List.of(block5Sidecar0)));
    assertThat(database.getNonCanonicalBlobSidecarColumnCount()).isEqualTo(4L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block1Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block2Sidecar0)).doesNotExist();

    // Pruning up to slot 1: No blobs pruned
    assertThat(database.pruneOldestNonCanonicalBlobSidecars(ONE, 10, blobSidecarsArchiver))
        .isFalse();
    assertNonCanonicalBlobSidecarKeys(
        block2Sidecar0.getSlot(),
        block5Sidecar0.getSlot(),
        blobSidecarToKey(block2Sidecar0),
        blobSidecarToKey(block2Sidecar1),
        blobSidecarToKey(block3Sidecar0),
        blobSidecarToKey(block5Sidecar0));
    assertNonCanonicalBlobSidecars(
        Map.of(
            block2Sidecar0.getSlot(), List.of(block2Sidecar0, block2Sidecar1),
            block3Sidecar0.getSlot(), List.of(block3Sidecar0),
            block5Sidecar0.getSlot(), List.of(block5Sidecar0)));
    assertThat(database.getNonCanonicalBlobSidecarColumnCount()).isEqualTo(4L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block1Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block2Sidecar0)).doesNotExist();

    // Prune blobs up to slot 3
    assertThat(
            database.pruneOldestNonCanonicalBlobSidecars(
                UInt64.valueOf(3), 10, blobSidecarsArchiver))
        .isFalse();
    assertNonCanonicalBlobSidecarKeys(
        block1Sidecar0.getSlot(), block5Sidecar0.getSlot(), blobSidecarToKey(block5Sidecar0));
    assertNonCanonicalBlobSidecars(Map.of(block5Sidecar0.getSlot(), List.of(block5Sidecar0)));
    assertThat(database.getNonCanonicalBlobSidecarColumnCount()).isEqualTo(1L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block2Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block3Sidecar0)).exists();
    assertThat(getSlotBlobsArchiveFile(block5Sidecar0)).doesNotExist();

    // Pruning all blobs
    assertThat(
            database.pruneOldestNonCanonicalBlobSidecars(
                UInt64.valueOf(5), 1, blobSidecarsArchiver))
        .isTrue();
    // No blobs should be left
    assertNonCanonicalBlobSidecarKeys(ZERO, UInt64.valueOf(10));
    assertThat(database.getNonCanonicalBlobSidecarColumnCount()).isEqualTo(0L);

    // check if the pruned blob was written to disk. Not validating contents here.
    assertThat(getSlotBlobsArchiveFile(block5Sidecar0)).exists();
  }

  @TestTemplate
  public void canGetAndUpdateCustodyGroupCount(final DatabaseContext context) throws IOException {
    initialize(context);
    assertThat(recentChainData.getCustodyGroupCount()).isEmpty();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setCustodyGroupCount(UInt64.ONE);
    assertThat(transaction.commit()).isCompleted();
    assertThat(recentChainData.getCustodyGroupCount()).contains(UInt64.ONE);
    assertThat(database.getCustodyGroupCount()).contains(UInt64.ONE);
  }

  @TestTemplate
  public void updateWeakSubjectivityState_clearValue(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();

    // Set an initial value
    final WeakSubjectivityUpdate initUpdate =
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(checkpoint);
    database.updateWeakSubjectivityState(initUpdate);
    assertThat(database.getWeakSubjectivityState().getCheckpoint()).contains(checkpoint);

    // Clear the checkpoint
    final WeakSubjectivityUpdate update = WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint();
    database.updateWeakSubjectivityState(update);

    assertThat(database.getWeakSubjectivityState().getCheckpoint()).isEmpty();
  }

  @TestTemplate
  public void shouldGetHotBlockByRoot(final DatabaseContext context) throws IOException {
    initialize(context);
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(block1, spec.calculateBlockCheckpoints(block1.getState()));
    transaction.putBlockAndState(block2, spec.calculateBlockCheckpoints(block2.getState()));

    commit(transaction);

    assertThat(database.getSignedBlock(block1.getRoot())).contains(block1.getBlock());
    assertThat(database.getSignedBlock(block2.getRoot())).contains(block2.getBlock());
  }

  @TestTemplate
  public void shouldStoreBlindedExecutionPayloadEnvelope(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope =
        getSignedBlindedExecutionPayloadEnvelope(blockAndState);

    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(blockAndState.getRoot(), signedBlindedExecutionPayloadEnvelope),
            Map.of(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));

    assertThat(database.getBlindedExecutionPayloadEnvelope(blockAndState.getRoot()))
        .contains(signedBlindedExecutionPayloadEnvelope);
  }

  @TestTemplate
  public void shouldPopulateGloasForkChoiceRebuildDataFromBlindedExecutionPayloadEnvelope(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedExecutionPayloadEnvelope executionPayload =
        chainBuilder.getExecutionPayloadAtSlot(blockAndState.getSlot()).orElseThrow();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(
        blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    transaction.putExecutionPayload(executionPayload, false);

    commit(transaction);

    final ExecutionPayloadBid bid = getExecutionPayloadBid(blockAndState);
    assertThat(getHotBlockMetadata(blockAndState).getGloasForkChoiceRebuildData())
        .contains(
            new GloasForkChoiceRebuildData(
                bid.getParentBlockHash(),
                bid.getBlockHash(),
                Optional.of(executionPayload.getMessage().getPayload().getBlockNumber()),
                Optional.of(executionPayload.getMessage().getPayload().getGasLimit())));
  }

  @TestTemplate
  public void shouldLeaveGloasPayloadBlockNumberEmptyWhenBlindedExecutionPayloadEnvelopeIsMissing(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(
        blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));

    commit(transaction);

    final ExecutionPayloadBid bid = getExecutionPayloadBid(blockAndState);
    assertThat(getHotBlockMetadata(blockAndState).getGloasForkChoiceRebuildData())
        .contains(
            new GloasForkChoiceRebuildData(
                bid.getParentBlockHash(), bid.getBlockHash(), Optional.empty()));
  }

  @TestTemplate
  public void shouldRejectMismatchedBlindedExecutionPayloadEnvelopeForGloasRebuildData(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(
        blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState()));
    final SignedBlindedExecutionPayloadEnvelope mismatchedEnvelope =
        dataStructureUtil
            .randomSignedExecutionPayloadEnvelopeForBlock(blockAndState.getBlock())
            .blind(spec);

    commit(transaction);
    storeBlindedExecutionPayloadEnvelope(blockAndState.getRoot(), mismatchedEnvelope);

    assertThatThrownBy(() -> getHotBlockMetadata(blockAndState))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not match GLOAS block bid");
  }

  @TestTemplate
  public void shouldRetainBlindedExecutionPayloadEnvelopeAfterFinalization(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope =
        getSignedBlindedExecutionPayloadEnvelope(blockAndState);
    final FinalizedChainData finalizedChainData =
        FinalizedChainData.builder()
            .latestFinalized(AnchorPoint.fromInitialBlockAndState(spec, blockAndState))
            .finalizedChildAndParent(
                genesisBlockAndState.getRoot(), genesisBlockAndState.getParentRoot())
            .build();

    // Store envelope
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(blockAndState.getRoot(), signedBlindedExecutionPayloadEnvelope),
            Map.of(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));

    // Finalize. Envelope must not be deleted for canonical blocks.
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.of(finalizedChainData),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.empty(),
            Map.of(genesisBlockAndState.getRoot(), genesisBlockAndState.getSlot()),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));

    // Envelope should still be present — canonical finalized blocks are not pruned
    assertThat(database.getBlindedExecutionPayloadEnvelope(blockAndState.getRoot()))
        .contains(signedBlindedExecutionPayloadEnvelope);
  }

  @TestTemplate
  public void shouldSkipMissingBlindedExecutionPayloadEnvelope(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final FinalizedChainData finalizedChainData =
        FinalizedChainData.builder()
            .latestFinalized(AnchorPoint.fromInitialBlockAndState(spec, blockAndState))
            .finalizedChildAndParent(
                genesisBlockAndState.getRoot(), genesisBlockAndState.getParentRoot())
            .build();

    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.of(finalizedChainData),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));

    assertThat(database.getBlindedExecutionPayloadEnvelope(blockAndState.getRoot())).isEmpty();
  }

  @TestTemplate
  public void shouldDeleteBlindedExecutionPayloadEnvelopeWhenBlockIsPruned(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalGloas(), BLSKeyGenerator.generateKeyPairs(8));
    initialize(context);
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope =
        getSignedBlindedExecutionPayloadEnvelope(blockAndState);

    // Store envelope
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(blockAndState.getRoot(), signedBlindedExecutionPayloadEnvelope),
            Map.of(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));

    // Verify it's stored
    assertThat(database.getBlindedExecutionPayloadEnvelope(blockAndState.getRoot()))
        .contains(signedBlindedExecutionPayloadEnvelope);

    // Delete the block (simulates non-canonical pruning)
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Optional.empty(),
            Map.of(blockAndState.getRoot(), blockAndState.getSlot()),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));

    // Verify envelope was removed
    assertThat(database.getBlindedExecutionPayloadEnvelope(blockAndState.getRoot())).isEmpty();
  }

  private void commit(final StoreTransaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  private StoredBlockMetadata getHotBlockMetadata(final SignedBlockAndState blockAndState) {
    return ((KvStoreDatabase) database).buildHotBlockMetadata().get(blockAndState.getRoot());
  }

  private ExecutionPayloadBid getExecutionPayloadBid(final SignedBlockAndState blockAndState) {
    return blockAndState
        .getBlock()
        .getMessage()
        .getBody()
        .getOptionalSignedExecutionPayloadBid()
        .map(SignedExecutionPayloadBid::getMessage)
        .orElseThrow();
  }

  private void storeBlindedExecutionPayloadEnvelope(
      final Bytes32 blockRoot, final SignedBlindedExecutionPayloadEnvelope envelope) {
    database.update(
        new StorageUpdate(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(blockRoot, envelope),
            Map.of(),
            Optional.empty(),
            Map.of(),
            Map.of(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true));
  }

  private SignedBlindedExecutionPayloadEnvelope getSignedBlindedExecutionPayloadEnvelope(
      final SignedBlockAndState blockAndState) {
    final SignedExecutionPayloadEnvelope executionPayload =
        chainBuilder.getExecutionPayloadAtSlot(blockAndState.getSlot()).orElseThrow();
    return executionPayload.blind(spec);
  }

  @TestTemplate
  public void shouldPruneHotBlocksAndBlobsAddedOverMultipleSessions(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final BlockOptions randomBlobsOptions = BlockOptions.create().setGenerateRandomBlobs(true);
    final UInt64 targetSlot = UInt64.valueOf(10);

    chainBuilder.generateBlocksUpToSlot(
        targetSlot.minus(UInt64.ONE).longValue(), randomBlobsOptions);
    final ChainBuilder forkA = chainBuilder.fork();
    final ChainBuilder forkB = chainBuilder.fork();

    // Add base blocks
    addBlocksAndBlobSidecars(
        chainBuilder.streamBlocksAndStates().collect(toList()),
        chainBuilder.streamBlobSidecars().collect(toList()));

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(targetSlot)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .toList();

    // Create several different blocks at the same slot
    final SignedBlockAndState blockA =
        forkA.generateBlockAtSlot(targetSlot, blockOptions.get(0).setGenerateRandomBlobs(true));
    final SignedBlockAndState blockB =
        forkB.generateBlockAtSlot(targetSlot, blockOptions.get(1).setGenerateRandomBlobs(true));
    final SignedBlockAndState blockC = chainBuilder.generateBlockAtSlot(10, randomBlobsOptions);
    final Set<Bytes32> block10Roots = Set.of(blockA.getRoot(), blockB.getRoot(), blockC.getRoot());
    // Sanity check
    assertThat(block10Roots.size()).isEqualTo(3);

    // Add blocks at same height sequentially
    add(List.of(blockA), forkA.getBlobSidecars(blockA.getRoot()));
    add(List.of(blockB), forkB.getBlobSidecars(blockB.getRoot()));
    add(List.of(blockC), chainBuilder.getBlobSidecars(blockC.getRoot()));

    // Verify all blocks are available
    assertThat(store.retrieveBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock().getMessage()));
    assertThat(store.retrieveBlock(blockB.getRoot()))
        .isCompletedWithValue(Optional.of(blockB.getBlock().getMessage()));
    assertThat(store.retrieveBlock(blockC.getRoot()))
        .isCompletedWithValue(Optional.of(blockC.getBlock().getMessage()));

    // verify all blob sidecars are available
    final List<SignedBeaconBlock> blocksWithAvailableSidecars =
        Stream.concat(
                Stream.concat(
                    chainBuilder.streamBlocksAndStates(ONE),
                    forkA.streamBlocksAndStates(targetSlot)),
                forkB.streamBlocksAndStates(targetSlot))
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    assertBlobSidecarsAvailabilityExceptPruned(blocksWithAvailableSidecars, List.of());

    // Finalize subsequent block to prune blocks a, b, and c
    final SignedBlockAndState finalBlock = chainBuilder.generateBlockAtSlot(11, randomBlobsOptions);
    add(List.of(finalBlock), chainBuilder.getBlobSidecars(finalBlock.getRoot()));

    final UInt64 finalEpoch = chainBuilder.getLatestEpoch().plus(ONE);
    final SignedBlockAndState finalizedBlock =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(finalEpoch);
    justifyAndFinalizeEpoch(finalEpoch, finalizedBlock);

    // Check pruning result
    final Set<Bytes32> rootsToPrune = new HashSet<>(block10Roots);
    rootsToPrune.add(genesisBlockAndState.getRoot());
    // Check that all blocks at slot 10 were pruned
    assertRecentDataWasPruned(store, rootsToPrune);

    // verify we have all canonical and finalized sidecars and blockA and blockB sidecars have been
    // pruned
    final List<SignedBeaconBlock> canonicalBlocksWithAvailableSidecars =
        chainBuilder
            .streamBlocksAndStates(ONE)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());

    assertBlobSidecarsAvailabilityExceptPruned(
        canonicalBlocksWithAvailableSidecars, List.of(blockA.getBlock(), blockB.getBlock()));
  }

  @TestTemplate
  public void shouldPruneHotBlocksInCurrentTransactionFromChainThatIsInvalided(
      final DatabaseContext context) throws IOException {
    initialize(context);
    final UInt64 commonAncestorSlot = UInt64.valueOf(5);

    chainBuilder.generateBlocksUpToSlot(commonAncestorSlot);
    final ChainBuilder forkA = chainBuilder;
    final ChainBuilder forkB = chainBuilder.fork();

    // Add base blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    // Forks diverge - generate block options to make each block unique
    final UInt64 divergingSlot = commonAncestorSlot.plus(1);
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(divergingSlot)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .toList();

    // Create several different blocks at the same slot
    final SignedBlockAndState blockA =
        forkA.generateBlockAtSlot(divergingSlot, blockOptions.get(0));
    final SignedBlockAndState blockB =
        forkB.generateBlockAtSlot(divergingSlot, blockOptions.get(1));

    // Add diverging blocks sequentially
    add(List.of(blockA));
    add(List.of(blockB));

    // Then build on both chains, into the next epoch
    final SignedBlockAndState blockA2 =
        forkA.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) * 2L + 2);
    final SignedBlockAndState blockB2 =
        forkB.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) * 2L + 2);

    // Add blocks while finalizing blockA at the same time
    StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(blockA2, spec.calculateBlockCheckpoints(blockA2.getState()));
    tx.putBlockAndState(blockB2, spec.calculateBlockCheckpoints(blockB2.getState()));
    justifyAndFinalizeEpoch(UInt64.ONE, blockA, tx);
    assertThat(tx.commit()).isCompleted();

    // Verify all fork B blocks were pruned
    assertThatSafeFuture(store.retrieveBlock(blockB.getRoot())).isCompletedWithEmptyOptional();
    assertThatSafeFuture(store.retrieveBlock(blockB2.getRoot())).isCompletedWithEmptyOptional();

    // And fork A should be available.
    assertThat(store.retrieveSignedBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock()));
    assertThat(store.retrieveSignedBlock(blockA2.getRoot()))
        .isCompletedWithValue(Optional.of(blockA2.getBlock()));
  }

  @TestTemplate
  public void getFinalizedState(final DatabaseContext context) throws IOException {
    initialize(context);
    generateCheckpoints();
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(UInt64.ONE);
    final SignedBlockAndState block2 =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(UInt64.ONE);
    final SignedBlockAndState block1 =
        chainBuilder.getBlockAndStateAtSlot(block2.getSlot().minus(UInt64.ONE));

    final List<SignedBlockAndState> allBlocks =
        chainBuilder.streamBlocksAndStates(0, block2.getSlot().longValue()).collect(toList());
    addBlocks(allBlocks);

    // Finalize block2
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setFinalizedCheckpoint(finalizedCheckpoint, false);
    commit(transaction);

    assertThat(database.getLatestAvailableFinalizedState(block2.getSlot()))
        .contains(block2.getState());
    assertThat(database.getLatestAvailableFinalizedState(block1.getSlot()))
        .contains(block1.getState());
  }

  @TestTemplate
  public void shouldStoreSingleValueFields(final DatabaseContext context) throws IOException {
    initialize(context);
    generateCheckpoints();

    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesisTime(UInt64.valueOf(3));
    transaction.setFinalizedCheckpoint(checkpoint1, false);
    transaction.setJustifiedCheckpoint(checkpoint2);
    transaction.setBestJustifiedCheckpoint(checkpoint3);

    commit(transaction);

    final UpdatableStore result = recreateStore();

    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @TestTemplate
  public void shouldStoreSingleValue_genesisTime(final DatabaseContext context) throws IOException {
    initialize(context);
    final UInt64 newGenesisTime = UInt64.valueOf(3);
    // Sanity check
    assertThat(store.getGenesisTime()).isNotEqualTo(newGenesisTime);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesisTime(newGenesisTime);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
  }

  @TestTemplate
  public void shouldStoreSingleValue_justifiedCheckpoint(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setJustifiedCheckpoint(newValue);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @TestTemplate
  public void shouldStoreSingleValue_finalizedCheckpoint(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getFinalizedCheckpoint()).isNotEqualTo(checkpoint3);

    justifyAndFinalizeEpoch(newValue.getEpoch(), checkpoint3BlockAndState);

    final UpdatableStore result = recreateStore();
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(newValue);
  }

  @TestTemplate
  public void shouldStoreSingleValue_bestJustifiedCheckpoint(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getBestJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setBestJustifiedCheckpoint(newValue);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getBestJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @TestTemplate
  public void shouldStoreSingleValue_singleBlockAndState(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
    // Sanity check
    assertThatSafeFuture(store.retrieveBlock(newBlock.getRoot())).isCompletedWithEmptyOptional();

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock, spec.calculateBlockCheckpoints(newBlock.getState()));
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.retrieveSignedBlock(newBlock.getRoot()))
        .isCompletedWithValue(Optional.of(newBlock.getBlock()));
    assertThat(result.retrieveBlockState(newBlock.getRoot()))
        .isCompletedWithValue(Optional.of(newBlock.getState()));
  }

  @TestTemplate
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Bytes32 genesisRoot = genesisBlockAndState.getRoot();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();

    final SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(
        blockAndState1, spec.calculateBlockCheckpoints(blockAndState1.getState()));
    transaction.putBlockAndState(
        blockAndState2, spec.calculateBlockCheckpoints(blockAndState2.getState()));

    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.retrieveSignedBlock(genesisRoot))
        .isCompletedWithValue(Optional.of(genesisBlockAndState.getBlock()));
    assertThat(result.retrieveSignedBlock(blockAndState1.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState1.getBlock()));
    assertThat(result.retrieveSignedBlock(blockAndState2.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState2.getBlock()));
    assertThat(result.retrieveBlockState(blockAndState1.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState1.getState()));
    assertThat(result.retrieveBlockState(blockAndState2.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState2.getState()));
  }

  @TestTemplate
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint2BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    // Finalize block
    justifyAndFinalizeEpoch(checkpoint1.getEpoch(), checkpoint1BlockAndState);

    final List<SignedBlockAndState> historicalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint1BlockAndState.getSlot().longValue())
            .collect(toList());
    historicalBlocks.remove(checkpoint1BlockAndState);
    final List<SignedBlockAndState> hotBlocks =
        chainBuilder
            .streamBlocksAndStates(
                checkpoint1BlockAndState.getSlot(), checkpoint2BlockAndState.getSlot())
            .toList();

    final UpdatableStore result = recreateStore();
    // Historical blocks should not be in the new store
    for (SignedBlockAndState historicalBlock : historicalBlocks) {
      assertThatSafeFuture(result.retrieveSignedBlock(historicalBlock.getRoot()))
          .isCompletedWithEmptyOptional();
      assertThatSafeFuture(result.retrieveBlockState(historicalBlock.getRoot()))
          .isCompletedWithEmptyOptional();
    }

    // Hot blocks should be available in the new store
    for (SignedBlockAndState hotBlock : hotBlocks) {
      assertThat(result.retrieveSignedBlock(hotBlock.getRoot()))
          .isCompletedWithValue(Optional.of(hotBlock.getBlock()));
      assertThat(result.retrieveBlockState(hotBlock.getRoot()))
          .isCompletedWithValue(Optional.of(hotBlock.getState()));
    }

    final Set<Bytes32> hotBlockRoots =
        hotBlocks.stream().map(SignedBlockAndState::getRoot).collect(Collectors.toSet());
    assertThat(result.getOrderedBlockRoots()).containsExactlyInAnyOrderElementsOf(hotBlockRoots);
  }

  @TestTemplate
  public void shouldRecordAndRetrieveGenesisInformation(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final MinGenesisTimeBlockEvent event =
        new MinGenesisTimeBlockEvent(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32());
    database.addMinGenesisTimeBlock(event);

    final Optional<MinGenesisTimeBlockEvent> fetch = database.getMinGenesisTimeBlock();
    assertThat(fetch).contains(event);
  }

  @TestTemplate
  public void handleFinalizationWhenCacheLimitsExceeded(final DatabaseContext context)
      throws IOException {
    initialize(context);

    final int startSlot = genesisBlockAndState.getSlot().intValue();
    final int minFinalSlot = startSlot + StoreConfig.DEFAULT_STATE_CACHE_SIZE + 10;
    final UInt64 finalizedEpoch = chainProperties.computeBestEpochFinalizableAtSlot(minFinalSlot);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Save all blocks and states in a single transaction
    final List<SignedBlockAndState> newBlocks =
        chainBuilder.streamBlocksAndStates(startSlot).collect(toList());
    add(newBlocks);
    // Then finalize
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    // All finalized blocks and states should be available
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        newBlocks.stream().map(SignedBlockAndState::getBlock).collect(toList());
    final Map<Bytes32, BeaconState> expectedFinalizedStates =
        newBlocks.stream()
            // Hot state is recorded for the first block of each epoch and we only use the available
            // states, so ensure that at least those are available (some others will be available
            // because they were in cache)
            .filter(
                blockAndState ->
                    blockAndState
                        .getSlot()
                        .equals(spec.computeStartSlotAtEpoch(blockAndState.getSlot())))
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertBlocksAvailable(expectedFinalizedBlocks);
    assertFinalizedStatesAvailable(expectedFinalizedStates);
  }

  @TestTemplate
  public void shouldRecordOptimisticTransitionExecutionPayloadWhenFinalized_singleTransaction(
      final DatabaseContext context) throws Exception {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in a single transaction
    add(newBlocks);

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<SlotAndExecutionPayloadSummary> transitionPayload =
        SlotAndExecutionPayloadSummary.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().getExecutionPayloadSummary().isDefaultPayload()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  @TestTemplate
  public void shouldNotRecordTransitionExecutionPayloadWhenNotOptimistic(
      final DatabaseContext context) throws Exception {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());

    // Save all blocks and states in a single transaction
    add(newBlocks);
    recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .onExecutionPayloadResult(transitionBlock.getRoot(), PayloadStatus.VALID, true);

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<ExecutionPayload> transitionPayload =
        transitionBlock.getBlock().getMessage().getBody().getOptionalExecutionPayload();
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().isDefault()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload()).isEmpty();
  }

  @TestTemplate
  public void shouldRecordOptimisticTransitionExecutionPayloadWhenFinalized_multiTransaction(
      final DatabaseContext context) throws Exception {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder.streamBlocksAndStates(genesisBlockAndState.getSlot().intValue()).toList();
    // Save all blocks and states in separate transactions
    for (SignedBlockAndState newBlock : newBlocks) {
      add(List.of(newBlock));
    }
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload()).isEmpty();

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<SlotAndExecutionPayloadSummary> transitionPayload =
        SlotAndExecutionPayloadSummary.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().getExecutionPayloadSummary().isDefaultPayload()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  @TestTemplate
  public void shouldPersistOptimisticTransitionExecutionPayload(final DatabaseContext context)
      throws Exception {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in a single transaction
    add(newBlocks);
    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<SlotAndExecutionPayloadSummary> transitionPayload =
        SlotAndExecutionPayloadSummary.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);

    restartStorage();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  @TestTemplate
  public void shouldClearOptimisticTransitionExecutionPayload(final DatabaseContext context)
      throws Exception {
    // Record optimistic transition execution payload.
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    add(newBlocks);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();
    final Optional<SlotAndExecutionPayloadSummary> transitionPayload =
        SlotAndExecutionPayloadSummary.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);

    // Clear optimistic transition payload
    final StoreTransaction clearTx = recentChainData.startStoreTransaction();
    clearTx.removeFinalizedOptimisticTransitionPayload();
    assertThat(clearTx.commit()).isCompleted();

    assertThat(store.getFinalizedOptimisticTransitionPayload()).isEmpty();

    restartStorage();
    assertThat(store.getFinalizedOptimisticTransitionPayload()).isEmpty();
  }

  @TestTemplate
  public void shouldNotRemoveOptimisticFinalizedExceptionPayloadWhenFinalizedNextUpdated(
      final DatabaseContext context) throws Exception {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in a single transaction
    add(newBlocks);

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    // Finalize the next epoch
    final List<SignedBlockAndState> laterBlocks =
        chainBuilder.generateBlocksUpToSlot(
            chainBuilder.getLatestSlot().plus(spec.getSlotsPerEpoch(chainBuilder.getLatestSlot())));
    add(laterBlocks);
    final Checkpoint finalizedCheckpoint2 =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx2 = recentChainData.startStoreTransaction();
    tx2.setFinalizedCheckpoint(finalizedCheckpoint2, false);
    assertThat(tx2.commit()).isCompleted();

    final Optional<SlotAndExecutionPayloadSummary> transitionPayload =
        SlotAndExecutionPayloadSummary.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().getExecutionPayloadSummary().isDefaultPayload()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  /**
   * Generates a chain in chainBuilder that can be finalized at the current epoch, including a merge
   * transition block.
   *
   * @return the merge transition block
   */
  private SignedBlockAndState generateChainWithFinalizableTransitionBlock(
      final DatabaseContext context) throws Exception {
    reset();
    setupWithSpec(TestSpecFactory.createMinimalBellatrix());
    initialize(context, StateStorageMode.PRUNE);

    final int startSlot = genesisBlockAndState.getSlot().intValue();
    final int minFinalSlot = startSlot + StoreConfig.DEFAULT_STATE_CACHE_SIZE + 10;
    final UInt64 finalizedEpoch = chainProperties.computeBestEpochFinalizableAtSlot(minFinalSlot);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    chainBuilder.generateBlocksUpToSlot(finalizedSlot.minus(5));
    final SignedBlockAndState transitionBlock =
        chainBuilder.generateBlockAtSlot(
            finalizedSlot.minus(4),
            BlockOptions.create()
                .setTransactions(Bytes32.ZERO)
                .setTerminalBlockHash(Bytes32.fromHexString("0x1234")));
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    return transitionBlock;
  }

  @TestTemplate
  public void shouldRecordFinalizedBlocksAndStates_pruneMode(final DatabaseContext context)
      throws IOException {
    testShouldRecordFinalizedBlocksAndStates(context, StateStorageMode.PRUNE, false);
  }

  @TestTemplate
  public void shouldRecordFinalizedBlocksAndStates_archiveMode(final DatabaseContext context)
      throws IOException {
    testShouldRecordFinalizedBlocksAndStates(context, StateStorageMode.ARCHIVE, false);
  }

  @TestTemplate
  public void testShouldRecordFinalizedBlocksAndStatesInBatchUpdate(final DatabaseContext context)
      throws IOException {
    testShouldRecordFinalizedBlocksAndStates(context, StateStorageMode.ARCHIVE, true);
  }

  @TestTemplate
  public void slotAndBlock_shouldStoreAndRetrieve(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomUInt64(), dataStructureUtil.randomBytes32());

    database.addHotStateRoots(Map.of(stateRoot, slotAndBlockRoot));

    final Optional<SlotAndBlockRoot> fromStorage =
        database.getSlotAndBlockRootFromStateRoot(stateRoot);

    assertThat(fromStorage.isPresent()).isTrue();
    assertThat(fromStorage.get()).isEqualTo(slotAndBlockRoot);
  }

  @TestTemplate
  public void getEarliestAvailableBlockSlot_withMissingFinalizedBlocks(
      final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec, new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()), anchorBlockAndState);
    initFromAnchor(anchor);

    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());
    // And finalize them
    justifyAndFinalizeEpoch(anchorEpoch.plus(1), chainBuilder.getLatestBlockAndState());

    assertThat(database.getEarliestAvailableBlockSlot()).contains(anchorBlockAndState.getSlot());
  }

  @TestTemplate
  public void getEarliestAvailableBlockSlot_noBlocksMissing(final DatabaseContext context)
      throws IOException {
    initialize(context);
    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());
    // And finalize them
    justifyAndFinalizeEpoch(UInt64.valueOf(1), chainBuilder.getLatestBlockAndState());

    assertThat(database.getEarliestAvailableBlockSlot()).contains(genesisBlockAndState.getSlot());
  }

  @TestTemplate
  public void slotAndBlock_shouldGetStateRootsBeforeSlot(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Bytes32 zeroStateRoot = insertRandomSlotAndBlock(0L, dataStructureUtil);
    final Bytes32 oneStateRoot = insertRandomSlotAndBlock(1L, dataStructureUtil);
    insertRandomSlotAndBlock(2L, dataStructureUtil);
    insertRandomSlotAndBlock(3L, dataStructureUtil);

    assertThat(database.getStateRootsBeforeSlot(UInt64.valueOf(2L)))
        .containsExactlyInAnyOrder(zeroStateRoot, oneStateRoot);
  }

  @TestTemplate
  public void slotAndBlock_shouldPurgeToSlot(final DatabaseContext context) throws IOException {
    initialize(context);
    insertRandomSlotAndBlock(0L, dataStructureUtil);
    insertRandomSlotAndBlock(1L, dataStructureUtil);
    final Bytes32 twoStateRoot = insertRandomSlotAndBlock(2L, dataStructureUtil);
    final Bytes32 threeStateRoot = insertRandomSlotAndBlock(3L, dataStructureUtil);

    database.pruneHotStateRoots(database.getStateRootsBeforeSlot(UInt64.valueOf(2L)));
    assertThat(database.getStateRootsBeforeSlot(UInt64.valueOf(10L)))
        .containsExactlyInAnyOrder(twoStateRoot, threeStateRoot);
  }

  @TestTemplate
  public void startupFromNonGenesisState_prune(final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisState(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void startupFromNonGenesisState_archive(final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisState(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void orphanedBlockStorageTest_withCanonicalBlocks(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), true);
    final CreateForkChainResult forkChainResult = createForkChain(false);
    assertBlocksAvailable(
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, forkChainResult.getFirstHotBlockSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList()));
  }

  @TestTemplate
  public void orphanedBlockStorageTest_multiple(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), true);
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Primary chain's next block is at 5
    primaryChain.generateBlockAtSlot(5);
    final ChainBuilder secondFork = primaryChain.fork();

    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(firstHotBlockSlot);
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlockAtSlot(firstHotBlockSlot);
    secondFork.generateBlockAtSlot(6);
    secondFork.generateBlockAtSlot(firstHotBlockSlot);

    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(
                primaryChain.streamBlocksAndStates(),
                forkChain.streamBlocksAndStates(),
                secondFork.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates);
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    assertThat(database.getNonCanonicalBlocksAtSlot(UInt64.valueOf(6)).size()).isEqualTo(2);
  }

  @TestTemplate
  public void storeNonCanonicalBlobsTest_multiple(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), true);
    final BlockOptions randomBlobsOptions =
        BlockOptions.create()
            .setGenerateRandomBlobs(true)
            .setGenerateRandomBlobsCount(Optional.of(2));
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3, randomBlobsOptions);
    final ChainBuilder forkChain = primaryChain.fork();
    // Primary chain's next block is at 5
    primaryChain.generateBlockAtSlot(5, randomBlobsOptions);
    final ChainBuilder secondFork = primaryChain.fork();

    final BlobSidecar blobSidecarPrimary2Slot2Index0 =
        dataStructureUtil.randomBlobSidecarForBlock(primaryChain.getBlockAtSlot(2), 0);
    database.storeBlobSidecar(blobSidecarPrimary2Slot2Index0);
    final BlobSidecar blobSidecarPrimary2Slot2Index1 =
        dataStructureUtil.randomBlobSidecarForBlock(primaryChain.getBlockAtSlot(2), 1);
    database.storeBlobSidecar(blobSidecarPrimary2Slot2Index1);

    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock =
        primaryChain.generateBlockAtSlot(7, randomBlobsOptions);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(firstHotBlockSlot, randomBlobsOptions);
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6, randomBlobsOptions);
    forkChain.generateBlockAtSlot(firstHotBlockSlot, randomBlobsOptions);
    secondFork.generateBlockAtSlot(6, randomBlobsOptions);
    secondFork.generateBlockAtSlot(firstHotBlockSlot, randomBlobsOptions);

    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(
                primaryChain.streamBlocksAndStates(),
                forkChain.streamBlocksAndStates(),
                secondFork.streamBlocksAndStates())
            .collect(Collectors.toSet());

    final List<BlobSidecar> allBlocksSidecars =
        Streams.concat(
                primaryChain.streamBlobSidecars(),
                forkChain.streamBlobSidecars(),
                secondFork.streamBlobSidecars())
            .collect(Collectors.toList());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates, allBlocksSidecars);

    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    final List<SignedBeaconBlock> nonCanonicalBlocksAt6 =
        database.getNonCanonicalBlocksAtSlot(UInt64.valueOf(6));
    final List<SignedBeaconBlock> nonCanonicalBlocksAt9 =
        database.getNonCanonicalBlocksAtSlot(UInt64.valueOf(9));

    // check that streamNonCanonicalBlobSidecarKeys for slot 6 and 9 returns 4 blobs (2 * 2) each
    assertThat(nonCanonicalBlocksAt6.size()).isEqualTo(2);
    nonCanonicalBlocksAt6.forEach(
        block -> {
          try (Stream<SlotAndBlockRootAndBlobIndex> slotAndBlockRootAndBlobIndexStream =
              database.streamNonCanonicalBlobSidecarKeys(UInt64.valueOf(6), UInt64.valueOf(6))) {
            assertThat(slotAndBlockRootAndBlobIndexStream.count()).isEqualTo(2 * 2);
          }
        });
    assertThat(nonCanonicalBlocksAt9.size()).isEqualTo(2);
    nonCanonicalBlocksAt9.forEach(
        block -> {
          try (Stream<SlotAndBlockRootAndBlobIndex> slotAndBlockRootAndBlobIndexStream =
              database.streamNonCanonicalBlobSidecarKeys(UInt64.valueOf(9), UInt64.valueOf(9))) {
            assertThat(slotAndBlockRootAndBlobIndexStream.count()).isEqualTo(2 * 2);
          }
        });

    // check that all blobs for slot 6 and 9 are stored as non canonical
    Streams.concat(nonCanonicalBlocksAt6.stream(), nonCanonicalBlocksAt9.stream())
        .flatMap(
            block ->
                Stream.of(
                    new SlotAndBlockRootAndBlobIndex(block.getSlot(), block.getRoot(), ZERO),
                    new SlotAndBlockRootAndBlobIndex(block.getSlot(), block.getRoot(), ONE)))
        .forEach(
            key -> {
              // retrieve expected blobSidecar from allBlocksSidecars
              final BlobSidecar blobSidecar =
                  allBlocksSidecars.stream()
                      .filter(sidecar -> blobSidecarToKey(sidecar).equals(key))
                      .findFirst()
                      .orElseThrow();

              assertThat(database.getNonCanonicalBlobSidecar(key)).contains(blobSidecar);
            });

    SlotAndBlockRootAndBlobIndex key =
        new SlotAndBlockRootAndBlobIndex(
            UInt64.valueOf(2), primaryChain.getBlockAtSlot(2).getRoot(), ZERO);
    assertThat(database.getNonCanonicalBlobSidecar(key)).isEmpty();

    key =
        new SlotAndBlockRootAndBlobIndex(
            UInt64.valueOf(2), primaryChain.getBlockAtSlot(2).getRoot(), ONE);
    assertThat(database.getNonCanonicalBlobSidecar(key)).isEmpty();
  }

  @TestTemplate
  public void orphanedBlockStorageTest_noCanonicalBlocks(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);
    final CreateForkChainResult forkChainResult = createForkChain(false);
    assertBlocksUnavailable(
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, forkChainResult.getFirstHotBlockSlot().longValue())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList()));
  }

  @TestTemplate
  public void shouldRecreateGenesisStateOnRestart_archiveMode(final DatabaseContext context)
      throws IOException {
    initialize(context, StateStorageMode.ARCHIVE);
    recreateGenesisStateOnRestart();
  }

  @TestTemplate
  public void shouldRecreateGenesisStateOnRestart_pruneMode(final DatabaseContext context)
      throws IOException {
    initialize(context, StateStorageMode.PRUNE);
    recreateGenesisStateOnRestart();
  }

  private void recreateGenesisStateOnRestart() {
    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
    assertThat(database.getEarliestAvailableBlockSlot()).contains(genesisBlockAndState.getSlot());
  }

  @TestTemplate
  public void shouldRecreateStoreOnRestart_withOffEpochBoundaryFinalizedBlock_archiveMode(
      final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.ARCHIVE);
    testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock();
  }

  @TestTemplate
  public void shouldRecreateStoreOnRestart_withOffEpochBoundaryFinalizedBlock_pruneMode(
      final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.PRUNE);
    testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock();
  }

  private void testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock() {

    // Create finalized block at slot prior to epoch boundary
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch).minus(UInt64.ONE);
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Add some more blocks
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    chainBuilder.generateBlockAtSlot(firstHotBlockSlot);
    chainBuilder.generateBlocksUpToSlot(firstHotBlockSlot.plus(10));

    // Save new blocks and finalized checkpoint
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    chainBuilder.streamBlocksAndStates(1).forEach(b -> add(tx, List.of(b), List.of()));
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, tx);
    tx.commit().join();

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
  }

  @TestTemplate
  public void shouldPersistOnDisk_pruneMode(final DatabaseContext context) throws Exception {
    testShouldPersistOnDisk(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void shouldPersistOnDisk_archiveMode(final DatabaseContext context) throws Exception {
    testShouldPersistOnDisk(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void shouldRecreateAnchorStoreOnRestart(final DatabaseContext context) throws Exception {
    createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec, new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()), anchorBlockAndState);
    initFromAnchor(anchor);

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
    assertThat(memoryStore.getInitialCheckpoint()).contains(anchor.getCheckpoint());
    assertThat(database.getEarliestAvailableBlockSlot()).contains(anchorBlockAndState.getSlot());
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsModified_setGenesis(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.close();
    assertThatThrownBy(() -> database.storeInitialAnchor(genesisAnchor))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsModified_update(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    final SignedBlockAndState newValue = chainBuilder.generateBlockAtSlot(1);
    // Sanity check
    assertThatSafeFuture(store.retrieveBlockState(newValue.getRoot()))
        .isCompletedWithEmptyOptional();

    assertThatThrownBy(() -> database.storeInitialAnchor(genesisAnchor))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void createMemoryStore_priorToGenesisTime(final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    final Optional<OnDiskStoreData> maybeData =
        ((KvStoreDatabase) database).createMemoryStore(() -> 0L);
    assertThat(maybeData).isNotEmpty();

    final OnDiskStoreData data = maybeData.get();
    final UpdatableStore store =
        StoreBuilder.create()
            .metricsSystem(new NoOpMetricsSystem())
            .specProvider(spec)
            .time(data.time())
            .anchor(data.anchor())
            .genesisTime(data.genesisTime())
            .latestFinalized(data.latestFinalized())
            .finalizedOptimisticTransitionPayload(data.finalizedOptimisticTransitionPayload())
            .justifiedCheckpoint(data.justifiedCheckpoint())
            .bestJustifiedCheckpoint(data.bestJustifiedCheckpoint())
            .blockInformation(data.blockInformation())
            .votes(data.votes())
            .asyncRunner(mock(AsyncRunner.class))
            .blockProvider(mock(BlockProvider.class))
            .stateProvider(mock(StateAndBlockSummaryProvider.class))
            .latestCanonicalBlockRoot(Optional.empty())
            .build();

    assertThat(store.getTimeSeconds()).isEqualTo(genesisTime);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_createMemoryStore(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(database::createMemoryStore).isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_getSlotForFinalizedBlockRoot(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.getSlotForFinalizedBlockRoot(Bytes32.ZERO))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_getSignedBlock(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.getSignedBlock(genesisCheckpoint.getRoot()))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_streamFinalizedBlocks(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.streamFinalizedBlocks(UInt64.ZERO, UInt64.ONE))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_streamFinalizedBlocksShuttingDown(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(UInt64.ZERO, UInt64.valueOf(1000L))) {
      database.close();
      assertThatThrownBy(stream::findAny).isInstanceOf(ShuttingDownException.class);
    }
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateHotDao(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    try (final HotUpdater updater = hotUpdater()) {
      database.close();
      assertThatThrownBy(() -> updater.setGenesisTime(UInt64.ONE))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @MustBeClosed
  private HotUpdater hotUpdater() {
    return ((KvStoreDatabase) database).hotUpdater();
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateFinalizedDao(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    try (final FinalizedUpdater updater = finalizedUpdater()) {
      SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
      database.close();
      assertThatThrownBy(() -> updater.addFinalizedState(newBlock.getRoot(), newBlock.getState()))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @MustBeClosed
  private FinalizedUpdater finalizedUpdater() {
    return ((KvStoreDatabase) database).finalizedUpdater();
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateEth1Dao(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    try (final HotUpdater updater = hotUpdater()) {
      final MinGenesisTimeBlockEvent genesisTimeBlockEvent =
          dataStructureUtil.randomMinGenesisTimeBlockEvent(1);
      database.close();
      assertThatThrownBy(() -> updater.addMinGenesisTimeBlock(genesisTimeBlockEvent))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_getHistoricalState(final DatabaseContext context)
      throws Exception {
    initialize(context);
    // Store genesis
    database.storeInitialAnchor(genesisAnchor);
    // Add a new finalized block to supersede genesis
    final SignedBlockAndState newBlock = chainBuilder.generateBlockAtSlot(1);
    final Checkpoint newCheckpoint = getCheckpointForBlock(newBlock.getBlock());
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock, spec.calculateBlockCheckpoints(newBlock.getState()));
    transaction.setFinalizedCheckpoint(newCheckpoint, false);
    transaction.commit().finishError(LOG);
    // Close db
    database.close();

    assertThatThrownBy(
            () ->
                database.getLatestAvailableFinalizedState(
                    genesisCheckpoint.getEpochStartSlot(spec)))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosedFromAnotherThread(
      final DatabaseContext context) throws Exception {

    for (int i = 0; i < 20; i++) {
      createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
      database.storeInitialAnchor(genesisAnchor);

      try (final HotUpdater updater = hotUpdater()) {
        final Thread dbCloserThread =
            new Thread(
                () -> {
                  try {
                    database.close();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });

        dbCloserThread.start();
        try {
          updater.setGenesisTime(UInt64.ONE);
        } catch (ShuttingDownException ignored) {
          // For this test to fail, we'd see exceptions other than ShuttingDownException.
          // Because it's a probabilistic test, it's possible that either no exception occurs, or
          // a ShuttingDownException, and both these outcomes are ok, but other exceptions are not.
        }

        dbCloserThread.join(500);
      }
    }
  }

  @TestTemplate
  public void shouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart__archive(
      final DatabaseContext context) throws Exception {
    testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void shouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart__prune(
      final DatabaseContext context) throws Exception {
    testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void shouldPersistHotStates_everyEpoch(final DatabaseContext context) throws Exception {
    final int storageFrequency = 1;
    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());

    // We should only be able to pull states at epoch boundaries
    final Set<UInt64> epochBoundarySlots = getEpochBoundarySlots(3);
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());

      if (epochBoundarySlots.contains(UInt64.valueOf(i))) {
        assertThat(actual).contains(blockAndState.getState());
      } else {
        assertThat(actual).isEmpty();
      }
    }
  }

  @TestTemplate
  public void shouldPersistHotStates_never(final DatabaseContext context) throws Exception {
    final int storageFrequency = 0;
    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());

    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());
      assertThat(actual).isEmpty();
    }
  }

  private void addBlocksWithHotStatePersistence(
      final DatabaseContext context, final int statePersistenceInEpochs, final int targetSlot)
      throws IOException {
    final StoreConfig storeConfig =
        StoreConfig.builder()
            .hotStatePersistenceFrequencyInEpochs(statePersistenceInEpochs)
            .build();
    createStorageSystem(context, StateStorageMode.ARCHIVE, storeConfig, false);
    initGenesis();
    chainBuilder.generateBlocksUpToSlot(targetSlot);

    // Add blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));
  }

  @TestTemplate
  public void shouldPersistHotStates_everyThirdEpoch(final DatabaseContext context)
      throws IOException {
    final int storageFrequency = 3;
    final UInt64 latestEpoch = UInt64.valueOf(3 * storageFrequency);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());

    // We should only be able to pull states at epoch boundaries
    final Set<UInt64> epochBoundarySlots = getEpochBoundarySlots(latestEpoch.intValue());
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());

      final UInt64 currentSlot = UInt64.valueOf(i);
      final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
      final boolean shouldPersistThisEpoch = currentEpoch.mod(storageFrequency).equals(UInt64.ZERO);
      if (epochBoundarySlots.contains(currentSlot) && shouldPersistThisEpoch) {
        assertThat(actual).contains(blockAndState.getState());
      } else {
        assertThat(actual).isEmpty();
      }
    }
  }

  @TestTemplate
  public void shouldClearStaleHotStates(final DatabaseContext context) throws IOException {
    final int storageFrequency = 1;
    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());
    justifyAndFinalizeEpoch(latestEpoch, chainBuilder.getLatestBlockAndState());

    // Hot states should be cleared out
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());
      assertThat(actual).isEmpty();
    }
  }

  @TestTemplate
  public void shouldHandleRestartWithUnrecoverableForkBlocks_archive(final DatabaseContext context)
      throws IOException {
    testShouldHandleRestartWithUnrecoverableForkBlocks(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void shouldHandleRestartWithUnrecoverableForkBlocks_prune(final DatabaseContext context)
      throws IOException {
    testShouldHandleRestartWithUnrecoverableForkBlocks(context, StateStorageMode.PRUNE);
  }

  private void testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    final long finalizedSlot = 7;
    final int hotBlockCount = 3;
    // Setup chains
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final long firstHotBlockSlot =
        finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE).longValue();
    for (int i = 0; i < hotBlockCount; i++) {
      chainBuilder.generateBlockAtSlot(firstHotBlockSlot + i);
    }
    final long lastSlot = chainBuilder.getLatestSlot().longValue();

    final StoreConfig config = StoreConfig.builder().stateCacheSize(16).build();
    // Setup database
    createStorageSystem(context, storageMode, config, false);
    initGenesis();

    add(chainBuilder.streamBlocksAndStates().collect(Collectors.toSet()));

    // Close database and rebuild from disk
    restartStorage();

    // Ensure all states are actually regenerated in memory since we expect every state to be stored
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState -> recentChainData.retrieveBlockState(blockAndState.getRoot()).join());

    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    // We should be able to access hot blocks and state
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        chainBuilder.streamBlocksAndStates(finalizedSlot, lastSlot).collect(toList());
    assertHotBlocksAndStatesInclude(expectedHotBlocksAndStates);

    final Map<Bytes32, BeaconState> historicalStates =
        chainBuilder
            .streamBlocksAndStates(0, 6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    if (storageMode.storesFinalizedStates()) {
      assertFinalizedStatesAvailable(historicalStates);
    } else {
      assertStatesUnavailable(
          historicalStates.values().stream().map(BeaconState::getSlot).collect(toList()));
    }
  }

  private void testShouldHandleRestartWithUnrecoverableForkBlocks(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);
    final CreateForkChainResult forkChainResult = createForkChain(true);

    // Fork states should be unavailable
    final UInt64 firstHotBlockSlot = forkChainResult.getFirstHotBlockSlot();
    final List<Bytes32> unavailableBlockRoots =
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, firstHotBlockSlot.longValue())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    final List<UInt64> unavailableBlockSlots =
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, firstHotBlockSlot.longValue())
            .map(SignedBlockAndState::getSlot)
            .collect(Collectors.toList());
    assertStatesUnavailable(unavailableBlockSlots);
    assertBlocksUnavailable(unavailableBlockRoots);
  }

  private Set<UInt64> getEpochBoundarySlots(final int toEpoch) {
    final Set<UInt64> epochBoundarySlots = new HashSet<>();
    for (int i = 1; i <= toEpoch; i++) {
      final UInt64 epochSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(i));
      epochBoundarySlots.add(epochSlot);
    }
    return epochBoundarySlots;
  }

  private void testShouldPersistOnDisk(
      final DatabaseContext context, final StateStorageMode storageMode) throws Exception {
    testShouldRecordFinalizedBlocksAndStates(context, storageMode, false);
  }

  private CreateForkChainResult createForkChain(final boolean restartStorage) {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(firstHotBlockSlot);
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlockAtSlot(firstHotBlockSlot);

    // Setup database

    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates);
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    if (restartStorage) {
      // Close database and rebuild from disk
      restartStorage();
    }
    return new CreateForkChainResult(forkChain, firstHotBlockSlot);
  }

  public void testStartupFromNonGenesisState(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);

    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec,
            new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()),
            anchorBlockAndState.getState(),
            Optional.empty());
    initFromAnchor(anchor);

    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());

    // Restart and check data is what we expect
    final UpdatableStore originalStore = recentChainData.getStore();
    restartStorage();

    StoreAssertions.assertStoresMatch(recentChainData.getStore(), originalStore);
    assertThat(recentChainData.getFinalizedCheckpoint()).contains(anchor.getCheckpoint());
  }

  @TestTemplate
  public void startupFromNonGenesisStateAndFinalizeNewCheckpoint_prune(
      final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisStateAndFinalizeNewCheckpoint(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void startupFromNonGenesisStateAndFinalizeNewCheckpoint_archive(
      final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisStateAndFinalizeNewCheckpoint(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void startupFromNonGenesisAndFinalizeShouldStoreFinalizedBlocksCorrectly(
      final DatabaseContext context) throws IOException {
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(6);
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final List<SignedBlockAndState> hotData = primaryChain.generateBlocksUpToSlot(21);
    // hot data includes the finalized block also
    hotData.add(finalizedBlock);

    // Setup database
    initialize(context, StateStorageMode.PRUNE);
    final Set<SignedBlockAndState> allBlocksAndStates =
        primaryChain.streamBlocksAndStates().collect(Collectors.toSet());
    addBlocks(new ArrayList<>(allBlocksAndStates));
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);
    final Set<Bytes32> blocksToPrune =
        primaryChain
            .streamBlocksAndStates(0, finalizedBlock.getSlot().longValue() - 1)
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    assertRecentDataWasPruned(store, blocksToPrune);
    restartStorage();

    // After restarting storage, we finalize a new block. This results in a number of finalized
    // blocks being pushed in the update that aren't currently hot blocks,
    // and they should end up in finalized storage within the same transaction
    final SignedBlockAndState finalizedBlock2 = hotData.get(5);
    final Checkpoint finalizedCheckpoint2 = getCheckpointForBlock(finalizedBlock2.getBlock());
    justifyAndFinalizeEpoch(finalizedCheckpoint2.getEpoch(), finalizedBlock2);

    // Check finalized data
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStates(0, 12)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
  }

  @TestTemplate
  void shouldStoreAndRetrieveVotes(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
    assertThat(database.getVotes()).isEmpty();

    final Map<UInt64, VoteTracker> voteBatch1 =
        Map.of(
            UInt64.valueOf(10), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(11), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(12), dataStructureUtil.randomVoteTracker());
    database.storeVotes(voteBatch1);

    assertThat(database.getVotes()).isEqualTo(voteBatch1);

    final Map<UInt64, VoteTracker> voteBatch2 =
        Map.of(
            UInt64.valueOf(10), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(13), dataStructureUtil.randomVoteTracker());
    database.storeVotes(voteBatch2);

    final Map<UInt64, VoteTracker> expected = new HashMap<>(voteBatch1);
    expected.putAll(voteBatch2);
    assertThat(database.getVotes()).isEqualTo(expected);
  }

  @TestTemplate
  public void shouldStoreReconstructedFinalizedState(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);
    assertThat(database.getLatestAvailableFinalizedState(ONE)).isEmpty();

    final BeaconState beaconState = dataStructureUtil.randomBeaconState(ONE);
    database.storeReconstructedFinalizedState(beaconState, dataStructureUtil.randomBytes32());

    assertThat(database.getLatestAvailableFinalizedState(ONE)).contains(beaconState);
  }

  @TestTemplate
  public void shouldStoreReconstructedFinalizedStateAndRoots(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);

    final BeaconState state1 = dataStructureUtil.randomBeaconState(ONE);
    final BeaconState state2 = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();

    assertThat(database.getLatestAvailableFinalizedState(UInt64.valueOf(2))).isEmpty();
    database.storeReconstructedFinalizedState(state1, blockRoot1);
    database.storeReconstructedFinalizedState(state2, blockRoot2);

    assertThat(database.getLatestAvailableFinalizedState(ONE)).contains(state1);
    assertThat(database.getLatestAvailableFinalizedState(UInt64.valueOf(2))).contains(state2);
    assertThat(getFinalizedStateRootsList())
        .containsExactlyInAnyOrder(
            Map.entry(state1.hashTreeRoot(), ONE),
            Map.entry(state2.hashTreeRoot(), UInt64.valueOf(2)));
  }

  @TestTemplate
  public void updateDepositSnapshot_setValue(final DatabaseContext context) throws IOException {
    initialize(context);
    final DepositTreeSnapshot depositTreeSnapshot = dataStructureUtil.randomDepositTreeSnapshot();
    assertThat(database.getFinalizedDepositSnapshot().isEmpty()).isTrue();

    database.setFinalizedDepositSnapshot(depositTreeSnapshot);
    assertThat(database.getFinalizedDepositSnapshot()).contains(depositTreeSnapshot);
  }

  @TestTemplate
  public void pruneFinalizedBlocks_shouldRemoveFinalizedBlocks(final DatabaseContext context)
      throws Exception {
    initialize(context, StateStorageMode.ARCHIVE);
    final List<SignedBlockAndState> blockAndStates = chainBuilder.generateBlocksUpToSlot(6);
    addBlocks(blockAndStates);
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(7);
    addBlocks(finalizedBlock);
    justifyAndFinalizeEpoch(
        spec.computeEpochAtSlot(finalizedBlock.getSlot()).plus(1), finalizedBlock);
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(6))).isPresent();

    final UInt64 lastPrunedSlot1 =
        database.pruneFinalizedBlocks(UInt64.valueOf(3), 100, UInt64.valueOf(10));
    assertThat(lastPrunedSlot1).isEqualTo(UInt64.valueOf(3));
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(0))).isEmpty();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(1))).isEmpty();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(2))).isEmpty();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(3))).isEmpty();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(4))).isPresent();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(5))).isPresent();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(6))).isPresent();

    final UInt64 lastPrunedSlot2 =
        database.pruneFinalizedBlocks(UInt64.valueOf(5), 1, UInt64.valueOf(10));
    assertThat(lastPrunedSlot2).isEqualTo(UInt64.valueOf(4));
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(4))).isEmpty();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(5))).isPresent();
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(6))).isPresent();

    final UInt64 lastPrunedSlot3 =
        database.pruneFinalizedBlocks(UInt64.valueOf(4), 1, UInt64.valueOf(10));
    assertThat(lastPrunedSlot3).isEqualTo(UInt64.valueOf(4));
  }

  @TestTemplate
  public void pruneFinalizedBlocks_UpdatesEarliestAvailableBlockSlot(final DatabaseContext context)
      throws Exception {
    initialize(context, StateStorageMode.ARCHIVE);
    final List<SignedBlockAndState> blockAndStates = chainBuilder.generateBlocksUpToSlot(5);
    addBlocks(blockAndStates);
    // Block 7 skipped simulating it was an empty block
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(7);
    addBlocks(finalizedBlock);
    justifyAndFinalizeEpoch(
        spec.computeEpochAtSlot(finalizedBlock.getSlot()).plus(1), finalizedBlock);
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(6))).isEmpty();

    final UInt64 lastPrunedSlot1 =
        database.pruneFinalizedBlocks(UInt64.valueOf(3), 100, UInt64.valueOf(10));
    assertThat(lastPrunedSlot1).isEqualTo(UInt64.valueOf(3));
    assertThat(database.getEarliestAvailableBlockSlot()).isEqualTo(Optional.of(UInt64.valueOf(4)));

    final UInt64 lastPrunedSlot2 =
        database.pruneFinalizedBlocks(UInt64.valueOf(5), 10, UInt64.valueOf(10));
    assertThat(lastPrunedSlot2).isEqualTo(UInt64.valueOf(5));
    // there's no slot 6 because that was purposely skipped so we expect the earliest available
    // block slot to be 7
    assertThat(database.getEarliestAvailableBlockSlot()).isEqualTo(Optional.of(UInt64.valueOf(7)));
  }

  @TestTemplate
  public void pruneFinalizedBlocks_UpdatesEarliestAvailableBlockSlotWhenLimited(
      final DatabaseContext context) throws Exception {
    initialize(context, StateStorageMode.ARCHIVE);
    final List<SignedBlockAndState> blockAndStates = chainBuilder.generateBlocksUpToSlot(5);
    addBlocks(blockAndStates);
    // Block 7 skipped simulating it was an empty block
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(7);
    addBlocks(finalizedBlock);
    justifyAndFinalizeEpoch(
        spec.computeEpochAtSlot(finalizedBlock.getSlot()).plus(1), finalizedBlock);
    assertThat(database.getFinalizedBlockAtSlot(UInt64.valueOf(6))).isEmpty();

    final UInt64 lastPrunedSlot1 =
        database.pruneFinalizedBlocks(UInt64.valueOf(3), 2, UInt64.valueOf(10));
    assertThat(lastPrunedSlot1).isEqualTo(UInt64.valueOf(1));
    assertThat(database.getEarliestAvailableBlockSlot()).isEqualTo(Optional.of(UInt64.valueOf(2)));

    final UInt64 lastPrunedSlot2 =
        database.pruneFinalizedBlocks(UInt64.valueOf(5), 10, UInt64.valueOf(10));
    assertThat(lastPrunedSlot2).isEqualTo(UInt64.valueOf(5));
    // there's no slot 6 because that was purposely skipped so we expect the earliest available
    // block slot to be 7
    assertThat(database.getEarliestAvailableBlockSlot()).isEqualTo(Optional.of(UInt64.valueOf(7)));
  }

  @TestTemplate
  public void
      pruneFinalizedBlocks_ClearEarliestAvailableBlockSlotVariableWhenNoBlocksLeftAfterPrune(
          final DatabaseContext context) throws Exception {
    initialize(context, StateStorageMode.ARCHIVE);
    final List<SignedBlockAndState> blockAndStates = chainBuilder.generateBlocksUpToSlot(5);
    addBlocks(blockAndStates);
    // Block 7 skipped simulating it was an empty block
    final SignedBlockAndState finalizedBlock = chainBuilder.generateBlockAtSlot(7);
    addBlocks(finalizedBlock);
    justifyAndFinalizeEpoch(
        spec.computeEpochAtSlot(finalizedBlock.getSlot()).plus(1), finalizedBlock);

    final UInt64 lastPrunedSlot1 =
        database.pruneFinalizedBlocks(UInt64.valueOf(7), 10, UInt64.valueOf(10));
    assertThat(lastPrunedSlot1).isEqualTo(UInt64.valueOf(7));
    assertThat(database.getEarliestAvailableBlockSlot()).isEqualTo(Optional.empty());
  }

  @TestTemplate
  public void shouldPersistLatestCanonicalBlockRoot(final DatabaseContext context)
      throws Exception {
    initialize(context);

    // it is initially empty
    assertThat(database.getLatestCanonicalBlockRoot()).isEmpty();

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    final Bytes32 randomBlockRoot = dataStructureUtil.randomBytes32();
    transaction.setLatestCanonicalBlockRoot(randomBlockRoot);

    commit(transaction);

    assertThat(database.getLatestCanonicalBlockRoot()).contains(randomBlockRoot);
  }

  @TestTemplate
  public void addSidecar_isOperative(final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSlotAndIdentifier columnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
    assertThat(database.getSidecar(columnSlotAndIdentifier).isEmpty()).isTrue();

    database.addSidecar(dataColumnSidecar);
    assertThat(database.getSidecar(columnSlotAndIdentifier)).contains(dataColumnSidecar);
  }

  @TestTemplate
  public void setFirstCustodyIncompleteSlot_isOperative(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);
    assertThat(database.getFirstCustodyIncompleteSlot().isEmpty()).isTrue();

    final UInt64 incompleteSlot = UInt64.valueOf(123);
    database.setFirstCustodyIncompleteSlot(UInt64.valueOf(123));
    assertThat(database.getFirstCustodyIncompleteSlot()).contains(incompleteSlot);
  }

  @TestTemplate
  public void setEarliestAvailableDataColumnSlot_isOperative(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);
    assertThat(database.getEarliestAvailableDataColumnSlot().isEmpty()).isTrue();

    final UInt64 earliestSlot = UInt64.valueOf(123);
    database.setEarliestAvailableDataColumnSlot(UInt64.valueOf(123));
    assertThat(database.getEarliestAvailableDataColumnSlot()).contains(earliestSlot);
  }

  @TestTemplate
  public void streamDataColumnIdentifiers_isOperative(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader blockHeader1 = dataStructureUtil.randomSignedBeaconBlockHeader();
    final DataColumnSidecar block1Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSlotAndIdentifier block1Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar0);
    final DataColumnSidecar block1Sidecar1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ONE);
    final DataColumnSlotAndIdentifier block1Column1 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar1);

    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(100));
    final DataColumnSidecar block2Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    final DataColumnSlotAndIdentifier block2Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block2Sidecar0);

    database.addSidecar(block1Sidecar0);
    database.addSidecar(block1Sidecar1);
    database.addSidecar(block2Sidecar0);

    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(block1Sidecar0.getSlot().plus(1))) {
      assertThat(dataColumnIdentifiersStream.toList()).isEmpty();
    }

    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(block1Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList())
          .containsExactly(block1Column0, block1Column1);
    }

    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(
            block1Sidecar0.getSlot().plus(1), block2Sidecar0.getSlot().plus(1))) {
      assertThat(dataColumnIdentifiersStream.toList()).containsExactly(block2Column0);
    }
  }

  @TestTemplate
  public void pruneAllSidecars_isOperative(final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader blockHeader1 =
        dataStructureUtil.randomSignedBeaconBlockHeader(ONE);
    final DataColumnSidecar block1Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSlotAndIdentifier block1Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar0);
    final DataColumnSidecar block1Sidecar1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ONE);
    final DataColumnSlotAndIdentifier block1Column1 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar1);

    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(100));
    final DataColumnSidecar block2Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    final DataColumnSlotAndIdentifier block2Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block2Sidecar0);

    database.addSidecar(block1Sidecar0);
    database.addSidecar(block1Sidecar1);
    database.addSidecar(block2Sidecar0);

    database.pruneAllSidecars(ZERO, 10);
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block2Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList())
          .containsExactly(block1Column0, block1Column1, block2Column0);
    }

    database.pruneAllSidecars(ONE, 10);
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block2Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).containsExactly(block2Column0);
    }

    database.pruneAllSidecars(block2Sidecar0.getSlot(), 10);
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block2Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).isEmpty();
    }
  }

  @TestTemplate
  public void getEarliestDataColumnSidecarSlot_tracksPruneWatermark(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader blockHeader1 =
        dataStructureUtil.randomSignedBeaconBlockHeader(ONE);
    final DataColumnSidecar block1Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSidecar block1Sidecar1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ONE);

    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(100));
    final DataColumnSidecar block2Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);

    // no data column sidecars have been stored yet
    assertThat(database.getEarliestDataColumnSidecarSlot()).isEmpty();

    database.addSidecar(block1Sidecar0);
    database.addSidecar(block1Sidecar1);
    database.addSidecar(block2Sidecar0);

    // before any pruning the earliest slot is the first stored slot
    assertThat(database.getEarliestDataColumnSidecarSlot()).contains(block1Sidecar0.getSlot());

    // pruning the oldest slot advances the watermark; the earliest slot follows it
    database.pruneAllSidecars(block1Sidecar0.getSlot(), 10);
    assertThat(database.getEarliestDataColumnSidecarSlot()).contains(block2Sidecar0.getSlot());

    // the watermark is persisted, so the next run resumes above the pruned slots rather than
    // rescanning from the first supported slot
    database.pruneAllSidecars(block2Sidecar0.getSlot(), 10);
    assertThat(database.getEarliestDataColumnSidecarSlot()).isEmpty();
  }

  @TestTemplate
  public void compactStorage_isOperativeAndPreservesData(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader blockHeader1 =
        dataStructureUtil.randomSignedBeaconBlockHeader(ONE);
    final DataColumnSidecar block1Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSlotAndIdentifier block1Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar0);

    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(100));
    final DataColumnSidecar block2Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    final DataColumnSlotAndIdentifier block2Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block2Sidecar0);

    database.addSidecar(block1Sidecar0);
    database.addSidecar(block2Sidecar0);

    // prune the oldest slot so there are tombstones for compaction to reclaim
    database.pruneAllSidecars(block1Sidecar0.getSlot(), 10);

    // compaction must run without error and leave the surviving data intact
    database.compactStorage();

    assertThat(database.getSidecar(block1Column0)).isEmpty();
    assertThat(database.getSidecar(block2Column0)).contains(block2Sidecar0);
    assertThat(database.getEarliestDataColumnSidecarSlot()).contains(block2Sidecar0.getSlot());
  }

  @TestTemplate
  public void pruneAllSidecars_pruneBasedOnSlots(final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader blockHeader1 =
        dataStructureUtil.randomSignedBeaconBlockHeader(ONE);
    final DataColumnSidecar block1Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSlotAndIdentifier block1Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar0);
    final DataColumnSidecar block1Sidecar1 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ONE);
    final DataColumnSlotAndIdentifier block1Column1 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar1);
    final DataColumnSidecar block1Sidecar2 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, UInt64.valueOf(2));
    final DataColumnSlotAndIdentifier block1Column2 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar2);

    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(100));
    final DataColumnSidecar block2Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    final DataColumnSlotAndIdentifier block2Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block2Sidecar0);
    final SignedBeaconBlockHeader blockHeader3 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(200));
    final DataColumnSidecar block3Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader3, ZERO);
    final DataColumnSlotAndIdentifier block3Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block3Sidecar0);

    database.addSidecar(block1Sidecar0);
    database.addSidecar(block1Sidecar1);
    database.addSidecar(block1Sidecar2);
    database.addSidecar(block2Sidecar0);
    database.addSidecar(block3Sidecar0);

    // not pruned yet should contain all sidecars
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList())
          .containsExactly(
              block1Column0, block1Column1, block1Column2, block2Column0, block3Column0);
    }

    // prune sidecars passing 2 as the limit should prune sidecars from the oldest 2 slots at or
    // before the cutoff,
    // leaving the sidecars from block header 3
    database.pruneAllSidecars(block3Sidecar0.getSlot(), 2);

    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).containsExactly(block3Column0);
    }

    database.pruneAllSidecars(block3Sidecar0.getSlot(), 2);
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).isEmpty();
    }
  }

  @TestTemplate
  public void pruneAllSidecars_pruneNonCanonical(final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader blockHeader1 =
        dataStructureUtil.randomSignedBeaconBlockHeader(ONE);
    final DataColumnSidecar block1Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader1, ZERO);
    final DataColumnSlotAndIdentifier block1Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar0);
    final DataColumnSidecar block1Sidecar1 =
        dataStructureUtil.randomDataColumnSidecar(
            blockHeader1, DataColumnSidecarFulu.required(block1Sidecar0).getKzgCommitments(), ONE);
    final DataColumnSlotAndIdentifier block1Column1 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar1);
    final DataColumnSidecar block1Sidecar2 =
        dataStructureUtil.randomDataColumnSidecar(
            blockHeader1,
            DataColumnSidecarFulu.required(block1Sidecar0).getKzgCommitments(),
            UInt64.valueOf(2));
    final DataColumnSlotAndIdentifier block1Column2 =
        DataColumnSlotAndIdentifier.fromDataColumn(block1Sidecar2);

    final SignedBeaconBlockHeader blockHeader2 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(100));
    final DataColumnSidecar block2Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader2, ZERO);
    final DataColumnSlotAndIdentifier block2Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block2Sidecar0);
    final SignedBeaconBlockHeader blockHeader3 =
        dataStructureUtil.randomSignedBeaconBlockHeader(
            blockHeader1.getMessage().getSlot().plus(200));
    final DataColumnSidecar block3Sidecar0 =
        dataStructureUtil.randomDataColumnSidecar(blockHeader3, ZERO);
    final DataColumnSlotAndIdentifier block3Column0 =
        DataColumnSlotAndIdentifier.fromDataColumn(block3Sidecar0);

    database.addSidecar(block1Sidecar0);
    database.addSidecar(block1Sidecar1);
    database.addSidecar(block1Sidecar2);
    database.addNonCanonicalSidecar(block2Sidecar0);
    database.addNonCanonicalSidecar(block3Sidecar0);

    // not pruned yet should contain all sidecars
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList())
          .containsExactly(block1Column0, block1Column1, block1Column2);
    }
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamNonCanonicalDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList())
          .containsExactly(block2Column0, block3Column0);
    }

    // prune sidecars passing 1 as the limit should prune the oldest eligible canonical slot and
    // non-canonical slot. The limit is applied separately to canonical and non-canonical sidecars.
    // So leaving only the non-canonical sidecar from block header 3
    database.pruneAllSidecars(block3Sidecar0.getSlot(), 1);

    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).isEmpty();
    }
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamNonCanonicalDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).containsExactly(block3Column0);
    }

    database.pruneAllSidecars(block3Sidecar0.getSlot(), 2);
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).isEmpty();
    }
    try (final Stream<DataColumnSlotAndIdentifier> dataColumnIdentifiersStream =
        database.streamNonCanonicalDataColumnIdentifiers(ZERO, block3Sidecar0.getSlot())) {
      assertThat(dataColumnIdentifiersStream.toList()).isEmpty();
    }
  }

  @TestTemplate
  public void dataColumnSidecarsProofs_lifecycle(final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final SignedBeaconBlockHeader header = dataStructureUtil.randomSignedBeaconBlockHeader();
    // we need to build sidecars with the same number of cells in each
    final List<KZGCommitment> kzgCommitments = dataStructureUtil.randomKZGCommitments(14);
    final SszList<SszKZGCommitment> sszKZGCommitments =
        SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
            .getDataColumnSidecarSchema()
            .getKzgCommitmentsSchema()
            .createFromElements(kzgCommitments.stream().map(SszKZGCommitment::new).toList());
    final List<DataColumnSidecar> dataColumnSidecarsExtension =
        Stream.iterate(UInt64.valueOf(64), UInt64::increment)
            .limit(64)
            .map(
                index ->
                    dataStructureUtil.randomDataColumnSidecar(header, sszKZGCommitments, index))
            .toList();
    final List<List<KZGProof>> expectedProofs =
        dataColumnSidecarsExtension.stream()
            .map(
                dataColumnSidecar ->
                    dataColumnSidecar.getKzgProofs().stream()
                        .map(SszKZGProof::getKZGProof)
                        .toList())
            .toList();

    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addDataColumnSidecarsProofs(
          header.getMessage().getSlot(),
          dataColumnSidecarsExtension.stream()
              .map(
                  sidecar -> sidecar.getKzgProofs().stream().map(SszKZGProof::getKZGProof).toList())
              .toList());
      updater.commit();
    }

    assertThat(database.getLastDataColumnSidecarsProofsSlot())
        .contains(header.getMessage().getSlot());
    assertThat(database.getDataColumnSidecarsProofs(header.getMessage().getSlot()))
        .contains(expectedProofs);

    // remove it
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.removeDataColumnSidecarsProofs(header.getMessage().getSlot());
      updater.commit();
    }

    assertThat(database.getLastDataColumnSidecarsProofsSlot()).isEmpty();
    assertThat(database.getDataColumnSidecarsProofs(header.getMessage().getSlot())).isEmpty();
  }

  @TestTemplate
  public void archiveSidecarsProofs_dropsExtensionColumnsAndRetainsProofs(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final int halfColumns = numberOfColumns / 2;
    final UInt64 slot = UInt64.valueOf(3);

    final List<List<KZGProof>> expectedExtensionProofs = storeFullColumnSet(slot, numberOfColumns);

    database.archiveSidecarsProofs(ZERO, slot);

    // only the reconstructable first half of the columns is retained
    assertThat(getStoredColumnIndices(slot))
        .containsExactlyElementsOf(
            Stream.iterate(ZERO, UInt64::increment).limit(halfColumns).toList());
    // the proofs of the dropped extension columns are archived for reconstruction
    assertThat(database.getDataColumnSidecarsProofs(slot)).contains(expectedExtensionProofs);
    assertThat(database.getLastDataColumnSidecarsProofsSlot()).contains(slot);
  }

  @TestTemplate
  public void archiveSidecarsProofs_skipsSlotWithIncompleteExtensionColumns(
      final DatabaseContext context) throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final UInt64 slot = UInt64.valueOf(3);

    final SignedBeaconBlockHeader header = dataStructureUtil.randomSignedBeaconBlockHeader(slot);
    final SszList<SszKZGCommitment> kzgCommitments = randomFuluKzgCommitments();
    // store every column except the last extension one, so the extension half is incomplete
    Stream.iterate(ZERO, UInt64::increment)
        .limit(numberOfColumns - 1L)
        .map(index -> dataStructureUtil.randomDataColumnSidecar(header, kzgCommitments, index))
        .forEach(database::addSidecar);

    database.archiveSidecarsProofs(ZERO, slot);

    // nothing archived and no column dropped: reconstruction from a partial half is impossible
    assertThat(database.getDataColumnSidecarsProofs(slot)).isEmpty();
    assertThat(database.getLastDataColumnSidecarsProofsSlot()).isEmpty();
    assertThat(getStoredColumnIndices(slot)).hasSize(numberOfColumns - 1);
  }

  @TestTemplate
  public void pruneAllSidecars_alsoRemovesArchivedProofs(final DatabaseContext context)
      throws IOException {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    initialize(context);

    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final UInt64 slot = UInt64.valueOf(3);

    try (final LogCaptor logCaptor = LogCaptor.forClass(KvStoreDatabase.class, Level.DEBUG)) {
      // archive the extension columns down to proofs: slot now holds first-half sidecars + proofs
      storeFullColumnSet(slot, numberOfColumns);
      database.archiveSidecarsProofs(ZERO, slot);
      assertThat(database.getDataColumnSidecarsProofs(slot)).isPresent();
      assertThat(database.getLastDataColumnSidecarsProofsSlot()).contains(slot);

      // pruning the slot must drop the retained proofs alongside the sidecars
      database.pruneAllSidecars(slot, 10);

      assertThat(getStoredColumnIndices(slot)).isEmpty();
      assertThat(database.getDataColumnSidecarsProofs(slot)).isEmpty();
      assertThat(database.getLastDataColumnSidecarsProofsSlot()).isEmpty();

      // both the archiving and the prune-time removal are observable in the logs at debug level
      assertThat(logCaptor.getDebugLogs())
          .anyMatch(log -> log.contains("Archiving data column sidecars to proofs"))
          .anyMatch(log -> log.contains("Removing archived data column sidecar proofs"));
    }
  }

  private List<List<KZGProof>> storeFullColumnSet(final UInt64 slot, final int numberOfColumns) {
    final SignedBeaconBlockHeader header = dataStructureUtil.randomSignedBeaconBlockHeader(slot);
    final SszList<SszKZGCommitment> kzgCommitments = randomFuluKzgCommitments();
    final List<DataColumnSidecar> sidecars =
        Stream.iterate(ZERO, UInt64::increment)
            .limit(numberOfColumns)
            .map(index -> dataStructureUtil.randomDataColumnSidecar(header, kzgCommitments, index))
            .toList();
    sidecars.forEach(database::addSidecar);
    return sidecars.stream()
        .skip(numberOfColumns / 2)
        .map(sidecar -> sidecar.getKzgProofs().stream().map(SszKZGProof::getKZGProof).toList())
        .toList();
  }

  private SszList<SszKZGCommitment> randomFuluKzgCommitments() {
    return SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
        .getDataColumnSidecarSchema()
        .getKzgCommitmentsSchema()
        .createFromElements(
            dataStructureUtil.randomKZGCommitments(14).stream()
                .map(SszKZGCommitment::new)
                .toList());
  }

  private List<UInt64> getStoredColumnIndices(final UInt64 slot) {
    try (final Stream<DataColumnSlotAndIdentifier> identifiers =
        database.streamDataColumnIdentifiers(slot, slot)) {
      return identifiers.map(DataColumnSlotAndIdentifier::columnIndex).sorted().toList();
    }
  }

  private List<Map.Entry<Bytes32, UInt64>> getFinalizedStateRootsList() {
    try (final Stream<Map.Entry<Bytes32, UInt64>> roots = database.getFinalizedStateRoots()) {
      return roots.map(entry -> Map.entry(entry.getKey(), entry.getValue())).collect(toList());
    }
  }

  public void testStartupFromNonGenesisStateAndFinalizeNewCheckpoint(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);

    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec,
            new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()),
            anchorBlockAndState.getState(),
            Optional.empty());
    initFromAnchor(anchor);

    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());
    // And finalize them
    final SignedBlockAndState newFinalizedBlockAndState = chainBuilder.getLatestBlockAndState();
    final UInt64 newFinalizedEpoch = anchorEpoch.plus(1);
    justifyAndFinalizeEpoch(newFinalizedEpoch, newFinalizedBlockAndState);

    // Restart and check data is what we expect
    final UpdatableStore originalStore = recentChainData.getStore();
    restartStorage();

    StoreAssertions.assertStoresMatch(recentChainData.getStore(), originalStore);
    assertThat(recentChainData.getFinalizedCheckpoint())
        .contains(new Checkpoint(newFinalizedEpoch, newFinalizedBlockAndState.getRoot()));
  }

  private Bytes32 insertRandomSlotAndBlock(
      final long slot, final DataStructureUtil dataStructureUtil) {
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(UInt64.valueOf(slot), dataStructureUtil.randomBytes32());
    database.addHotStateRoots(Map.of(stateRoot, slotAndBlockRoot));
    return stateRoot;
  }

  private void testShouldRecordFinalizedBlocksAndStates(
      final DatabaseContext context, final StateStorageMode storageMode, final boolean batchUpdate)
      throws IOException {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(7);
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final UInt64 pruneToSlot = finalizedCheckpoint.getEpochStartSlot(spec);
    // Add some blocks in the next epoch
    final UInt64 hotSlot = pruneToSlot.plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(hotSlot);
    forkChain.generateBlockAtSlot(hotSlot);

    // Setup database
    initialize(context, storageMode);

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    if (batchUpdate) {
      final StoreTransaction transaction = recentChainData.startStoreTransaction();
      add(transaction, allBlocksAndStates, List.of());
      justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, transaction);
      assertThat(transaction.commit()).isCompleted();
    } else {
      add(allBlocksAndStates);
      justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);
    }

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Streams.concat(
                primaryChain.streamBlocksAndStates(0, pruneToSlot.longValue()),
                forkChain.streamBlocksAndStates(0, pruneToSlot.longValue()))
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    blocksToPrune.remove(finalizedBlock.getRoot());

    // Check data was pruned from store
    assertRecentDataWasPruned(store, blocksToPrune);

    restartStorage();

    // Check hot data
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        List.of(finalizedBlock, primaryChain.getBlockAndStateAtSlot(hotSlot));
    assertHotBlocksAndStates(store, expectedHotBlocksAndStates);
    final SignedBlockAndState prunedForkBlock = forkChain.getBlockAndStateAtSlot(hotSlot);
    assertThat(store.containsBlock(prunedForkBlock.getRoot())).isFalse();

    // Check finalized data
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStates(0, 7)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(expectedFinalizedBlocks);
    assertBlocksAvailableByRoot(expectedFinalizedBlocks);
    assertFinalizedBlocksAvailableViaStream(
        1,
        3,
        primaryChain.getBlockAtSlot(1),
        primaryChain.getBlockAtSlot(2),
        primaryChain.getBlockAtSlot(3));

    if (storageMode.storesFinalizedStates()) {
      // Finalized states should be available
      final Map<Bytes32, BeaconState> expectedStates =
          primaryChain
              .streamBlocksAndStates(0, 7)
              .collect(toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
      assertFinalizedStatesAvailable(expectedStates);
    } else {
      // Check pruned states
      final List<UInt64> unavailableSlots =
          allBlocksAndStates.stream().map(SignedBlockAndState::getSlot).collect(toList());
      assertStatesUnavailable(unavailableSlots);
    }
  }

  private void assertFinalizedBlocksAvailableViaStream(
      final int fromSlot, final int toSlot, final SignedBeaconBlock... expectedBlocks) {
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(UInt64.valueOf(fromSlot), UInt64.valueOf(toSlot))) {
      assertThat(stream).containsExactly(expectedBlocks);
    }
  }

  private void assertBlocksFinalized(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedBlockAtSlot(block.getSlot()))
          .describedAs("Block at slot %s", block.getSlot())
          .contains(block);
    }
  }

  private void assertBlocksAvailableByRoot(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getSignedBlock(block.getRoot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block);
    }
  }

  private void assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(
      final List<SignedBeaconBlock> blocks) {
    final SignedBeaconBlock genesisBlock =
        database.getFinalizedBlockAtSlot(GENESIS_SLOT).orElseThrow();

    final List<SignedBeaconBlock> finalizedBlocks = new ArrayList<>();
    finalizedBlocks.add(genesisBlock);
    finalizedBlocks.addAll(blocks);
    for (int i = 1; i < finalizedBlocks.size(); i++) {
      final SignedBeaconBlock currentBlock = finalizedBlocks.get(i - 1);
      final SignedBeaconBlock nextBlock = finalizedBlocks.get(i);
      // All slots from the current block up to and excluding the next block should return the
      // current block
      for (long slot = currentBlock.getSlot().longValue();
          slot < nextBlock.getSlot().longValue();
          slot++) {
        assertThat(database.getLatestFinalizedBlockAtSlot(UInt64.valueOf(slot)))
            .describedAs("Latest finalized block at slot %s", slot)
            .contains(currentBlock);
      }
    }

    // Check that last block
    final SignedBeaconBlock lastFinalizedBlock = finalizedBlocks.getLast();
    for (int i = 0; i < 10; i++) {
      final UInt64 slot = lastFinalizedBlock.getSlot().plus(i);
      assertThat(database.getLatestFinalizedBlockAtSlot(slot))
          .describedAs("Latest finalized block at slot %s", slot)
          .contains(lastFinalizedBlock);
    }
  }

  private void assertHotBlocksAndStates(
      final UpdatableStore store, final Collection<SignedBlockAndState> blocksAndStates) {
    final List<UpdatableStore> storesToCheck = List.of(store, recreateStore());
    for (UpdatableStore currentStore : storesToCheck) {
      assertThat(currentStore.getOrderedBlockRoots())
          .hasSameElementsAs(
              blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

      final List<BeaconState> hotStates =
          currentStore.getOrderedBlockRoots().stream()
              .map(currentStore::retrieveBlockState)
              .map(
                  f -> {
                    assertThat(f).isCompleted();
                    return safeJoin(f);
                  })
              .flatMap(Optional::stream)
              .collect(toList());

      assertThat(hotStates)
          .hasSameElementsAs(
              blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
    }
  }

  private void assertHotBlocksAndStatesInclude(
      final Collection<SignedBlockAndState> blocksAndStates) {
    final UpdatableStore memoryStore = recreateStore();
    assertThat(memoryStore.getOrderedBlockRoots())
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

    final List<BeaconState> hotStates =
        memoryStore.getOrderedBlockRoots().stream()
            .map(memoryStore::retrieveBlockState)
            .map(
                f -> {
                  assertThat(f).isCompleted();
                  return safeJoin(f);
                })
            .flatMap(Optional::stream)
            .collect(toList());

    assertThat(hotStates)
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
  }

  private void assertFinalizedStatesAvailable(final Map<Bytes32, BeaconState> states) {
    for (BeaconState state : states.values()) {
      assertThat(database.getLatestAvailableFinalizedState(state.getSlot())).contains(state);
    }
  }

  private void assertStatesUnavailable(final Collection<UInt64> slots) {
    for (UInt64 slot : slots) {
      Optional<BeaconState> bs =
          database
              .getLatestAvailableFinalizedState(slot)
              .filter(state -> state.getSlot().equals(slot));
      assertThat(bs).isEmpty();
    }
  }

  private void assertBlocksUnavailable(final Collection<Bytes32> roots) {
    for (Bytes32 root : roots) {
      Optional<SignedBeaconBlock> bb = database.getSignedBlock(root);
      assertThat(bb).isEmpty();
    }
  }

  private void assertBlocksAvailable(final Collection<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock expectedBlock : blocks) {
      Optional<SignedBeaconBlock> actualBlock = database.getSignedBlock(expectedBlock.getRoot());
      assertThat(actualBlock).contains(expectedBlock);
    }
  }

  private void assertRecentDataWasPruned(
      final UpdatableStore store, final Set<Bytes32> prunedBlocks) {
    int index = 0;
    for (Bytes32 prunedBlock : prunedBlocks) {
      // Check pruned data has been removed from store
      assertThat(store.containsBlock(prunedBlock))
          .withFailMessage(
              "Store should not contain pruned block: " + prunedBlock + " (" + index + ")")
          .isFalse();
      assertThatSafeFuture(store.retrieveBlock(prunedBlock))
          .withFailMessage(
              "Store should not return a block from: " + prunedBlock + " (" + index + ")")
          .isCompletedWithEmptyOptional();
      assertThatSafeFuture(store.retrieveBlockState(prunedBlock))
          .withFailMessage(
              "Store should not return a state from: " + prunedBlock + " (" + index + ")")
          .isCompletedWithEmptyOptional();

      // Check hot data was pruned from db
      assertThat(database.getHotBlocks(Set.of(prunedBlock))).isEmpty();
      assertThat(database.getHotState(prunedBlock)).isEmpty();
      index++;
    }
  }

  private void assertBlobSidecarsAvailabilityExceptPruned(
      final Collection<SignedBeaconBlock> availableBlocksSidecars,
      final Collection<SignedBeaconBlock> prunedBlocksSidecars) {
    availableBlocksSidecars.forEach(
        block -> assertThat(database.getBlobSidecarKeys(block.getSlotAndBlockRoot())).isNotEmpty());
    prunedBlocksSidecars.forEach(
        block -> assertThat(database.getBlobSidecarKeys(block.getSlotAndBlockRoot())).isEmpty());
  }

  private void addBlocks(final SignedBlockAndState... blocks) {
    addBlocks(Arrays.asList(blocks));
  }

  private void addBlocks(final List<SignedBlockAndState> blocks) {
    addBlocksAndBlobSidecars(blocks, List.of());
  }

  private void addBlocksAndBlobSidecars(
      final List<SignedBlockAndState> blocks, final List<BlobSidecar> blobSidecars) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    for (SignedBlockAndState block : blocks) {
      transaction.putBlockAndState(
          block,
          blobSidecars.stream()
              .filter(blobSidecar -> blobSidecar.getBlockRoot().equals(block.getRoot()))
              .toList(),
          spec.calculateBlockCheckpoints(block.getState()));
    }
    commit(transaction);
  }

  private void add(final Collection<SignedBlockAndState> blocks) {
    add(blocks, List.of());
  }

  private void add(
      final Collection<SignedBlockAndState> blocks, final List<BlobSidecar> blobSidecars) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    add(transaction, blocks, blobSidecars);
    commit(transaction);
  }

  private void add(
      final StoreTransaction transaction,
      final Collection<SignedBlockAndState> blocksAndStates,
      final List<BlobSidecar> blobSidecars) {
    blocksAndStates.stream()
        .sorted(Comparator.comparing(SignedBlockAndState::getSlot))
        .forEach(
            blockAndState ->
                transaction.putBlockAndState(
                    blockAndState,
                    blobSidecars.stream()
                        .filter(
                            blobSidecar ->
                                blobSidecar.getBlockRoot().equals(blockAndState.getRoot()))
                        .toList(),
                    spec.calculateBlockCheckpoints(blockAndState.getState())));
  }

  private void justifyAndFinalizeEpoch(final UInt64 epoch, final SignedBlockAndState block) {
    StoreTransaction tx = recentChainData.startStoreTransaction();
    justifyAndFinalizeEpoch(epoch, block, tx);
    assertThat(tx.commit()).isCompleted();
  }

  private void justifyAndFinalizeEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction tx) {
    justifyEpoch(epoch, block, tx);
    finalizeEpoch(epoch, block, tx);
  }

  private void finalizeEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setFinalizedCheckpoint(checkpoint, false);
  }

  private void justifyEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setJustifiedCheckpoint(checkpoint);
  }

  private Checkpoint getCheckpointForBlock(final SignedBeaconBlock block) {
    final UInt64 blockEpoch = spec.computeEpochAtSlot(block.getSlot());
    final UInt64 blockEpochBoundary = spec.computeStartSlotAtEpoch(blockEpoch);
    final UInt64 checkpointEpoch =
        equivalentLongs(block.getSlot(), blockEpochBoundary) ? blockEpoch : blockEpoch.plus(ONE);
    return new Checkpoint(checkpointEpoch, block.getMessage().hashTreeRoot());
  }

  private boolean equivalentLongs(final UInt64 valA, final UInt64 valB) {
    return valA.compareTo(valB) == 0;
  }

  private void initGenesis() {
    recentChainData.initializeFromGenesis(genesisBlockAndState.getState(), UInt64.ZERO);
    store = recentChainData.getStore();
  }

  private void initFromAnchor(final AnchorPoint anchor) {
    recentChainData.initializeFromAnchorPoint(anchor, UInt64.ZERO);
    store = recentChainData.getStore();
  }

  private void generateCheckpoints() {
    while (chainBuilder.getLatestEpoch().longValue() < 3) {
      chainBuilder.generateNextBlock();
    }

    checkpoint1BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(1);
    checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(1);
    checkpoint2BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(2);
    checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(2);
    checkpoint3BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(3);
    checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(3);
  }

  private UpdatableStore recreateStore() {
    restartStorage();
    return storageSystem.recentChainData().getStore();
  }

  private void createStorageSystem(
      final DatabaseContext context,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks)
      throws IOException {
    this.storageMode = storageMode;
    if (context.isInMemoryStorage()) {
      setDefaultStorage(
          context.createInMemoryStorage(spec, storageMode, storeConfig, storeNonCanonicalBlocks));
    } else {
      final Path tmpDir = Files.createTempDirectory("storageTest");
      tmpDirectories.add(tmpDir.toFile());
      setDefaultStorage(
          context.createFileBasedStorage(
              spec, tmpDir, storageMode, storeConfig, storeNonCanonicalBlocks));
    }
  }

  private void setDefaultStorage(final StorageSystem storageSystem) {
    this.storageSystem = storageSystem;
    database = storageSystem.database();
    recentChainData = storageSystem.recentChainData();
    storageSystems.add(storageSystem);
  }

  private void assertBlobSidecarKeys(
      final UInt64 from, final UInt64 to, final SlotAndBlockRootAndBlobIndex... keys) {
    try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecarsStream =
        database.streamBlobSidecarKeys(from, to)) {
      final List<SlotAndBlockRootAndBlobIndex> keysFromDb = blobSidecarsStream.collect(toList());
      assertThat(keysFromDb).isEqualTo(Arrays.asList(keys));
    }
  }

  private void assertNonCanonicalBlobSidecarKeys(
      final UInt64 from, final UInt64 to, final SlotAndBlockRootAndBlobIndex... keys) {
    try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecarsStream =
        database.streamNonCanonicalBlobSidecarKeys(from, to)) {
      final List<SlotAndBlockRootAndBlobIndex> keysFromDb = blobSidecarsStream.collect(toList());
      assertThat(keysFromDb).isEqualTo(Arrays.asList(keys));
    }
  }

  private void assertBlobSidecars(final Map<UInt64, List<BlobSidecar>> blobSidecars) {
    final List<UInt64> slots = blobSidecars.keySet().stream().sorted().toList();
    if (slots.isEmpty()) {
      return;
    }

    final Map<UInt64, List<BlobSidecar>> blobSidecarsDb = new HashMap<>();
    try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecarsStream =
        database.streamBlobSidecarKeys(slots.getFirst(), slots.getLast())) {

      for (final Iterator<SlotAndBlockRootAndBlobIndex> iterator = blobSidecarsStream.iterator();
          iterator.hasNext(); ) {
        final SlotAndBlockRootAndBlobIndex key = iterator.next();
        final List<BlobSidecar> currentSlotBlobs =
            blobSidecarsDb.computeIfAbsent(key.getSlot(), __ -> new ArrayList<>());
        currentSlotBlobs.add(database.getBlobSidecar(key).orElseThrow());
      }
    }

    assertThat(blobSidecarsDb).isEqualTo(blobSidecars);
  }

  private void assertNonCanonicalBlobSidecars(final Map<UInt64, List<BlobSidecar>> blobSidecars) {
    final List<UInt64> slots = blobSidecars.keySet().stream().sorted().toList();
    if (slots.isEmpty()) {
      return;
    }

    final Map<UInt64, List<BlobSidecar>> blobSidecarsDb = new HashMap<>();
    try (final Stream<SlotAndBlockRootAndBlobIndex> blobSidecarsStream =
        database.streamNonCanonicalBlobSidecarKeys(slots.get(0), slots.get(slots.size() - 1))) {

      for (final Iterator<SlotAndBlockRootAndBlobIndex> iterator = blobSidecarsStream.iterator();
          iterator.hasNext(); ) {
        final SlotAndBlockRootAndBlobIndex key = iterator.next();
        final List<BlobSidecar> currentSlotBlobs =
            blobSidecarsDb.computeIfAbsent(key.getSlot(), __ -> new ArrayList<>());
        currentSlotBlobs.add(database.getNonCanonicalBlobSidecar(key).orElseThrow());
      }
    }

    assertThat(blobSidecarsDb).isEqualTo(blobSidecars);
  }

  public static class CreateForkChainResult {
    private final ChainBuilder forkChain;
    private final UInt64 firstHotBlockSlot;

    public CreateForkChainResult(final ChainBuilder forkChain, final UInt64 firstHotBlockSlot) {
      this.forkChain = forkChain;
      this.firstHotBlockSlot = firstHotBlockSlot;
    }

    public ChainBuilder getForkChain() {
      return forkChain;
    }

    public UInt64 getFirstHotBlockSlot() {
      return firstHotBlockSlot;
    }
  }

  private static SlotAndBlockRootAndBlobIndex blobSidecarToKey(final BlobSidecar blobSidecar) {
    return new SlotAndBlockRootAndBlobIndex(
        blobSidecar.getSlot(), blobSidecar.getBlockRoot(), blobSidecar.getIndex());
  }
}

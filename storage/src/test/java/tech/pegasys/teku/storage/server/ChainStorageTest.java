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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;

public class ChainStorageTest {
  private static final Logger LOG = LogManager.getLogger();
  private static final long CHAIN_LENGTH_EPOCHS = 3;
  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private ChainStorage chainStorage;

  private final Spec spec =
      TestSpecFactory.createMinimalWithFuluForkEpoch(UInt64.valueOf(CHAIN_LENGTH_EPOCHS));

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {

    storageSystem = storageSystemSupplier.get(dataDirectory, spec);
    chainStorage = storageSystem.chainStorage();
    chainBuilder = storageSystem.chainBuilder();

    chainBuilder.generateGenesis();
  }

  @AfterEach
  void tearDown() {
    try {
      if (storageSystem != null) {
        storageSystem.close();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_shouldAcceptValidBlocks_startFromAnchorWithBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocks(storageSystemSupplier, false, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_shouldAcceptValidBlocks_startFromAnchorWithoutBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocks(storageSystemSupplier, true, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_shouldAcceptValidBlocks_inBatches(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocks(storageSystemSupplier, true, true);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndBlobSidecars_shouldAcceptValidBlocks_startFromAnchorWithBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndBlobSidecars(storageSystemSupplier, false, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndBlobSidecars_shouldAcceptValidBlocks_startFromAnchorWithoutBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndBlobSidecars(storageSystemSupplier, true, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndBlobSidecars_shouldAcceptValidBlocks_inBatches(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndBlobSidecars(storageSystemSupplier, true, true);
  }

  public void testOnFinalizedBlocks(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches) {
    testOnFinalized(
        storageSystemSupplier, initializeWithAnchorStateAlone, executeInBatches, false, false);
  }

  public void testOnFinalizedBlocksAndBlobSidecars(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches) {
    testOnFinalized(
        storageSystemSupplier, initializeWithAnchorStateAlone, executeInBatches, true, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void
      onFinalizedBlocksAndDataColumnSidecars_shouldAcceptValidBlocks_startFromAnchorWithBlock(
          final String storageType,
          final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndDataColumnSidecars(storageSystemSupplier, false, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void
      onFinalizedBlocksAndDataColumnSidecars_shouldAcceptValidBlocks_startFromAnchorWithoutBlock(
          final String storageType,
          final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndDataColumnSidecars(storageSystemSupplier, true, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndDataColumnSidecars_shouldAcceptValidBlocks_inBatches(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndDataColumnSidecars(storageSystemSupplier, true, true);
  }

  public void testOnFinalizedBlocksAndDataColumnSidecars(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches) {
    testOnFinalized(
        storageSystemSupplier, initializeWithAnchorStateAlone, executeInBatches, false, true);
  }

  public void testOnFinalized(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches,
      final boolean testBlobSidecars,
      final boolean testDataColumnSidecars) {
    setup(storageSystemSupplier);

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(
        spec.slotsPerEpoch(ZERO) * CHAIN_LENGTH_EPOCHS,
        ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));

    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(3);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor point
    final AnchorPoint anchorPoint;
    final long firstMissingBlockSlot;
    if (initializeWithAnchorStateAlone) {
      anchorPoint =
          AnchorPoint.create(
              spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
      storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
      firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();
    } else {
      anchorPoint = AnchorPoint.create(spec, anchorCheckpoint, anchorBlockAndState);
      storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
      firstMissingBlockSlot = anchorBlockAndState.getSlot().minus(1).longValue();
    }

    // Collect missing ChainStorage Blocks
    final List<SignedBeaconBlock> missingHistoricalBlocks =
        getMissingHistoricalBlocks(firstMissingBlockSlot);

    // Collect missing ChainStorage BlobSidecars
    final Map<SlotAndBlockRoot, List<BlobSidecar>> missingHistoricalBlobSidecars =
        getMissingHistoricalBlobSidecars(missingHistoricalBlocks, firstMissingBlockSlot);
    final Map<SlotAndBlockRoot, List<BlobSidecar>> finalizedBlobSidecars =
        testBlobSidecars ? missingHistoricalBlobSidecars : Map.of();
    assertThat(chainStorage.getEarliestAvailableBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
    Optional<UInt64> maybeEarliestBlobSidecarSlot = Optional.empty();
    if (!finalizedBlobSidecars.isEmpty()) {
      maybeEarliestBlobSidecarSlot =
          finalizedBlobSidecars.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .filter(entry -> !entry.getValue().isEmpty())
              .findFirst()
              .map(entry -> entry.getValue().getFirst().getSlot());
    }

    // Collect missing ChainStorage DataColumnSidecars
    final Map<SlotAndBlockRoot, List<DataColumnSidecar>> missingHistoricalDataColumnSidecars =
        getMissingHistoricalDataColumnSidecars(firstMissingBlockSlot);
    final Map<SlotAndBlockRoot, List<DataColumnSidecar>> finalizedDataColumnSidecars =
        testDataColumnSidecars ? missingHistoricalDataColumnSidecars : Map.of();
    assertThat(chainStorage.getEarliestAvailableDataColumnSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
    Optional<UInt64> maybeEarliestDataColumnSidecarSlot = Optional.empty();
    if (!finalizedDataColumnSidecars.isEmpty()) {
      maybeEarliestDataColumnSidecarSlot =
          finalizedDataColumnSidecars.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .filter(entry -> !entry.getValue().isEmpty())
              .findFirst()
              .map(entry -> entry.getValue().getFirst().getSlot());
    }

    // Store Block and BlobSidecars
    if (executeInBatches) {
      final int batchSize = missingHistoricalBlocks.size() / 3;
      final List<List<SignedBeaconBlock>> batches =
          Lists.partition(missingHistoricalBlocks, batchSize);
      for (int i = batches.size() - 1; i >= 0; i--) {
        final List<SignedBeaconBlock> batch = batches.get(i);
        chainStorage
            .onFinalizedBlocks(batch, finalizedBlobSidecars, Map.of(), maybeEarliestBlobSidecarSlot)
            .finishDebug(LOG);
      }
    } else {
      chainStorage
          .onFinalizedBlocks(
              missingHistoricalBlocks,
              finalizedBlobSidecars,
              Map.of(),
              maybeEarliestBlobSidecarSlot)
          .finishDebug(LOG);
    }
    // Store DataColumnSidecars
    maybeEarliestDataColumnSidecarSlot.ifPresent(
        uInt64 -> chainStorage.onEarliestAvailableDataColumnSlot(uInt64).finishDebug(LOG));
    finalizedDataColumnSidecars
        .values()
        .forEach(
            listOfSidecars ->
                listOfSidecars.forEach(
                    sidecar -> chainStorage.onNewSidecar(sidecar).finishDebug(LOG)));

    // Verify blocks and blob sidecars are now available
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> blockResult =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(blockResult).isCompletedWithOptionalContaining(missingHistoricalBlock);
      final SafeFuture<List<BlobSidecar>> blobSidecarsResult =
          chainStorage
              .getBlobSidecarKeys(
                  missingHistoricalBlock.getSlot(),
                  missingHistoricalBlock.getSlot(),
                  Long.MAX_VALUE)
              .thenCompose(
                  list ->
                      SafeFuture.collectAll(
                              list.stream().map(key -> chainStorage.getBlobSidecar(key)))
                          .thenApply(
                              listOfOptionals ->
                                  listOfOptionals.stream()
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .collect(Collectors.toList())));

      if (testBlobSidecars) {
        // verify blob sidecars for a block are available
        assertThatSafeFuture(blobSidecarsResult)
            .isCompletedWithValueMatching(
                list ->
                    list.equals(
                        missingHistoricalBlobSidecars.getOrDefault(
                            missingHistoricalBlock.getSlotAndBlockRoot(),
                            Collections.emptyList())));
        assertThat(maybeEarliestBlobSidecarSlot).isNotEmpty();
        assertThat(chainStorage.getEarliestAvailableBlobSidecarSlot())
            .isCompletedWithValue(maybeEarliestBlobSidecarSlot);
      } else {
        assertThatSafeFuture(blobSidecarsResult).isCompletedWithValueMatching(List::isEmpty);
        assertThat(chainStorage.getEarliestAvailableBlobSidecarSlot())
            .isCompletedWithValueMatching(Optional::isEmpty);
      }
    }

    // Verify DataColumnSidecars are now available
    for (Map.Entry<SlotAndBlockRoot, List<DataColumnSidecar>> missingDataColumnSidecar :
        missingHistoricalDataColumnSidecars.entrySet()) {

      final SafeFuture<List<DataColumnSidecar>> dataColumnSidecarsResult =
          chainStorage
              .getDataColumnIdentifiers(
                  missingDataColumnSidecar.getKey().getSlot(),
                  missingDataColumnSidecar.getKey().getSlot(),
                  UInt64.MAX_VALUE)
              .thenCompose(
                  list ->
                      SafeFuture.collectAll(list.stream().map(key -> chainStorage.getSidecar(key)))
                          .thenApply(
                              listOfOptionals ->
                                  listOfOptionals.stream()
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .collect(Collectors.toList())));

      if (testDataColumnSidecars) {
        // verify data column sidecars for a block are available
        assertThatSafeFuture(dataColumnSidecarsResult)
            .isCompletedWithValueMatching(
                list ->
                    list.equals(
                        missingHistoricalDataColumnSidecars.getOrDefault(
                            missingDataColumnSidecar.getKey(), Collections.emptyList())));
        assertThat(maybeEarliestDataColumnSidecarSlot).isNotEmpty();
        assertThat(chainStorage.getEarliestAvailableDataColumnSlot())
            .isCompletedWithValue(maybeEarliestDataColumnSidecarSlot);
      } else {
        assertThatSafeFuture(dataColumnSidecarsResult).isCompletedWithValueMatching(List::isEmpty);
        assertThat(chainStorage.getEarliestAvailableDataColumnSlot())
            .isCompletedWithValueMatching(Optional::isEmpty);
      }
    }
  }

  private List<SignedBeaconBlock> getMissingHistoricalBlocks(final long firstMissingBlockSlot) {
    final List<SignedBeaconBlock> missingHistoricalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .toList();
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> blockResult =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(blockResult).isCompletedWithEmptyOptional();
    }

    return missingHistoricalBlocks;
  }

  private Map<SlotAndBlockRoot, List<BlobSidecar>> getMissingHistoricalBlobSidecars(
      final List<SignedBeaconBlock> missingHistoricalBlocks, final long firstMissingBlockSlot) {
    final Map<SlotAndBlockRoot, List<BlobSidecar>> missingHistoricalBlobSidecars =
        chainBuilder
            .streamBlobSidecars(0, firstMissingBlockSlot)
            .flatMap(entry -> entry.getValue().stream())
            .collect(Collectors.groupingBy(BlobSidecar::getSlotAndBlockRoot));
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<List<SlotAndBlockRootAndBlobIndex>> sidecarKeysResult =
          chainStorage.getBlobSidecarKeys(
              missingHistoricalBlock.getSlot(), missingHistoricalBlock.getSlot(), Long.MAX_VALUE);
      assertThatSafeFuture(sidecarKeysResult).isCompletedWithValueMatching(List::isEmpty);
    }

    return missingHistoricalBlobSidecars;
  }

  private Map<SlotAndBlockRoot, List<DataColumnSidecar>> getMissingHistoricalDataColumnSidecars(
      final long firstMissingBlockSlot) {
    final int dataColumnSidecars = spec.getNumberOfDataColumns().orElse(128);
    final Map<SlotAndBlockRoot, List<DataColumnSidecar>> missingHistoricalDataColumnSidecars =
        chainBuilder
            .streamDataColumnSidecars(
                0,
                firstMissingBlockSlot,
                IntStream.range(0, dataColumnSidecars)
                    .mapToObj(UInt64::valueOf)
                    .collect(Collectors.toList()))
            .flatMap(entry -> entry.getValue().stream())
            .collect(Collectors.groupingBy(DataColumnSidecar::getSlotAndBlockRoot));

    for (Map.Entry<SlotAndBlockRoot, List<DataColumnSidecar>> missingDataColumnSidecar :
        missingHistoricalDataColumnSidecars.entrySet()) {
      for (DataColumnSidecar dataColumnSidecar : missingDataColumnSidecar.getValue()) {
        final DataColumnSlotAndIdentifier key =
            DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar);
        final SafeFuture<Optional<DataColumnSidecar>> dataColumnSidecarResult =
            chainStorage.getSidecar(key);
        assertThatSafeFuture(dataColumnSidecarResult).isCompletedWithEmptyOptional();
      }
    }

    return missingHistoricalDataColumnSidecars;
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void testOverrideEarliestBlobSidecarSlot(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(
        spec.slotsPerEpoch(ZERO) * 3L,
        ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));

    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(3);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor point
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Now try to store missing historical blocks and blob sidecars
    final List<SignedBeaconBlock> missingHistoricalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    final Map<SlotAndBlockRoot, List<BlobSidecar>> missingHistoricalBlobSidecars =
        chainBuilder
            .streamBlobSidecars(0, firstMissingBlockSlot)
            .flatMap(entry -> entry.getValue().stream())
            .collect(Collectors.groupingBy(BlobSidecar::getSlotAndBlockRoot));

    // Sanity check - blocks and blob sidecars should be unavailable initially
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> blockResult =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(blockResult).isCompletedWithEmptyOptional();
      final SafeFuture<List<SlotAndBlockRootAndBlobIndex>> sidecarKeysResult =
          chainStorage.getBlobSidecarKeys(
              missingHistoricalBlock.getSlot(), missingHistoricalBlock.getSlot(), Long.MAX_VALUE);
      assertThatSafeFuture(sidecarKeysResult).isCompletedWithValueMatching(List::isEmpty);
    }

    assertThat(chainStorage.getEarliestAvailableBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    // Store in batches with incorrect earliestBlobSidecarSlot until latest
    final int batchSize = missingHistoricalBlocks.size() / 3;
    final List<List<SignedBeaconBlock>> batches =
        Lists.partition(missingHistoricalBlocks, batchSize);
    for (int i = batches.size() - 1; i >= 0; i--) {
      final List<SignedBeaconBlock> batch = batches.get(i);
      // earliestBlobSidecarSlot: 2, 1, 0. Correct only in the last batch
      chainStorage
          .onFinalizedBlocks(
              batch, missingHistoricalBlobSidecars, Map.of(), Optional.of(UInt64.valueOf(i)))
          .finishDebug(LOG);
    }

    // The correct earliest BlobSidecar slot, no matter of order saving
    assertThat(chainStorage.getEarliestAvailableBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(ZERO));
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_nonMatchingBlocks(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final int epochs = 3;
    final int chainSize = spec.slotsPerEpoch(ZERO) * epochs;

    // Create fork
    final ChainBuilder forkBuilder = chainBuilder.fork();
    // Skip a block to create a divergent chain
    forkBuilder.generateBlockAtSlot(2);
    forkBuilder.generateBlocksUpToSlot(chainSize);

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(chainSize);
    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(epochs);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Try to save non-matching fork blocks
    final List<SignedBeaconBlock> invalidBlocks =
        forkBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    final SafeFuture<Void> result =
        chainStorage.onFinalizedBlocks(invalidBlocks, Map.of(), Map.of(), Optional.empty());
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_missingBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final int epochs = 3;
    final int chainSize = spec.slotsPerEpoch(ZERO) * epochs;

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(chainSize);
    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(epochs);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Get set of blocks to save
    final List<SignedBeaconBlock> blocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    // Remove a block from the middle
    blocks.remove(blocks.size() / 2);

    final SafeFuture<Void> result =
        chainStorage.onFinalizedBlocks(blocks, Map.of(), Map.of(), Optional.empty());
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_gapBetweenBatchAndEarliestKnownBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final int epochs = 3;
    final int chainSize = spec.slotsPerEpoch(ZERO) * epochs;

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(chainSize);
    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(epochs);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Get set of blocks to save
    final List<SignedBeaconBlock> blocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    // Remove a block from the end
    blocks.removeLast();

    final SafeFuture<Void> result =
        chainStorage.onFinalizedBlocks(blocks, Map.of(), Map.of(), Optional.empty());
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }
}

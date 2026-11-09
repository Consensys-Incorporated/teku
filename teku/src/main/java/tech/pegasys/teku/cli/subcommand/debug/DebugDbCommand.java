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

package tech.pegasys.teku.cli.subcommand.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;

@Command(
    name = "db",
    description = "Debugging tools for inspecting the contents of the database",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DebugDbCommand implements Runnable {
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  @Command(
      name = "get-finalized-state",
      description = "Get the finalized state, if available, as SSZ",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getFinalizedState(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              required = true,
              names = {"--slot", "-s"},
              description =
                  "The slot to retrieve the state for. If unavailable the closest available state will be returned")
          final long slot)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      return writeState(
          outputFile, database.getLatestAvailableFinalizedState(UInt64.valueOf(slot)));
    }
  }

  @Command(
      name = "get-earliest-available-block-slot",
      description = "Get the earliest available block slot in the database",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getEarliestAvailableBlockSlot(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--timed", "-t"},
              description = "Prints the time taken to retrieve the earliest available block slot",
              defaultValue = "true",
              fallbackValue = "true",
              showDefaultValue = Visibility.ALWAYS)
          final boolean verbose)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final long startTime = System.currentTimeMillis();
      final Optional<UInt64> earliestAvailableBlockSlot = database.getEarliestAvailableBlockSlot();
      final long endTime = System.currentTimeMillis();
      earliestAvailableBlockSlot.ifPresent(System.out::println);
      if (verbose) {
        System.out.println(
            "Time taken to retrieve the earliest available block slot: "
                + (endTime - startTime)
                + "ms");
      }
      return 0;
    }
  }

  @Command(
      name = "get-earliest-data-column-slot",
      description = "Get the earliest available data-column slot in the database",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getEarliestDataColumnSlot(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--timed", "-t"},
              description = "Prints the time taken to retrieve the earliest available block slot",
              defaultValue = "true",
              fallbackValue = "true",
              showDefaultValue = Visibility.ALWAYS)
          final boolean verbose)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final long startTime = System.currentTimeMillis();
      final Optional<UInt64> earliestAvailableDataColumnSlot =
          database.getEarliestDataColumnSidecarSlot();
      final long endTime = System.currentTimeMillis();
      earliestAvailableDataColumnSlot.ifPresent(System.out::println);
      if (verbose) {
        System.out.println(
            "Time taken to retrieve the earliest available data column slot: "
                + (endTime - startTime)
                + "ms");
      }
      return 0;
    }
  }

  @Command(
      name = "get-state-summary",
      description = "Report count, first slot, and last slot of stored finalized states.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getStateSummary(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final long[] count = {0};
      final UInt64[] firstSlot = {null};
      final UInt64[] lastSlot = {null};
      try (Stream<UInt64> stream =
          database.streamFinalizedStateSlots(UInt64.ZERO, UInt64.valueOf(Long.MAX_VALUE))) {
        stream.forEach(
            slot -> {
              count[0]++;
              if (firstSlot[0] == null) {
                firstSlot[0] = slot;
              }
              lastSlot[0] = slot;
            });
      }
      if (count[0] == 0) {
        System.out.println("No finalized states found.");
      } else {
        System.out.printf("Finalized states: %d%n", count[0]);
        System.out.printf("First slot:       %s%n", firstSlot[0]);
        System.out.printf("Last slot:        %s%n", lastSlot[0]);
      }
      final long hotStateCount =
          database.getColumnCounts(Optional.of("HOT_STATES_BY_ROOT")).values().stream()
              .mapToLong(Long::longValue)
              .sum();
      System.out.printf("Hot states:       %d%n", hotStateCount);
      return 0;
    }
  }

  @Command(
      name = "get-finalized-state-indices",
      description = "Display the slots of finalized states that are stored.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getFinalizedStateIndices(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--start-slot"},
              defaultValue = "0",
              description = "The start index of the range to display")
          final long startSlot,
      @Option(
              required = true,
              names = {"--end-slot"},
              defaultValue = "9223372036854775807",
              description = "The end index of the range to display")
          final long endSlot)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      System.out.println("Searching for finalized states in the finalized store");
      try (Stream<UInt64> stream =
          database.streamFinalizedStateSlots(UInt64.valueOf(startSlot), UInt64.valueOf(endSlot))) {
        stream.forEach(System.out::println);
      }
    }
    return 0;
  }

  @Command(
      name = "get-latest-finalized-state",
      description = "Get the latest finalized state, if available, as SSZ",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getLatestFinalizedState(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              names = {"--block-output"},
              description = "File to write the block matching the latest finalized state to")
          final Path blockOutputFile)
      throws Exception {
    final AsyncRunnerFactory asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(
            new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
    final AsyncRunner asyncRunner = asyncRunnerFactory.create("async", 1);
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Optional<AnchorPoint> finalizedAnchor =
          database
              .createMemoryStore()
              .map(
                  storeData ->
                      StoreBuilder.create()
                          .asyncRunner(asyncRunner)
                          .onDiskStoreData(storeData)
                          .metricsSystem(new NoOpMetricsSystem())
                          .specProvider(eth2NetworkOptions.getNetworkConfiguration().getSpec())
                          .blockProvider(BlockProvider.NOOP)
                          .stateProvider(StateAndBlockSummaryProvider.NOOP)
                          .build())
              .map(UpdatableStore::getLatestFinalized);
      int result = writeState(outputFile, finalizedAnchor.map(AnchorPoint::getState));
      if (result == 0 && blockOutputFile != null) {
        final Optional<SignedBeaconBlock> finalizedBlock =
            finalizedAnchor.flatMap(AnchorPoint::getSignedBeaconBlock);
        result = writeBlock(blockOutputFile, finalizedBlock);
      }
      return result;
    } finally {
      asyncRunner.shutdown();
    }
  }

  @Command(
      name = "get-column-counts",
      description = "Count entries in columns in storage tables",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getColumnCounts(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--filter"},
              description = "Only count the columns that contain the specified filter.")
          final String filter)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Map<String, Long> counts = database.getColumnCounts(Optional.ofNullable(filter));
      final int width = counts.keySet().stream().mapToInt(String::length).max().orElse(1);
      final String fmt = String.format("%%%ds: %%d%%n", width);
      new TreeMap<>(counts).forEach((label, count) -> System.out.printf(fmt, label, count));
    }
    return 0;
  }

  @Command(
      name = "get-variables",
      description = "Check variable values",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getVariables(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--filter"},
              description = "Only get variables that match a given filter.")
          final String filter)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Map<String, Optional<String>> variables = database.getVariables();
      variables
          .keySet()
          .forEach(
              k -> {
                if (filter == null || k.contains(filter)) {
                  System.out.printf("%-33s: %s\n", k, variables.get(k).orElse("EMPTY"));
                }
              });
    }
    return 0;
  }

  @Command(
      name = "validate-block-history",
      description = "Validate the chain of finalized blocks via parent references",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int validateBlockHistory(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    final long startTimeMillis = System.currentTimeMillis();
    long counter = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {

      Optional<SignedBeaconBlock> maybeFinalizedBlock = database.getLastAvailableFinalizedBlock();
      Optional<SignedBeaconBlock> maybeEarliestBlock = database.getEarliestAvailableBlock();
      while (maybeFinalizedBlock.isPresent()) {
        final SignedBeaconBlock currentBlock = maybeFinalizedBlock.get();
        if (counter == 0) {
          System.out.printf(
              "Tracing chain from best finalized block %s (%s)%n",
              currentBlock.getRoot(), currentBlock.getSlot());
        }
        final Optional<SignedBeaconBlock> maybeParent =
            database.getSignedBlock(currentBlock.getParentRoot());
        if (maybeParent.isEmpty()) {
          if (maybeEarliestBlock.isPresent()
              && maybeEarliestBlock.get().getRoot().equals(currentBlock.getRoot())) {
            System.out.printf(
                "Found back to earliest block %s (%s)%n",
                currentBlock.getRoot(), currentBlock.getSlot());
            break;
          }
          System.err.printf(
              "ERROR: Unable to locate parent %s of block %s (%s)%n",
              currentBlock.getParentRoot(), currentBlock.getRoot(), currentBlock.getSlot());
          maybeFinalizedBlock = findFinalizedBlockBeforeSlot(database, currentBlock.getSlot());
          if (maybeFinalizedBlock.isEmpty()) {
            break;
          } else {
            System.out.printf(
                "Starting to dig back from new parent: %s (%s)%n",
                maybeFinalizedBlock.get().getRoot(), maybeFinalizedBlock.get().getSlot());
          }
        } else {
          checkFinalizedIndices(database, maybeParent.get());
          maybeFinalizedBlock = maybeParent;
        }
        counter++;
        if (counter % 5_000 == 0) {
          System.out.printf("%s (%s)...%n", currentBlock.getRoot(), currentBlock.getSlot());
        }
      }
    } catch (DatabaseStorageException ex) {
      System.out.println("Failed to open database");
    }
    final long endTimeMillis = System.currentTimeMillis();
    System.out.printf(
        "Done. Checked %s blocks in %s ms%n", counter, endTimeMillis - startTimeMillis);
    return 0;
  }

  @Command(
      name = "dump-hot-blocks",
      description = "Writes all non-finalized blocks in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int dumpHotBlocks(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write blocks to")
          final Path outputFile)
      throws Exception {
    int index = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions);
        final Stream<Bytes> blockStream = database.streamHotBlocksAsSsz().map(Map.Entry::getValue);
        final ZipOutputStream out =
            new ZipOutputStream(
                Files.newOutputStream(
                    outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      for (final Iterator<Bytes> iterator = blockStream.iterator(); iterator.hasNext(); ) {
        out.putNextEntry(new ZipEntry(index + ".ssz"));
        final Bytes blockData = iterator.next();
        out.write(blockData.toArrayUnsafe());
        index++;
      }
    }
    System.out.println("Wrote " + index + " blocks to " + outputFile.toAbsolutePath());
    return 0;
  }

  @Command(
      name = "get-hot-block-slot-to-root",
      description = "Writes all the slots and roots of stored hot blocks, will be in hash order.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getHotBlockSlotToRoot(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions);
        final Stream<Map.Entry<Bytes, Bytes>> hotBlockStream = database.streamHotBlocksAsSsz()) {
      for (final Iterator<Map.Entry<Bytes, Bytes>> hotBlockIterator = hotBlockStream.iterator();
          hotBlockIterator.hasNext(); ) {
        final Map.Entry<Bytes, Bytes> rootAndHotBlock = hotBlockIterator.next();
        final Bytes32 hotBlockRoot = Bytes32.wrap(rootAndHotBlock.getKey());
        final Optional<SignedBeaconBlock> hotBlock = database.getHotBlock(hotBlockRoot);
        System.out.println(
            String.format(
                "%s, %s", hotBlock.orElseThrow().getSlot().toString(), hotBlockRoot.toHexString()));
      }
    }
    return 0;
  }

  @Command(
      name = "dump-blob-sidecars",
      description = "Writes all blob sidecars in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int dumpBlobSidecars(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--from-slot"},
              description = "Dump blob sidecars starting from a given slot (inclusive)")
          final Long fromSlot,
      @Option(
              names = {"--to-slot"},
              description = "Dump blob sidecars up to a given slot (inclusive)")
          final Long toSlot,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write blob sidecars to")
          final Path outputFile)
      throws Exception {

    final UInt64 from = Optional.ofNullable(fromSlot).map(UInt64::valueOf).orElse(UInt64.ZERO);
    final UInt64 to = Optional.ofNullable(toSlot).map(UInt64::valueOf).orElse(UInt64.MAX_VALUE);

    if (from.isGreaterThan(to)) {
      throw new InvalidConfigurationException("--from-slot must less then or equal to --to-slot");
    }

    int index = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions);
        final ZipOutputStream out =
            new ZipOutputStream(
                Files.newOutputStream(
                    outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

      try (final Stream<SlotAndBlockRootAndBlobIndex> keyStream =
          database.streamBlobSidecarKeys(from, to)) {

        for (final Iterator<SlotAndBlockRootAndBlobIndex> it = keyStream.iterator();
            it.hasNext(); ) {
          final SlotAndBlockRootAndBlobIndex key = it.next();
          final BlobSidecar blobSidecar = database.getBlobSidecar(key).orElseThrow();
          out.putNextEntry(
              new ZipEntry(
                  blobSidecar.getSlot()
                      + "_"
                      + blobSidecar.getBlockRoot().toUnprefixedHexString()
                      + ".ssz"));
          blobSidecar.sszSerialize(out);
          index++;
        }
      }

      System.out.println("Wrote " + index + " blob sidecars to " + outputFile.toAbsolutePath());
    }
    return 0;
  }

  @Command(
      name = "dump-data-columns",
      description = "Writes all data column sidecars in the database as a zip of SSZ files",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int dumpDataColumnSidecars(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--from-slot"},
              description = "Dump data-columns starting from a given slot (inclusive)")
          final Long fromSlot,
      @Option(
              names = {"--to-slot"},
              description = "Dump data-columns up to a given slot (inclusive)")
          final Long toSlot,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write data-columns to")
          final Path outputFile)
      throws Exception {

    final UInt64 from = Optional.ofNullable(fromSlot).map(UInt64::valueOf).orElse(UInt64.ZERO);
    final UInt64 to = Optional.ofNullable(toSlot).map(UInt64::valueOf).orElse(UInt64.MAX_VALUE);

    if (from.isGreaterThan(to)) {
      throw new InvalidConfigurationException("--from-slot must less then or equal to --to-slot");
    }

    int index = 0;
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions);
        final ZipOutputStream out =
            new ZipOutputStream(
                Files.newOutputStream(
                    outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

      try (final Stream<DataColumnSlotAndIdentifier> keyStream =
          database.streamDataColumnIdentifiers(from, to)) {

        for (final Iterator<DataColumnSlotAndIdentifier> it = keyStream.iterator();
            it.hasNext(); ) {
          final DataColumnSlotAndIdentifier key = it.next();
          final Optional<DataColumnSidecar> column = database.getSidecar(key);
          if (column.isPresent()) {
            try {
              out.putNextEntry(
                  new ZipEntry(
                      column.get().getSlot()
                          + "_"
                          + key.getSlotAndBlockRoot().getBlockRoot()
                          + "_"
                          + key.columnIndex()
                          + ".ssz"));
              column.get().sszSerialize(out);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            index++;
          }
        }
      }

      System.out.println(
          "Wrote " + index + " data-column-sidecars to " + outputFile.toAbsolutePath());
    }
    return 0;
  }

  @Command(
      name = "validate-blob-sidecars",
      description = "Validate the blob sidecars",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int validateBlobSidecars(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--current-slot", "-s"},
              description =
                  "Provide the current slot from which calculate Data Availability window")
          final Long currentSlot,
      @Option(
              names = {"--lower-data-availability-epoch", "-l"},
              description = "Override the Data Availability window lower epoch")
          final Long lowerDataAvailabilityEpoch)
      throws Exception {

    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();

    final SpecConfigDeneb specConfigDeneb =
        spec.forMilestone(SpecMilestone.DENEB).getConfig().toVersionDeneb().orElseThrow();

    final UInt64 denebActivationEpoch = specConfigDeneb.getDenebForkEpoch();

    final UInt64 currentEpoch =
        eth2NetworkOptions
            .getNetworkConfiguration()
            .getSpec()
            .computeEpochAtSlot(UInt64.valueOf(currentSlot));

    final UInt64 lowerEpochBoundDataAvailability =
        lowerDataAvailabilityEpoch != null
            ? currentEpoch
                .minusMinZero(UInt64.valueOf(lowerDataAvailabilityEpoch))
                .max(denebActivationEpoch)
            : currentEpoch
                .minusMinZero(specConfigDeneb.getMinEpochsForBlobSidecarsRequests())
                .max(denebActivationEpoch);

    final UInt64 lowerSlotBoundDataAvailability =
        eth2NetworkOptions
            .getNetworkConfiguration()
            .getSpec()
            .computeStartSlotAtEpoch(lowerEpochBoundDataAvailability);

    long missingFinalizedBlobSidecar = 0;
    long missingHotBlobsSidecars = 0;
    long orphanBlobsSidecars = 0;
    long unprunedBlobsSidecars = 0;
    long blocksToSidecarsCheckCounter = 0;
    long sidecarsToBlocksCheckCounter = 0;
    long commitmentsToBlobSidecarsCheckCounter = 0;

    final long startTimeMillis = System.currentTimeMillis();

    Optional<UInt64> maybeEarliestBlobSidecarSlot = Optional.empty();
    Optional<UInt64> maybeLatestFinalizedSlot = Optional.empty();
    Optional<SignedBeaconBlock> maybeEarliestBlock = Optional.empty();

    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {

      // Checking blobs for finalized blocks
      Optional<SignedBeaconBlock> maybeFinalizedBlock = database.getLastAvailableFinalizedBlock();
      maybeEarliestBlock = database.getEarliestAvailableBlock();
      maybeEarliestBlobSidecarSlot = database.getEarliestBlobSidecarSlot();
      maybeLatestFinalizedSlot = maybeFinalizedBlock.map(SignedBeaconBlock::getSlot);
      while (maybeFinalizedBlock.isPresent()) {
        final SignedBeaconBlock currentBlock = maybeFinalizedBlock.get();
        if (blocksToSidecarsCheckCounter == 0) {
          System.out.printf(
              "Checking blobs sidecars tracing back chain from best finalized block %s (%s)%n",
              currentBlock.getRoot(), currentBlock.getSlot());
        }

        // Checking blobs sidecar existence
        final int expectedBlobSidecarsCount =
            currentBlock
                .getMessage()
                .getBody()
                .getOptionalBlobKzgCommitments()
                .map(SszList::size)
                .orElse(0);

        commitmentsToBlobSidecarsCheckCounter += expectedBlobSidecarsCount;

        for (int blobSidecarIndex = 0;
            blobSidecarIndex < expectedBlobSidecarsCount;
            blobSidecarIndex++) {
          final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex =
              new SlotAndBlockRootAndBlobIndex(
                  currentBlock.getSlot(), currentBlock.getRoot(), UInt64.valueOf(blobSidecarIndex));

          final Optional<BlobSidecar> maybeCurrentBlobSidecar;
          if (maybeEarliestBlobSidecarSlot.isEmpty()
              || maybeEarliestBlobSidecarSlot.get().isGreaterThan(currentBlock.getSlot())) {
            maybeCurrentBlobSidecar = Optional.empty();
          } else {
            maybeCurrentBlobSidecar = database.getBlobSidecar(slotAndBlockRootAndBlobIndex);
          }

          if (maybeCurrentBlobSidecar.isEmpty()
              && currentBlock.getSlot().isGreaterThanOrEqualTo(lowerSlotBoundDataAvailability)) {
            // Inside DA window but missing
            missingFinalizedBlobSidecar++;
            System.err.printf(
                "ERROR: Unable to locate finalized blob sidecar %s%n",
                slotAndBlockRootAndBlobIndex);
          }
        }

        final Optional<SignedBeaconBlock> maybeParent =
            database.getSignedBlock(currentBlock.getParentRoot());
        if (maybeParent.isEmpty()) {
          if (maybeEarliestBlock.isPresent()
              && maybeEarliestBlock.get().getRoot().equals(currentBlock.getRoot())) {
            System.out.printf(
                "Found blob sidecars back to earliest block %s (%s)%n",
                currentBlock.getRoot(), currentBlock.getSlot());
            break;
          }
        } else {
          checkFinalizedIndices(database, maybeParent.get());
          maybeFinalizedBlock = maybeParent;
        }
        blocksToSidecarsCheckCounter++;
        if (blocksToSidecarsCheckCounter % 5_000 == 0) {
          System.out.printf("%s (%s)...%n", currentBlock.getRoot(), currentBlock.getSlot());
        }
      }

      // Checking blobs sidecars for hot blocks

      try (final Stream<Map.Entry<Bytes, Bytes>> hotBlockStream = database.streamHotBlocksAsSsz()) {
        // Iterate through hot blocks
        System.out.printf("Checking blob sidecars excess for hot blocks...%n");

        for (final Iterator<Map.Entry<Bytes, Bytes>> hotBlockIterator = hotBlockStream.iterator();
            hotBlockIterator.hasNext(); ) {
          blocksToSidecarsCheckCounter++;

          final Map.Entry<Bytes, Bytes> rootAndHotBlock = hotBlockIterator.next();
          final Bytes hotBlockData = rootAndHotBlock.getValue();
          final UInt64 hotBlockSlot =
              BeaconBlockInvariants.extractSignedBlockContainerSlot(hotBlockData);

          if (hotBlockSlot.isGreaterThanOrEqualTo(lowerSlotBoundDataAvailability)) {
            // exclude finalized hot blocks (keep non finalized hot blocks only so they are not
            // counted twice)
            if (hotBlockSlot.isLessThanOrEqualTo(maybeLatestFinalizedSlot.orElse(UInt64.ZERO))) {
              System.err.printf(
                  "ERROR: Skipped hot block at slot %s. The block is already finalized%n",
                  hotBlockSlot);
              continue;
            }
            final Bytes32 hotBlockRoot = Bytes32.wrap(rootAndHotBlock.getKey());
            final Optional<SignedBeaconBlock> hotBlock = database.getHotBlock(hotBlockRoot);
            if (hotBlock.isPresent()) {
              final int expectedBlobSidecarsCount =
                  hotBlock
                      .get()
                      .getMessage()
                      .getBody()
                      .getOptionalBlobKzgCommitments()
                      .map(SszList::size)
                      .orElse(0);

              commitmentsToBlobSidecarsCheckCounter += expectedBlobSidecarsCount;

              for (int blobSidecarIndex = 0;
                  blobSidecarIndex < expectedBlobSidecarsCount;
                  blobSidecarIndex++) {
                final SlotAndBlockRootAndBlobIndex blobSidecarKey =
                    new SlotAndBlockRootAndBlobIndex(
                        hotBlockSlot, hotBlockRoot, UInt64.valueOf(blobSidecarIndex));

                // Checking blob sidecar existence
                final Optional<BlobSidecar> maybeCurrentBlobSidecar =
                    database.getBlobSidecar(blobSidecarKey);
                if (maybeCurrentBlobSidecar.isEmpty()
                    && hotBlockSlot.isGreaterThanOrEqualTo(lowerSlotBoundDataAvailability)) {
                  // inside DA window but missing
                  missingHotBlobsSidecars++;
                  System.err.printf(
                      "ERROR: Unable to locate hot blob sidecar %s%n", blobSidecarKey);
                }
              }
            }
          }
        }
      }

      // Checking orphaned blob sidecars
      try (final Stream<SlotAndBlockRootAndBlobIndex> blobsSidecarKeys =
          database.streamBlobSidecarKeys(UInt64.ZERO, UInt64.MAX_VALUE)) {
        for (final Iterator<SlotAndBlockRootAndBlobIndex> blobSidecarsIterator =
                blobsSidecarKeys.iterator();
            blobSidecarsIterator.hasNext(); ) {
          final SlotAndBlockRootAndBlobIndex blobSidecarKey = blobSidecarsIterator.next();

          sidecarsToBlocksCheckCounter++;

          final Optional<SignedBeaconBlock> maybeFinalizedBlockFound =
              database
                  .getFinalizedBlockAtSlot(blobSidecarKey.getSlot())
                  .filter(block -> block.getRoot().equals(blobSidecarKey.getBlockRoot()));

          final Optional<SignedBeaconBlock> maybeHotBlockFound =
              database.getHotBlock(blobSidecarKey.getBlockRoot());

          if (maybeHotBlockFound.isEmpty() && maybeFinalizedBlockFound.isEmpty()) {
            orphanBlobsSidecars++;
            System.err.printf(
                "ERROR: blob sidecar has no corresponding finalized or hot block %s%n",
                blobSidecarKey);
          }

          // check Data Availability
          if (blobSidecarKey.getSlot().isLessThan(lowerSlotBoundDataAvailability)) {
            unprunedBlobsSidecars++;
            System.err.printf("WARNING: non pruned blobs sidecar %s%n", blobSidecarKey);
          }
        }
      }
    } catch (DatabaseStorageException ex) {
      System.out.println("Failed to open database");
      ex.printStackTrace();
    }

    final long endTimeMillis = System.currentTimeMillis();
    System.out.printf(
        "Done. Performed %s block->blob_sidecars, %s blob_sidecars->block checks and %s block_commitments->blob_sidecars in %s ms%n",
        blocksToSidecarsCheckCounter,
        sidecarsToBlocksCheckCounter,
        commitmentsToBlobSidecarsCheckCounter,
        endTimeMillis - startTimeMillis);
    System.out.printf(
        "\tCalculated Data Availability window (slots): %s - %s%n",
        lowerSlotBoundDataAvailability, currentSlot);
    System.out.printf("\tEarliest BlobSidecars slot: %s%n", maybeEarliestBlobSidecarSlot);
    System.out.printf(
        "\tEarliest finalized block slot: %s%n",
        maybeEarliestBlock.map(SignedBeaconBlock::getSlot));
    System.out.printf("\tLatest finalized block slot: %s%n", maybeLatestFinalizedSlot);
    System.out.printf("\tMissing finalized Blob Sidecars: %s%n", missingFinalizedBlobSidecar);
    System.out.printf("\tMissing hot Blob Sidecars: %s%n", missingHotBlobsSidecars);
    System.out.printf(
        "\tOrphaned Blob Sidecars (corresponding block is missing): %s%n", orphanBlobsSidecars);
    System.out.printf(
        "\tNon-pruned BlobSidecars (older than DA lower bound): %s%n", unprunedBlobsSidecars);

    return 0;
  }

  @Command(
      name = "delete-hot-blocks",
      description =
          "Deletes all non-justified blocks from the database which are from an invalid hard fork",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int deleteHotBlocks(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              names = {"--delete-all", "-a"},
              description =
                  "Delete all non-justified blocks, not just those from an invalid hard fork",
              defaultValue = "false",
              fallbackValue = "true",
              showDefaultValue = Visibility.ALWAYS)
          final boolean deleteAll)
      throws Exception {
    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Optional<Checkpoint> justified = database.getJustifiedCheckpoint();
      if (justified.isEmpty()) {
        System.out.println(
            "Unable to delete hot blocks because the justified checkpoint is not available");
        return 1;
      }
      final UInt64 justifiedSlot = justified.get().getEpochStartSlot(spec);
      System.out.println("Justified slot is " + justifiedSlot);
      final Set<Bytes32> blockRootsToDelete = new HashSet<>();
      try (final Stream<Map.Entry<Bytes, Bytes>> blockStream = database.streamHotBlocksAsSsz()) {
        // Iterate through and find the block roots to delete first
        for (final Iterator<Map.Entry<Bytes, Bytes>> iterator = blockStream.iterator();
            iterator.hasNext(); ) {
          final Map.Entry<Bytes, Bytes> rootAndBlock = iterator.next();
          final Bytes blockData = rootAndBlock.getValue();
          final UInt64 blockSlot = BeaconBlockInvariants.extractSignedBlockContainerSlot(blockData);
          final boolean shouldDelete;
          final boolean isJustifiedBlock = blockSlot.isLessThanOrEqualTo(justifiedSlot);
          if (deleteAll) {
            if (isJustifiedBlock) {
              System.out.printf(
                  "Not deleting block at slot %s as it has been justified%n", blockSlot);
              shouldDelete = false;
            } else {
              shouldDelete = true;
            }
          } else {
            final boolean canParse = canParseBlock(spec, blockData);
            if (!canParse && isJustifiedBlock) {
              System.out.printf(
                  "Block at slot %s is justified but cannot be parsed. Unable to recover this database.%n",
                  blockSlot);
              return 1;
            }
            shouldDelete = !canParse;
          }

          if (shouldDelete) {
            blockRootsToDelete.add(Bytes32.wrap(rootAndBlock.getKey()));
          }
        }
      }

      System.out.printf("Deleting %d blocks%n", blockRootsToDelete.size());
      database.deleteHotBlocks(blockRootsToDelete);
    }
    return 0;
  }

  @Command(
      name = "compact-db",
      description =
          "Trigger a full compaction of the database to reclaim disk space left behind by pruning. "
              + "This is an expensive, I/O-heavy operation and must only be run while the node is "
              + "stopped.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int compactDb(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    final long startTime = System.currentTimeMillis();
    System.out.println("Compacting database. This may take a long time...");
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      database.compactStorage();
    }
    System.out.printf(
        "Database compaction completed in %d ms%n", System.currentTimeMillis() - startTime);
    return 0;
  }

  private boolean canParseBlock(final Spec spec, final Bytes blockData) {
    try {
      spec.deserializeSignedBeaconBlock(blockData);
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  private Optional<SignedBeaconBlock> findFinalizedBlockBeforeSlot(
      final Database database, final UInt64 knownSlot) {
    UInt64 parentSearchSlot = knownSlot.decrement().decrement();

    while (parentSearchSlot.isGreaterThan(knownSlot.minus(128))) {
      Optional<Bytes32> blockRoot = database.getFinalizedBlockRootBySlot(parentSearchSlot);
      if (blockRoot.isPresent()) {
        Optional<SignedBeaconBlock> block = database.getSignedBlock(blockRoot.get());
        if (block.isEmpty()) {
          System.err.printf(
              "ERROR: Found index at slot %s (%s), but block could not be retrieved%n",
              blockRoot.get(), parentSearchSlot);
        } else {
          return block;
        }
      } else {
        System.err.printf("WARNING: No block found at slot %s%n", parentSearchSlot);
      }
      parentSearchSlot = parentSearchSlot.decrement();
    }
    System.err.printf(
        "Searched back 128 blocks from last known block, and couldn't find anything helpful, cannot continue checking via parent references..%n");
    return Optional.empty();
  }

  private void checkFinalizedIndices(final Database database, final SignedBeaconBlock parentBlock) {

    UInt64 parentSlot = parentBlock.getSlot();
    Optional<UInt64> indexSlot = database.getSlotForFinalizedBlockRoot(parentBlock.getRoot());
    if (indexSlot.isEmpty() || !indexSlot.get().equals(parentSlot)) {
      System.err.printf(
          "Finalized block index for root %s reports slot %s, expected slot %s%n",
          parentBlock.getRoot(), (indexSlot.isPresent() ? indexSlot.get() : "BLANK"), parentSlot);
    }
  }

  private Database createDatabase(
      final BeaconNodeDataOptions beaconNodeDataOptions,
      final Eth2NetworkOptions eth2NetworkOptions) {
    final Eth2NetworkConfiguration networkConfiguration =
        eth2NetworkOptions.getNetworkConfiguration();
    final Spec spec = networkConfiguration.getSpec();
    final DataConfig dataConfig = beaconNodeDataOptions.getDataConfig();
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            DataDirLayout.createFrom(dataConfig).getBeaconDataDirectory(),
            StorageConfiguration.builder()
                .eth1DepositContract(networkConfiguration.getEth1DepositContractAddress())
                .specProvider(spec)
                .dataConfig(dataConfig)
                .build(),
            Optional.empty());
    return databaseFactory.createDatabase();
  }

  private int writeState(final Path outputFile, final Optional<BeaconState> state) {
    if (state.isEmpty()) {
      System.err.println("No state available.");
      return 2;
    }
    try {
      Files.write(outputFile, state.get().sszSerialize().toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write state to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }

  private int writeBlock(final Path outputFile, final Optional<SignedBeaconBlock> block) {
    if (block.isEmpty()) {
      System.err.println("No block available.");
      return 2;
    }
    try {
      Files.write(outputFile, block.get().sszSerialize().toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write block to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }
}

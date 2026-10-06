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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.service.serviceutils.layout.DataConfig.DEFAULT_DEBUG_DATA_DUMPING_ENABLED;
import static tech.pegasys.teku.storage.server.StorageConfiguration.DEFAULT_ROCKSDB_BLOB_DB_ENABLED;
import static tech.pegasys.teku.storage.server.StorageConfiguration.DEFAULT_STATE_REBUILD_TIMEOUT_SECONDS;

import java.nio.file.Path;
import java.time.Duration;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.StorageConfiguration;

public class BeaconNodeDataOptions extends ValidatorClientDataOptions {

  @Option(
      names = {"--data-beacon-path"},
      paramLabel = "<FILENAME>",
      description = "Path to beacon node data\n  Default: <data-base-path>/beacon",
      arity = "1")
  private Path dataBeaconPath;

  @CommandLine.Option(
      names = {"--data-storage-mode"},
      paramLabel = "<STORAGE_MODE>",
      description =
          "Sets the strategy for handling historical chain data.  (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;

  @CommandLine.Option(
      names = {"--data-storage-archive-frequency"},
      paramLabel = "<FREQUENCY>",
      description =
          "Sets the frequency, in slots, at which to store finalized states to disk. "
              + "This option is ignored if --data-storage-mode is set to PRUNE",
      arity = "1")
  private long dataStorageFrequency = StorageConfiguration.DEFAULT_STORAGE_FREQUENCY;

  @CommandLine.Option(
      names = {"--Xdata-storage-archive-finalized-states-retained"},
      paramLabel = "<INTEGER>",
      description =
          "Sets the period of retained finalized states in disk, in slots. "
              + "This option is ignored if --data-storage-mode is set to PRUNE or MINIMAL",
      arity = "1",
      hidden = true)
  private long dataStorageRetainedSlots = StorageConfiguration.DEFAULT_STORAGE_RETAINED_SLOTS;

  @CommandLine.Option(
      names = {"--Xdata-storage-state-pruning-interval"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Interval in seconds between finalized state pruning",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private long statePruningIntervalSeconds =
      StorageConfiguration.DEFAULT_STATE_PRUNING_INTERVAL.toSeconds();

  @CommandLine.Option(
      names = {"--Xdata-storage-state-pruning-limit"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Maximum number of finalized states that can be pruned in each pruning session",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private int statePruningLimit = StorageConfiguration.DEFAULT_STATE_PRUNING_LIMIT;

  @CommandLine.Option(
      names = {"--Xdata-storage-create-db-version"},
      paramLabel = "<VERSION>",
      description = "Database version to create",
      arity = "1",
      hidden = true)
  private String createDbVersion = null;

  @CommandLine.Option(
      names = {"--data-storage-non-canonical-blocks-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Store non-canonical blocks and associated blobs if they exist",
      fallbackValue = "true",
      arity = "0..1")
  private boolean storeNonCanonicalBlocksEnabled =
      StorageConfiguration.DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;

  /**
   * Default value selected based on experimentation to minimise memory usage without affecting sync
   * time. Not that states later in the chain with more validators have more branches so need a
   * bigger cache. We may periodically need to review this setting but it shouldn't need to change
   * often.
   */
  @CommandLine.Option(
      names = {"--Xdata-storage-max-known-node-cache-size"},
      paramLabel = "<INTEGER>",
      description = "Maximum size of the in-memory known node cache for finalized states",
      arity = "1",
      hidden = true)
  private int maxKnownNodeCacheSize = StorageConfiguration.DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;

  @CommandLine.Option(
      names = {"--reconstruct-historic-states"},
      paramLabel = "<BOOLEAN>",
      description = "",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS)
  private Boolean reconstructHistoricStates =
      SyncConfig.DEFAULT_RECONSTRUCT_HISTORIC_STATES_ENABLED;

  @CommandLine.Option(
      names = {"--Xdata-storage-block-pruning-interval"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Interval in seconds between finalized block pruning",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private long blockPruningIntervalSeconds =
      StorageConfiguration.DEFAULT_BLOCK_PRUNING_INTERVAL.toSeconds();

  @CommandLine.Option(
      names = {"--Xdata-storage-block-pruning-limit"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Maximum number of blocks that can be pruned in each pruning session",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private int blockPruningLimit = StorageConfiguration.DEFAULT_BLOCK_PRUNING_LIMIT;

  @CommandLine.Option(
      names = {"--Xdata-storage-blobs-pruning-interval"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Interval in seconds between blob sidecars pruning",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private long blobsPruningIntervalSeconds =
      StorageConfiguration.DEFAULT_BLOBS_PRUNING_INTERVAL.toSeconds();

  @CommandLine.Option(
      names = {"--Xdata-storage-blobs-pruning-limit"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description =
          "Maximum number of blocks of blob sidecars that can be pruned in each pruning session",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private int blobsPruningLimit = StorageConfiguration.DEFAULT_BLOBS_PRUNING_LIMIT;

  @CommandLine.Option(
      names = {"--Xdata-storage-data-column-pruning-interval"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description = "Interval in seconds between data column sidecars pruning",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private long dataColumnPruningIntervalSeconds =
      StorageConfiguration.DEFAULT_DATA_COLUMN_PRUNING_INTERVAL.toSeconds();

  @CommandLine.Option(
      names = {"--Xdata-storage-data-column-pruning-limit"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description =
          "Maximum number of blocks of data column sidecars that can be pruned in each pruning session",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private int dataColumnPruningLimit = StorageConfiguration.DEFAULT_DATA_COLUMN_PRUNING_LIMIT;

  @CommandLine.Option(
      names = {"--Xdata-storage-pruning-warn-timeout"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description =
          "Duration in seconds after which a pruning task will log a warning if not completed",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private long pruningWarnTimeoutSeconds =
      StorageConfiguration.DEFAULT_PRUNING_WARN_TIMEOUT.toSeconds();

  @CommandLine.Option(
      names = {"--Xdata-storage-blobs-archive-path"},
      hidden = true,
      paramLabel = "<STRING>",
      description = "Path to write pruned blobs",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private String blobsArchivePath = null;

  @Option(
      names = {"--Xdata-storage-state-rebuild-timeout-seconds"},
      hidden = true,
      paramLabel = "<seconds>",
      description =
          "Only allow up to an allocated period of time to attempt to rebuild a missing finalized state.",
      arity = "1")
  private int stateRebuildTimeoutSeconds = DEFAULT_STATE_REBUILD_TIMEOUT_SECONDS;

  @Option(
      names = {"--Xdebug-data-dumping-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enable saving objects to files that cause problems when processing, for example rejected blocks or invalid gossip.\n Default: <data-base-path>/debug",
      fallbackValue = "true",
      hidden = true,
      arity = "0..1")
  private boolean debugDataDumpingEnabled = DEFAULT_DEBUG_DATA_DUMPING_ENABLED;

  @CommandLine.Option(
      names = {"--Xdata-storage-rocksdb-blob-db-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enable RocksDB BlobDB for column families containing static data. "
              + "BlobDB separates large values into blob files, which can reduce write amplification.",
      fallbackValue = "true",
      arity = "0..1")
  private boolean rocksdbBlobDbEnabled = DEFAULT_ROCKSDB_BLOB_DB_ENABLED;

  @CommandLine.Option(
      names = {"--force-clear-db"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Force deletion of the beacon chain database on startup. "
              + "This will delete all chain data but preserve validator slashing protection. "
              + "Use with caution - all historical chain data will be lost.",
      fallbackValue = "true",
      arity = "0..1")
  private boolean forceClearDb = false;

  @Override
  protected DataConfig.Builder configureDataConfig(final DataConfig.Builder config) {
    return super.configureDataConfig(config)
        .beaconDataPath(dataBeaconPath)
        .debugDataDumpingEnabled(debugDataDumpingEnabled);
  }

  @Override
  public void configure(final TekuConfiguration.Builder builder) {
    super.configure(builder);
    builder.storageConfiguration(
        b ->
            b.dataStorageMode(dataStorageMode)
                .dataStorageFrequency(dataStorageFrequency)
                .dataStorageCreateDbVersion(parseDatabaseVersion())
                .storeNonCanonicalBlocks(storeNonCanonicalBlocksEnabled)
                .maxKnownNodeCacheSize(maxKnownNodeCacheSize)
                .blockPruningInterval(Duration.ofSeconds(blockPruningIntervalSeconds))
                .blockPruningLimit(blockPruningLimit)
                .stateRebuildTimeoutSeconds(stateRebuildTimeoutSeconds)
                .blobsPruningInterval(Duration.ofSeconds(blobsPruningIntervalSeconds))
                .blobsPruningLimit(blobsPruningLimit)
                .dataColumnPruningInterval(Duration.ofSeconds(dataColumnPruningIntervalSeconds))
                .dataColumnPruningLimit(dataColumnPruningLimit)
                .blobsArchivePath(blobsArchivePath)
                .retainedSlots(dataStorageRetainedSlots)
                .statePruningInterval(Duration.ofSeconds(statePruningIntervalSeconds))
                .statePruningLimit(statePruningLimit)
                .forceClearDb(forceClearDb)
                .rocksdbBlobDbEnabled(rocksdbBlobDbEnabled)
                .pruningWarnTimeout(Duration.ofSeconds(pruningWarnTimeoutSeconds)));
    builder.sync(
        b ->
            b.fetchAllHistoricBlocks(dataStorageMode.storesAllBlocks())
                .reconstructHistoricStatesEnabled(reconstructHistoricStates));
  }

  public DatabaseVersion parseDatabaseVersion() {
    if (createDbVersion == null) {
      return dataStorageFrequency == 1
          ? DatabaseVersion.ROCKSDB_TREE
          : DatabaseVersion.DEFAULT_VERSION;
    }
    return DatabaseVersion.fromString(createDbVersion).orElse(DatabaseVersion.DEFAULT_VERSION);
  }
}

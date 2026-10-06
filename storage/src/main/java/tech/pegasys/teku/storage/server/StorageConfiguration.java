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

package tech.pegasys.teku.storage.server;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.NOT_SET;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.STORAGE_MODE_FILENAME;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;

public class StorageConfiguration {
  public static final boolean DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED = false;
  public static final boolean DEFAULT_ROCKSDB_BLOB_DB_ENABLED = false;
  public static final int DEFAULT_STATE_REBUILD_TIMEOUT_SECONDS = 120;
  public static final long DEFAULT_STORAGE_FREQUENCY = 2048L;
  public static final int DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE = 5_000_000;
  public static final Duration DEFAULT_BLOCK_PRUNING_INTERVAL = Duration.ofMinutes(15);
  public static final int DEFAULT_BLOCK_PRUNING_LIMIT = 5000;
  public static final Duration DEFAULT_BLOBS_PRUNING_INTERVAL = Duration.ofMinutes(1);
  public static final Duration DEFAULT_DATA_COLUMN_PRUNING_INTERVAL = Duration.ofMinutes(1);
  public static final Duration DEFAULT_STATE_PRUNING_INTERVAL = Duration.ofMinutes(5);
  public static final long DEFAULT_STORAGE_RETAINED_SLOTS = 0;
  public static final int DEFAULT_STATE_PRUNING_LIMIT = 1;

  // 60/12 = 5 blocks/slots per minute * 6 max blobs per block = 30 blobs per minute at maximum,
  // This value prunes blobs by slots, using 12 to allow for catch up.
  public static final int DEFAULT_BLOBS_PRUNING_LIMIT = 12;

  public static final int DEFAULT_DATA_COLUMN_PRUNING_LIMIT = 64;
  public static final Duration DEFAULT_PRUNING_WARN_TIMEOUT = Duration.ofSeconds(60);

  // Max limit we have tested so far without seeing perf degradation
  public static final int MAX_STATE_PRUNE_LIMIT = 100;

  private final Eth1Address eth1DepositContract;

  private final StateStorageMode dataStorageMode;
  private final long dataStorageFrequency;
  private final DatabaseVersion dataStorageCreateDbVersion;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;
  private final int maxKnownNodeCacheSize;
  private final Duration blockPruningInterval;
  private final int blockPruningLimit;
  private final Duration statePruningInterval;
  private final Duration blobsPruningInterval;
  private final Duration dataColumnPruningInterval;
  private final int blobsPruningLimit;
  private final int dataColumnPruningLimit;
  private final String blobsArchivePath;
  private final long retainedSlots;
  private final int statePruningLimit;

  private final int stateRebuildTimeoutSeconds;
  private final boolean forceClearDb;
  private final boolean rocksdbBlobDbEnabled;
  private final Duration pruningWarnTimeout;

  private StorageConfiguration(
      final Eth1Address eth1DepositContract,
      final StateStorageMode dataStorageMode,
      final long dataStorageFrequency,
      final DatabaseVersion dataStorageCreateDbVersion,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Duration blockPruningInterval,
      final int blockPruningLimit,
      final Duration blobsPruningInterval,
      final int blobsPruningLimit,
      final Duration dataColumnPruningInterval,
      final int dataColumnPruningLimit,
      final String blobsArchivePath,
      final int stateRebuildTimeoutSeconds,
      final long retainedSlots,
      final Duration statePruningInterval,
      final int statePruningLimit,
      final Spec spec,
      final boolean forceClearDb,
      final boolean rocksdbBlobDbEnabled,
      final Duration pruningWarnTimeout) {
    this.eth1DepositContract = eth1DepositContract;
    this.dataStorageMode = dataStorageMode;
    this.dataStorageFrequency = dataStorageFrequency;
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
    this.blockPruningInterval = blockPruningInterval;
    this.blockPruningLimit = blockPruningLimit;
    this.blobsPruningInterval = blobsPruningInterval;
    this.blobsPruningLimit = blobsPruningLimit;
    this.blobsArchivePath = blobsArchivePath;
    this.dataColumnPruningInterval = dataColumnPruningInterval;
    this.dataColumnPruningLimit = dataColumnPruningLimit;
    this.stateRebuildTimeoutSeconds = stateRebuildTimeoutSeconds;
    this.retainedSlots = retainedSlots;
    this.statePruningInterval = statePruningInterval;
    this.statePruningLimit = statePruningLimit;
    this.spec = spec;
    this.forceClearDb = forceClearDb;
    this.rocksdbBlobDbEnabled = rocksdbBlobDbEnabled;
    this.pruningWarnTimeout = pruningWarnTimeout;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Eth1Address getEth1DepositContract() {
    return eth1DepositContract;
  }

  public StateStorageMode getDataStorageMode() {
    return dataStorageMode;
  }

  public int getStateRebuildTimeoutSeconds() {
    return stateRebuildTimeoutSeconds;
  }

  public long getDataStorageFrequency() {
    return dataStorageFrequency;
  }

  public DatabaseVersion getDataStorageCreateDbVersion() {
    return dataStorageCreateDbVersion;
  }

  public boolean isStoreNonCanonicalBlocksEnabled() {
    return storeNonCanonicalBlocks;
  }

  public int getMaxKnownNodeCacheSize() {
    return maxKnownNodeCacheSize;
  }

  public Duration getBlockPruningInterval() {
    return blockPruningInterval;
  }

  public int getBlockPruningLimit() {
    return blockPruningLimit;
  }

  public Duration getBlobsPruningInterval() {
    return blobsPruningInterval;
  }

  public int getBlobsPruningLimit() {
    return blobsPruningLimit;
  }

  public Duration getDataColumnPruningInterval() {
    return dataColumnPruningInterval;
  }

  public int getDataColumnPruningLimit() {
    return dataColumnPruningLimit;
  }

  public Optional<String> getBlobsArchivePath() {
    return Optional.ofNullable(blobsArchivePath);
  }

  public long getRetainedSlots() {
    return retainedSlots;
  }

  public Duration getStatePruningInterval() {
    return statePruningInterval;
  }

  public int getStatePruningLimit() {
    return statePruningLimit;
  }

  public Spec getSpec() {
    return spec;
  }

  public boolean isForceClearDb() {
    return forceClearDb;
  }

  public boolean isRocksdbBlobDbEnabled() {
    return rocksdbBlobDbEnabled;
  }

  public Duration getPruningWarnTimeout() {
    return pruningWarnTimeout;
  }

  public static final class Builder {
    private static final Logger LOG = LogManager.getLogger();
    private Eth1Address eth1DepositContract;
    private StateStorageMode dataStorageMode = StateStorageMode.DEFAULT_MODE;
    private long dataStorageFrequency = DEFAULT_STORAGE_FREQUENCY;
    private DatabaseVersion dataStorageCreateDbVersion = DatabaseVersion.DEFAULT_VERSION;
    private Spec spec;
    private DataConfig dataConfig;
    private boolean storeNonCanonicalBlocks = DEFAULT_STORE_NON_CANONICAL_BLOCKS_ENABLED;
    private int maxKnownNodeCacheSize = DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE;
    private Duration blockPruningInterval = DEFAULT_BLOCK_PRUNING_INTERVAL;
    private int blockPruningLimit = DEFAULT_BLOCK_PRUNING_LIMIT;
    private Duration blobsPruningInterval = DEFAULT_BLOBS_PRUNING_INTERVAL;
    private int blobsPruningLimit = DEFAULT_BLOBS_PRUNING_LIMIT;
    private Duration dataColumnPruningInterval = DEFAULT_DATA_COLUMN_PRUNING_INTERVAL;
    private int dataColumnPruningLimit = DEFAULT_DATA_COLUMN_PRUNING_LIMIT;
    private String blobsArchivePath = null;
    private int stateRebuildTimeoutSeconds = DEFAULT_STATE_REBUILD_TIMEOUT_SECONDS;
    private Duration statePruningInterval = DEFAULT_STATE_PRUNING_INTERVAL;
    private long retainedSlots = DEFAULT_STORAGE_RETAINED_SLOTS;
    private int statePruningLimit = DEFAULT_STATE_PRUNING_LIMIT;
    private boolean forceClearDb = false;
    private boolean rocksdbBlobDbEnabled = DEFAULT_ROCKSDB_BLOB_DB_ENABLED;
    private Duration pruningWarnTimeout = DEFAULT_PRUNING_WARN_TIMEOUT;

    private Builder() {}

    public Builder eth1DepositContract(final Eth1Address eth1DepositContract) {
      this.eth1DepositContract = eth1DepositContract;
      return this;
    }

    public Builder eth1DepositContractDefault(final Eth1Address eth1DepositContract) {
      if (this.eth1DepositContract == null) {
        this.eth1DepositContract = eth1DepositContract;
      }
      return this;
    }

    public Builder dataStorageMode(final StateStorageMode dataStorageMode) {
      this.dataStorageMode = dataStorageMode;
      return this;
    }

    public Builder dataStorageFrequency(final long dataStorageFrequency) {
      if (dataStorageFrequency < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid dataStorageFrequency: %d", dataStorageFrequency));
      }
      this.dataStorageFrequency = dataStorageFrequency;
      return this;
    }

    public Builder dataStorageCreateDbVersion(final DatabaseVersion dataStorageCreateDbVersion) {
      this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
      return this;
    }

    public Builder dataConfig(final DataConfig dataConfig) {
      this.dataConfig = dataConfig;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder storeNonCanonicalBlocks(final boolean storeNonCanonicalBlocks) {
      this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
      return this;
    }

    public Builder maxKnownNodeCacheSize(final int maxKnownNodeCacheSize) {
      if (maxKnownNodeCacheSize < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid maxKnownNodeCacheSize: %d", maxKnownNodeCacheSize));
      }
      this.maxKnownNodeCacheSize = maxKnownNodeCacheSize;
      return this;
    }

    public Builder blockPruningInterval(final Duration blockPruningInterval) {
      if (blockPruningInterval.isNegative() || blockPruningInterval.isZero()) {
        throw new InvalidConfigurationException("Block pruning interval must be positive");
      }
      this.blockPruningInterval = blockPruningInterval;
      return this;
    }

    public Builder blockPruningLimit(final int blockPruningLimit) {
      if (blockPruningLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid blockPruningLimit: %d", blockPruningLimit));
      }
      this.blockPruningLimit = blockPruningLimit;
      return this;
    }

    public Builder blobsPruningInterval(final Duration blobsPruningInterval) {
      if (blobsPruningInterval.isNegative() || blobsPruningInterval.isZero()) {
        throw new InvalidConfigurationException("Blobs pruning interval must be positive");
      }
      this.blobsPruningInterval = blobsPruningInterval;
      return this;
    }

    public Builder blobsPruningLimit(final int blobsPruningLimit) {
      if (blobsPruningLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid blobsPruningLimit: %d", blobsPruningLimit));
      }
      this.blobsPruningLimit = blobsPruningLimit;
      return this;
    }

    public Builder dataColumnPruningInterval(final Duration dataColumnPruningInterval) {
      if (dataColumnPruningInterval.isNegative() || dataColumnPruningInterval.isZero()) {
        throw new InvalidConfigurationException(
            "DataColumn sidecar pruning interval must be positive");
      }
      this.dataColumnPruningInterval = dataColumnPruningInterval;
      return this;
    }

    public Builder dataColumnPruningLimit(final int dataColumnPruningLimit) {
      if (dataColumnPruningLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid dataColumnPruningLimit: %d", dataColumnPruningLimit));
      }
      this.dataColumnPruningLimit = dataColumnPruningLimit;
      return this;
    }

    public Builder blobsArchivePath(final String blobsArchivePath) {
      if (blobsArchivePath != null) {
        File file = Path.of(blobsArchivePath).toFile();
        if (!file.exists()) {
          throw new InvalidConfigurationException(
              String.format("Blobs archive path does not exist: '%s'", blobsArchivePath));
        }
      }
      this.blobsArchivePath = blobsArchivePath;
      return this;
    }

    public Builder retainedSlots(final long retainedSlots) {
      if (retainedSlots < 0) {
        throw new InvalidConfigurationException(
            "Invalid number of slots to retain finalized states for");
      }
      this.retainedSlots = retainedSlots;
      return this;
    }

    public Builder statePruningInterval(final Duration statePruningInterval) {
      if (statePruningInterval.isNegative() || statePruningInterval.isZero()) {
        throw new InvalidConfigurationException("Block pruning interval must be positive");
      }
      if (statePruningInterval.toSeconds() < 30L
          || statePruningInterval.toSeconds() > Duration.ofDays(1).toSeconds()) {
        throw new InvalidConfigurationException(
            "Block pruning interval must be a value between 30 seconds and 1 day");
      }
      this.statePruningInterval = statePruningInterval;
      return this;
    }

    public Builder statePruningLimit(final int statePruningLimit) {
      if (statePruningLimit < 0 || statePruningLimit > MAX_STATE_PRUNE_LIMIT) {
        throw new InvalidConfigurationException(
            String.format("Invalid statePruningLimit: %d", statePruningLimit));
      }
      this.statePruningLimit = statePruningLimit;
      return this;
    }

    public Builder forceClearDb(final boolean forceClearDb) {
      this.forceClearDb = forceClearDb;
      return this;
    }

    public Builder rocksdbBlobDbEnabled(final boolean rocksdbBlobDbEnabled) {
      this.rocksdbBlobDbEnabled = rocksdbBlobDbEnabled;
      return this;
    }

    public Builder pruningWarnTimeout(final Duration pruningWarnTimeout) {
      this.pruningWarnTimeout = pruningWarnTimeout;
      return this;
    }

    public StorageConfiguration build() {
      determineDataStorageMode();
      validateStatePruningConfiguration();
      return new StorageConfiguration(
          eth1DepositContract,
          dataStorageMode,
          dataStorageFrequency,
          dataStorageCreateDbVersion,
          storeNonCanonicalBlocks,
          maxKnownNodeCacheSize,
          blockPruningInterval,
          blockPruningLimit,
          blobsPruningInterval,
          blobsPruningLimit,
          dataColumnPruningInterval,
          dataColumnPruningLimit,
          blobsArchivePath,
          stateRebuildTimeoutSeconds,
          retainedSlots,
          statePruningInterval,
          statePruningLimit,
          spec,
          forceClearDb,
          rocksdbBlobDbEnabled,
          pruningWarnTimeout);
    }

    private void determineDataStorageMode() {
      if (dataConfig != null) {
        final DataDirLayout dataDirLayout = DataDirLayout.createFrom(dataConfig);
        final Path beaconDataDirectory = dataDirLayout.getBeaconDataDirectory();

        Optional<StateStorageMode> storageModeFromStoredFile;
        try {
          storageModeFromStoredFile =
              DatabaseStorageModeFileHelper.readStateStorageMode(
                  beaconDataDirectory.resolve(STORAGE_MODE_FILENAME));
        } catch (final DatabaseStorageException e) {
          if (dataStorageMode == NOT_SET) {
            throw e;
          } else {
            storageModeFromStoredFile = Optional.empty();
          }
        }

        this.dataStorageMode =
            determineStorageDefault(
                beaconDataDirectory.toFile().exists(), storageModeFromStoredFile, dataStorageMode);
      } else {
        if (dataStorageMode.equals(NOT_SET)) {
          dataStorageMode = PRUNE;
        }
      }
    }

    private void validateStatePruningConfiguration() {
      if (dataStorageFrequency == 1 && retainedSlots > 0) {
        // If we are in tree mode, we don't want to allow the state pruner to run
        throw new InvalidConfigurationException(
            "State pruner cannot be enabled using tree mode storage");
      }
    }

    public Builder stateRebuildTimeoutSeconds(final int stateRebuildTimeoutSeconds) {
      if (stateRebuildTimeoutSeconds < 10 || stateRebuildTimeoutSeconds > 300) {
        LOG.warn(
            "State rebuild timeout is set outside of sensible defaults of 10 -> 300, {} was defined. Cannot be below "
                + "1, will allow the value to exceed 300.",
            stateRebuildTimeoutSeconds);
      }
      this.stateRebuildTimeoutSeconds = Math.max(stateRebuildTimeoutSeconds, 1);
      LOG.debug("stateRebuildTimeoutSeconds = {}", stateRebuildTimeoutSeconds);
      return this;
    }
  }

  static StateStorageMode determineStorageDefault(
      final boolean isExistingStore,
      final Optional<StateStorageMode> maybeHistoricStorageMode,
      final StateStorageMode modeRequested) {
    if (modeRequested != NOT_SET) {
      return modeRequested;
    }

    if (maybeHistoricStorageMode.isPresent()) {
      final StateStorageMode stateStorageMode = maybeHistoricStorageMode.get();
      if (stateStorageMode == PRUNE) {
        STATUS_LOG.warnUsageOfImplicitPruneDataStorageMode();
      }
      return stateStorageMode;
    }

    if (isExistingStore) {
      STATUS_LOG.warnUsageOfImplicitPruneDataStorageMode();
      return PRUNE;
    } else {
      return MINIMAL;
    }
  }
}

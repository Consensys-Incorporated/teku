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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.storage.server.StateStorageMode.ARCHIVE;
import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;

import java.nio.file.Path;
import java.time.Duration;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StorageConfiguration;

public class BeaconNodeDataOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final Path TEST_PATH = Path.of("/tmp/teku");
  private static final String GENESIS_STATE =
      "https://221EMZ2YSdriVVdXx:5058f100c7@eth2-beacon-mainnet.infura.io/eth/v1/debug/beacon/states/finalized";

  @Test
  public void dataPath_shouldReadFromConfigurationFile() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromFile("dataOptions_config.yaml");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(tekuConfiguration.dataConfig().getDataBasePath()).isEqualTo(TEST_PATH);
    assertThat(config.getDataStorageMode()).isEqualTo(ARCHIVE);
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.V4);
    assertThat(config.getDataStorageFrequency()).isEqualTo(128L);
  }

  @Test
  public void dataStorageMode_shouldAcceptPrune() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "prune");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageMode()).isEqualTo(PRUNE);
  }

  @Test
  public void dataStorageMode_shouldAcceptArchive() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "archive");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void dataPath_shouldAcceptNonDefaultValues() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--data-path", TEST_PATH.toString());
    assertThat(config.dataConfig().getDataBasePath()).isEqualTo(TEST_PATH);
  }

  @Test
  public void dataStorageFrequency_shouldDefault() {
    final StorageConfiguration config = getTekuConfigurationFromArguments().storageConfiguration();
    assertThat(config.getDataStorageFrequency()).isEqualTo(2048L);
  }

  @Test
  public void dataStorageFrequency_shouldAcceptNonDefaultValues() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1024000");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageFrequency()).isEqualTo(1024000L);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldDefault() {
    final StorageConfiguration config = getTekuConfigurationFromArguments().storageConfiguration();
    final DatabaseVersion expectedDefault = DatabaseVersion.V6;
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(expectedDefault);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldOverrideIfFrequencyIsLowAndSupported() {
    final StorageConfiguration config =
        getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1")
            .storageConfiguration();
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.ROCKSDB_TREE);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldAcceptNonDefaultValues() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xdata-storage-create-db-version", "noop");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.NOOP);
  }

  @Test
  public void shouldAcceptReconstructHistoricStatesValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--data-storage-mode",
            "ARCHIVE",
            "--genesis-state",
            GENESIS_STATE,
            "--reconstruct-historic-states",
            "true");
    assertThat(tekuConfiguration.sync().isReconstructHistoricStatesEnabled()).isEqualTo(true);
  }

  @Test
  public void missingGenesisState_expectInvalidReconstructHistoricStatesValue() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .eth2NetworkConfig(b -> b.applyNetworkDefaults(Eth2Network.MINIMAL))
                    .storageConfiguration(b -> b.dataStorageMode(ARCHIVE))
                    .sync(b -> b.reconstructHistoricStatesEnabled(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Genesis state required when reconstructing historic states");
  }

  @Test
  public void usingDefaultGenesisState_expectValidReconstructHistoricStatesValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--data-storage-mode",
            "ARCHIVE",
            "--network",
            "mainnet",
            "--reconstruct-historic-states",
            "true");

    assertThat(tekuConfiguration.sync().isReconstructHistoricStatesEnabled()).isEqualTo(true);
  }

  @Test
  public void pruneDataStorageMode_expectInvalidReconstructHistoricStatesValue() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .eth2NetworkConfig(b -> b.customGenesisState(GENESIS_STATE))
                    .storageConfiguration(b -> b.dataStorageMode(PRUNE))
                    .sync(b -> b.reconstructHistoricStatesEnabled(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Cannot reconstruct historic states without using ARCHIVE data storage mode");
  }

  @Test
  void shouldSetBlockPruningOptions() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--data-storage-mode=MINIMAL",
            "--Xdata-storage-block-pruning-interval=150",
            "--Xdata-storage-block-pruning-limit=50");
    assertThat(config.storageConfiguration().getDataStorageMode()).isEqualTo(MINIMAL);
    assertThat(config.storageConfiguration().getBlockPruningInterval())
        .isEqualTo(Duration.ofSeconds(150));
    assertThat(config.storageConfiguration().getBlockPruningLimit()).isEqualTo(50);
  }

  @Test
  void shouldSetBlobsPruningOptions() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xdata-storage-blobs-pruning-interval=55", "--Xdata-storage-blobs-pruning-limit=10");
    assertThat(config.storageConfiguration().getBlobsPruningInterval())
        .isEqualTo(Duration.ofSeconds(55));
    assertThat(config.storageConfiguration().getBlobsPruningLimit()).isEqualTo(10);
  }

  @Test
  void shouldSetBlobsPruningArchivePath() {
    // path needs to exist.
    String someTempPath = Files.temporaryFolderPath();
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xdata-storage-blobs-archive-path=" + someTempPath);

    assertThat(config.storageConfiguration().getBlobsArchivePath())
        .isPresent()
        .get()
        .isEqualTo(someTempPath);
  }

  @Test
  void shouldNotAllowPruningBlocksAndReconstructingStates() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .storageConfiguration(b -> b.dataStorageMode(MINIMAL))
                    .sync(b -> b.reconstructHistoricStatesEnabled(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Cannot reconstruct historic states without using ARCHIVE data storage mode");
  }

  @Test
  void debugDataDumpingEnabled_shouldDefaultFalse() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments();
    assertThat(tekuConfig.dataConfig().isDebugDataDumpingEnabled()).isEqualTo(false);
  }

  @Test
  void shouldSetDebugDataDumpingEnabled() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--Xdebug-data-dumping-enabled=true");
    assertThat(tekuConfig.dataConfig().isDebugDataDumpingEnabled()).isEqualTo(true);
  }

  @Test
  void rocksdbBlobDbEnabled_shouldDefaultToFalse() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments();
    assertThat(tekuConfig.storageConfiguration().isRocksdbBlobDbEnabled()).isEqualTo(false);
  }

  @Test
  void rocksdbBlobDbEnabled_shouldAcceptTrue() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--Xdata-storage-rocksdb-blob-db-enabled=true");
    assertThat(tekuConfig.storageConfiguration().isRocksdbBlobDbEnabled()).isEqualTo(true);
  }

  @Test
  void rocksdbBlobDbEnabled_shouldAcceptFalse() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--Xdata-storage-rocksdb-blob-db-enabled=false");
    assertThat(tekuConfig.storageConfiguration().isRocksdbBlobDbEnabled()).isEqualTo(false);
  }

  @Test
  void rocksdbBlobDbEnabled_shouldUseFallbackValue() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--Xdata-storage-rocksdb-blob-db-enabled");
    assertThat(tekuConfig.storageConfiguration().isRocksdbBlobDbEnabled()).isEqualTo(true);
  }

  @Test
  void forceClearDb_shouldDefaultToFalse() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments();
    assertThat(tekuConfig.storageConfiguration().isForceClearDb()).isEqualTo(false);
  }

  @Test
  void forceClearDb_shouldAcceptTrue() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments("--force-clear-db=true");
    assertThat(tekuConfig.storageConfiguration().isForceClearDb()).isEqualTo(true);
  }

  @Test
  void forceClearDb_shouldAcceptFalse() {
    final TekuConfiguration tekuConfig =
        getTekuConfigurationFromArguments("--force-clear-db=false");
    assertThat(tekuConfig.storageConfiguration().isForceClearDb()).isEqualTo(false);
  }

  @Test
  void forceClearDb_shouldUseFallbackValue() {
    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments("--force-clear-db");
    assertThat(tekuConfig.storageConfiguration().isForceClearDb()).isEqualTo(true);
  }
}

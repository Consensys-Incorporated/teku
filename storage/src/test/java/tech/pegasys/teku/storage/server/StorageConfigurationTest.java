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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static tech.pegasys.teku.storage.server.StateStorageMode.ARCHIVE;
import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.NOT_SET;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;
import static tech.pegasys.teku.storage.server.VersionedDatabaseFactory.STORAGE_MODE_FILENAME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class StorageConfigurationTest {

  final Spec spec = TestSpecFactory.createMinimalPhase0();
  final Eth1Address eth1Address =
      Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC");

  public static Stream<Arguments> getStateStorageDefaultScenarios() {
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(false, Optional.empty(), NOT_SET, MINIMAL));
    args.add(Arguments.of(false, Optional.empty(), ARCHIVE, ARCHIVE));
    args.add(Arguments.of(false, Optional.empty(), MINIMAL, MINIMAL));
    args.add(Arguments.of(false, Optional.empty(), PRUNE, PRUNE));
    args.add(Arguments.of(true, Optional.empty(), NOT_SET, PRUNE));
    args.add(Arguments.of(true, Optional.of(ARCHIVE), NOT_SET, ARCHIVE));
    args.add(Arguments.of(true, Optional.of(PRUNE), ARCHIVE, ARCHIVE));
    args.add(Arguments.of(true, Optional.of(ARCHIVE), MINIMAL, MINIMAL));
    return args.stream();
  }

  // a previously used version that is recorded will be used if the requested mode is NOT_SET.
  // if a mode is specified, it should be used, regardless of what was previously used
  // not set has 2 meanings - basically there's a smart default taking place to handle the old
  // default of prune, if the data-storage is already present
  @ParameterizedTest
  @MethodSource("getStateStorageDefaultScenarios")
  void shouldDetermineCorrectStorageModeGivenInputs(
      final boolean isExistingStore,
      final Optional<StateStorageMode> maybePreviousStorageMode,
      final StateStorageMode requestedMode,
      final StateStorageMode expectedResult) {

    assertThat(
            StorageConfiguration.determineStorageDefault(
                isExistingStore, maybePreviousStorageMode, requestedMode))
        .isEqualTo(expectedResult);
  }

  @Test
  public void shouldFailIfDatabaseStorageModeFileIsInvalidAndNoExplicitOptionIsSet(
      @TempDir final Path dir) throws IOException {
    createInvalidStorageModeFile(dir);
    final DataConfig dataConfig = DataConfig.builder().beaconDataPath(dir).build();

    final StorageConfiguration.Builder storageConfigBuilder =
        StorageConfiguration.builder()
            .specProvider(spec)
            .dataConfig(dataConfig)
            .eth1DepositContract(eth1Address);

    assertThatThrownBy(storageConfigBuilder::build).isInstanceOf(DatabaseStorageException.class);
  }

  @Test
  public void shouldSucceedIfDatabaseStorageModeFileIsInvalidAndExplicitOptionIsSet(
      @TempDir final Path dir) throws IOException {
    createInvalidStorageModeFile(dir);
    final DataConfig dataConfig = DataConfig.builder().beaconDataPath(dir).build();

    final StorageConfiguration storageConfig =
        StorageConfiguration.builder()
            .specProvider(spec)
            .dataConfig(dataConfig)
            .eth1DepositContract(eth1Address)
            .dataStorageMode(ARCHIVE)
            .build();

    assertThat(storageConfig.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void shouldFailIfUserConfiguresStatePruneSlotsRetainedLowerThanAllowed() {

    assertThatThrownBy(
            () -> StorageConfiguration.builder().dataStorageMode(ARCHIVE).retainedSlots(-1))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Invalid number of slots to retain finalized states for");
  }

  @Test
  public void shouldFailIfUserConfiguresStatePrunerLowerThanAllowed() {

    assertThatThrownBy(
            () ->
                StorageConfiguration.builder()
                    .dataStorageMode(ARCHIVE)
                    .statePruningInterval(Duration.ofSeconds(1)))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "Block pruning interval must be a value between 30 seconds and 1 day");
  }

  @Test
  public void shouldFailIfUserConfiguresStatePrunerIntervalGreaterThanAllowed() {

    assertThatThrownBy(
            () ->
                StorageConfiguration.builder()
                    .dataStorageMode(ARCHIVE)
                    .statePruningInterval(Duration.ofDays(2)))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "Block pruning interval must be a value between 30 seconds and 1 day");
  }

  @Test
  public void shouldFailIfUserConfiguresStatePrunerLimitGreaterThanAllowed() {

    assertThatThrownBy(
            () -> StorageConfiguration.builder().dataStorageMode(ARCHIVE).statePruningLimit(101))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Invalid statePruningLimit:");
  }

  private static void createInvalidStorageModeFile(final Path dir) throws IOException {
    // An empty storage mode path is invalid
    Files.createFile(dir.resolve(STORAGE_MODE_FILENAME));
  }

  @Test
  public void shouldFailIfUserEnableStatePrunerUsingTreeMode() {
    assertThatThrownBy(
            () ->
                StorageConfiguration.builder()
                    .dataStorageMode(ARCHIVE)
                    .dataStorageFrequency(1)
                    .retainedSlots(2048)
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("State pruner cannot be enabled using tree mode storage");
  }
}

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

package tech.pegasys.teku.services.chainstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedConstruction;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.pruner.DataColumnSidecarPruner;
import tech.pegasys.teku.storage.server.pruner.StatePruner;

class StorageServiceTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final StorageConfiguration storageConfiguration = mock(StorageConfiguration.class);
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final DataDirLayout dataDirLayout = mock(DataDirLayout.class);
  private final Eth1Address eth1DepositContract = mock(Eth1Address.class);
  private final Spec spec = mock(Spec.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private StorageService storageService;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    when(serviceConfig.getMetricsSystem()).thenReturn(metricsSystem);
    when(dataDirLayout.getBeaconDataDirectory()).thenReturn(tempDir);
    when(serviceConfig.getDataDirLayout()).thenReturn(dataDirLayout);
    when(storageConfiguration.getDataStorageCreateDbVersion()).thenReturn(DatabaseVersion.NOOP);
    when(storageConfiguration.getMaxKnownNodeCacheSize())
        .thenReturn(StorageConfiguration.DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE);
    when(storageConfiguration.getDataStorageFrequency())
        .thenReturn(StorageConfiguration.DEFAULT_STORAGE_FREQUENCY);
    when(storageConfiguration.getStatePruningLimit())
        .thenReturn(StorageConfiguration.DEFAULT_STATE_PRUNING_LIMIT);
    when(storageConfiguration.getStatePruningInterval())
        .thenReturn(StorageConfiguration.DEFAULT_STATE_PRUNING_INTERVAL);
    when(storageConfiguration.getEth1DepositContract()).thenReturn(eth1DepositContract);
    when(storageConfiguration.isStoreNonCanonicalBlocksEnabled()).thenReturn(false);
    when(storageConfiguration.getSpec()).thenReturn(spec);

    when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);

    final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();
    when(serviceConfig.getAsyncRunnerFactory()).thenReturn(asyncRunnerFactory);

    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();
    when(serviceConfig.createAsyncRunner(any(), anyInt(), anyInt(), anyInt()))
        .thenReturn(stubAsyncRunner);

    storageService =
        new StorageService(serviceConfig, storageConfiguration, false, false, Optional.empty());
  }

  @Test
  void shouldNotSetupStatePrunerWhenArchiveMode() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.ARCHIVE);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> maybeStatePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(maybeStatePruner).isEmpty();
  }

  @Test
  void shouldSetupStatePrunerWhenArchiveModeAndRetentionSlotsEnabled() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.ARCHIVE);
    when(storageConfiguration.getRetainedSlots()).thenReturn(5L);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> maybeStatePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(maybeStatePruner).isPresent();
    final StatePruner statePruner = maybeStatePruner.get();
    assertThat(statePruner.isRunning()).isTrue();
    assertThat(statePruner.getPruneInterval())
        .isEqualTo(StorageConfiguration.DEFAULT_STATE_PRUNING_INTERVAL);
  }

  @ParameterizedTest
  @EnumSource(
      value = StateStorageMode.class,
      names = {"PRUNE", "MINIMAL"})
  void shouldSetupStatePrunerWhenPruneMode(final StateStorageMode stateStorageMode) {
    when(storageConfiguration.getDataStorageMode()).thenReturn(stateStorageMode);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> maybeStatePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(maybeStatePruner).isPresent();
    final StatePruner statePruner = maybeStatePruner.get();
    assertThat(statePruner.isRunning()).isTrue();
    assertThat(statePruner.getPruneInterval()).isEqualTo(StorageService.STATE_PRUNING_INTERVAL);
  }

  @ParameterizedTest
  @EnumSource(
      value = StateStorageMode.class,
      names = {"PRUNE", "MINIMAL"})
  void shouldSetupStatePrunerWithCustomInterval(final StateStorageMode stateStorageMode) {
    when(storageConfiguration.getDataStorageMode()).thenReturn(stateStorageMode);
    final Duration customPruningInterval = Duration.ofSeconds(8);
    when(storageConfiguration.getStatePruningInterval()).thenReturn(customPruningInterval);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> maybeStatePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(maybeStatePruner).isPresent();
    final StatePruner statePruner = maybeStatePruner.get();
    assertThat(statePruner.isRunning()).isTrue();
    assertThat(statePruner.getPruneInterval()).isEqualTo(customPruningInterval);
  }

  @Test
  void shouldStartSuccessfullyWhenForceClearDbEnabled() {
    // Set forceClearDb to true
    when(storageConfiguration.isForceClearDb()).thenReturn(true);
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.PRUNE);

    // Service should start successfully even with forceClearDb enabled
    final SafeFuture<?> future = storageService.doStart();
    assertThat(future).isCompleted();
  }

  @Test
  void shouldNotifyPrunerAfterSidecarWriteWithoutEnablingArchival() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.ARCHIVE);
    when(spec.isMilestoneSupported(SpecMilestone.FULU)).thenReturn(true);
    try (final MockedConstruction<DataColumnSidecarPruner> construction =
        mockConstruction(
            DataColumnSidecarPruner.class,
            (pruner, context) -> when(pruner.start()).thenAnswer(__ -> SafeFuture.COMPLETE))) {
      assertThat(storageService.start()).isCompleted();
      assertThat(construction.constructed()).hasSize(1);
      final DataColumnSidecarPruner pruner = construction.constructed().getFirst();
      final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
      final UInt64 slot = UInt64.valueOf(12);
      when(sidecar.getSlot()).thenReturn(slot);

      assertThat(storageService.getChainStorage().onNewSidecar(sidecar)).isCompleted();

      verify(pruner).onSidecarStored(slot);
      verify(eventChannels, never()).subscribe(eq(SidecarArchivePrunableChannel.class), any());
    }
  }

  @Test
  void shouldAcceptSidecarWriteWithoutPruner() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.ARCHIVE);
    assertThat(storageService.start()).isCompleted();
    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    when(sidecar.getSlot()).thenReturn(UInt64.ONE);

    assertThat(storageService.getChainStorage().onNewSidecar(sidecar)).isCompleted();

    assertThat(storageService.getDataColumnSidecarPruner()).isEmpty();
  }
}

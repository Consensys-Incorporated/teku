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

package tech.pegasys.teku.storage.storageSystem;

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import tech.pegasys.teku.beacon.pow.api.TrackingEth1EventsChannel;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.RecentlyValidatedDataColumnSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlobSidecarProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.store.StoreConfig;

public class StorageSystem implements AutoCloseable {
  private final ChainBuilder chainBuilder;
  private final ChainUpdater chainUpdater;
  private final TrackingEth1EventsChannel eth1EventsChannel = new TrackingEth1EventsChannel();

  private final TrackingChainHeadChannel chainHeadChannel;
  private final StubMetricsSystem metricsSystem;
  private final RecentChainData recentChainData;
  private final StateStorageMode storageMode;
  private final CombinedChainDataClient combinedChainDataClient;
  private final ChainStorage chainStorage;
  private final Database database;
  private final RestartedStorageSupplier restartedSupplier;

  private final BlobSidecarManager blobSidecarManager;

  private StorageSystem(
      final StubMetricsSystem metricsSystem,
      final TrackingChainHeadChannel chainHeadChannel,
      final StateStorageMode storageMode,
      final ChainStorage chainStorage,
      final Database database,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final RestartedStorageSupplier restartedSupplier,
      final ChainBuilder chainBuilder,
      final BlobSidecarManager blobSidecarManager,
      final Spec spec) {
    this.metricsSystem = metricsSystem;
    this.chainStorage = chainStorage;
    this.recentChainData = recentChainData;
    this.chainHeadChannel = chainHeadChannel;
    this.storageMode = storageMode;
    this.database = database;
    this.combinedChainDataClient = combinedChainDataClient;
    this.restartedSupplier = restartedSupplier;
    this.blobSidecarManager = blobSidecarManager;

    this.chainBuilder = chainBuilder;
    this.chainUpdater =
        new ChainUpdater(
            this.recentChainData, this.chainBuilder, this.blobSidecarManager, spec, chainStorage);
  }

  static StorageSystem create(
      final Database database,
      final RestartedStorageSupplier restartedSupplier,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final Spec spec,
      final ChainBuilder chainBuilder,
      final int stateRebuildTimeoutSeconds) {
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();

    // Create and start storage server
    final ChainStorage chainStorageServer =
        ChainStorage.create(
            database,
            spec,
            storageMode,
            stateRebuildTimeoutSeconds,
            BlobSidecarsArchiver.NOOP,
            __ -> {});

    // Create recent chain data
    final FinalizedCheckpointChannel finalizedCheckpointChannel =
        new StubFinalizedCheckpointChannel();
    final TrackingChainHeadChannel chainHeadChannel = new TrackingChainHeadChannel();
    final RecentChainData recentChainData =
        StorageBackedRecentChainData.createImmediately(
            SYNC_RUNNER,
            metricsSystem,
            storeConfig,
            SingleBlockProvider.NOOP,
            SingleBlobSidecarProvider.NOOP,
            RecentlyValidatedDataColumnSlotProvider.NOOP,
            ExecutionPayloadProvider.NOOP,
            BlindedExecutionPayloadProvider.NOOP,
            chainStorageServer,
            chainStorageServer,
            chainStorageServer,
            finalizedCheckpointChannel,
            chainHeadChannel,
            ValidatorIsConnectedProvider.ALWAYS,
            spec);

    // Create combined client
    final CombinedChainDataClient combinedChainDataClient =
        new CombinedChainDataClient(recentChainData, chainStorageServer, spec);

    final BlobSidecarManager blobSidecarManager = BlobSidecarManager.NOOP;

    // Return storage system
    return new StorageSystem(
        metricsSystem,
        chainHeadChannel,
        storageMode,
        chainStorageServer,
        database,
        recentChainData,
        combinedChainDataClient,
        restartedSupplier,
        chainBuilder,
        blobSidecarManager,
        spec);
  }

  public StateAndBlockSummary getChainHead() {
    return safeJoin(recentChainData.getChainHead().orElseThrow().asStateAndBlockSummary());
  }

  public StubMetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public RecentChainData recentChainData() {
    return recentChainData;
  }

  public ChainBuilder chainBuilder() {
    return chainBuilder;
  }

  public ChainUpdater chainUpdater() {
    return chainUpdater;
  }

  public BlobSidecarManager blobSidecarManager() {
    return blobSidecarManager;
  }

  public DepositStorage createDepositStorage() {
    return DepositStorage.create(database);
  }

  public Database database() {
    return database;
  }

  public ChainStorage chainStorage() {
    return chainStorage;
  }

  public StorageSystem restarted() {
    return restarted(storageMode);
  }

  public StorageSystem restarted(final StateStorageMode storageMode) {
    try {
      database.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return restartedSupplier.restart(storageMode);
  }

  public CombinedChainDataClient combinedChainDataClient() {
    return combinedChainDataClient;
  }

  public TrackingChainHeadChannel chainHeadChannel() {
    return chainHeadChannel;
  }

  public TrackingEth1EventsChannel eth1EventsChannel() {
    return eth1EventsChannel;
  }

  @Override
  public void close() throws Exception {
    this.database.close();
  }

  public interface RestartedStorageSupplier {
    StorageSystem restart(final StateStorageMode storageMode);
  }
}

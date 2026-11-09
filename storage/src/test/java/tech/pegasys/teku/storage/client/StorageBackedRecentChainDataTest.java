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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.RecentlyValidatedDataColumnSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlobSidecarProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubChainHeadChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.store.StoreAssertions;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class StorageBackedRecentChainDataTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final BeaconState initialState =
      new DataStructureUtil(3, spec).randomBeaconState(UInt64.ZERO);

  private final StorageQueryChannel storageQueryChannel = mock(StorageQueryChannel.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final VoteUpdateChannel voteUpdateChannel = mock(VoteUpdateChannel.class);
  private final FinalizedCheckpointChannel finalizedCheckpointChannel =
      new StubFinalizedCheckpointChannel();
  private final ChainHeadChannel chainHeadChannel = new StubChainHeadChannel();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ValidatorIsConnectedProvider validatorIsConnectedProvider =
      ValidatorIsConnectedProvider.ALWAYS;

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequest()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<OnDiskStoreData>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(5).build();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            storeConfig,
            asyncRunner,
            SingleBlockProvider.NOOP,
            SingleBlobSidecarProvider.NOOP,
            RecentlyValidatedDataColumnSlotProvider.NOOP,
            ExecutionPayloadProvider.NOOP,
            BlindedExecutionPayloadProvider.NOOP,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();

    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store response to complete initialization
    final OnDiskStoreData storeData =
        StoreBuilder.forkChoiceStoreBuilder(
            spec, AnchorPoint.fromGenesisState(spec, initialState), UInt64.ZERO);
    storeRequestFuture.complete(Optional.of(storeData));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    final UpdatableStore expectedStore =
        StoreBuilder.create()
            .onDiskStoreData(storeData)
            .asyncRunner(asyncRunner)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(BlockProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .storeConfig(storeConfig)
            .build();
    StoreAssertions.assertStoresMatch(client.get().getStore(), expectedStore);
  }

  @Test
  public void storageBackedClient_storeInitializeWithTransitionedAnchor() throws Exception {
    SafeFuture<Optional<OnDiskStoreData>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            StoreConfig.createDefault(),
            asyncRunner,
            SingleBlockProvider.NOOP,
            SingleBlobSidecarProvider.NOOP,
            RecentlyValidatedDataColumnSlotProvider.NOOP,
            ExecutionPayloadProvider.NOOP,
            BlindedExecutionPayloadProvider.NOOP,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    chainBuilder.generateGenesis();
    final UInt64 checkpointStartSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState anchorBlock =
        chainBuilder.generateBlockAtSlot(checkpointStartSlot.minus(2));
    final BeaconState anchorState = spec.processSlots(anchorBlock.getState(), checkpointStartSlot);
    final Checkpoint checkpoint =
        new Checkpoint(spec.computeNextEpochBoundary(anchorState.getSlot()), anchorBlock.getRoot());
    final AnchorPoint anchorPoint =
        AnchorPoint.create(spec, checkpoint, anchorState, Optional.of(anchorBlock.getBlock()));

    storeRequestFuture.complete(
        Optional.of(StoreBuilder.forkChoiceStoreBuilder(spec, anchorPoint, UInt64.ZERO)));

    assertThat(client).isCompleted();
    final RecentChainData initializedClient = client.get();
    assertThat(initializedClient.getSlotForBlockRoot(anchorBlock.getRoot()))
        .contains(anchorBlock.getSlot());
    assertThat(
            initializedClient.getBlockRootInEffectBySlot(
                anchorBlock.getSlot(), anchorBlock.getRoot()))
        .contains(anchorBlock.getRoot());
    assertThat(
            initializedClient.getBlockRootInEffectBySlot(
                anchorState.getSlot(), anchorBlock.getRoot()))
        .contains(anchorBlock.getRoot());
  }

  @Test
  public void storageBackedClient_storeInitializeViaNewGenesisState()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<OnDiskStoreData>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest()).thenReturn(storeRequestFuture);

    final StoreConfig storeConfig =
        StoreConfig.builder().hotStatePersistenceFrequencyInEpochs(5).build();
    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            storeConfig,
            asyncRunner,
            SingleBlockProvider.NOOP,
            SingleBlobSidecarProvider.NOOP,
            RecentlyValidatedDataColumnSlotProvider.NOOP,
            ExecutionPayloadProvider.NOOP,
            BlindedExecutionPayloadProvider.NOOP,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store event to complete initialization
    storeRequestFuture.complete(Optional.empty());
    assertThat(client).isCompleted();
    assertStoreNotInitialized(client.get());
    assertThat(client.get().getStore()).isNull();

    // Now set the genesis state
    final UpdatableStore genesisStore =
        StoreBuilder.create()
            .onDiskStoreData(
                StoreBuilder.forkChoiceStoreBuilder(
                    spec, AnchorPoint.fromGenesisState(spec, initialState), UInt64.ZERO))
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(BlockProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP)
            .storeConfig(storeConfig)
            .build();
    client.get().initializeFromGenesis(initialState, UInt64.ZERO);
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    StoreAssertions.assertStoresMatch(client.get().getStore(), genesisStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequestAfterTimeout()
      throws ExecutionException, InterruptedException {
    SafeFuture<Optional<OnDiskStoreData>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest())
        .thenReturn(SafeFuture.failedFuture(new TimeoutException()))
        .thenReturn(storeRequestFuture);

    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            StoreConfig.createDefault(),
            asyncRunner,
            SingleBlockProvider.NOOP,
            SingleBlobSidecarProvider.NOOP,
            RecentlyValidatedDataColumnSlotProvider.NOOP,
            ExecutionPayloadProvider.NOOP,
            BlindedExecutionPayloadProvider.NOOP,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();

    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();

    // Now set the genesis state
    final OnDiskStoreData storeData =
        StoreBuilder.forkChoiceStoreBuilder(
            spec, AnchorPoint.fromGenesisState(spec, initialState), UInt64.ZERO);
    final StoreBuilder genesisStoreBuilder =
        StoreBuilder.create()
            .onDiskStoreData(storeData)
            .asyncRunner(SYNC_RUNNER)
            .metricsSystem(new StubMetricsSystem())
            .specProvider(spec)
            .blockProvider(BlockProvider.NOOP)
            .stateProvider(StateAndBlockSummaryProvider.NOOP);
    storeRequestFuture.complete(Optional.of(storeData));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    StoreAssertions.assertStoresMatch(client.get().getStore(), genesisStoreBuilder.build());
  }

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequestAfterIOException() {
    SafeFuture<Optional<OnDiskStoreData>> storeRequestFuture = new SafeFuture<>();
    when(storageQueryChannel.onStoreRequest())
        .thenReturn(SafeFuture.failedFuture(new IOException()))
        .thenReturn(storeRequestFuture);

    final SafeFuture<RecentChainData> client =
        StorageBackedRecentChainData.create(
            new StubMetricsSystem(),
            StoreConfig.createDefault(),
            asyncRunner,
            SingleBlockProvider.NOOP,
            SingleBlobSidecarProvider.NOOP,
            RecentlyValidatedDataColumnSlotProvider.NOOP,
            ExecutionPayloadProvider.NOOP,
            BlindedExecutionPayloadProvider.NOOP,
            storageQueryChannel,
            storageUpdateChannel,
            voteUpdateChannel,
            finalizedCheckpointChannel,
            chainHeadChannel,
            validatorIsConnectedProvider,
            spec);

    // We should have posted a request to get the store from storage
    verify(storageQueryChannel).onStoreRequest();

    assertThat(client).isCompletedExceptionally();
  }

  private void assertStoreInitialized(final RecentChainData client) {
    final AtomicBoolean initialized = new AtomicBoolean(false);
    client.subscribeStoreInitialized(() -> initialized.set(true));
    assertThat(initialized).isTrue();
  }

  private void assertStoreNotInitialized(final RecentChainData client) {
    final AtomicBoolean initialized = new AtomicBoolean(false);
    client.subscribeStoreInitialized(() -> initialized.set(true));
    assertThat(initialized).isFalse();
  }

  private void assertStoreIsSet(final RecentChainData client) {
    assertThat(client.getStore()).isNotNull();

    // With a store set, we shouldn't be allowed to overwrite the store by setting the genesis state
    assertThatThrownBy(() -> client.initializeFromGenesis(initialState, UInt64.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Failed to initialize from state: store has already been initialized");
  }
}

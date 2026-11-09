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

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.RecentlyValidatedDataColumnSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlobSidecarProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubChainHeadChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.store.StoreConfig;

public class MemoryOnlyRecentChainData extends RecentChainData {

  private MemoryOnlyRecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final ValidatorIsConnectedProvider validatorIsConnectedProvider,
      final Spec spec) {
    super(
        asyncRunner,
        metricsSystem,
        storeConfig,
        BlockProvider.NOOP,
        ExecutionPayloadProvider.NOOP,
        BlindedExecutionPayloadProvider.NOOP,
        SingleBlockProvider.NOOP,
        SingleBlobSidecarProvider.NOOP,
        RecentlyValidatedDataColumnSlotProvider.NOOP,
        StateAndBlockSummaryProvider.NOOP,
        EarliestBlobSidecarSlotProvider.NOOP,
        storageUpdateChannel,
        voteUpdateChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        validatorIsConnectedProvider,
        spec);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static RecentChainData create() {
    return builder().build();
  }

  public static RecentChainData create(final Spec spec) {
    return builder().specProvider(spec).build();
  }

  public static RecentChainData create(final ChainHeadChannel chainHeadChannel) {
    return builder().reorgEventChannel(chainHeadChannel).build();
  }

  public static class Builder {
    private StoreConfig storeConfig = StoreConfig.createDefault();
    private Spec spec = TestSpecFactory.createMinimalPhase0();
    private StorageUpdateChannel storageUpdateChannel = new StubStorageUpdateChannel();
    private FinalizedCheckpointChannel finalizedCheckpointChannel =
        new StubFinalizedCheckpointChannel();
    private ChainHeadChannel chainHeadChannel = new StubChainHeadChannel();

    private ValidatorIsConnectedProvider validatorIsConnectedProvider =
        ValidatorIsConnectedProvider.ALWAYS;

    public RecentChainData build() {
      return new MemoryOnlyRecentChainData(
          SYNC_RUNNER,
          new NoOpMetricsSystem(),
          storeConfig,
          storageUpdateChannel,
          votes -> {},
          finalizedCheckpointChannel,
          chainHeadChannel,
          validatorIsConnectedProvider,
          spec);
    }

    public Builder storeConfig(final StoreConfig storeConfig) {
      checkNotNull(storeConfig);
      this.storeConfig = storeConfig;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
    }

    public Builder storageUpdateChannel(final StorageUpdateChannel storageUpdateChannel) {
      checkNotNull(storageUpdateChannel);
      this.storageUpdateChannel = storageUpdateChannel;
      return this;
    }

    public Builder validatorIsConnectedProvider(
        final ValidatorIsConnectedProvider validatorIsConnectedProvider) {
      this.validatorIsConnectedProvider = validatorIsConnectedProvider;
      return this;
    }

    public Builder finalizedCheckpointChannel(
        final FinalizedCheckpointChannel finalizedCheckpointChannel) {
      checkNotNull(finalizedCheckpointChannel);
      this.finalizedCheckpointChannel = finalizedCheckpointChannel;
      return this;
    }

    public Builder reorgEventChannel(final ChainHeadChannel chainHeadChannel) {
      checkNotNull(chainHeadChannel);
      this.chainHeadChannel = chainHeadChannel;
      return this;
    }
  }
}

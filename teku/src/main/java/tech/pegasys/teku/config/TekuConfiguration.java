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

package tech.pegasys.teku.config;

import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig.MetricsConfigBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.nat.NatConfiguration;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.config.WireLogsConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFactory;
import tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.services.zkchain.ZkChainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.NetworkingSpecConfigDeneb;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidCircuitBreakerFactory;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.InteropConfig.InteropConfigBuilder;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientConfiguration;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class TekuConfiguration {
  private final Eth2NetworkConfiguration eth2NetworkConfiguration;
  private final StorageConfiguration storageConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final DataConfig dataConfig;
  private final MetricsConfig metricsConfig;
  private final BeaconChainConfiguration beaconChainConfig;
  private final ValidatorClientConfiguration validatorClientConfig;
  private final PowchainConfiguration powchainConfiguration;
  private final ExecutionLayerConfiguration executionLayerConfiguration;
  private final NatConfiguration natConfiguration;
  private final ValidatorRestApiConfig validatorRestApiConfig;
  private final ZkChainConfiguration zkChainConfiguration;
  private final BeaconNodeConfig beaconNodeConfig;

  private TekuConfiguration(
      final Eth2NetworkConfiguration eth2NetworkConfiguration,
      final Spec spec,
      final StorageConfiguration storageConfiguration,
      final WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig,
      final PowchainConfiguration powchainConfiguration,
      final ExecutionLayerConfiguration executionLayerConfiguration,
      final InteropConfig interopConfig,
      final DataConfig dataConfig,
      final P2PConfig p2PConfig,
      final SyncConfig syncConfig,
      final BeaconRestApiConfig beaconRestApiConfig,
      final MetricsConfig metricsConfig,
      final StoreConfig storeConfig,
      final NatConfiguration natConfiguration,
      final ValidatorRestApiConfig validatorRestApiConfig,
      final BeaconChainControllerFactory beaconChainControllerFactory,
      final ZkChainConfiguration zkChainConfiguration,
      final BeaconNodeConfig beaconNodeConfig) {
    this.eth2NetworkConfiguration = eth2NetworkConfiguration;
    this.storageConfiguration = storageConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.powchainConfiguration = powchainConfiguration;
    this.executionLayerConfiguration = executionLayerConfiguration;
    this.dataConfig = dataConfig;
    this.metricsConfig = metricsConfig;
    this.beaconChainConfig =
        new BeaconChainConfiguration(
            eth2NetworkConfiguration,
            weakSubjectivityConfig,
            validatorConfig,
            interopConfig,
            p2PConfig,
            syncConfig,
            beaconRestApiConfig,
            powchainConfiguration,
            createExecutionPayloadBidCircuitBreakerFactory(spec, executionLayerConfiguration),
            executionLayerConfiguration,
            storeConfig,
            spec,
            beaconChainControllerFactory,
            metricsConfig,
            zkChainConfiguration);
    this.validatorClientConfig =
        new ValidatorClientConfiguration(
            validatorConfig, interopConfig, validatorRestApiConfig, spec);
    this.natConfiguration = natConfiguration;
    this.validatorRestApiConfig = validatorRestApiConfig;
    this.zkChainConfiguration = zkChainConfiguration;
    this.beaconNodeConfig = beaconNodeConfig;
  }

  public static Builder builder() {
    return new Builder();
  }

  private static ExecutionPayloadBidCircuitBreakerFactory
      createExecutionPayloadBidCircuitBreakerFactory(
          final Spec spec, final ExecutionLayerConfiguration executionLayerConfiguration) {
    if (!executionLayerConfiguration.isBuilderCircuitBreakerEnabled()) {
      return ExecutionPayloadBidCircuitBreakerFactory.NOOP;
    }
    return ExecutionPayloadBidCircuitBreakerFactory.create(
        spec,
        executionLayerConfiguration.getBuilderCircuitBreakerWindow(),
        executionLayerConfiguration.getBuilderCircuitBreakerAllowedFaults(),
        executionLayerConfiguration.getBuilderCircuitBreakerAllowedConsecutiveFaults());
  }

  public Eth2NetworkConfiguration eth2NetworkConfiguration() {
    return eth2NetworkConfiguration;
  }

  public StorageConfiguration storageConfiguration() {
    return storageConfiguration;
  }

  public WeakSubjectivityConfig weakSubjectivity() {
    return weakSubjectivityConfig;
  }

  public BeaconChainConfiguration beaconChain() {
    return beaconChainConfig;
  }

  public ValidatorClientConfiguration validatorClient() {
    return validatorClientConfig;
  }

  public P2PConfig p2p() {
    return beaconChainConfig.p2pConfig();
  }

  public DiscoveryConfig discovery() {
    return p2p().getDiscoveryConfig();
  }

  public NetworkConfig network() {
    return p2p().getNetworkConfig();
  }

  public SyncConfig sync() {
    return beaconChain().syncConfig();
  }

  public PowchainConfiguration powchain() {
    return powchainConfiguration;
  }

  public ExecutionLayerConfiguration executionLayer() {
    return executionLayerConfiguration;
  }

  public DataConfig dataConfig() {
    return dataConfig;
  }

  public MetricsConfig metricsConfig() {
    return metricsConfig;
  }

  public NatConfiguration natConfiguration() {
    return natConfiguration;
  }

  public ValidatorRestApiConfig validatorRestApiConfig() {
    return validatorRestApiConfig;
  }

  public ZkChainConfiguration zkChainConfiguration() {
    return zkChainConfiguration;
  }

  public BeaconNodeConfig beaconNodeConfig() {
    return beaconNodeConfig;
  }

  public static class Builder {
    private final Eth2NetworkConfiguration.Builder eth2NetworkConfigurationBuilder =
        Eth2NetworkConfiguration.builder().applyMainnetNetworkDefaults();
    private final StorageConfiguration.Builder storageConfigurationBuilder =
        StorageConfiguration.builder();
    private final WeakSubjectivityConfig.Builder weakSubjectivityBuilder =
        WeakSubjectivityConfig.builder();
    private final ValidatorConfig.Builder validatorConfigBuilder = ValidatorConfig.builder();
    private final PowchainConfiguration.Builder powchainConfigBuilder =
        PowchainConfiguration.builder();
    private final ZkChainConfiguration.Builder zkChainConfigBuilder =
        ZkChainConfiguration.builder();
    private final ExecutionLayerConfiguration.Builder executionLayerConfigBuilder =
        ExecutionLayerConfiguration.builder();
    private final InteropConfig.InteropConfigBuilder interopConfigBuilder = InteropConfig.builder();
    private final DataConfig.Builder dataConfigBuilder = DataConfig.builder();
    private final P2PConfig.Builder p2pConfigBuilder = P2PConfig.builder();
    private final SyncConfig.Builder syncConfigBuilder = SyncConfig.builder();
    private final BeaconRestApiConfig.BeaconRestApiConfigBuilder restApiBuilder =
        BeaconRestApiConfig.builder();
    private final ValidatorRestApiConfig.ValidatorRestApiConfigBuilder
        validatorRestApiConfigBuilder = ValidatorRestApiConfig.builder();
    private final MetricsConfig.MetricsConfigBuilder metricsConfigBuilder = MetricsConfig.builder();
    private final NatConfiguration.Builder natConfigBuilder = NatConfiguration.builder();
    private final StoreConfig.Builder storeConfigBuilder = StoreConfig.builder();

    private final BeaconNodeConfig.Builder beaconNodeConfigBuilder = BeaconNodeConfig.builder();

    private BeaconChainControllerFactory beaconChainControllerFactory =
        BeaconChainControllerFactory.DEFAULT;

    private Builder() {}

    public TekuConfiguration build() {
      // Create spec, and pass spec to other builders that require it
      final Eth2NetworkConfiguration eth2NetworkConfiguration =
          eth2NetworkConfigurationBuilder.build();
      final Spec spec = eth2NetworkConfiguration.getSpec();
      final DataConfig dataConfig = dataConfigBuilder.build();

      // Add specs
      interopConfigBuilder.specProvider(spec);
      storageConfigurationBuilder.specProvider(spec);
      weakSubjectivityBuilder.specProvider(spec);
      p2pConfigBuilder.specProvider(spec);
      powchainConfigBuilder.specProvider(spec);
      executionLayerConfigBuilder.specProvider(spec);

      Eth1Address depositContractAddress = eth2NetworkConfiguration.getEth1DepositContractAddress();
      Optional<UInt64> depositContractDeployBlock =
          eth2NetworkConfiguration.getEth1DepositContractDeployBlock();
      storageConfigurationBuilder.eth1DepositContractDefault(depositContractAddress);
      storageConfigurationBuilder.dataConfig(dataConfig);
      powchainConfigBuilder.depositContractDefault(depositContractAddress);
      powchainConfigBuilder.depositContractDeployBlockDefault(depositContractDeployBlock);
      p2pConfigBuilder.discovery(
          b -> b.bootnodesDefault(eth2NetworkConfiguration.getDiscoveryBootnodes()));
      restApiBuilder.eth1DepositContractAddressDefault(depositContractAddress);

      final ValidatorConfig validatorConfig = validatorConfigBuilder.build();

      final P2PConfig p2PConfig = p2pConfigBuilder.build();
      syncConfigBuilder.isSyncEnabledDefault(p2PConfig.getNetworkConfig().isEnabled());

      final StorageConfiguration storageConfiguration = storageConfigurationBuilder.build();
      // the storageConfiguration may change the dataStorage mode, so make sure any change is
      // reflected in the sync config builder
      syncConfigBuilder.fetchAllHistoricBlocks(
          storageConfiguration.getDataStorageMode().storesAllBlocks());

      final SyncConfig syncConfig = syncConfigBuilder.build();

      final ZkChainConfiguration zkChainConfiguration = zkChainConfigBuilder.build();

      final long maxAllowedBatchSize =
          spec.getNetworkingConfigDeneb()
              .map(NetworkingSpecConfigDeneb::getMaxRequestBlocksDeneb)
              .orElse(spec.getNetworkingConfig().getMaxRequestBlocks());

      if (syncConfig.getForwardSyncBatchSize() > maxAllowedBatchSize) {
        throw new InvalidConfigurationException(
            "Forward sync batch size cannot be greater than " + maxAllowedBatchSize);
      }

      if (syncConfig.getHistoricalSyncBatchSize() > maxAllowedBatchSize) {
        throw new InvalidConfigurationException(
            "Historical sync batch size cannot be greater than " + maxAllowedBatchSize);
      }

      // Check for invalid config settings
      if (syncConfig.isReconstructHistoricStatesEnabled()
          && eth2NetworkConfiguration.getNetworkBoostrapConfig().getGenesisState().isEmpty()) {
        throw new InvalidConfigurationException(
            "Genesis state required when reconstructing historic states");
      }

      if (syncConfig.isReconstructHistoricStatesEnabled()) {
        if (!storageConfiguration.getDataStorageMode().storesFinalizedStates()
            || !storageConfiguration.getDataStorageMode().storesAllBlocks()) {
          throw new InvalidConfigurationException(
              "Cannot reconstruct historic states without using ARCHIVE data storage mode");
        }
      }

      if (syncConfig.isReconstructHistoricStatesEnabled()) {
        if (storageConfiguration.getRetainedSlots() > 0) {
          throw new InvalidConfigurationException(
              "Cannot reconstruct historic states with state pruning enabled");
        }
      }

      if (storageConfiguration.getRetainedSlots() > 0) {
        if (!storageConfiguration.getDataStorageMode().storesFinalizedStates()) {
          throw new InvalidConfigurationException(
              "Cannot enable state pruning without using ARCHIVE data storage mode");
        }
      }

      if (syncConfig.isReconstructHistoricStatesEnabled()) {
        if (storageConfiguration.getRetainedSlots() > 0) {
          throw new InvalidConfigurationException(
              "Cannot reconstruct historic states with state pruning enabled");
        }
      }

      if (storageConfiguration.getRetainedSlots() > 0
          && storageConfiguration.getRetainedSlots()
              < storageConfiguration.getDataStorageFrequency()) {
        throw new InvalidConfigurationException(
            "Frequency of storing finalized states must be less than or equal to the number of retained slots");
      }

      return new TekuConfiguration(
          eth2NetworkConfiguration,
          spec,
          storageConfiguration,
          weakSubjectivityBuilder.build(),
          validatorConfig,
          powchainConfigBuilder.build(),
          executionLayerConfigBuilder.build(),
          interopConfigBuilder.build(),
          dataConfig,
          p2PConfig,
          syncConfig,
          restApiBuilder.build(),
          metricsConfigBuilder.build(),
          storeConfigBuilder.build(),
          natConfigBuilder.build(),
          validatorRestApiConfigBuilder.build(),
          beaconChainControllerFactory,
          zkChainConfiguration,
          beaconNodeConfigBuilder.build());
    }

    public Builder eth2NetworkConfig(final Consumer<Eth2NetworkConfiguration.Builder> consumer) {
      consumer.accept(eth2NetworkConfigurationBuilder);
      return this;
    }

    public Builder natConfig(final Consumer<NatConfiguration.Builder> consumer) {
      consumer.accept(natConfigBuilder);
      return this;
    }

    public Builder storageConfiguration(final Consumer<StorageConfiguration.Builder> consumer) {
      consumer.accept(storageConfigurationBuilder);
      return this;
    }

    public Builder weakSubjectivity(
        final Consumer<WeakSubjectivityConfig.Builder> wsConfigConsumer) {
      wsConfigConsumer.accept(weakSubjectivityBuilder);
      return this;
    }

    public Builder validator(final Consumer<ValidatorConfig.Builder> validatorConfigConsumer) {
      validatorConfigConsumer.accept(validatorConfigBuilder);
      return this;
    }

    public Builder powchain(final Consumer<PowchainConfiguration.Builder> consumer) {
      consumer.accept(powchainConfigBuilder);
      return this;
    }

    public Builder zkchain(final Consumer<ZkChainConfiguration.Builder> consumer) {
      consumer.accept(zkChainConfigBuilder);
      return this;
    }

    public Builder executionLayer(final Consumer<ExecutionLayerConfiguration.Builder> consumer) {
      consumer.accept(executionLayerConfigBuilder);
      return this;
    }

    public Builder interop(final Consumer<InteropConfigBuilder> interopConfigBuilderConsumer) {
      interopConfigBuilderConsumer.accept(interopConfigBuilder);
      return this;
    }

    public Builder data(final Consumer<DataConfig.Builder> dataConfigConsumer) {
      dataConfigConsumer.accept(dataConfigBuilder);
      return this;
    }

    public Builder p2p(final Consumer<P2PConfig.Builder> p2pConfigConsumer) {
      p2pConfigConsumer.accept(p2pConfigBuilder);
      return this;
    }

    public Builder discovery(final Consumer<DiscoveryConfig.Builder> consumer) {
      p2p(b -> b.discovery(consumer));
      return this;
    }

    public Builder network(final Consumer<NetworkConfig.Builder> consumer) {
      p2p(b -> b.network(consumer));
      return this;
    }

    public Builder wireLogs(final Consumer<WireLogsConfig.Builder> consumer) {
      p2p(b -> b.network(n -> n.wireLogs(consumer)));
      return this;
    }

    public Builder sync(final Consumer<SyncConfig.Builder> consumer) {
      consumer.accept(syncConfigBuilder);
      return this;
    }

    public Builder restApi(
        final Consumer<BeaconRestApiConfig.BeaconRestApiConfigBuilder>
            beaconRestApiConfigConsumer) {
      beaconRestApiConfigConsumer.accept(restApiBuilder);
      return this;
    }

    public Builder validatorApi(
        final Consumer<ValidatorRestApiConfig.ValidatorRestApiConfigBuilder> validatorApiConsumer) {
      validatorApiConsumer.accept(validatorRestApiConfigBuilder);
      return this;
    }

    public Builder metrics(final Consumer<MetricsConfigBuilder> metricsConfigBuilderConsumer) {
      metricsConfigBuilderConsumer.accept(metricsConfigBuilder);
      return this;
    }

    public Builder store(final Consumer<StoreConfig.Builder> storeConfigBuilderConsumer) {
      storeConfigBuilderConsumer.accept(storeConfigBuilder);
      return this;
    }

    public Builder beaconChainControllerFactory(
        final BeaconChainControllerFactory beaconChainControllerFactory) {
      this.beaconChainControllerFactory = beaconChainControllerFactory;
      return this;
    }

    public Builder beaconNode(final Consumer<BeaconNodeConfig.Builder> consumer) {
      consumer.accept(beaconNodeConfigBuilder);
      return this;
    }
  }
}

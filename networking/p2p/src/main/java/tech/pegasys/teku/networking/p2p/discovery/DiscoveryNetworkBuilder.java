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

package tech.pegasys.teku.networking.p2p.discovery;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.discovery.noop.NoOpDiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.KeyValueStore;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class DiscoveryNetworkBuilder {

  public static DiscoveryNetworkBuilder create() {
    return new DiscoveryNetworkBuilder();
  }

  protected MetricsSystem metricsSystem;
  protected AsyncRunner asyncRunner;
  protected KeyValueStore<String, Bytes> kvStore;
  protected P2PNetwork<?> p2pNetwork;
  protected PeerPools peerPools;
  protected PeerSelectionStrategy peerSelectionStrategy;
  protected DiscoveryConfig discoveryConfig;
  protected NetworkConfig p2pConfig;
  protected Spec spec;
  protected SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier;
  protected TimeProvider timeProvider;

  protected DiscoveryService discoveryService;
  protected ConnectionManager connectionManager;

  protected DiscoveryNetworkBuilder() {}

  protected void initMissingDefaults() {
    if (discoveryService == null) {
      discoveryService = createDiscoveryService();
    }

    if (connectionManager == null) {
      connectionManager = createConnectionManager();
    }
  }

  public DiscoveryNetwork<?> build() {
    initMissingDefaults();

    checkNotNull(p2pNetwork);
    checkNotNull(discoveryService);
    checkNotNull(connectionManager);
    checkNotNull(spec);
    checkNotNull(currentSchemaDefinitionsSupplier);

    return new DiscoveryNetwork<>(
        p2pNetwork, discoveryService, connectionManager, spec, currentSchemaDefinitionsSupplier);
  }

  protected ConnectionManager createConnectionManager() {
    checkNotNull(metricsSystem);
    checkNotNull(discoveryService);
    checkNotNull(asyncRunner);
    checkNotNull(p2pNetwork);
    checkNotNull(peerSelectionStrategy);
    checkNotNull(discoveryConfig);
    checkNotNull(timeProvider);

    return new ConnectionManager(
        metricsSystem,
        discoveryService,
        asyncRunner,
        timeProvider,
        p2pNetwork,
        peerSelectionStrategy,
        discoveryConfig.getStaticPeers().stream().map(p2pNetwork::createPeerAddress).toList(),
        peerPools);
  }

  protected DiscoveryService createDiscoveryService() {
    final DiscoveryService discoveryService;

    checkNotNull(discoveryConfig);
    if (discoveryConfig.isDiscoveryEnabled()) {
      checkNotNull(metricsSystem);
      checkNotNull(asyncRunner);
      checkNotNull(p2pConfig);
      checkNotNull(kvStore);
      checkNotNull(p2pNetwork);
      checkNotNull(currentSchemaDefinitionsSupplier);
      checkNotNull(spec);

      discoveryService =
          new DiscV5Service(
              metricsSystem,
              asyncRunner,
              discoveryConfig,
              p2pConfig,
              kvStore,
              p2pNetwork.getPrivateKey(),
              currentSchemaDefinitionsSupplier,
              spec.getNumberOfCustodyGroups(),
              DiscV5Service.createDefaultDiscoverySystemBuilder(),
              DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER);
    } else {
      discoveryService = new NoOpDiscoveryService();
    }
    return discoveryService;
  }

  public DiscoveryNetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public DiscoveryNetworkBuilder asyncRunner(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public DiscoveryNetworkBuilder kvStore(final KeyValueStore<String, Bytes> kvStore) {
    this.kvStore = kvStore;
    return this;
  }

  public DiscoveryNetworkBuilder p2pNetwork(final P2PNetwork<?> p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
    return this;
  }

  public DiscoveryNetworkBuilder peerPools(final PeerPools peerPools) {
    this.peerPools = peerPools;
    return this;
  }

  public DiscoveryNetworkBuilder peerSelectionStrategy(
      final PeerSelectionStrategy peerSelectionStrategy) {
    this.peerSelectionStrategy = peerSelectionStrategy;
    return this;
  }

  public DiscoveryNetworkBuilder discoveryConfig(final DiscoveryConfig discoveryConfig) {
    this.discoveryConfig = discoveryConfig;
    return this;
  }

  public DiscoveryNetworkBuilder p2pConfig(final NetworkConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
    return this;
  }

  public DiscoveryNetworkBuilder spec(final Spec spec) {
    this.spec = spec;
    return this;
  }

  public DiscoveryNetworkBuilder currentSchemaDefinitionsSupplier(
      final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier) {
    this.currentSchemaDefinitionsSupplier = currentSchemaDefinitionsSupplier;
    return this;
  }

  public DiscoveryNetworkBuilder discoveryService(final DiscoveryService discoveryService) {
    this.discoveryService = discoveryService;
    return this;
  }

  public DiscoveryNetworkBuilder connectionManager(final ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
    return this;
  }

  public DiscoveryNetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }
}

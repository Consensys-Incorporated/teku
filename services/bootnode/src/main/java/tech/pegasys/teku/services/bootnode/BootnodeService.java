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

package tech.pegasys.teku.services.bootnode;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.PrivateKeyProvider;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPrivateKeyLoader;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;

public class BootnodeService extends Service {

  private static final Logger LOG = LogManager.getLogger();
  private static final String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";
  private static final int PRINT_KNOWN_NODES_COUNT_INTERVAL_IN_SECONDS = 5;

  private final DiscoveryService discoveryService;
  private final AsyncRunner asyncRunner;

  public BootnodeService(
      final ServiceConfig serviceConfig,
      final NetworkConfig networkConfig,
      final P2PConfig p2PConfig,
      final Eth2NetworkConfiguration eth2NetworkConfiguration) {
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();
    checkNotNull(metricsSystem);

    asyncRunner =
        serviceConfig.createAsyncRunner(
            "bootnode",
            eth2NetworkConfiguration.getAsyncP2pMaxThreads(),
            eth2NetworkConfiguration.getAsyncP2pMaxQueue());
    final KeyValueStore<String, Bytes> kvStore =
        new FileKeyValueStore(
            serviceConfig
                .getDataDirLayout()
                .getBootnodeDataDirectory()
                .resolve(KEY_VALUE_STORE_SUBDIRECTORY));

    final SchemaDefinitionsSupplier currentSchemaDefinitionsSupplier =
        () -> eth2NetworkConfiguration.getSpec().getGenesisSchemaDefinitions();
    checkNotNull(currentSchemaDefinitionsSupplier);

    final PrivateKeyProvider privateKeyProvider =
        new LibP2PPrivateKeyLoader(kvStore, networkConfig.getPrivateKeySource());

    discoveryService =
        new DiscV5Service(
            metricsSystem,
            asyncRunner,
            p2PConfig.getDiscoveryConfig(),
            networkConfig,
            kvStore,
            Bytes.wrap(privateKeyProvider.get().raw()),
            currentSchemaDefinitionsSupplier,
            eth2NetworkConfiguration.getSpec().getNumberOfCustodyGroups(),
            DiscV5Service.createDefaultDiscoverySystemBuilder(),
            DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER);

    asyncRunner.runWithFixedDelay(
        () -> LOG.info("Total peers known: {}", discoveryService.streamKnownPeers().count()),
        Duration.ofSeconds(PRINT_KNOWN_NODES_COUNT_INTERVAL_IN_SECONDS),
        e -> LOG.error("Error checking known peers", e));
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.info("Starting Bootnode service...");

    return discoveryService
        .start()
        .thenPeek(
            __ -> {
              LOG.info("Bootnode service started");

              discoveryService
                  .getEnr()
                  .ifPresentOrElse(
                      enr -> LOG.info("Bootnode ENR = {}", enr),
                      () -> LOG.warn("Unable to retrieve Bootnode ENR"));
            });
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.info("Stopping Bootnode service...");

    return discoveryService
        .stop()
        .thenRun(asyncRunner::shutdown)
        .thenPeek(__ -> LOG.info("Bootnode service stopped"));
  }
}

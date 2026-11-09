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

package tech.pegasys.teku.network.p2p;

import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
import tech.pegasys.teku.network.p2p.peer.SimplePeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.reputation.DefaultReputationManager;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

public class DiscoveryNetworkFactory {

  private static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 9000;
  private static final int MAX_PORT = 12000;

  private final List<DiscoveryNetwork<?>> networks = new ArrayList<>();

  public DiscoveryTestNetworkBuilder builder() {
    return new DiscoveryTestNetworkBuilder();
  }

  public void stopAll() throws InterruptedException, ExecutionException, TimeoutException {
    Waiter.waitFor(
        SafeFuture.allOf(networks.stream().map(DiscoveryNetwork::stop).toArray(SafeFuture[]::new)));
  }

  public class DiscoveryTestNetworkBuilder {

    private final List<String> staticPeers = new ArrayList<>();
    private final List<String> bootnodes = new ArrayList<>();
    private final Spec spec = TestSpecFactory.createMinimalPhase0();

    private TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

    private DiscoveryTestNetworkBuilder() {}

    public DiscoveryTestNetworkBuilder staticPeer(final String staticPeer) {
      this.staticPeers.add(staticPeer);
      return this;
    }

    public DiscoveryTestNetworkBuilder bootnode(final String bootnode) {
      this.bootnodes.add(bootnode);
      return this;
    }

    public DiscoveryTestNetworkBuilder timeProvider(final TimeProvider timeProvider) {
      this.timeProvider = timeProvider;
      return this;
    }

    public DiscoveryNetwork<?> buildAndStart() throws Exception {
      int attempt = 1;
      while (true) {
        final Random random = new Random();
        final int port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT - 1);
        final int quicPort = port + 1;
        final DiscoveryConfig discoveryConfig =
            DiscoveryConfig.builder()
                .listenUdpPort(port)
                .staticPeers(staticPeers)
                .bootnodes(bootnodes)
                .build();
        final NetworkConfig config =
            NetworkConfig.builder()
                .listenPort(port)
                .listenQuicPort(quicPort)
                .advertisedIp(Optional.of("127.0.0.1"))
                .networkInterface("127.0.0.1")
                .build();
        final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
        final PeerPools peerPools = new PeerPools();
        final ReputationManager reputationManager =
            new DefaultReputationManager(
                metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY, peerPools);
        final PeerSelectionStrategy peerSelectionStrategy =
            new SimplePeerSelectionStrategy(new TargetPeerRange(20, 30, 0));
        final DiscoveryNetwork<?> network =
            DiscoveryNetworkBuilder.create()
                .metricsSystem(metricsSystem)
                .asyncRunner(DelayedExecutorAsyncRunner.create())
                .kvStore(new MemKeyValueStore<>())
                .p2pNetwork(
                    LibP2PNetworkBuilder.create()
                        .asyncRunner(DelayedExecutorAsyncRunner.create())
                        .config(config)
                        .networkingSpecConfig(spec.getNetworkingConfig())
                        .privateKeyProvider(PrivateKeyGenerator::generate)
                        .reputationManager(reputationManager)
                        .metricsSystem(METRICS_SYSTEM)
                        .rpcMethods(Collections.emptyList())
                        .peerHandlers(Collections.emptyList())
                        .preparedGossipMessageFactory(
                            (topic, payload, networkingSpecConfig, arrivalTimestamp) -> {
                              throw new UnsupportedOperationException();
                            })
                        .gossipTopicFilter(topic -> true)
                        .timeProvider(timeProvider)
                        .build())
                .peerPools(peerPools)
                .peerSelectionStrategy(peerSelectionStrategy)
                .discoveryConfig(discoveryConfig)
                .p2pConfig(config)
                .spec(spec)
                .timeProvider(timeProvider)
                .currentSchemaDefinitionsSupplier(spec::getGenesisSchemaDefinitions)
                .build();
        try {
          network.start().get(30, TimeUnit.SECONDS);
          networks.add(network);
          return network;
        } catch (final ExecutionException e) {
          if (ExceptionUtil.hasCause(e, BindException.class)) {
            if (attempt > 10) {
              throw new RuntimeException("Failed to find a free port after multiple attempts", e);
            }
            LOG.info(
                "Port conflict detected, retrying with a new port. Original message: {}",
                e.getMessage());
            attempt++;
            Waiter.waitFor(network.stop());
          } else {
            throw e;
          }
        }
      }
    }
  }
}

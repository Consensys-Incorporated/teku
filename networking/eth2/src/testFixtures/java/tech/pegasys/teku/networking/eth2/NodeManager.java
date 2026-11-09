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

package tech.pegasys.teku.networking.eth2;

import java.util.List;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelExceptionHandler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class NodeManager {
  private static final Logger LOG = LogManager.getLogger();
  private static final Spec DEFAULT_SPEC = TestSpecFactory.createMinimalPhase0();

  private final BlockGossipChannel blockGossipChannel;
  private final RecentChainData storageClient;
  private final ChainBuilder chainBuilder;
  private final ChainUpdater chainUpdater;
  private final Eth2P2PNetwork eth2P2PNetwork;

  private NodeManager(
      final BlockGossipChannel blockGossipChannel,
      final RecentChainData storageClient,
      final ChainBuilder chainBuilder,
      final ChainUpdater chainUpdater,
      final Eth2P2PNetwork eth2P2PNetwork) {
    this.blockGossipChannel = blockGossipChannel;
    this.storageClient = storageClient;
    this.chainBuilder = chainBuilder;
    this.chainUpdater = chainUpdater;
    this.eth2P2PNetwork = eth2P2PNetwork;
  }

  public static NodeManager create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys)
      throws Exception {
    return create(spec, asyncRunner, networkFactory, validatorKeys, c -> {});
  }

  @Deprecated
  public static NodeManager create(
      final AsyncRunner asyncRunner,
      final Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      final Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    return create(DEFAULT_SPEC, asyncRunner, networkFactory, validatorKeys, configureNetwork);
  }

  public static NodeManager create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      final Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final RecentChainData storageClient = MemoryOnlyRecentChainData.create(spec);
    ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    ChainUpdater chainUpdater = new ChainUpdater(storageClient, chainBuilder, spec);
    chainUpdater.initializeGenesis();
    return create(
        spec,
        asyncRunner,
        networkFactory,
        configureNetwork,
        storageClient,
        chainBuilder,
        chainUpdater);
  }

  public static NodeManager create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final Eth2P2PNetworkFactory networkFactory,
      final Consumer<Eth2P2PNetworkBuilder> configureNetwork,
      final RecentChainData storageClient,
      final ChainBuilder chainBuilder,
      final ChainUpdater chainUpdater)
      throws Exception {
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(
            ChannelExceptionHandler.THROWING_HANDLER, new NoOpMetricsSystem());

    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory
            .builder()
            .spec(spec)
            .eventChannels(eventChannels)
            .recentChainData(storageClient);

    configureNetwork.accept(networkBuilder);

    final BlockGossipChannel blockGossipChannel =
        eventChannels.getPublisher(BlockGossipChannel.class, asyncRunner);

    final Eth2P2PNetwork eth2P2PNetwork = networkBuilder.startNetwork();
    return new NodeManager(
        blockGossipChannel, storageClient, chainBuilder, chainUpdater, eth2P2PNetwork);
  }

  public SafeFuture<Peer> connect(final NodeManager peer) {
    final PeerAddress peerAddress =
        eth2P2PNetwork.createPeerAddress(peer.network().getNodeAddresses().get(0));
    return eth2P2PNetwork.connect(peerAddress);
  }

  public ChainUpdater getChainUpdater() {
    return chainUpdater;
  }

  public ChainBuilder getChainBuilder() {
    return chainBuilder;
  }

  public Eth2P2PNetwork network() {
    return eth2P2PNetwork;
  }

  public RecentChainData storageClient() {
    return storageClient;
  }

  public void gossipBlock(final SignedBeaconBlock block) {
    blockGossipChannel.publishBlock(block).finishDebug(LOG);
  }
}

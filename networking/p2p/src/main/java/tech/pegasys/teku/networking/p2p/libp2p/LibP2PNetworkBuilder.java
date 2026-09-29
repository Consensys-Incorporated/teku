/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.p2p.libp2p;

import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.MAXIMUM_ACTIVE_INBOUND_RPC_STREAMS;
import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.REMOTE_OPEN_STREAMS_RATE_LIMIT;
import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.dsl.Builder.Defaults;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.etc.types.ByteArrayExtKt;
import io.libp2p.protocol.Identify;
import io.libp2p.protocol.Ping;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.quic.QuicTransport;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.PrivateKeyProvider;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.InboundRpcStreamLimiter;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.DualStackPortBindings;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class LibP2PNetworkBuilder {
  public static final boolean DEFAULT_RECORD_MESSAGE_ARRIVAL = false;

  public static LibP2PNetworkBuilder create() {
    return new LibP2PNetworkBuilder();
  }

  protected AsyncRunner asyncRunner;
  protected NetworkConfig config;
  protected NetworkingSpecConfig networkingSpecConfig;
  protected PrivateKeyProvider privateKeyProvider;
  protected ReputationManager reputationManager;
  protected MetricsSystem metricsSystem;
  protected List<RpcMethod<?, ?, ?>> rpcMethods;
  protected List<PeerHandler> peerHandlers;
  protected PreparedGossipMessageFactory preparedGossipMessageFactory;
  protected GossipTopicFilter gossipTopicFilter;
  protected TimeProvider timeProvider;

  protected Firewall firewall = new Firewall(Duration.ofSeconds(30));
  protected MuxFirewall muxFirewall =
      new MuxFirewall(REMOTE_OPEN_STREAMS_RATE_LIMIT, REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT);

  protected LibP2PGossipNetwork gossipNetwork;

  protected Defaults hostBuilderDefaults = Defaults.None;
  protected Host host;

  protected List<? extends RpcHandler<?, ?, ?>> rpcHandlers;
  protected PeerManager peerManager;
  protected boolean recordMessageArrival = DEFAULT_RECORD_MESSAGE_ARRIVAL;

  protected LibP2PNetworkBuilder() {}

  public P2PNetwork<Peer> build() {

    gossipNetwork = createGossipNetwork();
    // Setup rpc methods
    rpcHandlers = createRpcHandlers();
    // Setup peers
    peerManager = createPeerManager();

    final PrivKey privKey = privateKeyProvider.get();
    final NodeId nodeId = new LibP2PNodeId(PeerId.fromPubKey(privKey.publicKey()));

    Preconditions.checkState(
        config.isTcpEnabled() || config.isQuicEnabled(),
        "At least one of the TCP or QUIC transports must be enabled");

    final List<Multiaddr> advertisedAddresses = buildAdvertisedAddresses(config, nodeId);

    host = createHost(privKey, advertisedAddresses);

    final List<Integer> listenPorts = buildListenPorts(config);

    return new LibP2PNetwork(
        host.getPrivKey(),
        nodeId,
        host,
        peerManager,
        advertisedAddresses,
        gossipNetwork,
        listenPorts,
        config.isQuicEnabled());
  }

  /**
   * Builds the multiaddrs advertised to peers (e.g. via the discovery ENR). Only enabled transports
   * are advertised, so peers are never told to dial a transport the node is not serving.
   */
  @VisibleForTesting
  static List<Multiaddr> buildAdvertisedAddresses(final NetworkConfig config, final NodeId nodeId) {
    final List<String> advertisedIps = config.getAdvertisedIps();
    Preconditions.checkState(
        advertisedIps.size() == 1 || advertisedIps.size() == 2,
        "The configured advertised IPs must be either 1 or 2");
    final boolean singleStack = advertisedIps.size() == 1;
    return advertisedIps.stream()
        .flatMap(
            advertisedIp -> {
              final List<Multiaddr> addresses = new ArrayList<>();
              if (config.isTcpEnabled()) {
                addresses.add(
                    MultiaddrUtil.fromInetSocketAddress(
                        new InetSocketAddress(
                            advertisedIp, advertisedTcpPort(config, advertisedIp, singleStack)),
                        nodeId));
              }
              if (config.isQuicEnabled()) {
                addresses.add(
                    MultiaddrUtil.fromInetSocketAddressAsQuic(
                        new InetSocketAddress(
                            advertisedIp, advertisedQuicPort(config, advertisedIp, singleStack)),
                        nodeId));
              }
              return addresses.stream();
            })
        .toList();
  }

  /** Builds the listen multiaddrs for the host, including only enabled transports. */
  @VisibleForTesting
  static String[] buildListenAddresses(final NetworkConfig config) {
    final List<String> networkInterfaces = config.getNetworkInterfaces();
    Preconditions.checkState(
        networkInterfaces.size() == 1 || networkInterfaces.size() == 2,
        "The configured network interfaces must be either 1 or 2");
    final boolean singleStack = networkInterfaces.size() == 1;
    return networkInterfaces.stream()
        .flatMap(
            networkInterface -> {
              final List<String> addresses = new ArrayList<>();
              if (shouldListenOnTcp(config, networkInterface)) {
                addresses.add(
                    MultiaddrUtil.fromInetSocketAddress(
                            new InetSocketAddress(
                                networkInterface,
                                listenTcpPort(config, networkInterface, singleStack)))
                        .toString());
              }
              if (shouldListenOnQuic(config, networkInterface)) {
                addresses.add(
                    MultiaddrUtil.fromInetSocketAddressAsQuic(
                            new InetSocketAddress(
                                networkInterface,
                                listenQuicPort(config, networkInterface, singleStack)))
                        .toString());
              }
              return addresses.stream();
            })
        .toArray(String[]::new);
  }

  /**
   * Builds the list of ports the node listens on, including only enabled transports. Used for
   * diagnostics (e.g. reporting which ports failed to bind).
   */
  @VisibleForTesting
  static List<Integer> buildListenPorts(final NetworkConfig config) {
    final List<String> networkInterfaces = config.getNetworkInterfaces();
    Preconditions.checkState(
        networkInterfaces.size() == 1 || networkInterfaces.size() == 2,
        "The configured network interfaces must be either 1 or 2");
    final boolean singleStack = networkInterfaces.size() == 1;
    return networkInterfaces.stream()
        .flatMap(
            networkInterface -> {
              final List<Integer> ports = new ArrayList<>();
              if (shouldListenOnTcp(config, networkInterface)) {
                ports.add(listenTcpPort(config, networkInterface, singleStack));
              }
              if (shouldListenOnQuic(config, networkInterface)) {
                ports.add(listenQuicPort(config, networkInterface, singleStack));
              }
              return ports.stream();
            })
        .toList();
  }

  private static int advertisedTcpPort(
      final NetworkConfig config, final String advertisedIp, final boolean singleStack) {
    if (singleStack) {
      return config.getAdvertisedPort();
    }
    return switch (IPVersionResolver.resolve(advertisedIp)) {
      case IP_V4 -> config.getAdvertisedPort();
      case IP_V6 -> config.getAdvertisedPortIpv6();
    };
  }

  private static int advertisedQuicPort(
      final NetworkConfig config, final String advertisedIp, final boolean singleStack) {
    if (singleStack) {
      return config.getAdvertisedQuicPort();
    }
    return switch (IPVersionResolver.resolve(advertisedIp)) {
      case IP_V4 -> config.getAdvertisedQuicPort();
      case IP_V6 -> config.getAdvertisedQuicPortIpv6();
    };
  }

  private static int listenTcpPort(
      final NetworkConfig config, final String networkInterface, final boolean singleStack) {
    if (singleStack) {
      return config.getListenPort();
    }
    return switch (IPVersionResolver.resolve(networkInterface)) {
      case IP_V4 -> config.getListenPort();
      case IP_V6 -> config.getListenPortIpv6();
    };
  }

  private static int listenQuicPort(
      final NetworkConfig config, final String networkInterface, final boolean singleStack) {
    if (singleStack) {
      return config.getListenQuicPort();
    }
    return switch (IPVersionResolver.resolve(networkInterface)) {
      case IP_V4 -> config.getListenQuicPort();
      case IP_V6 -> config.getListenQuicPortIpv6();
    };
  }

  private static boolean shouldListenOnTcp(
      final NetworkConfig config, final String networkInterface) {
    return config.isTcpEnabled()
        && DualStackPortBindings.shouldListenOnAddress(
            config.getNetworkInterfaces(),
            networkInterface,
            config.getListenPort(),
            config.getListenPortIpv6());
  }

  private static boolean shouldListenOnQuic(
      final NetworkConfig config, final String networkInterface) {
    return config.isQuicEnabled()
        && DualStackPortBindings.shouldListenOnAddress(
            config.getNetworkInterfaces(),
            networkInterface,
            config.getListenQuicPort(),
            config.getListenQuicPortIpv6());
  }

  protected List<? extends RpcHandler<?, ?, ?>> createRpcHandlers() {
    final InboundRpcStreamLimiter inboundRpcStreamLimiter =
        new InboundRpcStreamLimiter(MAXIMUM_ACTIVE_INBOUND_RPC_STREAMS);
    return rpcMethods.stream()
        .map(m -> new RpcHandler<>(asyncRunner, m, metricsSystem, inboundRpcStreamLimiter))
        .toList();
  }

  protected LibP2PGossipNetwork createGossipNetwork() {
    return createLibP2PGossipNetworkBuilder()
        .metricsSystem(metricsSystem)
        .gossipConfig(config.getGossipConfig())
        .networkingSpecConfig(networkingSpecConfig)
        .defaultMessageFactory(preparedGossipMessageFactory)
        .gossipTopicFilter(gossipTopicFilter)
        .logWireGossip(config.getWireLogsConfig().isLogWireGossip())
        .timeProvider(timeProvider)
        .recordArrivalTime(recordMessageArrival)
        .build();
  }

  protected PeerManager createPeerManager() {
    return new PeerManager(
        metricsSystem,
        reputationManager,
        peerHandlers,
        rpcHandlers,
        (peerId) -> gossipNetwork.getGossip().getGossipScore(peerId));
  }

  protected Host createHost(final PrivKey privKey, final List<Multiaddr> advertisedAddresses) {
    final String[] listenAddrs = buildListenAddresses(config);

    return BuilderJKt.hostJ(
        hostBuilderDefaults,
        b -> {
          b.getIdentity().setFactory(() -> privKey);
          if (config.isTcpEnabled()) {
            b.getTransports().add(TcpTransport::new);
          }
          if (config.isQuicEnabled()) {
            b.getSecureTransports().add(QuicTransport::ECDSA);
          }
          b.getSecureChannels().add(NoiseXXSecureChannel::new);

          // Yamux must take precedence during negotiation
          if (config.isYamuxEnabled()) {
            // https://github.com/Consensys-Incorporated/teku/issues/7532
            final int maxBufferedConnectionWrites = 150 * 1024 * 1024;
            b.getMuxers().add(StreamMuxerProtocol.getYamux(maxBufferedConnectionWrites));
          }
          b.getMuxers().add(StreamMuxerProtocol.getMplex());

          b.getNetwork().listen(listenAddrs);
          advertisedAddresses.forEach(
              advertisedAddr ->
                  b.getProtocols()
                      .addAll(getDefaultProtocols(privKey.publicKey(), advertisedAddr)));
          b.getProtocols().add(gossipNetwork.getGossip());
          b.getProtocols().addAll(rpcHandlers);

          if (config.getWireLogsConfig().isLogWireCipher()) {
            b.getDebug().getBeforeSecureHandler().addLogger(LogLevel.DEBUG, "wire.ciphered");
          }
          b.getDebug().getBeforeSecureHandler().addNettyHandler(firewall);

          if (config.getWireLogsConfig().isLogWirePlain()) {
            b.getDebug().getAfterSecureHandler().addLogger(LogLevel.DEBUG, "wire.plain");
          }
          if (config.getWireLogsConfig().isLogWireMuxFrames()) {
            b.getDebug().getMuxFramesHandler().addLogger(LogLevel.DEBUG, "wire.mux");
          }

          b.getConnectionHandlers().add(peerManager);

          b.getDebug().getMuxFramesHandler().addHandler(muxFirewall);
        });
  }

  protected List<ProtocolBinding<?>> getDefaultProtocols(
      final PubKey nodePubKey, final Multiaddr advertisedAddr) {
    final Ping ping = new Ping();
    final IdentifyOuterClass.Identify identifyMsg =
        IdentifyOuterClass.Identify.newBuilder()
            .setProtocolVersion("ipfs/0.1.0")
            .setAgentVersion(VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.VERSION)
            .setPublicKey(ByteArrayExtKt.toProtobuf(nodePubKey.bytes()))
            .addListenAddrs(ByteArrayExtKt.toProtobuf(advertisedAddr.serialize()))
            .setObservedAddr(ByteArrayExtKt.toProtobuf(advertisedAddr.serialize()))
            .addAllProtocols(ping.getProtocolDescriptor().getAnnounceProtocols())
            .addAllProtocols(
                gossipNetwork.getGossip().getProtocolDescriptor().getAnnounceProtocols())
            .build();
    return List.of(ping, new Identify(identifyMsg));
  }

  protected LibP2PGossipNetworkBuilder createLibP2PGossipNetworkBuilder() {
    return LibP2PGossipNetworkBuilder.create();
  }

  public LibP2PNetworkBuilder asyncRunner(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public LibP2PNetworkBuilder config(final NetworkConfig config) {
    this.config = config;
    return this;
  }

  public LibP2PNetworkBuilder networkingSpecConfig(
      final NetworkingSpecConfig networkingSpecConfig) {
    this.networkingSpecConfig = networkingSpecConfig;
    return this;
  }

  public LibP2PNetworkBuilder privateKeyProvider(final PrivateKeyProvider privateKeyProvider) {
    this.privateKeyProvider = privateKeyProvider;
    return this;
  }

  public LibP2PNetworkBuilder reputationManager(final ReputationManager reputationManager) {
    this.reputationManager = reputationManager;
    return this;
  }

  public LibP2PNetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public LibP2PNetworkBuilder rpcMethods(final List<RpcMethod<?, ?, ?>> rpcMethods) {
    this.rpcMethods = rpcMethods;
    return this;
  }

  public LibP2PNetworkBuilder peerHandlers(final List<PeerHandler> peerHandlers) {
    this.peerHandlers = peerHandlers;
    return this;
  }

  public LibP2PNetworkBuilder preparedGossipMessageFactory(
      final PreparedGossipMessageFactory preparedGossipMessageFactory) {
    this.preparedGossipMessageFactory = preparedGossipMessageFactory;
    return this;
  }

  public LibP2PNetworkBuilder gossipTopicFilter(final GossipTopicFilter gossipTopicFilter) {
    this.gossipTopicFilter = gossipTopicFilter;
    return this;
  }

  public LibP2PNetworkBuilder hostBuilderDefaults(final Defaults hostBuilderDefaults) {
    this.hostBuilderDefaults = hostBuilderDefaults;
    return this;
  }

  public LibP2PNetworkBuilder firewall(final Firewall firewall) {
    this.firewall = firewall;
    return this;
  }

  public LibP2PNetworkBuilder muxFirewall(final MuxFirewall muxFirewall) {
    this.muxFirewall = muxFirewall;
    return this;
  }

  public LibP2PNetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public LibP2PNetworkBuilder recordMessageArrival(final boolean recordMessageArrival) {
    this.recordMessageArrival = recordMessageArrival;
    return this;
  }
}

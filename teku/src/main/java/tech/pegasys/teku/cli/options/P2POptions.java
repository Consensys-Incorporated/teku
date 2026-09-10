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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory.DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_COLUMN_CUSTODY_BACKFILLER_BATCH_SIZE;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_COLUMN_CUSTODY_BACKFILLER_POLL_PERIOD_SECONDS;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_DOWNLOAD_TIMEOUT_MS;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_RECOVERY_TIMEOUT_MS;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_SIDECAR_RETRIEVAL_OVERLAP_FRACTION;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_LOWER_BOUND;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_LOWER_BOUND_ALL_SUBNETS;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_UPPER_BOUND;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_UPPER_BOUND_ALL_SUBNETS;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_EXECUTOR_MAX_QUEUE_SIZE_ALL_SUBNETS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.cli.converter.OptionalIntConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.MultilineEntriesReader;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrPeerAddress;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;

public class P2POptions {
  @Mixin private final NatOptions natOptions = new NatOptions();

  @Option(
      names = {"--p2p-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables P2P",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pEnabled = true;

  @Option(
      names = {"--p2p-interface", "--p2p-interfaces"},
      paramLabel = "<NETWORK>",
      description =
          """
              The network interface(s) on which the node listens for P2P communication.
              You can define up to 2 interfaces, with one being IPv4 and the other IPv6. (Default: 0.0.0.0)""",
      split = ",",
      arity = "1..2")
  private List<String> p2pInterfaces = NetworkConfig.DEFAULT_P2P_INTERFACE;

  @Option(
      names = {"--p2p-port"},
      paramLabel = "<INTEGER>",
      description = "P2P port",
      arity = "1")
  private int p2pPort = NetworkConfig.DEFAULT_P2P_PORT;

  @Option(
      names = {"--p2p-port-ipv6"},
      paramLabel = "<INTEGER>",
      description = "P2P IPv6 port. This port is only used when listening over both IPv4 and IPv6.",
      arity = "1")
  private int p2pPortIpv6 = NetworkConfig.DEFAULT_P2P_PORT_IPV6;

  @Option(
      names = {"--p2p-udp-port"},
      paramLabel = "<INTEGER>",
      description = "UDP port used for discovery. The default is the port specified in --p2p-port",
      arity = "1")
  private Integer p2pUdpPort;

  @Option(
      names = {"--p2p-udp-port-ipv6"},
      paramLabel = "<INTEGER>",
      description =
          "IPv6 UDP port used for discovery. This port is only used when listening over both IPv4 and IPv6. The "
              + "default is the port specified in --p2p-port-ipv6",
      arity = "1")
  private Integer p2pUdpPortIpv6;

  @Option(
      names = {"--Xp2p-quic-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables QUIC transport",
      fallbackValue = "true",
      hidden = true,
      arity = "0..1")
  private boolean p2pQuicEnabled = NetworkConfig.DEFAULT_QUIC_ENABLED;

  @Option(
      names = {"--Xp2p-tcp-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables TCP transport",
      fallbackValue = "true",
      hidden = true,
      arity = "0..1")
  private boolean p2pTcpEnabled = NetworkConfig.DEFAULT_TCP_ENABLED;

  @Option(
      names = {"--p2p-quic-port"},
      paramLabel = "<INTEGER>",
      description = "P2P QUIC port",
      arity = "1")
  private Integer p2pQuicPort = NetworkConfig.DEFAULT_P2P_QUIC_PORT;

  @Option(
      names = {"--p2p-quic-port-ipv6"},
      paramLabel = "<INTEGER>",
      description =
          "P2P IPv6 QUIC port. This port is only used when listening over both IPv4 and IPv6.",
      arity = "1")
  private int p2pQuicPortIpv6 = NetworkConfig.DEFAULT_P2P_QUIC_PORT_IPV6;

  @Option(
      names = {"--p2p-discovery-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables discv5 discovery",
      fallbackValue = "true",
      arity = "0..1")
  private boolean p2pDiscoveryEnabled = DiscoveryConfig.DEFAULT_P2P_DISCOVERY_ENABLED;

  @Option(
      names = {"--p2p-discovery-bootnodes"},
      paramLabel = "<enr:-...>",
      description = "List of ENRs of the bootnodes",
      split = ",",
      arity = "0..*")
  private List<String> p2pDiscoveryBootnodes = null;

  @Option(
      names = {"--p2p-discovery-bootnodes-url"},
      paramLabel = "<STRING>",
      description =
          "ENRs of bootnodes. This should be a file or URL pointing to a txt file with one ENR per line.",
      arity = "1")
  private String p2pDiscoveryBootnodesUrl;

  @Option(
      names = {"--p2p-advertised-ip", "--p2p-advertised-ips"},
      paramLabel = "<NETWORK>",
      description =
          "P2P advertised IP address(es). You can define up to 2 addresses, with one being IPv4 and the other IPv6. "
              + "(Default: 127.0.0.1)",
      split = ",",
      arity = "1..2")
  private List<String> p2pAdvertisedIps;

  @Option(
      names = {"--p2p-advertised-port"},
      paramLabel = "<INTEGER>",
      description = "P2P advertised port. The default is the port specified in --p2p-port",
      arity = "1")
  private Integer p2pAdvertisedPort;

  @Option(
      names = {"--p2p-advertised-port-ipv6"},
      paramLabel = "<INTEGER>",
      description =
          """
              P2P advertised IPv6 port. This port is only used when advertising both IPv4 and IPv6 addresses.
              The default is the port specified in --p2p-port-ipv6.""",
      arity = "1")
  private Integer p2pAdvertisedPortIpv6;

  @Option(
      names = {"--p2p-advertised-udp-port"},
      paramLabel = "<INTEGER>",
      description =
          "Advertised UDP port to external peers. The default is the port specified in --p2p-advertised-port",
      arity = "1")
  private Integer p2pAdvertisedUdpPort;

  @Option(
      names = {"--p2p-advertised-udp-port-ipv6"},
      paramLabel = "<INTEGER>",
      description =
          """
              Advertised IPv6 UDP port to external peers. This port is only used when advertising both IPv4 and IPv6 addresses.
              The default is the port specified in --p2p-advertised-port-ipv6.""",
      arity = "1")
  private Integer p2pAdvertisedUdpPortIpv6;

  @Option(
      names = {"--p2p-advertised-quic-port"},
      paramLabel = "<INTEGER>",
      description =
          "P2P advertised QUIC port. The default is the port specified in --p2p-quic-port",
      arity = "1")
  private Integer p2pAdvertisedQuicPort;

  @Option(
      names = {"--p2p-advertised-quic-port-ipv6"},
      paramLabel = "<INTEGER>",
      description =
          """
                      P2P advertised IPv6 QUIC port. This port is only used when advertising both IPv4 and IPv6 addresses.
                      The default is the port specified in --p2p-quic-port-ipv6.""",
      arity = "1")
  private Integer p2pAdvertisedQuicPortIpv6;

  @Option(
      names = {"--p2p-private-key-file"},
      paramLabel = "<FILENAME>",
      description =
          "This node's private key file in LibP2P format. If not specified, uses or generates a key which is stored "
              + "within the <beacon-data-dir>.",
      arity = "1")
  private String p2pPrivateKeyFile = null;

  @Option(
      names = {"--Xp2p-private-key-file-secp256k1"},
      paramLabel = "<FILENAME>",
      description =
          "This node's private key file of Secp256k1 type. Only single private key option should be specified.",
      hidden = true,
      arity = "1")
  private String p2pPrivateKeyFileSecp256k1 = null;

  @Option(
      names = {"--Xp2p-private-key-file-ecdsa"},
      paramLabel = "<FILENAME>",
      description =
          "This node's private key file of ECDSA type. Only single private key option should be specified.",
      hidden = true,
      arity = "1")
  private String p2pPrivateKeyFileEcdsa = null;

  @Option(
      names = {"--p2p-peer-lower-bound"},
      paramLabel = "<INTEGER>",
      description = "Lower bound on the target number of peers",
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt p2pLowerBound = OptionalInt.empty();

  @Option(
      names = {"--p2p-peer-upper-bound"},
      paramLabel = "<INTEGER>",
      description = "Upper bound on the target number of peers",
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt p2pUpperBound = OptionalInt.empty();

  @Option(
      names = {"--Xp2p-target-subnet-subscriber-count"},
      paramLabel = "<INTEGER>",
      description = "Target number of peers subscribed to each attestation subnet",
      arity = "1",
      hidden = true)
  private int p2pTargetSubnetSubscriberCount = P2PConfig.DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT;

  @Option(
      names = {"--Xp2p-minimum-randomly-selected-peer-count"},
      paramLabel = "<INTEGER>",
      description =
          "Number of peers that should be selected randomly (default 30%% of lower-bound target)",
      arity = "1",
      hidden = true)
  private Integer minimumRandomlySelectedPeerCount;

  @Option(
      names = {"--p2p-static-peers-url"},
      paramLabel = "<URL>",
      description =
          "Specifies a URL or file containing a list of 'static' peers (one per line) with which to establish and "
              + "maintain connections. Accepts multiaddr format.",
      arity = "1")
  private String p2pStaticPeersUrl;

  @Option(
      names = {"--p2p-static-peers"},
      paramLabel = "<PEER_ADDRESSES>",
      description =
          "Specifies a comma-separated list of 'static' peers with which to establish and maintain connections. "
              + "Accepts multiaddr format.",
      split = ",",
      arity = "1")
  private final List<String> p2pStaticPeers = new ArrayList<>();

  @Option(
      names = {"--p2p-direct-peers"},
      paramLabel = "<PEER_ADDRESSES>",
      description =
          """
              Specifies a list of 'direct' peers with which to establish and maintain connections.
              Direct peers are static peers with which this node will always exchange full messages, regardless of peer scoring mechanisms.
              Such peers will also need to enable you as direct in order to work.""",
      split = ",",
      arity = "0..*")
  private List<String> p2pDirectPeers = new ArrayList<>();

  @Option(
      names = {"--Xp2p-multipeer-sync-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables multipeer sync",
      fallbackValue = "true",
      hidden = true,
      arity = "0..1")
  private boolean multiPeerSyncEnabled = SyncConfig.DEFAULT_MULTI_PEER_SYNC_ENABLED;

  @Option(
      names = {"--Xp2p-historical-sync-batch-size"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Number of blocks/blobs being requested in a single batch to a single peer, while syncing historical data.\n"
              + "NOTE: the actual size for blobs being requested in a single batch will be up to `maxBlobsPerBlock` "
              + "times the value of this parameter.",
      hidden = true,
      arity = "1")
  private Integer historicalSyncBatchSize = SyncConfig.DEFAULT_HISTORICAL_SYNC_BATCH_SIZE;

  @Option(
      names = {"--Xp2p-sync-batch-size"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Number of blocks/blobs being requested in a single batch to a single peer, while syncing.\n"
              + "NOTE: the actual size for blobs being requested in a single batch will be up to `maxBlobsPerBlock` "
              + "times the value of this parameter.",
      hidden = true,
      arity = "1")
  private Integer forwardSyncBatchSize = SyncConfig.DEFAULT_FORWARD_SYNC_BATCH_SIZE;

  @Option(
      names = {"--Xp2p-sync-max-pending-batches"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Maximum number of concurrent batches being requested to peers, while syncing.",
      hidden = true,
      arity = "1")
  private Integer forwardSyncMaxPendingBatches =
      SyncConfig.DEFAULT_FORWARD_SYNC_MAX_PENDING_BATCHES;

  @Option(
      names = {"--Xp2p-sync-blocks-rate-limit"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Number of blocks being requested per minute to a single peer, while syncing.",
      hidden = true,
      arity = "1")
  private Integer forwardSyncBlocksRateLimit =
      SyncConfig.DEFAULT_FORWARD_SYNC_MAX_BLOCKS_PER_MINUTE;

  @Option(
      names = {"--Xp2p-sync-max-distance-from-head"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Maximum number slots to jump back when trying to find a common ancestor with target chain.",
      hidden = true,
      arity = "1")
  private Integer forwardSyncMaxDistanceFromHead;

  @Option(
      names = {"--Xp2p-max-concurrently-sampled-blocks"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Maximum number of recent blocks we keep in the temporary cache to serve requests of recent blocks that have not being fully imported.",
      hidden = true,
      arity = "1")
  private Integer maxConcurrentlySampledBlocks = SyncConfig.DEFAULT_MAX_RECENTLY_SAMPLED_BLOCKS;

  @Option(
      names = {"--Xp2p-sync-blob-sidecars-rate-limit"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Number of blobs being requested per minute to a single peer, while syncing.",
      hidden = true,
      arity = "1")
  private Integer forwardSyncBlobSidecarsRateLimit =
      SyncConfig.DEFAULT_FORWARD_SYNC_MAX_BLOB_SIDECARS_PER_MINUTE;

  @Option(
      names = {
        "--Xp2p-sidecar-custody-sync-batch-size",
        "--Xp2p-reworked-sidecar-custody-sync-batch-size"
      },
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Backfill sync custody batch size in slots",
      arity = "1",
      hidden = true)
  private Integer sidecarCustodySyncBatchSize = DEFAULT_COLUMN_CUSTODY_BACKFILLER_BATCH_SIZE;

  @Option(
      names = {
        "--Xp2p-sidecar-custody-sync-poll-period-seconds",
        "--Xp2p-reworked-sidecar-custody-sync-poll-period-seconds"
      },
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Backfill sync custody poll period",
      arity = "1",
      hidden = true)
  private Integer sidecarCustodySyncPollPeriodSeconds =
      DEFAULT_COLUMN_CUSTODY_BACKFILLER_POLL_PERIOD_SECONDS;

  @Option(
      names = {"--Xp2p-sidecar-cancel-timeout-ms"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "",
      arity = "1",
      hidden = true)
  private Integer sidecarCancelTimeoutMs = DEFAULT_RECOVERY_TIMEOUT_MS;

  @Option(
      names = {"--Xp2p-sidecar-download-timeout-ms"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "",
      arity = "1",
      hidden = true)
  private Integer sidecarDownloadTimeoutMs = DEFAULT_DOWNLOAD_TIMEOUT_MS;

  @Option(
      names = {"--Xp2p-sidecar-retrieval-overlap-fraction"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Fraction of pending data column sidecar retrievals that may be hedged with a redundant "
              + "request to an alternate custody peer when the original peer is slow to respond. "
              + "Any positive value permits at least one hedged retrieval. Set to 0 to disable.",
      arity = "1",
      hidden = true)
  private Double sidecarRetrievalOverlapFraction = DEFAULT_SIDECAR_RETRIEVAL_OVERLAP_FRACTION;

  @Option(
      names = {"--p2p-subscribe-all-subnets-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "",
      arity = "0..1",
      fallbackValue = "true")
  private boolean subscribeAllSubnetsEnabled = P2PConfig.DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED;

  @Option(
      names = {"--p2p-subscribe-all-custody-subnets-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "",
      arity = "0..1",
      fallbackValue = "true")
  private boolean subscribeAllCustodySubnetsEnabled =
      P2PConfig.DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED;

  @Option(
      names = {"--Xp2p-gossip-scoring-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables experimental gossip scoring",
      hidden = true,
      arity = "0..1",
      fallbackValue = "true")
  private boolean gossipScoringEnabled = P2PConfig.DEFAULT_GOSSIP_SCORING_ENABLED;

  @Option(
      names = {"--Xpeer-blocks-rate-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requested blocks per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerBlocksRateLimit = P2PConfig.DEFAULT_PEER_BLOCKS_RATE_LIMIT;

  @Option(
      names = {"--Xpeer-blob-sidecars-rate-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requested blobs per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerBlobSidecarsRateLimit = P2PConfig.DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT;

  @Option(
      names = {"--Xp2p-gossip-blobs-after-block-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enables experimental behaviour in which blobs are gossiped after the block has been gossiped to at least "
              + "one peer.",
      hidden = true,
      arity = "0..1",
      fallbackValue = "true")
  private boolean gossipBlobsAfterBlockEnabled = P2PConfig.DEFAULT_GOSSIP_BLOBS_AFTER_BLOCK_ENABLED;

  @Option(
      names = {"--Xp2p-gossip-snappy-aircompressor-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Use aircompressor-v3 for gossip snappy encoding and decoding",
      hidden = true,
      arity = "0..1",
      fallbackValue = "true")
  private boolean gossipSnappyAircompressorEnabled =
      P2PConfig.DEFAULT_GOSSIP_SNAPPY_AIRCOMPRESSOR_ENABLED;

  @Option(
      names = {"--Xp2p-rpc-snappy-aircompressor-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Use aircompressor-v3 for RPC snappy encoding and decoding",
      hidden = true,
      arity = "0..1",
      fallbackValue = "true")
  private boolean rpcSnappyAircompressorEnabled =
      P2PConfig.DEFAULT_RPC_SNAPPY_AIRCOMPRESSOR_ENABLED;

  @Option(
      names = {"--Xpeer-all-topics-filter-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Add all topic filtering to p2p configuration.",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean allTopicsFilterEnabled = P2PConfig.DEFAULT_PEER_ALL_TOPIC_FILTER_ENABLED;

  @Option(
      names = {"--Xexecution-proof-topics-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enable all execution proof topics",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean executionProofTopicEnabled = P2PConfig.DEFAULT_EXECUTION_PROOF_GOSSIP_ENABLED;

  @Option(
      names = {"--Xpeer-request-limit"},
      paramLabel = "<NUMBER>",
      description =
          "The number of requests per peer to allow per minute before disconnecting the peer.",
      arity = "1",
      hidden = true)
  private Integer peerRequestLimit = P2PConfig.DEFAULT_PEER_REQUEST_LIMIT;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-max-threads"},
      paramLabel = "<NUMBER>",
      description = "Maximum number of threads to use for aggregated signature verification",
      arity = "1",
      hidden = true)
  private int batchVerifyMaxThreads = P2PConfig.DEFAULT_BATCH_VERIFY_MAX_THREADS;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-queue-capacity"},
      paramLabel = "<NUMBER>",
      description = "Maximum queue size for pending aggregated signature verification",
      arity = "1",
      converter = OptionalIntConverter.class,
      hidden = true)
  private OptionalInt batchVerifyQueueCapacity = OptionalInt.empty();

  @Option(
      names = {"--Xp2p-batch-verify-signatures-max-batch-size"},
      paramLabel = "<NUMBER>",
      description = "Maximum number of verification tasks to include in a single batch",
      arity = "1",
      hidden = true)
  private int batchVerifyMaxBatchSize = P2PConfig.DEFAULT_BATCH_VERIFY_MAX_BATCH_SIZE;

  @Option(
      names = {"--Xp2p-batch-verify-signatures-strict-thread-limit-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "When enabled, signature verification is entirely constrained to the max threads with no use of shared "
              + "executor pools",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean batchVerifyStrictThreadLimitEnabled =
      P2PConfig.DEFAULT_BATCH_VERIFY_STRICT_THREAD_LIMIT_ENABLED;

  @Option(
      names = {"--p2p-discovery-site-local-addresses-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Whether discovery accepts messages and peer records with site local (RFC1918) addresses",
      arity = "0..1",
      fallbackValue = "true")
  private boolean siteLocalAddressesEnabled = DiscoveryConfig.DEFAULT_SITE_LOCAL_ADDRESSES_ENABLED;

  @Option(
      names = {"--Xp2p-yamux-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables yamux multiplexing",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean yamuxEnabled = NetworkConfig.DEFAULT_YAMUX_ENABLED;

  // More about flood publishing
  // https://github.com/libp2p/specs/blob/master/pubsub/gossipsub/gossipsub-v1.1.md#flood-publishing
  @Option(
      names = {"--Xp2p-flood-max-message-size-threshold"},
      paramLabel = "<NUMBER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Maximum size (in bytes) of a message that will be flood published",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private int floodPublishMaxMessageSizeThreshold =
      GossipConfig.DEFAULT_FLOOD_PUBLISH_MAX_MESSAGE_SIZE_THRESHOLD;

  @Option(
      names = {"--Xcustody-group-count-override"},
      paramLabel = "<NUMBER>",
      description =
          "Override the number of custody groups. If it's lower than node configuration requirement, "
              + "the value is ignored. If it's higher than maximum number of custody groups, the value is set to "
              + "allowed maximum.",
      arity = "1",
      hidden = true)
  private int custodyGroupCountOverride = P2PConfig.DEFAULT_CUSTODY_GROUP_COUNT_OVERRIDE;

  @Option(
      names = {"--Xdas-publish-withhold-columns-every-slots"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description =
          "If set will not publish non-custodied DataColumnSidecars on block production once in configured number of slots",
      arity = "1")
  private int dasPublishWithholdColumnsEverySlots =
      P2PConfig.DEFAULT_DAS_PUBLISH_WITHHOLD_COLUMNS_EVERY_SLOTS;

  @Option(
      names = {"--Xdas-disable-el-recovery"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "If set will disable attempts to recover blobs from EL to build DataColumnSidecars",
      arity = "0..1",
      fallbackValue = "true")
  private boolean dasDisableElRecovery = P2PConfig.DEFAULT_DAS_DISABLE_EL_RECOVERY;

  @Option(
      names = {"--Xcolumns-data-availability-half-check-enabled"},
      hidden = true,
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enables faster block import when 50% of all sidecar columns available without waiting "
              + "to obtain the remaining columns, which continue to download in the background. "
              + "Works only on nodes with 50%+ columns custody requirements.",
      arity = "0..1",
      fallbackValue = "true")
  private boolean columnsDataAvailabilityHalfCheckEnabled =
      P2PConfig.DEFAULT_COLUMNS_DATA_AVAILABILITY_HALF_CHECK_ENABLED;

  @Option(
      names = {"--Xp2p-historical-data-max-concurrent-queries"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description =
          "Limits the number of concurrent queries to historical data when handling RPC requests. Use 0 to allow "
              + "unlimited concurrent queries.",
      showDefaultValue = Visibility.ALWAYS,
      arity = "1")
  private int historicalDataMaxConcurrentQueries =
      P2PConfig.DEFAULT_HISTORICAL_DATA_MAX_CONCURRENT_QUERIES;

  @Option(
      names = {"--Xp2p-historical-data-max-query-queue-size"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description =
          "Limits the number of queries being queued when handling RPC requests. It has no effect if max-concurrent-queries is set to 0.",
      showDefaultValue = Visibility.ALWAYS,
      arity = "1")
  private int historicalDataMaxQueryQueueSize = P2PConfig.DEFAULT_HISTORICAL_MAX_QUERY_QUEUE_SIZE;

  @Option(
      names = {"--Xmin-bid-increment-percentage"},
      hidden = true,
      paramLabel = "<INTEGER>",
      description =
          "Minimum bid increment percentage for execution payload bid gossip validation. "
              + "New bids must exceed the current highest bid by at least this percentage. "
              + "Used for DoS protection against bid spamming. Default: 1 (1%)",
      arity = "1",
      defaultValue = "1",
      showDefaultValue = Visibility.ALWAYS)
  private int minBidIncrementPercentage = P2PConfig.DEFAULT_MIN_BID_INCREMENT_PERCENTAGE;

  private OptionalInt getP2pLowerBound() {
    if (p2pUpperBound.isPresent() && p2pLowerBound.isPresent()) {
      return p2pLowerBound.getAsInt() < p2pUpperBound.getAsInt() ? p2pLowerBound : p2pUpperBound;
    }
    return p2pLowerBound;
  }

  private OptionalInt getP2pUpperBound() {
    if (p2pUpperBound.isPresent() && p2pLowerBound.isPresent()) {
      return p2pLowerBound.getAsInt() > p2pUpperBound.getAsInt() ? p2pLowerBound : p2pUpperBound;
    }
    return p2pUpperBound;
  }

  private List<String> getStaticPeersList() {
    final List<String> staticPeers = new ArrayList<>(p2pStaticPeers);

    if (p2pStaticPeersUrl != null) {
      try {
        final List<String> peersFromUrl = MultilineEntriesReader.readEntries(p2pStaticPeersUrl);
        staticPeers.addAll(peersFromUrl);
      } catch (final Exception e) {
        throw new InvalidConfigurationException(
            "Error reading static peers from " + p2pStaticPeersUrl, e);
      }
    }

    return staticPeers;
  }

  private List<String> getBootnodes() {
    final List<String> bootnodes = new ArrayList<>();

    if (p2pDiscoveryBootnodes != null) {
      bootnodes.addAll(p2pDiscoveryBootnodes);
    }

    if (p2pDiscoveryBootnodesUrl != null) {
      try {
        final List<String> bootnodesFromUrl =
            MultilineEntriesReader.readEntries(p2pDiscoveryBootnodesUrl);
        for (final String bootnode : bootnodesFromUrl) {
          if (bootnode.startsWith("- enr:-")) {
            // clean up yaml entries
            bootnodes.add(bootnode.substring(2));
          } else if (bootnode.startsWith("enr")) {
            // require they start with ENR
            bootnodes.add(bootnode);
          } else {
            throw new InvalidConfigurationException(
                String.format(
                    "Invalid bootnode found in URL (%s): %s", p2pDiscoveryBootnodesUrl, bootnode));
          }
        }
      } catch (final InvalidConfigurationException e) {
        throw e;
      } catch (final Exception e) {
        throw new InvalidConfigurationException(
            "Error reading bootnodes from " + p2pDiscoveryBootnodesUrl, e);
      }
    }

    return bootnodes;
  }

  public void configure(final TekuConfiguration.Builder builder) {
    // From a discovery configuration perspective, direct peers are static peers
    p2pStaticPeers.addAll(p2pDirectPeers);

    builder
        .p2p(
            b -> {
              b.subscribeAllSubnetsEnabled(subscribeAllSubnetsEnabled)
                  .subscribeAllCustodySubnetsEnabled(subscribeAllCustodySubnetsEnabled)
                  .batchVerifyMaxThreads(batchVerifyMaxThreads)
                  .batchVerifyMaxBatchSize(batchVerifyMaxBatchSize)
                  .batchVerifyStrictThreadLimitEnabled(batchVerifyStrictThreadLimitEnabled)
                  .targetSubnetSubscriberCount(p2pTargetSubnetSubscriberCount)
                  .isGossipScoringEnabled(gossipScoringEnabled)
                  .peerBlocksRateLimit(peerBlocksRateLimit)
                  .peerBlobSidecarsRateLimit(peerBlobSidecarsRateLimit)
                  .allTopicsFilterEnabled(allTopicsFilterEnabled)
                  .peerRequestLimit(peerRequestLimit)
                  .floodPublishMaxMessageSizeThreshold(floodPublishMaxMessageSizeThreshold)
                  .gossipBlobsAfterBlockEnabled(gossipBlobsAfterBlockEnabled)
                  .gossipSnappyAircompressorEnabled(gossipSnappyAircompressorEnabled)
                  .rpcSnappyAircompressorEnabled(rpcSnappyAircompressorEnabled)
                  .custodyGroupCountOverride(custodyGroupCountOverride)
                  .dasPublishWithholdColumnsEverySlots(dasPublishWithholdColumnsEverySlots)
                  .dasDisableElRecovery(dasDisableElRecovery)
                  .historicalDataMaxConcurrentQueries(historicalDataMaxConcurrentQueries)
                  .historicalDataMaxQueryQueueSize(historicalDataMaxQueryQueueSize)
                  .executionProofTopicEnabled(executionProofTopicEnabled)
                  .sidecarRecoveryTimeout(sidecarCancelTimeoutMs)
                  .sidecarDownloadTimeout(sidecarDownloadTimeoutMs)
                  .sidecarRetrievalOverlapFraction(sidecarRetrievalOverlapFraction)
                  .sidecarSyncPollPeriod(sidecarCustodySyncPollPeriodSeconds)
                  .sidecarSyncBatchSize(sidecarCustodySyncBatchSize)
                  .columnsDataAvailabilityHalfCheckEnabled(columnsDataAvailabilityHalfCheckEnabled)
                  .minBidIncrementPercentage(minBidIncrementPercentage);
              batchVerifyQueueCapacity.ifPresent(b::batchVerifyQueueCapacity);
            })
        .discovery(
            d -> {
              if (p2pDiscoveryBootnodes != null || p2pDiscoveryBootnodesUrl != null) {
                d.bootnodes(getBootnodes());
              }
              if (minimumRandomlySelectedPeerCount != null) {
                d.minRandomlySelectedPeers(minimumRandomlySelectedPeerCount);
              }
              if (p2pUdpPort != null) {
                d.listenUdpPort(p2pUdpPort);
              }
              if (p2pUdpPortIpv6 != null) {
                d.listenUdpPortIpv6(p2pUdpPortIpv6);
              }
              if (p2pAdvertisedUdpPort != null) {
                d.advertisedUdpPort(OptionalInt.of(p2pAdvertisedUdpPort));
              }
              final OptionalInt maybeUpperBound = getP2pUpperBound();
              final OptionalInt maybeLowerBound = getP2pLowerBound();
              d.minPeers(
                  maybeLowerBound.orElse(
                      subscribeAllSubnetsEnabled
                          ? DEFAULT_P2P_PEERS_LOWER_BOUND_ALL_SUBNETS
                          : DEFAULT_P2P_PEERS_LOWER_BOUND));
              d.maxPeers(
                  maybeUpperBound.orElse(
                      subscribeAllSubnetsEnabled
                          ? DEFAULT_P2P_PEERS_UPPER_BOUND_ALL_SUBNETS
                          : DEFAULT_P2P_PEERS_UPPER_BOUND));
              if (p2pAdvertisedUdpPortIpv6 != null) {
                d.advertisedUdpPortIpv6(OptionalInt.of(p2pAdvertisedUdpPortIpv6));
              }
              d.isDiscoveryEnabled(p2pDiscoveryEnabled)
                  .staticPeers(getStaticPeersList())
                  .siteLocalAddressesEnabled(siteLocalAddressesEnabled);
            })
        .network(
            n -> {
              if (p2pPrivateKeyFile != null) {
                n.privateKeyFile(p2pPrivateKeyFile);
              }
              if (p2pPrivateKeyFileSecp256k1 != null) {
                n.privateKeyFileSecp256k1(p2pPrivateKeyFileSecp256k1);
              }
              if (p2pPrivateKeyFileEcdsa != null) {
                n.privateKeyFileEcdsa(p2pPrivateKeyFileEcdsa);
              }

              if (p2pAdvertisedPort != null) {
                n.advertisedPort(OptionalInt.of(p2pAdvertisedPort));
              }
              if (p2pAdvertisedPortIpv6 != null) {
                n.advertisedPortIpv6(OptionalInt.of(p2pAdvertisedPortIpv6));
              }
              if (p2pAdvertisedQuicPort != null) {
                n.advertisedQuicPort(OptionalInt.of(p2pAdvertisedQuicPort));
              }
              if (p2pAdvertisedQuicPortIpv6 != null) {
                n.advertisedQuicPortIpv6(OptionalInt.of(p2pAdvertisedQuicPortIpv6));
              }
              if (!p2pDirectPeers.isEmpty()) {
                n.directPeers(
                    p2pDirectPeers.stream()
                        .map(MultiaddrPeerAddress::fromAddress)
                        .map(MultiaddrPeerAddress::getId)
                        .toList());
              }
              n.networkInterfaces(p2pInterfaces)
                  .isEnabled(p2pEnabled)
                  .listenPort(p2pPort)
                  .listenPortIpv6(p2pPortIpv6)
                  .listenQuicPort(p2pQuicPort)
                  .listenQuicPortIpv6(p2pQuicPortIpv6)
                  .advertisedIps(Optional.ofNullable(p2pAdvertisedIps))
                  .yamuxEnabled(yamuxEnabled)
                  .quicEnabled(p2pQuicEnabled)
                  .tcpEnabled(p2pTcpEnabled);
            })
        .sync(
            s ->
                s.isMultiPeerSyncEnabled(multiPeerSyncEnabled)
                    .historicalSyncBatchSize(historicalSyncBatchSize)
                    .forwardSyncMaxBlocksPerMinute(forwardSyncBlocksRateLimit)
                    .forwardSyncMaxBlobSidecarsPerMinute(forwardSyncBlobSidecarsRateLimit)
                    .forwardSyncBatchSize(forwardSyncBatchSize)
                    .forwardSyncMaxPendingBatches(forwardSyncMaxPendingBatches)
                    .forwardSyncMaxDistanceFromHead(forwardSyncMaxDistanceFromHead)
                    .maxRecentlySampledBlocks(maxConcurrentlySampledBlocks));

    if (subscribeAllSubnetsEnabled) {
      builder
          .validator(
              v -> v.executorMaxQueueSizeIfDefault(DEFAULT_EXECUTOR_MAX_QUEUE_SIZE_ALL_SUBNETS))
          .eth2NetworkConfig(
              eth ->
                  eth.asyncP2pMaxQueueIfDefault(DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS)
                      .asyncBeaconChainMaxQueueIfDefault(DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS))
          .p2p(p2p -> p2p.batchVerifyQueueCapacityIfDefault(DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS));
    }
    natOptions.configure(builder);
  }
}

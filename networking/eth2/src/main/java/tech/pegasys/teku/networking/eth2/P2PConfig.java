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

package tech.pegasys.teku.networking.eth2;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig.DEFAULT_FLOOD_PUBLISH_MAX_MESSAGE_SIZE_THRESHOLD;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networking.eth2.gossip.config.Eth2Context;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;

public class P2PConfig {

  private static final Logger LOG = LogManager.getLogger();

  public static final int DEFAULT_PEER_BLOCKS_RATE_LIMIT = 500;
  // 250 MB per peer per minute (~ 4.16 MB/s)
  public static final int DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT = 2000;

  public static final int DEFAULT_PEER_REQUEST_LIMIT = 100;

  public static final boolean DEFAULT_PEER_ALL_TOPIC_FILTER_ENABLED = true;
  public static final int DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT = 3;
  public static final boolean DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED = false;
  public static final boolean DEFAULT_GOSSIP_SCORING_ENABLED = true;
  public static final boolean DEFAULT_GOSSIP_BLOBS_AFTER_BLOCK_ENABLED = true;
  public static final boolean DEFAULT_GOSSIP_SNAPPY_AIRCOMPRESSOR_ENABLED = true;
  public static final boolean DEFAULT_RPC_SNAPPY_AIRCOMPRESSOR_ENABLED = true;
  public static final boolean DEFAULT_DAS_DISABLE_EL_RECOVERY = false;
  public static final boolean DEFAULT_COLUMNS_DATA_AVAILABILITY_HALF_CHECK_ENABLED = true;
  public static final int DEFAULT_BATCH_VERIFY_MAX_THREADS =
      Math.max(4, Runtime.getRuntime().availableProcessors() / 2);
  public static final int DEFAULT_BATCH_VERIFY_QUEUE_CAPACITY = 30_000;
  public static final int DEFAULT_BATCH_VERIFY_MAX_BATCH_SIZE = 250;
  public static final boolean DEFAULT_BATCH_VERIFY_STRICT_THREAD_LIMIT_ENABLED = false;
  // it's not allowed to set less than requirement which is > 0, so it's safe value
  public static final int DEFAULT_CUSTODY_GROUP_COUNT_OVERRIDE = 0;
  public static final int DEFAULT_DAS_PUBLISH_WITHHOLD_COLUMNS_EVERY_SLOTS = -1;
  public static final int DEFAULT_RECOVERY_TIMEOUT_MS = 300_000;
  public static final int DEFAULT_DOWNLOAD_TIMEOUT_MS = 240_000;
  public static final double DEFAULT_SIDECAR_RETRIEVAL_OVERLAP_FRACTION = 0.05;

  public static final int DEFAULT_COLUMN_CUSTODY_BACKFILLER_POLL_PERIOD_SECONDS = 30;
  public static final int DEFAULT_COLUMN_CUSTODY_BACKFILLER_BATCH_SIZE = 3;

  // RocksDB is configured with 6 background jobs and threads (DEFAULT_MAX_BACKGROUND_JOBS and
  // DEFAULT_BACKGROUND_THREAD_COUNT)
  // The storage query channel allows up to 10 parallel queries (STORAGE_QUERY_CHANNEL_PARALLELISM)
  // To avoid resource saturation and ensure capacity for other tasks, we limit historical data
  // queries to 3
  public static final int DEFAULT_HISTORICAL_DATA_MAX_CONCURRENT_QUERIES = 3;
  public static final int DEFAULT_HISTORICAL_MAX_QUERY_QUEUE_SIZE = 500;
  public static final boolean DEFAULT_EXECUTION_PROOF_GOSSIP_ENABLED = false;

  public static final int DEFAULT_MIN_BID_INCREMENT_PERCENTAGE = 1;

  private final Spec spec;
  private final NetworkConfig networkConfig;
  private final DiscoveryConfig discoveryConfig;
  private final GossipConfigurator gossipConfigurator;
  private final NetworkingSpecConfig networkingSpecConfig;

  private final GossipEncoding gossipEncoding;
  private final int targetSubnetSubscriberCount;
  private final boolean subscribeAllSubnetsEnabled;
  private final int custodyGroupCountOverride;
  private final OptionalInt dasPublishWithholdColumnsEverySlots;
  private final boolean dasDisableElRecovery;
  private final int historicalDataMaxConcurrentQueries;
  private final int historicalDataMaxQueryQueueSize;
  private final int peerBlocksRateLimit;
  private final int peerBlobSidecarsRateLimit;
  private final int peerRequestLimit;
  private final int batchVerifyMaxThreads;
  private final int batchVerifyQueueCapacity;
  private final int batchVerifyMaxBatchSize;
  private final boolean batchVerifyStrictThreadLimitEnabled;
  private final boolean isGossipBlobsAfterBlockEnabled;
  private final boolean isGossipSnappyAircompressorEnabled;
  private final boolean isRpcSnappyAircompressorEnabled;
  private final boolean allTopicsFilterEnabled;
  private final int sidecarRecoveryTimeout;
  private final int sidecarDownloadTimeout;
  private final double sidecarRetrievalOverlapFraction;
  private final int sidecarSyncBatchSize;
  private final int sidecarSyncPollPeriod;
  private final boolean columnsDataAvailabilityHalfCheckEnabled;
  private final boolean executionProofTopicEnabled;
  private final boolean subscribeAllCustodySubnetsEnabled;
  private final int minBidIncrementPercentage;

  private P2PConfig(
      final Spec spec,
      final NetworkConfig networkConfig,
      final DiscoveryConfig discoveryConfig,
      final GossipConfigurator gossipConfigurator,
      final GossipEncoding gossipEncoding,
      final int targetSubnetSubscriberCount,
      final boolean subscribeAllSubnetsEnabled,
      final int custodyGroupCountOverride,
      final OptionalInt dasPublishWithholdColumnsEverySlots,
      final boolean dasDisableElRecovery,
      final int historicalDataMaxConcurrentQueries,
      final int historicalDataMaxQueryQueueSize,
      final int peerBlocksRateLimit,
      final int peerBlobSidecarsRateLimit,
      final int peerRequestLimit,
      final int batchVerifyMaxThreads,
      final int batchVerifyQueueCapacity,
      final int batchVerifyMaxBatchSize,
      final boolean batchVerifyStrictThreadLimitEnabled,
      final boolean allTopicsFilterEnabled,
      final boolean isGossipBlobsAfterBlockEnabled,
      final boolean isGossipSnappyAircompressorEnabled,
      final boolean isRpcSnappyAircompressorEnabled,
      final int sidecarRecoveryTimeout,
      final int sidecarDownloadTimeout,
      final double sidecarRetrievalOverlapFraction,
      final Integer sidecarSyncBatchSize,
      final Integer sidecarSyncPollPeriod,
      final boolean columnsDataAvailabilityHalfCheckEnabled,
      final boolean executionProofTopicEnabled,
      final boolean subscribeAllCustodySubnetsEnabled,
      final int minBidIncrementPercentage) {
    this.spec = spec;
    this.networkConfig = networkConfig;
    this.discoveryConfig = discoveryConfig;
    this.gossipConfigurator = gossipConfigurator;
    this.gossipEncoding = gossipEncoding;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
    this.subscribeAllSubnetsEnabled = subscribeAllSubnetsEnabled;
    this.custodyGroupCountOverride = custodyGroupCountOverride;
    this.dasPublishWithholdColumnsEverySlots = dasPublishWithholdColumnsEverySlots;
    this.dasDisableElRecovery = dasDisableElRecovery;
    this.historicalDataMaxConcurrentQueries = historicalDataMaxConcurrentQueries;
    this.historicalDataMaxQueryQueueSize = historicalDataMaxQueryQueueSize;
    this.peerBlocksRateLimit = peerBlocksRateLimit;
    this.peerBlobSidecarsRateLimit = peerBlobSidecarsRateLimit;
    this.peerRequestLimit = peerRequestLimit;
    this.batchVerifyMaxThreads = batchVerifyMaxThreads;
    this.batchVerifyQueueCapacity = batchVerifyQueueCapacity;
    this.batchVerifyMaxBatchSize = batchVerifyMaxBatchSize;
    this.batchVerifyStrictThreadLimitEnabled = batchVerifyStrictThreadLimitEnabled;
    this.networkingSpecConfig = spec.getNetworkingConfig();
    this.allTopicsFilterEnabled = allTopicsFilterEnabled;
    this.isGossipBlobsAfterBlockEnabled = isGossipBlobsAfterBlockEnabled;
    this.isGossipSnappyAircompressorEnabled = isGossipSnappyAircompressorEnabled;
    this.isRpcSnappyAircompressorEnabled = isRpcSnappyAircompressorEnabled;
    this.sidecarDownloadTimeout = sidecarDownloadTimeout;
    this.sidecarRecoveryTimeout = sidecarRecoveryTimeout;
    this.sidecarRetrievalOverlapFraction = sidecarRetrievalOverlapFraction;
    this.sidecarSyncBatchSize = sidecarSyncBatchSize;
    this.sidecarSyncPollPeriod = sidecarSyncPollPeriod;
    this.columnsDataAvailabilityHalfCheckEnabled = columnsDataAvailabilityHalfCheckEnabled;
    this.executionProofTopicEnabled = executionProofTopicEnabled;
    this.subscribeAllCustodySubnetsEnabled = subscribeAllCustodySubnetsEnabled;
    this.minBidIncrementPercentage = minBidIncrementPercentage;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Spec getSpec() {
    return spec;
  }

  public NetworkConfig getNetworkConfig() {
    return networkConfig;
  }

  public DiscoveryConfig getDiscoveryConfig() {
    return discoveryConfig;
  }

  public GossipConfigurator getGossipConfigurator() {
    return gossipConfigurator;
  }

  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
  }

  public int getTargetSubnetSubscriberCount() {
    return targetSubnetSubscriberCount;
  }

  public boolean isSubscribeAllSubnetsEnabled() {
    return subscribeAllSubnetsEnabled;
  }

  public int getTotalCustodyGroupCount(final SpecVersion specVersion) {
    final SpecConfigFulu specConfig = SpecConfigFulu.required(specVersion.getConfig());
    final int minCustodyGroupRequirement = specConfig.getCustodyRequirement();
    final int maxGroups = specConfig.getNumberOfCustodyGroups();
    return Integer.min(
        maxGroups, Integer.max(minCustodyGroupRequirement, custodyGroupCountOverride));
  }

  public OptionalInt getDasPublishWithholdColumnsEverySlots() {
    return dasPublishWithholdColumnsEverySlots;
  }

  public boolean isDasDisableElRecovery() {
    return dasDisableElRecovery;
  }

  public int getHistoricalDataMaxConcurrentQueries() {
    return historicalDataMaxConcurrentQueries;
  }

  public int getHistoricalDataMaxQueryQueueSize() {
    return historicalDataMaxQueryQueueSize;
  }

  public int getPeerBlocksRateLimit() {
    return peerBlocksRateLimit;
  }

  public int getPeerBlobSidecarsRateLimit() {
    return peerBlobSidecarsRateLimit;
  }

  public int getPeerRequestLimit() {
    return peerRequestLimit;
  }

  public int getBatchVerifyMaxThreads() {
    return batchVerifyMaxThreads;
  }

  public int getBatchVerifyMaxBatchSize() {
    return batchVerifyMaxBatchSize;
  }

  public int getBatchVerifyQueueCapacity() {
    return batchVerifyQueueCapacity;
  }

  public boolean isBatchVerifyStrictThreadLimitEnabled() {
    return batchVerifyStrictThreadLimitEnabled;
  }

  public NetworkingSpecConfig getNetworkingSpecConfig() {
    return networkingSpecConfig;
  }

  public boolean isAllTopicsFilterEnabled() {
    return allTopicsFilterEnabled;
  }

  public boolean isExecutionProofTopicEnabled() {
    return executionProofTopicEnabled;
  }

  public boolean isGossipBlobsAfterBlockEnabled() {
    return isGossipBlobsAfterBlockEnabled;
  }

  public boolean isGossipSnappyAircompressorEnabled() {
    return isGossipSnappyAircompressorEnabled;
  }

  public boolean isRpcSnappyAircompressorEnabled() {
    return isRpcSnappyAircompressorEnabled;
  }

  public int getSidecarRecoveryTimeout() {
    return sidecarRecoveryTimeout;
  }

  public int getSidecarDownloadTimeout() {
    return sidecarDownloadTimeout;
  }

  public double getSidecarRetrievalOverlapFraction() {
    return sidecarRetrievalOverlapFraction;
  }

  public int getSidecarSyncBatchSize() {
    return sidecarSyncBatchSize;
  }

  public int getSidecarSyncPollPeriod() {
    return sidecarSyncPollPeriod;
  }

  public boolean isColumnsDataAvailabilityHalfCheckEnabled() {
    return columnsDataAvailabilityHalfCheckEnabled;
  }

  public boolean isSubscribedToAllCustodySubnetsEnabled() {
    return subscribeAllCustodySubnetsEnabled;
  }

  public int getMinBidIncrementPercentage() {
    return minBidIncrementPercentage;
  }

  public static class Builder {
    private final NetworkConfig.Builder networkConfig = NetworkConfig.builder();
    private final DiscoveryConfig.Builder discoveryConfig = DiscoveryConfig.builder();

    private Spec spec;
    private Boolean isGossipScoringEnabled = DEFAULT_GOSSIP_SCORING_ENABLED;
    private boolean gossipSnappyAircompressorEnabled = DEFAULT_GOSSIP_SNAPPY_AIRCOMPRESSOR_ENABLED;
    private boolean rpcSnappyAircompressorEnabled = DEFAULT_RPC_SNAPPY_AIRCOMPRESSOR_ENABLED;
    private Integer targetSubnetSubscriberCount = DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT;
    private Boolean subscribeAllSubnetsEnabled = DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED;
    private Boolean subscribeAllCustodySubnetsEnabled = DEFAULT_SUBSCRIBE_ALL_SUBNETS_ENABLED;
    private int custodyGroupCountOverride = DEFAULT_CUSTODY_GROUP_COUNT_OVERRIDE;
    private int dasPublishWithholdColumnsEverySlots =
        DEFAULT_DAS_PUBLISH_WITHHOLD_COLUMNS_EVERY_SLOTS;
    private boolean dasDisableElRecovery = DEFAULT_DAS_DISABLE_EL_RECOVERY;
    private int historicalDataMaxConcurrentQueries = DEFAULT_HISTORICAL_DATA_MAX_CONCURRENT_QUERIES;
    private int historicalDataMaxQueryQueueSize = DEFAULT_HISTORICAL_MAX_QUERY_QUEUE_SIZE;
    private Integer peerBlocksRateLimit = DEFAULT_PEER_BLOCKS_RATE_LIMIT;
    private Integer peerBlobSidecarsRateLimit = DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT;
    private Integer peerRequestLimit = DEFAULT_PEER_REQUEST_LIMIT;
    private int batchVerifyMaxThreads = DEFAULT_BATCH_VERIFY_MAX_THREADS;
    private OptionalInt batchVerifyQueueCapacity = OptionalInt.empty();
    private int batchVerifyMaxBatchSize = DEFAULT_BATCH_VERIFY_MAX_BATCH_SIZE;
    private boolean batchVerifyStrictThreadLimitEnabled =
        DEFAULT_BATCH_VERIFY_STRICT_THREAD_LIMIT_ENABLED;
    private boolean allTopicsFilterEnabled = DEFAULT_PEER_ALL_TOPIC_FILTER_ENABLED;
    private int floodPublishMaxMessageSizeThreshold =
        DEFAULT_FLOOD_PUBLISH_MAX_MESSAGE_SIZE_THRESHOLD;
    private boolean gossipBlobsAfterBlockEnabled = DEFAULT_GOSSIP_BLOBS_AFTER_BLOCK_ENABLED;
    private boolean executionProofTopicEnabled = DEFAULT_EXECUTION_PROOF_GOSSIP_ENABLED;
    private Integer sidecarRecoveryTimeout = DEFAULT_RECOVERY_TIMEOUT_MS;
    private Integer sidecarDownloadTimeout = DEFAULT_DOWNLOAD_TIMEOUT_MS;
    private Double sidecarRetrievalOverlapFraction = DEFAULT_SIDECAR_RETRIEVAL_OVERLAP_FRACTION;

    private boolean columnsDataAvailabilityHalfCheckEnabled =
        DEFAULT_COLUMNS_DATA_AVAILABILITY_HALF_CHECK_ENABLED;
    private Integer sidecarSyncBatchSize = DEFAULT_COLUMN_CUSTODY_BACKFILLER_BATCH_SIZE;
    private Integer sidecarSyncPollPeriod = DEFAULT_COLUMN_CUSTODY_BACKFILLER_POLL_PERIOD_SECONDS;
    private Integer minBidIncrementPercentage = DEFAULT_MIN_BID_INCREMENT_PERCENTAGE;

    private Builder() {}

    public P2PConfig build() {
      validate();

      if (!gossipSnappyAircompressorEnabled) {
        LOG.warn("Gossip snappy encoding is using snappy-java; aircompressor is disabled");
      }
      if (!rpcSnappyAircompressorEnabled) {
        LOG.warn("RPC snappy encoding is using snappy-java; aircompressor is disabled");
      }

      final GossipConfigurator gossipConfigurator =
          isGossipScoringEnabled
              ? GossipConfigurator.scoringEnabled(spec)
              : GossipConfigurator.NOOP;
      final GossipEncoding gossipEncoding =
          gossipSnappyAircompressorEnabled
              ? GossipEncoding.SSZ_SNAPPY_AIRCOMPRESSOR
              : GossipEncoding.SSZ_SNAPPY;
      final SpecConfig specConfig = spec.getGenesisSpecConfig();
      final Eth2Context eth2Context =
          Eth2Context.builder()
              .activeValidatorCount(specConfig.getMinGenesisActiveValidatorCount())
              .gossipEncoding(gossipEncoding)
              .build();
      networkConfig.gossipConfig(
          builder -> {
            gossipConfigurator.configure(builder, eth2Context);
            builder.seenTTL(
                Duration.ofSeconds(
                    (long) specConfig.getSecondsPerSlot() * specConfig.getSlotsPerEpoch() * 2));
            builder.floodPublishMaxMessageSizeThreshold(floodPublishMaxMessageSizeThreshold);
          });

      final NetworkConfig networkConfig = this.networkConfig.build();
      discoveryConfig.listenUdpPortDefault(networkConfig.getListenPort());
      discoveryConfig.listenUdpPortIpv6Default(networkConfig.getListenPortIpv6());
      discoveryConfig.advertisedUdpPortDefault(networkConfig.getOptionalAdvertisedPort());
      discoveryConfig.advertisedUdpPortIpv6Default(networkConfig.getOptionalAdvertisedPortIpv6());

      if (subscribeAllCustodySubnetsEnabled) {
        custodyGroupCountOverride = Integer.MAX_VALUE;
      }
      final OptionalInt dasPublishWithholdColumnsEverySlotsOptional;
      if (dasPublishWithholdColumnsEverySlots == DEFAULT_DAS_PUBLISH_WITHHOLD_COLUMNS_EVERY_SLOTS) {
        dasPublishWithholdColumnsEverySlotsOptional = OptionalInt.empty();
      } else {
        dasPublishWithholdColumnsEverySlotsOptional =
            OptionalInt.of(dasPublishWithholdColumnsEverySlots);
      }

      return new P2PConfig(
          spec,
          networkConfig,
          discoveryConfig.build(),
          gossipConfigurator,
          gossipEncoding,
          targetSubnetSubscriberCount,
          subscribeAllSubnetsEnabled,
          custodyGroupCountOverride,
          dasPublishWithholdColumnsEverySlotsOptional,
          dasDisableElRecovery,
          historicalDataMaxConcurrentQueries,
          historicalDataMaxQueryQueueSize,
          peerBlocksRateLimit,
          peerBlobSidecarsRateLimit,
          peerRequestLimit,
          batchVerifyMaxThreads,
          batchVerifyQueueCapacity.orElse(DEFAULT_BATCH_VERIFY_QUEUE_CAPACITY),
          batchVerifyMaxBatchSize,
          batchVerifyStrictThreadLimitEnabled,
          allTopicsFilterEnabled,
          gossipBlobsAfterBlockEnabled,
          gossipSnappyAircompressorEnabled,
          rpcSnappyAircompressorEnabled,
          sidecarRecoveryTimeout,
          sidecarDownloadTimeout,
          sidecarRetrievalOverlapFraction,
          sidecarSyncBatchSize,
          sidecarSyncPollPeriod,
          columnsDataAvailabilityHalfCheckEnabled,
          executionProofTopicEnabled,
          subscribeAllCustodySubnetsEnabled,
          minBidIncrementPercentage);
    }

    private void validate() {
      checkNotNull(spec);
    }

    public Builder network(final Consumer<NetworkConfig.Builder> consumer) {
      consumer.accept(networkConfig);
      return this;
    }

    public Builder discovery(final Consumer<DiscoveryConfig.Builder> consumer) {
      consumer.accept(discoveryConfig);
      return this;
    }

    public Builder specProvider(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
    }

    public Builder isGossipScoringEnabled(final Boolean gossipScoringEnabled) {
      checkNotNull(gossipScoringEnabled);
      isGossipScoringEnabled = gossipScoringEnabled;
      return this;
    }

    public Builder targetSubnetSubscriberCount(final Integer targetSubnetSubscriberCount) {
      checkNotNull(targetSubnetSubscriberCount);
      if (targetSubnetSubscriberCount < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid targetSubnetSubscriberCount: %d", targetSubnetSubscriberCount));
      }
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public Builder subscribeAllSubnetsEnabled(final Boolean subscribeAllSubnetsEnabled) {
      checkNotNull(subscribeAllSubnetsEnabled);
      this.subscribeAllSubnetsEnabled = subscribeAllSubnetsEnabled;
      return this;
    }

    public Builder custodyGroupCountOverride(final int custodyGroupCountOverride) {
      this.custodyGroupCountOverride = custodyGroupCountOverride;
      return this;
    }

    public Builder dasPublishWithholdColumnsEverySlots(
        final int dasPublishWithholdColumnsEverySlots) {
      this.dasPublishWithholdColumnsEverySlots = dasPublishWithholdColumnsEverySlots;
      return this;
    }

    public Builder dasDisableElRecovery(final boolean dasDisableElRecovery) {
      this.dasDisableElRecovery = dasDisableElRecovery;
      return this;
    }

    public Builder historicalDataMaxConcurrentQueries(
        final int historicalDataMaxConcurrentQueries) {
      this.historicalDataMaxConcurrentQueries = historicalDataMaxConcurrentQueries;
      return this;
    }

    public Builder historicalDataMaxQueryQueueSize(final int historicalDataMaxQueryQueueSize) {
      this.historicalDataMaxQueryQueueSize = historicalDataMaxQueryQueueSize;
      return this;
    }

    public Builder subscribeAllCustodySubnetsEnabled(
        final Boolean subscribeAllCustodySubnetsEnabled) {
      checkNotNull(subscribeAllCustodySubnetsEnabled);
      this.subscribeAllCustodySubnetsEnabled = subscribeAllCustodySubnetsEnabled;
      return this;
    }

    public Builder peerBlocksRateLimit(final Integer peerBlocksRateLimit) {
      checkNotNull(peerBlocksRateLimit);
      if (peerBlocksRateLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid peerBlocksRateLimit: %d", peerBlocksRateLimit));
      }
      this.peerBlocksRateLimit = peerBlocksRateLimit;
      return this;
    }

    public Builder peerBlobSidecarsRateLimit(final Integer peerBlobSidecarsRateLimit) {
      checkNotNull(peerBlobSidecarsRateLimit);
      if (peerBlobSidecarsRateLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid peerBlobSidecarsRateLimit: %d", peerBlobSidecarsRateLimit));
      }
      this.peerBlobSidecarsRateLimit = peerBlobSidecarsRateLimit;
      return this;
    }

    public Builder peerRequestLimit(final Integer peerRequestLimit) {
      checkNotNull(peerRequestLimit);
      if (peerRequestLimit < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid peerRequestLimit: %d", peerRequestLimit));
      }
      this.peerRequestLimit = peerRequestLimit;
      return this;
    }

    public Builder floodPublishMaxMessageSizeThreshold(
        final int floodPublishMaxMessageSizeThreshold) {
      this.floodPublishMaxMessageSizeThreshold = floodPublishMaxMessageSizeThreshold;
      return this;
    }

    public Builder gossipBlobsAfterBlockEnabled(final boolean gossipBlobsAfterBlockEnabled) {
      this.gossipBlobsAfterBlockEnabled = gossipBlobsAfterBlockEnabled;
      return this;
    }

    public Builder gossipSnappyAircompressorEnabled(
        final boolean gossipSnappyAircompressorEnabled) {
      this.gossipSnappyAircompressorEnabled = gossipSnappyAircompressorEnabled;
      return this;
    }

    public Builder rpcSnappyAircompressorEnabled(final boolean rpcSnappyAircompressorEnabled) {
      this.rpcSnappyAircompressorEnabled = rpcSnappyAircompressorEnabled;
      return this;
    }

    public Builder batchVerifyMaxThreads(final int batchVerifyMaxThreads) {
      if (batchVerifyMaxThreads < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid batchVerifyMaxThreads: %d", batchVerifyMaxThreads));
      }
      this.batchVerifyMaxThreads = batchVerifyMaxThreads;
      return this;
    }

    public Builder batchVerifyQueueCapacityIfDefault(final int batchVerifyQueueCapacity) {
      if (this.batchVerifyQueueCapacity.isEmpty()) {
        return this.batchVerifyQueueCapacity(batchVerifyQueueCapacity);
      }
      return this;
    }

    public Builder batchVerifyQueueCapacity(final int batchVerifyQueueCapacity) {
      if (batchVerifyQueueCapacity < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid batchVerifyQueueCapacity: %d", batchVerifyQueueCapacity));
      }
      this.batchVerifyQueueCapacity = OptionalInt.of(batchVerifyQueueCapacity);
      return this;
    }

    public Builder batchVerifyMaxBatchSize(final int batchVerifyMaxBatchSize) {
      if (batchVerifyMaxBatchSize < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid batchVerifyMaxBatchSize: %d", batchVerifyMaxBatchSize));
      }
      this.batchVerifyMaxBatchSize = batchVerifyMaxBatchSize;
      return this;
    }

    public Builder batchVerifyStrictThreadLimitEnabled(
        final boolean batchVerifyStrictThreadLimitEnabled) {
      this.batchVerifyStrictThreadLimitEnabled = batchVerifyStrictThreadLimitEnabled;
      return this;
    }

    public Builder allTopicsFilterEnabled(final boolean allTopicsFilterEnabled) {
      this.allTopicsFilterEnabled = allTopicsFilterEnabled;
      return this;
    }

    public Builder executionProofTopicEnabled(final boolean executionProofTopicEnabled) {
      this.executionProofTopicEnabled = executionProofTopicEnabled;
      return this;
    }

    public Builder sidecarRecoveryTimeout(final Integer sidecarRecoveryTimeout) {
      this.sidecarRecoveryTimeout = sidecarRecoveryTimeout;
      return this;
    }

    public Builder sidecarDownloadTimeout(final Integer sidecarDownloadTimeout) {
      this.sidecarDownloadTimeout = sidecarDownloadTimeout;
      return this;
    }

    public Builder sidecarRetrievalOverlapFraction(final Double sidecarRetrievalOverlapFraction) {
      checkNotNull(sidecarRetrievalOverlapFraction);
      if (sidecarRetrievalOverlapFraction < 0.0 || sidecarRetrievalOverlapFraction > 1.0) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid sidecarRetrievalOverlapFraction: %s (must be within [0, 1])",
                sidecarRetrievalOverlapFraction));
      }
      this.sidecarRetrievalOverlapFraction = sidecarRetrievalOverlapFraction;
      return this;
    }

    public Builder sidecarSyncBatchSize(final Integer sidecarSyncBatchSize) {
      this.sidecarSyncBatchSize = sidecarSyncBatchSize;
      return this;
    }

    public Builder sidecarSyncPollPeriod(final Integer sidecarSyncPollPeriod) {
      this.sidecarSyncPollPeriod = sidecarSyncPollPeriod;
      return this;
    }

    public Builder columnsDataAvailabilityHalfCheckEnabled(
        final boolean columnsDataAvailabilityHalfCheckEnabled) {
      this.columnsDataAvailabilityHalfCheckEnabled = columnsDataAvailabilityHalfCheckEnabled;
      return this;
    }

    public Builder minBidIncrementPercentage(final int minBidIncrementPercentage) {
      this.minBidIncrementPercentage = minBidIncrementPercentage;
      return this;
    }
  }
}

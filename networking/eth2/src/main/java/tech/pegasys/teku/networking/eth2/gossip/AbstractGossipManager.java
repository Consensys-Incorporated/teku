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

package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractGossipManager<T extends SszData> implements GossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;

  private final Eth2TopicHandler<T> topicHandler;

  private Optional<TopicChannel> channel = Optional.empty();
  private final GossipFailureLogger gossipFailureLogger;
  private final Function<T, Optional<UInt64>> getSlotForMessage;

  protected AbstractGossipManager(
      final RecentChainData recentChainData,
      final GossipTopicName topicName,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<T> processor,
      final SszSchema<T> gossipType,
      final Function<T, Optional<UInt64>> getSlotForMessage,
      final Function<T, UInt64> getEpochForMessage,
      final NetworkingSpecConfig networkingConfig,
      final GossipFailureLogger gossipFailureLogger,
      final DebugDataDumper debugDataDumper) {
    this(
        recentChainData,
        topicName,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        gossipType,
        getSlotForMessage,
        getEpochForMessage,
        networkingConfig,
        gossipFailureLogger,
        debugDataDumper,
        Integer.MAX_VALUE,
        Duration.ZERO,
        new NoOpMetricsSystem());
  }

  protected AbstractGossipManager(
      final RecentChainData recentChainData,
      final GossipTopicName topicName,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<T> processor,
      final SszSchema<T> gossipType,
      final Function<T, Optional<UInt64>> getSlotForMessage,
      final Function<T, UInt64> getEpochForMessage,
      final NetworkingSpecConfig networkingConfig,
      final GossipFailureLogger gossipFailureLogger,
      final DebugDataDumper debugDataDumper,
      final int maxInFlightMessages,
      final Duration inFlightTimeout,
      final MetricsSystem metricsSystem) {
    this.gossipNetwork = gossipNetwork;
    this.topicHandler =
        new Eth2TopicHandler<>(
            recentChainData,
            asyncRunner,
            processor,
            gossipEncoding,
            forkDigest,
            topicName.toString(),
            new OperationMilestoneValidator<>(
                recentChainData.getSpec(), forkInfo.getFork(), getEpochForMessage),
            gossipType,
            networkingConfig,
            debugDataDumper,
            maxInFlightMessages,
            inFlightTimeout,
            metricsSystem
                .createLabelledCounter(
                    TekuMetricCategory.NETWORK,
                    "gossip_messages_in_flight_limit_discarded_total",
                    "Total number of gossip messages discarded because too many messages for the topic were already being processed",
                    "topic")
                .labels(topicName.toString()));
    if (maxInFlightMessages != Integer.MAX_VALUE) {
      metricsSystem
          .createLabelledSuppliedGauge(
              TekuMetricCategory.NETWORK,
              "gossip_messages_in_flight",
              "Number of gossip messages currently being processed for the topic",
              "topic")
          .labels(topicHandler::getInFlightMessageCount, topicName.toString());
    }
    this.gossipEncoding = gossipEncoding;
    this.gossipFailureLogger = gossipFailureLogger;
    this.getSlotForMessage = getSlotForMessage;
  }

  @VisibleForTesting
  public Eth2TopicHandler<T> getTopicHandler() {
    return topicHandler;
  }

  protected void publishMessage(final T message) {
    publishMessageWithFeedback(message).finishStackTrace();
  }

  /**
   * This method is designed to return a future that only completes successfully whenever the gossip
   * was succeeded (sent to at least one peer) or failed.
   */
  protected SafeFuture<Void> publishMessageWithFeedback(final T message) {
    return channel
        .map(c -> c.gossip(gossipEncoding.encode(message)))
        .orElse(SafeFuture.failedFuture(new IllegalStateException("Gossip channel not available")))
        .handle(
            (__, err) -> {
              if (err != null) {
                gossipFailureLogger.log(err, getSlotForMessage.apply(message));
              } else {
                LOG.trace(
                    "Successfully gossiped message with root {} on {}",
                    message::hashTreeRoot,
                    topicHandler::getTopic);
              }
              return null;
            });
  }

  @Override
  public void subscribe() {
    if (channel.isEmpty()) {
      channel = Optional.of(gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler));
    }
  }

  @Override
  public void unsubscribe() {
    if (channel.isPresent()) {
      channel.get().close();
      channel = Optional.empty();
    }
  }
}

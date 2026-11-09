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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.PubsubMessage;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;

public class GossipHandler implements Function<MessageApi, CompletableFuture<ValidationResult>> {
  private static final Logger LOG = LogManager.getLogger();

  private static final SafeFuture<ValidationResult> VALIDATION_FAILED =
      SafeFuture.completedFuture(ValidationResult.Invalid);

  private final Topic topic;
  private final PubsubPublisherApi publisher;
  private final TopicHandler handler;
  private final Counter messageCounter;

  public GossipHandler(
      final MetricsSystem metricsSystem,
      final Topic topic,
      final PubsubPublisherApi publisher,
      final TopicHandler handler) {
    this.topic = topic;
    this.publisher = publisher;
    this.handler = handler;
    this.messageCounter =
        metricsSystem
            .createLabelledCounter(
                TekuMetricCategory.LIBP2P,
                "gossip_messages_total",
                "Total number of gossip messages received (avoid libp2p deduplication)",
                "topic")
            .labels(topic.getTopic());
  }

  @Override
  public SafeFuture<ValidationResult> apply(final MessageApi message) {
    messageCounter.inc();
    final int messageSize = message.getData().readableBytes();
    final int maxMessageSize = handler.getMaxMessageSize();
    if (messageSize > maxMessageSize) {
      LOG.trace(
          "Rejecting gossip message of length {} which exceeds maximum size of {}",
          messageSize,
          maxMessageSize);
      return VALIDATION_FAILED;
    }
    LOG.trace("Received message for topic {}", topic);

    final PubsubMessage pubsubMessage = message.getOriginalMessage();
    if (!(pubsubMessage instanceof PreparedPubsubMessage gossipPubsubMessage)) {
      throw new IllegalArgumentException(
          "Don't know this PubsubMessage implementation: " + pubsubMessage.getClass());
    }
    return handler.handleMessage(gossipPubsubMessage.getPreparedMessage());
  }

  public SafeFuture<Void> gossip(final Bytes bytes) {
    LOG.trace("Gossiping {}: {} bytes", topic, bytes.size());
    return SafeFuture.of(publisher.publish(Unpooled.wrappedBuffer(bytes.toArrayUnsafe()), topic))
        .thenRun(() -> LOG.trace("Successfully gossiped message on {}", topic));
  }

  public String getTopic() {
    return topic.getTopic();
  }
}

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

import io.libp2p.core.pubsub.PubsubSubscription;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

public class LibP2PTopicChannel implements TopicChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final GossipHandler topicHandler;
  private final PubsubSubscription subscription;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public LibP2PTopicChannel(
      final GossipHandler topicHandler, final PubsubSubscription subscription) {
    this.topicHandler = topicHandler;
    this.subscription = subscription;
  }

  @Override
  public SafeFuture<Void> gossip(final Bytes data) {
    if (closed.get()) {
      return SafeFuture.failedFuture(new IllegalStateException("Topic channel is closed"));
    }
    return topicHandler.gossip(data);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      LOG.trace("Unsubscribe from topic: {}", topicHandler.getTopic());
      subscription.unsubscribe();
    }
  }
}

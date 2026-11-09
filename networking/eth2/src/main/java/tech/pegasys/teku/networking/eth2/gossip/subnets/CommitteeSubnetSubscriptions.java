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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

abstract class CommitteeSubnetSubscriptions {

  protected final GossipNetwork gossipNetwork;
  protected final GossipEncoding gossipEncoding;

  private final Int2ObjectMap<RequestedSubscription> subnetIdToSubscription =
      new Int2ObjectOpenHashMap<>();
  private boolean subscribed = false;

  protected CommitteeSubnetSubscriptions(
      final GossipNetwork gossipNetwork, final GossipEncoding gossipEncoding) {
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
  }

  protected synchronized Optional<TopicChannel> getChannelForSubnet(final int subnetId) {
    return Optional.ofNullable(subnetIdToSubscription.getOrDefault(subnetId, null))
        .flatMap(RequestedSubscription::getChannel);
  }

  public synchronized void subscribeToSubnetId(final int subnetId) {
    final RequestedSubscription subscription =
        subnetIdToSubscription.computeIfAbsent(subnetId, this::createChannelForSubnetId);
    // Don't subscribe if we've been told to unsubscribe from everything
    if (subscribed) {
      subscription.subscribe();
    }
  }

  public synchronized void unsubscribeFromSubnetId(final int subnetId) {
    final RequestedSubscription subscription = subnetIdToSubscription.remove(subnetId);
    if (subscription != null) {
      subscription.unsubscribe();
    }
  }

  private RequestedSubscription createChannelForSubnetId(final int subnetId) {
    final Eth2TopicHandler<?> topicHandler = createTopicHandler(subnetId);
    return new RequestedSubscription(topicHandler);
  }

  protected abstract Eth2TopicHandler<?> createTopicHandler(final int subnetId);

  public synchronized void subscribe() {
    if (subscribed) {
      return;
    }
    subscribed = true;
    subnetIdToSubscription.values().forEach(RequestedSubscription::subscribe);
  }

  public synchronized void unsubscribe() {
    if (!subscribed) {
      return;
    }
    subscribed = false;
    subnetIdToSubscription.values().forEach(RequestedSubscription::unsubscribe);
  }

  @VisibleForTesting
  public Eth2TopicHandler<?> getSubscribedTopicHandler(final int subnetId) {
    return subnetIdToSubscription.get(subnetId).topicHandler;
  }

  private class RequestedSubscription {
    private final Eth2TopicHandler<?> topicHandler;
    private Optional<TopicChannel> channel = Optional.empty();

    private RequestedSubscription(final Eth2TopicHandler<?> topicHandler) {
      this.topicHandler = topicHandler;
    }

    public void subscribe() {
      if (channel.isPresent()) {
        return;
      }
      channel = Optional.of(gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler));
    }

    public void unsubscribe() {
      channel.ifPresent(TopicChannel::close);
      channel = Optional.empty();
    }

    public Optional<TopicChannel> getChannel() {
      return channel;
    }
  }
}

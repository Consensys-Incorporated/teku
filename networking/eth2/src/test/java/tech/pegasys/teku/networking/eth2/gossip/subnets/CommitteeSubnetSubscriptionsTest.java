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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.verification.VerificationMode;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

class CommitteeSubnetSubscriptionsTest {

  public static final String TOPIC_PREFIX = "some-topic";
  private final IntObjectMap<Eth2TopicHandler<?>> topicHandlers = new IntObjectHashMap<>();
  private final Map<String, TopicChannel> topicChannels = new HashMap<>();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  private final CommitteeSubnetSubscriptions subscriptions =
      new TestCommitteeSubnetSubscriptions(gossipNetwork, gossipEncoding);

  @BeforeEach
  void setUp() {
    when(gossipNetwork.subscribe(any(), any()))
        .thenAnswer(
            invocation -> {
              final String topicName = invocation.getArgument(0);
              return topicChannels.computeIfAbsent(topicName, __ -> mock(TopicChannel.class));
            });
  }

  @Test
  void shouldDelaySubscribingToSubnetsUntilSubscribeCalled() {
    subscriptions.subscribeToSubnetId(1);
    subscriptions.subscribeToSubnetId(2);

    verifyNoInteractions(gossipNetwork);

    subscriptions.subscribe();

    verifySubscribedToTopic(1);
    verifySubscribedToTopic(2);
  }

  @Test
  void shouldCloseAllTopicChannelsWhenUnsubscribeCalled() {
    subscriptions.subscribe();
    subscriptions.subscribeToSubnetId(1);
    subscriptions.subscribeToSubnetId(2);

    subscriptions.unsubscribe();

    assertThat(topicChannels).hasSize(2);
    topicChannels.values().forEach(channel -> verify(channel).close());
  }

  @Test
  void shouldResubscribeToPreviousSubscriptions() {
    subscriptions.subscribe();
    subscriptions.subscribeToSubnetId(1);
    subscriptions.subscribeToSubnetId(2);

    subscriptions.unsubscribe();

    subscriptions.subscribeToSubnetId(3);

    subscriptions.subscribe();

    verifySubscribedToTopic(1, times(2));
    verifySubscribedToTopic(2, times(2));
    verifySubscribedToTopic(3, times(1));
  }

  @Test
  void shouldNotResubscribeToSubnetsThatWereUnsubscribed() {
    subscriptions.subscribe();
    subscriptions.subscribeToSubnetId(1);
    subscriptions.subscribeToSubnetId(2);

    subscriptions.unsubscribe();

    subscriptions.unsubscribeFromSubnetId(1);

    subscriptions.subscribe();

    verifySubscribedToTopic(1, times(1));
    verifySubscribedToTopic(2, times(2));
  }

  @Test
  void shouldGetChannelForSubscribedSubnet() {
    subscriptions.subscribe();
    subscriptions.subscribeToSubnetId(1);

    assertThat(subscriptions.getChannelForSubnet(1)).contains(getTopicChannel(1));
  }

  @Test
  void shouldGetEmptyChannelForSubnetThatWasNotSubscribedTo() {
    subscriptions.subscribe();

    assertThat(subscriptions.getChannelForSubnet(1)).isEmpty();
  }

  @Test
  void shouldGetEmptyChannelForSubnetThatWasIndividuallyUnsubscribed() {
    subscriptions.subscribeToSubnetId(1);
    subscriptions.subscribe();

    subscriptions.unsubscribeFromSubnetId(1);

    assertThat(subscriptions.getChannelForSubnet(1)).isEmpty();
  }

  @Test
  void shouldGetEmptyChannelForAllSubnetsBeforeSubscribing() {
    subscriptions.subscribeToSubnetId(1);

    assertThat(subscriptions.getChannelForSubnet(1)).isEmpty(); // Requested
    assertThat(subscriptions.getChannelForSubnet(2)).isEmpty(); // Not requested
  }

  @Test
  void shouldGetEmptyChannelForAllSubnetsAfterUnsubscribing() {
    subscriptions.subscribe();
    subscriptions.subscribeToSubnetId(1);

    subscriptions.unsubscribe();

    assertThat(subscriptions.getChannelForSubnet(1)).isEmpty(); // Requested
    assertThat(subscriptions.getChannelForSubnet(2)).isEmpty(); // Not requested
  }

  @Test
  void shouldGetChannelForSubscribedSubnetsAfterResubscribing() {
    subscriptions.subscribe();
    subscriptions.subscribeToSubnetId(1);

    subscriptions.unsubscribe();
    subscriptions.subscribe();

    assertThat(subscriptions.getChannelForSubnet(1)).contains(getTopicChannel(1)); // Requested
    assertThat(subscriptions.getChannelForSubnet(2)).isEmpty(); // Not requested
  }

  private void verifySubscribedToTopic(final int subnetId) {
    verifySubscribedToTopic(subnetId, times(1));
  }

  private void verifySubscribedToTopic(final int subnetId, final VerificationMode times) {
    final Eth2TopicHandler<?> handler = getTopicHandler(subnetId);
    verify(gossipNetwork, times).subscribe(handler.getTopic(), handler);
  }

  private TopicChannel getTopicChannel(final int subnetId) {
    return topicChannels.get(getTopicHandler(subnetId).getTopic());
  }

  private Eth2TopicHandler<?> getTopicHandler(final int subnetId) {
    return topicHandlers.computeIfAbsent(
        subnetId,
        __ -> {
          final Eth2TopicHandler<?> topicHandler = mock(Eth2TopicHandler.class);
          when(topicHandler.getTopic()).thenReturn(getSubnetTopicName(subnetId));
          return topicHandler;
        });
  }

  private class TestCommitteeSubnetSubscriptions extends CommitteeSubnetSubscriptions {

    protected TestCommitteeSubnetSubscriptions(
        final GossipNetwork gossipNetwork, final GossipEncoding gossipEncoding) {
      super(gossipNetwork, gossipEncoding);
    }

    @Override
    protected Eth2TopicHandler<?> createTopicHandler(final int subnetId) {
      return getTopicHandler(subnetId);
    }
  }

  private String getSubnetTopicName(final int subnetId) {
    return TOPIC_PREFIX + "-" + subnetId;
  }
}

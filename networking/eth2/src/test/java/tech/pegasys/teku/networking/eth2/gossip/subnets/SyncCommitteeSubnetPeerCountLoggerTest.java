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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class SyncCommitteeSubnetPeerCountLoggerTest {
  private static final int TARGET_SUBSCRIBER_COUNT = 3;
  private static final int SUBNET_ID = 1;
  private static final int CONNECTED_PEER_COUNT = 20;

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);

  private final SyncCommitteeSubnetPeerCountLogger logger =
      new SyncCommitteeSubnetPeerCountLogger(timeProvider, TARGET_SUBSCRIBER_COUNT);

  @Test
  void shouldNotLogWhenAtTargetOrNotSubscribed() {
    try (final LogCaptor logCaptor = logCaptor()) {
      update(subscriptions(true, TARGET_SUBSCRIBER_COUNT));
      update(subscriptions(false, 0));

      assertThat(logCaptor.getWarnLogs()).isEmpty();
    }
  }

  @Test
  void shouldNotLogWhileNoPeersAreConnected() {
    try (final LogCaptor logCaptor = logCaptor()) {
      logger.onSubscriptionsUpdated(subscriptions(true, 0), 0);

      assertThat(logCaptor.getWarnLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogWhenSubnetIsBelowTarget() {
    try (final LogCaptor logCaptor = logCaptor()) {
      update(subscriptions(true, 2));

      assertThat(logCaptor.getWarnLogs())
          .containsExactly("Sync committee subnet 1 has 2 subscribed peers, below target of 3");
    }
  }

  @Test
  void shouldThrottleRepeatedWarningsForTheSameSubnet() {
    try (final LogCaptor logCaptor = logCaptor()) {
      update(subscriptions(true, 2));
      timeProvider.advanceTimeBySeconds(59);
      update(subscriptions(true, 2));

      assertThat(logCaptor.getWarnLogs()).hasSize(1);
    }
  }

  @Test
  void shouldWarnAgainOnceTheThrottlingPeriodHasElapsed() {
    try (final LogCaptor logCaptor = logCaptor()) {
      update(subscriptions(true, 2));
      timeProvider.advanceTimeBySeconds(60);
      update(subscriptions(true, 2));

      assertThat(logCaptor.getWarnLogs()).hasSize(2);
    }
  }

  @Test
  void shouldThrottleSubnetsIndependently() {
    try (final LogCaptor logCaptor = logCaptor()) {
      final PeerSubnetSubscriptions subscriptions = mock(PeerSubnetSubscriptions.class);
      when(subscriptions.isSyncCommitteeSubnetRelevant(anyInt())).thenReturn(true);
      when(subscriptions.getSubscriberCountForSyncCommitteeSubnet(anyInt())).thenReturn(0);

      logger.onSubscriptionsUpdated(subscriptions, CONNECTED_PEER_COUNT);

      assertThat(logCaptor.getWarnLogs()).hasSize(SYNC_COMMITTEE_SUBNET_COUNT);
    }
  }

  private void update(final PeerSubnetSubscriptions subscriptions) {
    logger.onSubscriptionsUpdated(subscriptions, CONNECTED_PEER_COUNT);
  }

  private LogCaptor logCaptor() {
    return LogCaptor.forClass(SyncCommitteeSubnetPeerCountLogger.class);
  }

  private PeerSubnetSubscriptions subscriptions(final boolean relevant, final int subscriberCount) {
    final PeerSubnetSubscriptions subscriptions = mock(PeerSubnetSubscriptions.class);
    when(subscriptions.isSyncCommitteeSubnetRelevant(anyInt())).thenReturn(false);
    when(subscriptions.isSyncCommitteeSubnetRelevant(SUBNET_ID)).thenReturn(relevant);
    when(subscriptions.getSubscriberCountForSyncCommitteeSubnet(SUBNET_ID))
        .thenReturn(subscriberCount);
    return subscriptions;
  }
}

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

import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.time.Throttler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteeSubnetPeerCountLogger {
  private static final Logger LOG = LogManager.getLogger();

  private static final UInt64 WARNING_PERIOD_SECONDS = UInt64.valueOf(60);

  private final TimeProvider timeProvider;
  private final int targetSubscriberCount;

  private final List<Throttler<Logger>> loggerThrottlerBySubnetId =
      IntStream.range(0, SYNC_COMMITTEE_SUBNET_COUNT)
          .mapToObj(__ -> new Throttler<>(LOG, WARNING_PERIOD_SECONDS))
          .toList();

  public SyncCommitteeSubnetPeerCountLogger(
      final TimeProvider timeProvider, final int targetSubscriberCount) {
    this.timeProvider = timeProvider;
    this.targetSubscriberCount = targetSubscriberCount;
  }

  public void onSubscriptionsUpdated(
      final PeerSubnetSubscriptions subscriptions, final int connectedPeerCount) {
    if (connectedPeerCount == 0) {
      return;
    }
    IntStream.range(0, SYNC_COMMITTEE_SUBNET_COUNT)
        .forEach(
            subnetId -> {
              if (!subscriptions.isSyncCommitteeSubnetRelevant(subnetId)) {
                return;
              }
              final int subscriberCount =
                  subscriptions.getSubscriberCountForSyncCommitteeSubnet(subnetId);
              if (subscriberCount >= targetSubscriberCount) {
                return;
              }
              loggerThrottlerBySubnetId
                  .get(subnetId)
                  .invoke(
                      timeProvider.getTimeInSeconds(),
                      log ->
                          log.warn(
                              "Sync committee subnet {} has {} subscribed peers, below target of {}",
                              subnetId,
                              subscriberCount,
                              targetSubscriberCount));
            });
  }
}

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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public class AttestationTopicSubscriber implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  static final String GAUGE_AGGREGATION_SUBNETS_LABEL = "aggregation_subnet_%02d";
  static final String GAUGE_PERSISTENT_SUBNETS_LABEL = "persistent_subnet_%02d";
  private final Int2ObjectMap<UInt64> subnetIdToUnsubscribeSlot = new Int2ObjectOpenHashMap<>();
  private final IntSet persistentSubnetIdSet = new IntOpenHashSet();
  private final Eth2P2PNetwork eth2P2PNetwork;
  private final Spec spec;
  private final SettableLabelledGauge subnetSubscriptionsGauge;
  private final AtomicReference<UInt64> currentSlot = new AtomicReference<>(null);

  public AttestationTopicSubscriber(
      final Spec spec,
      final Eth2P2PNetwork eth2P2PNetwork,
      final SettableLabelledGauge subnetSubscriptionsGauge) {
    this.spec = spec;
    this.eth2P2PNetwork = eth2P2PNetwork;
    this.subnetSubscriptionsGauge = subnetSubscriptionsGauge;
  }

  public synchronized void subscribeToCommitteeForAggregation(
      final int committeeIndex, final UInt64 committeesAtSlot, final UInt64 aggregationSlot) {
    final int subnetId =
        spec.computeSubnetForCommittee(
            aggregationSlot, UInt64.valueOf(committeeIndex), committeesAtSlot);
    final UInt64 currentUnsubscriptionSlot = subnetIdToUnsubscribeSlot.getOrDefault(subnetId, ZERO);
    final UInt64 unsubscribeSlot = currentUnsubscriptionSlot.max(aggregationSlot);
    final UInt64 maybeCurrentSlot = currentSlot.get();
    if (maybeCurrentSlot != null && unsubscribeSlot.isLessThan(maybeCurrentSlot)) {
      LOG.trace(
          "Skipping outdated aggregation subnet {} with unsubscribe due at slot {}",
          subnetId,
          unsubscribeSlot);
      return;
    }

    if (currentUnsubscriptionSlot.equals(ZERO)) {
      eth2P2PNetwork.subscribeToAttestationSubnetId(subnetId);
      toggleAggregateSubscriptionMetric(subnetId, false);
      LOG.trace(
          "Subscribing to aggregation subnet {} with unsubscribe due at slot {}",
          subnetId,
          unsubscribeSlot);
    } else {
      if (aggregationSlot.isGreaterThan(currentUnsubscriptionSlot)
          && persistentSubnetIdSet.contains(subnetId)) {
        toggleAggregateSubscriptionMetric(subnetId, true);
        persistentSubnetIdSet.remove(subnetId);
      }
      LOG.trace(
          "Already subscribed to aggregation subnet {}, updating unsubscription slot to {}",
          subnetId,
          unsubscribeSlot);
    }
    subnetIdToUnsubscribeSlot.put(subnetId, unsubscribeSlot);
  }

  private void togglePersistentSubscriptionMetric(final int subnetId, final boolean reset) {
    subnetSubscriptionsGauge.set(1, String.format(GAUGE_PERSISTENT_SUBNETS_LABEL, subnetId));
    if (reset) {
      subnetSubscriptionsGauge.set(0, String.format(GAUGE_AGGREGATION_SUBNETS_LABEL, subnetId));
    }
  }

  private void toggleAggregateSubscriptionMetric(final int subnetId, final boolean reset) {
    subnetSubscriptionsGauge.set(1, String.format(GAUGE_AGGREGATION_SUBNETS_LABEL, subnetId));
    if (reset) {
      subnetSubscriptionsGauge.set(0, String.format(GAUGE_PERSISTENT_SUBNETS_LABEL, subnetId));
    }
  }

  public synchronized void subscribeToPersistentSubnets(
      final Set<SubnetSubscription> newSubscriptions) {
    boolean shouldUpdateENR = false;

    for (SubnetSubscription subnetSubscription : newSubscriptions) {
      final int subnetId = subnetSubscription.subnetId();
      final UInt64 maybeCurrentSlot = currentSlot.get();
      if (maybeCurrentSlot != null
          && subnetSubscription.unsubscriptionSlot().isLessThan(maybeCurrentSlot)) {
        LOG.trace(
            "Skipping outdated persistent subnet {} with unsubscribe due at slot {}",
            subnetId,
            subnetSubscription.unsubscriptionSlot());
        continue;
      }

      shouldUpdateENR = persistentSubnetIdSet.add(subnetId) || shouldUpdateENR;
      LOG.trace(
          "Subscribing to persistent subnet {} with unsubscribe due at slot {}",
          subnetId,
          subnetSubscription.unsubscriptionSlot());
      if (subnetIdToUnsubscribeSlot.containsKey(subnetId)) {
        final UInt64 existingUnsubscriptionSlot = subnetIdToUnsubscribeSlot.get(subnetId);
        final UInt64 unsubscriptionSlot =
            existingUnsubscriptionSlot.max(subnetSubscription.unsubscriptionSlot());
        LOG.trace(
            "Already subscribed to subnet {}, updating unsubscription slot to {}",
            subnetId,
            unsubscriptionSlot);
        togglePersistentSubscriptionMetric(subnetId, true);
        subnetIdToUnsubscribeSlot.put(subnetId, unsubscriptionSlot);
      } else {
        eth2P2PNetwork.subscribeToAttestationSubnetId(subnetId);
        togglePersistentSubscriptionMetric(subnetId, false);
        LOG.trace("Subscribed to new persistent subnet {}", subnetId);
        subnetIdToUnsubscribeSlot.put(subnetId, subnetSubscription.unsubscriptionSlot());
      }
    }

    if (shouldUpdateENR) {
      eth2P2PNetwork.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
    }
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    currentSlot.set(slot);
    boolean shouldUpdateENR = false;

    final Iterator<Int2ObjectMap.Entry<UInt64>> iterator =
        subnetIdToUnsubscribeSlot.int2ObjectEntrySet().iterator();
    while (iterator.hasNext()) {
      final Int2ObjectMap.Entry<UInt64> entry = iterator.next();
      if (entry.getValue().compareTo(slot) < 0) {
        final int subnetId = entry.getIntKey();
        LOG.trace("Unsubscribing from subnet {}", subnetId);
        eth2P2PNetwork.unsubscribeFromAttestationSubnetId(subnetId);
        if (persistentSubnetIdSet.contains(subnetId)) {
          persistentSubnetIdSet.remove(subnetId);
          subnetSubscriptionsGauge.set(0, String.format(GAUGE_PERSISTENT_SUBNETS_LABEL, subnetId));
          shouldUpdateENR = true;
        } else {
          subnetSubscriptionsGauge.set(0, String.format(GAUGE_AGGREGATION_SUBNETS_LABEL, subnetId));
        }

        iterator.remove();
      }
    }

    if (shouldUpdateENR) {
      eth2P2PNetwork.setLongTermAttestationSubnetSubscriptions(persistentSubnetIdSet);
    }
  }
}

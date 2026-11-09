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

import static java.util.Collections.emptySet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public class NodeBasedStableSubnetSubscriber implements StableSubnetSubscriber {

  private static final Logger LOG = LogManager.getLogger();
  private final AttestationTopicSubscriber persistentSubnetSubscriber;
  private final NavigableSet<SubnetSubscription> subnetSubscriptions = new TreeSet<>();
  private final Spec spec;
  private final int subnetsPerNode;
  private final UInt256 discoveryNodeId;

  public NodeBasedStableSubnetSubscriber(
      final AttestationTopicSubscriber persistentSubnetSubscriber,
      final Spec spec,
      final UInt256 discoveryNodeId) {
    this.persistentSubnetSubscriber = persistentSubnetSubscriber;
    this.spec = spec;
    this.subnetsPerNode = spec.getNetworkingConfig().getSubnetsPerNode();
    this.discoveryNodeId = discoveryNodeId;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // Iterate through current subscriptions to remove the ones that have expired
    final Iterator<SubnetSubscription> iterator = subnetSubscriptions.iterator();
    while (iterator.hasNext()) {
      final SubnetSubscription subnetSubscription = iterator.next();
      if (subnetSubscription.unsubscriptionSlot().isGreaterThan(slot)) {
        break;
      }
      iterator.remove();
    }

    // Adjust the number of subscriptions
    // If there are new subscriptions, pass the new subscription set to BeaconNode
    final Set<SubnetSubscription> newSubnetSubscriptions =
        adjustNumberOfSubscriptionsToNodeRequirement(slot);
    if (!newSubnetSubscriptions.isEmpty()) {
      persistentSubnetSubscriber.subscribeToPersistentSubnets(newSubnetSubscriptions);
    }
  }

  /**
   * Adjusts the number of subscriptions to SUBNETS_PER_NODE. Returns the set of new subscriptions
   * that were added, if there were no new subscriptions, it returns an empty set.
   */
  private Set<SubnetSubscription> adjustNumberOfSubscriptionsToNodeRequirement(
      final UInt64 currentSlot) {

    if (subnetSubscriptions.size() >= subnetsPerNode) {
      return emptySet();
    }

    final List<UInt64> nodeSubscribedSubnets =
        spec.atSlot(currentSlot)
            .miscHelpers()
            .computeSubscribedSubnets(discoveryNodeId, spec.computeEpochAtSlot(currentSlot));

    LOG.trace(
        "Computed persistent subnets {}",
        nodeSubscribedSubnets.stream().map(UInt64::toString).collect(Collectors.joining(", ")));

    final Iterator<UInt64> nodeSubscribedSubnetsIterator = nodeSubscribedSubnets.iterator();

    final Set<SubnetSubscription> newSubnetSubscriptions = new HashSet<>();

    while (subnetSubscriptions.size() < subnetsPerNode) {
      final SubnetSubscription newSubnetSubscription =
          subscribeToSubnet(
              nodeSubscribedSubnetsIterator.next().intValue(),
              spec.atSlot(currentSlot)
                  .miscHelpers()
                  .calculateNodeSubnetUnsubscriptionSlot(discoveryNodeId, currentSlot));
      newSubnetSubscriptions.add(newSubnetSubscription);
    }
    return newSubnetSubscriptions;
  }

  private SubnetSubscription subscribeToSubnet(
      final int subnetId, final UInt64 unsubscriptionSlot) {
    final SubnetSubscription subnetSubscription =
        new SubnetSubscription(subnetId, unsubscriptionSlot);
    subnetSubscriptions.removeIf(subscription -> subscription.subnetId() == subnetId);
    subnetSubscriptions.add(subnetSubscription);
    return subnetSubscription;
  }
}

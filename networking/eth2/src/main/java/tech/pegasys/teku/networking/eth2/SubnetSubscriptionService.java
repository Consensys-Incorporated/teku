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

package tech.pegasys.teku.networking.eth2;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.infrastructure.subscribers.ObservableValue;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;

/**
 * Service tracks long term attestation subnet subscriptions and notifies subscribers on their
 * changes
 */
public class SubnetSubscriptionService {
  ObservableValue<Iterable<Integer>> subnetSubscriptions = new ObservableValue<>(true);

  public synchronized void setSubscriptions(final Iterable<Integer> subnetIndices) {
    subnetSubscriptions.set(subnetIndices);
  }

  public synchronized void addSubscription(final int subnet) {
    final Set<Integer> values = getSubnets();
    values.add(subnet);
    subnetSubscriptions.set(values);
  }

  public synchronized void removeSubscription(final int subnet) {
    final Set<Integer> values = getSubnets();
    values.remove(subnet);
    subnetSubscriptions.set(values);
  }

  public synchronized long subscribeToUpdates(final ValueObserver<Iterable<Integer>> observer) {
    return subnetSubscriptions.subscribe(observer);
  }

  public void unsubscribe(final long subscriptionId) {
    subnetSubscriptions.unsubscribe(subscriptionId);
  }

  public Set<Integer> getSubnets() {
    return subnetSubscriptions.get().map(Sets::newHashSet).orElseGet(HashSet::new);
  }
}

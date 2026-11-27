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

package tech.pegasys.teku.statetransition.lightclient;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;

/**
 * Passes an update on to its delegate only if it advances the last update passed on, using the same
 * rules the light client p2p spec uses for forwarding updates on gossip:
 *
 * <ul>
 *   <li>a finality update is newer if its finalized slot is higher, or if it has the same finalized
 *       slot and reaches sync committee supermajority while the previous one did not
 *   <li>an optimistic update is newer if its attested slot is higher
 * </ul>
 *
 * <p>The delegate is called while holding this filter's lock, so updates are delivered in the order
 * they were accepted. The lock is only used by this filter, so no other code is blocked.
 */
public class NewerLightClientUpdateFilter<T> implements Consumer<T> {

  private final BiPredicate<T, T> isNewer;
  private final Consumer<T> delegate;

  private Optional<T> latestDelivered = Optional.empty();

  private NewerLightClientUpdateFilter(
      final BiPredicate<T, T> isNewer, final Consumer<T> delegate) {
    this.isNewer = isNewer;
    this.delegate = delegate;
  }

  public static NewerLightClientUpdateFilter<LightClientFinalityUpdate> forFinalityUpdates(
      final Consumer<LightClientFinalityUpdate> delegate) {
    return new NewerLightClientUpdateFilter<>(
        NewerLightClientUpdateFilter::isNewerFinalityUpdate, delegate);
  }

  public static NewerLightClientUpdateFilter<LightClientOptimisticUpdate> forOptimisticUpdates(
      final Consumer<LightClientOptimisticUpdate> delegate) {
    return new NewerLightClientUpdateFilter<>(
        NewerLightClientUpdateFilter::isNewerOptimisticUpdate, delegate);
  }

  public static boolean isNewerFinalityUpdate(
      final LightClientFinalityUpdate update, final LightClientFinalityUpdate previous) {
    final UInt64 finalizedSlot = update.getFinalizedHeader().getBeacon().getSlot();
    final UInt64 previousFinalizedSlot = previous.getFinalizedHeader().getBeacon().getSlot();
    if (!finalizedSlot.equals(previousFinalizedSlot)) {
      return finalizedSlot.isGreaterThan(previousFinalizedSlot);
    }
    return LightClientUpdateStore.hasSupermajority(update.getSyncAggregate())
        && !LightClientUpdateStore.hasSupermajority(previous.getSyncAggregate());
  }

  public static boolean isNewerOptimisticUpdate(
      final LightClientOptimisticUpdate update, final LightClientOptimisticUpdate previous) {
    return update
        .getAttestedHeader()
        .getBeacon()
        .getSlot()
        .isGreaterThan(previous.getAttestedHeader().getBeacon().getSlot());
  }

  @Override
  public synchronized void accept(final T update) {
    if (latestDelivered.isPresent() && !isNewer.test(update, latestDelivered.get())) {
      return;
    }
    latestDelivered = Optional.of(update);
    delegate.accept(update);
  }
}

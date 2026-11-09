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

package tech.pegasys.teku.storage.server.state;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.Database;

public class FinalizedStateCache {

  private static final Logger LOG = LogManager.getLogger(FinalizedStateCache.class);

  private static final long MAX_REGENERATE_SLOTS = 10_000L;

  /**
   * Note this is a best effort basis to track what states are cached. Slots are added here slightly
   * before the stateCache is actually updated and removed slightly after they are evicted from the
   * cache.
   */
  private final NavigableSet<UInt64> availableSlots = new ConcurrentSkipListSet<>();

  private final LoadingCache<UInt64, BeaconState> stateCache;

  public FinalizedStateCache(
      final Spec spec,
      final Database database,
      final int maximumCacheSize,
      final boolean useSoftReferences,
      final int stateRebuildTimeoutSeconds) {
    this(
        spec,
        database,
        maximumCacheSize,
        useSoftReferences,
        stateRebuildTimeoutSeconds,
        MAX_REGENERATE_SLOTS);
  }

  FinalizedStateCache(
      final Spec spec,
      final Database database,
      final int maximumCacheSize,
      final boolean useSoftReferences,
      final int stateRebuildTimeoutSeconds,
      final long maxRegenerateSlots) {
    final CacheBuilder<UInt64, BeaconState> cacheBuilder =
        CacheBuilder.newBuilder()
            .maximumSize(maximumCacheSize)
            .removalListener(this::onRemovedFromCache);
    if (useSoftReferences) {
      cacheBuilder.softValues();
    }
    this.stateCache =
        cacheBuilder.build(
            new StateCacheLoader(
                spec, database, stateRebuildTimeoutSeconds, maxRegenerateSlots, this));
  }

  private void onRemovedFromCache(
      final RemovalNotification<UInt64, BeaconState> removalNotification) {
    if (removalNotification.getCause() != RemovalCause.REPLACED) {
      availableSlots.remove(removalNotification.getKey());
    }
  }

  public Optional<BeaconState> getFinalizedState(final UInt64 slot) {
    try {
      return Optional.of(stateCache.getUnchecked(slot));
    } catch (final UncheckedExecutionException e) {
      if (Throwables.getRootCause(e) instanceof StateUnavailableException) {
        return Optional.empty();
      }
      LOG.error("Error while regenerating state for slot {}", slot, e);
      throw new RuntimeException("Error while regenerating state", e);
    }
  }

  Optional<BeaconState> getLatestStateFromCache(final UInt64 slot) {
    return Optional.ofNullable(availableSlots.floor(slot)).map(stateCache::getIfPresent);
  }

  NavigableSet<UInt64> getAvailableSlots() {
    return availableSlots;
  }

  /**
   * Cache doesn't allow returning null but we may not be able to regenerate a state so throw this
   * exception and catch it in {@link #getFinalizedState(UInt64)}
   */
  static class StateUnavailableException extends RuntimeException {}
}

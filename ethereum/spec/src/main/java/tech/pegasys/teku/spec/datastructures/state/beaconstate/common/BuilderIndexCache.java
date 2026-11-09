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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

public class BuilderIndexCache {
  private final Cache<BLSPublicKey, Integer> builderIndices;
  private final AtomicInteger lastCachedIndex;

  private static final int INDEX_NONE = -1;
  public static final BuilderIndexCache NO_OP_INSTANCE =
      new BuilderIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE);

  @VisibleForTesting
  BuilderIndexCache(final Cache<BLSPublicKey, Integer> builderIndices, final int lastCachedIndex) {
    this.builderIndices = builderIndices;
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
  }

  public BuilderIndexCache() {
    this.builderIndices = LRUCache.create(Integer.MAX_VALUE - 1);
    this.lastCachedIndex = new AtomicInteger(INDEX_NONE);
  }

  public Optional<Integer> getBuilderIndex(final BeaconState state, final BLSPublicKey publicKey) {
    final SszList<Builder> builders = BeaconStateGloas.required(state).getBuilders();
    final Optional<Integer> builderIndex = builderIndices.getCached(publicKey);
    if (builderIndex.isPresent()) {
      // The cache is shared across states, so a cached index may be stale for the state being
      // queried
      return builderIndex.filter(index -> index < builders.size());
    }
    return findIndexFromState(builders, publicKey);
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    builderIndices.invalidateWithNewValue(pubKey, updatedIndex);
  }

  public void invalidate(final BLSPublicKey pubKey) {
    builderIndices.invalidate(pubKey);
  }

  public BuilderIndexCache copy() {
    return new BuilderIndexCache(builderIndices.copy(), lastCachedIndex.get());
  }

  @VisibleForTesting
  int getLastCachedIndex() {
    return lastCachedIndex.get();
  }

  @VisibleForTesting
  int getCacheSize() {
    return builderIndices.size();
  }

  private void updateLastIndex(final int i) {
    lastCachedIndex.updateAndGet(curr -> Math.max(curr, i));
  }

  @VisibleForTesting
  Cache<BLSPublicKey, Integer> getBuilderIndices() {
    return builderIndices;
  }

  private Optional<Integer> findIndexFromState(
      final SszList<Builder> builders, final BLSPublicKey publicKey) {
    final int initialCacheSize = getCacheSize();
    for (int i = Math.max(lastCachedIndex.get() + 1, 0); i < builders.size(); i++) {
      final BLSPublicKey pubKey = builders.get(i).getPublicKey();
      builderIndices.invalidateWithNewValue(pubKey, i);
      if (pubKey.equals(publicKey)) {
        if (initialCacheSize < getCacheSize()) {
          updateLastIndex(i);
        }
        return Optional.of(i);
      }
    }
    if (initialCacheSize < getCacheSize()) {
      updateLastIndex(getCacheSize() - 1);
    }
    return Optional.empty();
  }
}

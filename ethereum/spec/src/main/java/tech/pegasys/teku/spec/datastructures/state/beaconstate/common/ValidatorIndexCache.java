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
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.ValidatorFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorIndexCache {
  private final Cache<BLSPublicKey, Integer> validatorIndices;
  private final AtomicInteger lastCachedIndex;

  private static final int INDEX_NONE = -1;
  public static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE);

  @VisibleForTesting
  ValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> validatorIndices, final int lastCachedIndex) {
    this.validatorIndices = validatorIndices;
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
  }

  public ValidatorIndexCache() {
    this.validatorIndices = LRUCache.create(Integer.MAX_VALUE - 1);
    this.lastCachedIndex = new AtomicInteger(INDEX_NONE);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    final SszList<Validator> validators = state.getValidators();
    final Optional<Integer> validatorIndex = validatorIndices.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      // The cache is shared across states, so a cached index may be stale for the state being
      // queried
      return validatorIndex.filter(index -> index < validators.size());
    }
    return findIndexFromState(validators, publicKey);
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    validatorIndices.invalidateWithNewValue(pubKey, updatedIndex);
  }

  @VisibleForTesting
  int getLastCachedIndex() {
    return lastCachedIndex.get();
  }

  @VisibleForTesting
  int getCacheSize() {
    return validatorIndices.size();
  }

  private void updateLastIndex(final int i) {
    lastCachedIndex.updateAndGet(curr -> Math.max(curr, i));
  }

  @VisibleForTesting
  Cache<BLSPublicKey, Integer> getValidatorIndices() {
    return validatorIndices;
  }

  private Optional<Integer> findIndexFromState(
      final SszList<Validator> validatorList, final BLSPublicKey publicKey) {
    final int initialCacheSize = getCacheSize();
    final int[] foundIndex = {INDEX_NONE};
    // Read the pubkeys from the packed validator bytes, without creating Validator views. Each key
    // is built over a copy of its 48 bytes, so the cache never pins the registry's packed arrays.
    validatorList.forEachElementSsz(
        Math.max(lastCachedIndex.get() + 1, 0),
        (i, data, offset) -> {
          final BLSPublicKey pubKey =
              BLSPublicKey.fromBytesCompressed(ValidatorFields.pubkey(data, offset));
          validatorIndices.invalidateWithNewValue(pubKey, i);
          if (pubKey.equals(publicKey)) {
            foundIndex[0] = i;
            return false;
          }
          return true;
        });
    if (foundIndex[0] != INDEX_NONE) {
      if (initialCacheSize < getCacheSize()) {
        updateLastIndex(foundIndex[0]);
      }
      return Optional.of(foundIndex[0]);
    }
    if (initialCacheSize < getCacheSize()) {
      updateLastIndex(getCacheSize() - 1);
    }
    return Optional.empty();
  }
}

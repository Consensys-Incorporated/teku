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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;

public class ValidatorIndexCache {
  private final Cache<BLSPublicKey, Integer> validatorIndices;
  private final AtomicInteger lastCachedIndex;
  // PQ validators are keyed by XMSS public keys instead
  private final Cache<Bytes32, Integer> xmssValidatorIndices;
  private final AtomicInteger lastCachedXmssIndex = new AtomicInteger(INDEX_NONE);
  private final boolean cachingEnabled;

  private static final int INDEX_NONE = -1;
  public static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE);

  @VisibleForTesting
  ValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> validatorIndices, final int lastCachedIndex) {
    this.validatorIndices = validatorIndices;
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
    this.cachingEnabled = !(validatorIndices instanceof NoOpCache);
    this.xmssValidatorIndices =
        cachingEnabled ? LRUCache.create(Integer.MAX_VALUE - 1) : NoOpCache.getNoOpCache();
  }

  public ValidatorIndexCache() {
    this(LRUCache.create(Integer.MAX_VALUE - 1), INDEX_NONE);
  }

  /** The index of the validator with BLS key {@code publicKey}. PQ validators have none. */
  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    if (state.toVersionPq().isPresent()) {
      return Optional.empty();
    }
    final SszList<Validator> validators = state.getValidators();
    final Optional<Integer> validatorIndex = validatorIndices.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      // The cache is shared across states, so a cached index may be stale for the state being
      // queried
      return validatorIndex.filter(index -> index < validators.size());
    }
    return findIndexFromState(validators, publicKey);
  }

  /**
   * PQ: the index of the validator with XMSS public key {@code xmssPublicKey}.
   *
   * <p>The cache is shared by the states of every branch, so a cached index is only used once the
   * key at that index in {@code state} is checked to match.
   */
  public Optional<Integer> getValidatorIndexByXmssKey(
      final BeaconState state, final Bytes32 xmssPublicKey) {
    final SszList<Validator> validators = state.getValidators();
    final Optional<Integer> cachedIndex = xmssValidatorIndices.getCached(xmssPublicKey);
    if (cachedIndex.isPresent()) {
      final int index = cachedIndex.get();
      if (index < validators.size() && xmssKeyAt(validators, index).equals(xmssPublicKey)) {
        return cachedIndex;
      }
      // Cached from another branch
      return scanForXmssKey(validators, xmssPublicKey, 0);
    }
    return scanForXmssKey(validators, xmssPublicKey, lastCachedXmssIndex.get() + 1);
  }

  private Optional<Integer> scanForXmssKey(
      final SszList<Validator> validators, final Bytes32 xmssPublicKey, final int fromIndex) {
    for (int i = fromIndex; i < validators.size(); i++) {
      final Bytes32 key = xmssKeyAt(validators, i);
      xmssValidatorIndices.invalidateWithNewValue(key, i);
      if (cachingEnabled) {
        final int cachedIndex = i;
        lastCachedXmssIndex.updateAndGet(current -> Math.max(current, cachedIndex));
      }
      if (key.equals(xmssPublicKey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  private static Bytes32 xmssKeyAt(final SszList<Validator> validators, final int index) {
    return ((ValidatorPq) validators.get(index)).getXmssPublicKey();
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
    for (int i = Math.max(lastCachedIndex.get() + 1, 0); i < validatorList.size(); i++) {
      final BLSPublicKey pubKey = validatorList.get(i).getPublicKey();
      validatorIndices.invalidateWithNewValue(pubKey, i);
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

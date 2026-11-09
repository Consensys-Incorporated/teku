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

package tech.pegasys.teku.infrastructure.collections.cache;

import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;

/**
 * Cache made around LRU-map with fixed size, removing eldest entries (by added) when the space is
 * over
 *
 * @param <K> Keys type
 * @param <V> Values type
 */
public class LRUCache<K, V> implements Cache<K, V> {

  public static <K, V> LRUCache<K, V> create(final int capacity) {
    return new LRUCache<>(LimitedMap.createNonSynchronized(capacity));
  }

  private final LimitedMap<K, V> cacheData;

  private LRUCache(final LimitedMap<K, V> cacheData) {
    this.cacheData = cacheData;
  }

  @Override
  public synchronized Cache<K, V> copy() {
    return new LRUCache<>(cacheData.copy());
  }

  /**
   * Queries value from the cache. If it's not found there, fallback function is used to calculate
   * value. After calculation result is put in cache and returned.
   *
   * @param key Key to query
   * @param fallback Fallback function for calculation of the result in case of missed cache entry
   * @return expected value result for provided key
   */
  @Override
  public synchronized V get(final K key, final Function<K, V> fallback) {
    V result = cacheData.get(key);

    if (result == null) {
      result = fallback.apply(key);
      if (result != null) {
        cacheData.put(key, result);
      }
    }

    return result;
  }

  @Override
  public synchronized Optional<V> getCached(final K key) {
    return Optional.ofNullable(cacheData.get(key));
  }

  @Override
  public synchronized void invalidate(final K key) {
    cacheData.remove(key);
  }

  @Override
  public synchronized void invalidateWithNewValue(final K key, final V newValue) {
    invalidate(key);
    get(key, k -> newValue);
  }

  @Override
  public synchronized void clear() {
    cacheData.clear();
  }

  @Override
  public synchronized int size() {
    return cacheData.size();
  }
}

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

package tech.pegasys.teku.infrastructure.ssz.cache;

import java.util.Optional;
import java.util.function.Function;

/**
 * Cache
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
public interface Cache<K, V> {
  /**
   * Queries value from the cache. If it's not found there, fallback function is used to calculate
   * value. After calculation result is put in cache and returned.
   *
   * @param key Key to query
   * @param fallback Fallback function for calculation of the result in case of missed cache entry
   * @return expected value result for provided key
   */
  V get(K key, Function<K, V> fallback);

  /**
   * Optionally returns the value corresponding to the passed <code>key</code> is it's in the cache
   */
  Optional<V> getCached(K key);

  /** Creates independent copy of this Cache instance */
  Cache<K, V> copy();

  /** Creates independent copy of this Cache instance while possibly clearing this cache content */
  default Cache<K, V> transfer() {
    return copy();
  }

  /** Removes cache entry */
  void invalidate(K key);

  /** Replace cached entry with a new value * */
  default void invalidateWithNewValue(final K key, final V newValue) {
    invalidate(key);
    get(key, k -> newValue);
  }

  /** Clears all cached values */
  void clear();
}

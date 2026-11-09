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

package tech.pegasys.teku.infrastructure.collections;

import com.google.common.cache.CacheBuilder;
import java.util.Map;

public interface LimitedMap<K, V> extends Map<K, V> {

  /**
   * Creates a limited map. The returned map is NOT thread safe and evicts the least recently used
   * items.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  static <K, V> LimitedMap<K, V> createNonSynchronized(final int maxSize) {
    return new NonSynchronizedLimitedMap<>(maxSize);
  }

  /**
   * Creates a limited map.
   *
   * <p>The returned map is safe for concurrent access <strong>except iteration</strong> and evicts
   * the least recently accessed items like LRU cache. Iteration requires synchronizing on the map
   * instance.
   *
   * <p>Synchronized instances are generally faster than iterable versions.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  static <K, V> LimitedMap<K, V> createSynchronizedLRU(final int maxSize) {
    return new SynchronizedLimitedMap<>(maxSize, true);
  }

  /**
   * Creates a limited map.
   *
   * <p>The returned map is safe for concurrent access <strong>except iteration</strong> and evicts
   * the oldest inserted or updated items. Reads do not refresh the eviction order. Iteration
   * requires synchronizing on the map instance.
   *
   * <p>Synchronized instances are generally faster than iterable versions.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  static <K, V> LimitedMap<K, V> createSynchronizedNatural(final int maxSize) {
    return new SynchronizedLimitedMap<>(maxSize, false);
  }

  /**
   * Creates a limited map. The returned map is safe for all forms of concurrent access including
   * iteration and evicts the least recently used items.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  static <K, V> Map<K, V> createSynchronizedIterable(final int maxSize) {
    return CacheBuilder.newBuilder().maximumSize(maxSize).<K, V>build().asMap();
  }

  /**
   * Creates a limited map using soft references for values. The returned map is safe for concurrent
   * access and evicts the least recently used items.
   *
   * <p>Items may be evicted before maxSize is reached if the garbage collector needs to free up
   * memory.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded or when the GC evicts
   *     them.
   */
  static <K, V> Map<K, V> createSoft(final int maxSize) {
    return CacheBuilder.newBuilder().maximumSize(maxSize).softValues().<K, V>build().asMap();
  }

  int getMaxSize();

  LimitedMap<K, V> copy();
}

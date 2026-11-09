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

import java.util.Collections;
import java.util.Set;

/** Helper that creates a set with a maximum capacity. */
public final class LimitedSet {

  private LimitedSet() {}

  /**
   * Creates a limited set.
   *
   * <p>The returned set is safe for concurrent access <strong>except iteration</strong> and evicts
   * the least recently used items. There is no thread safe way to iterate this set.
   *
   * <p>Synchronized instances are generally faster than iterable versions.
   *
   * @param maxSize The maximum number of elements to keep in the set.
   * @param <T> The type of object held in the set.
   * @return A set that will evict elements when the max size is exceeded.
   */
  public static <T> Set<T> createSynchronizedLRU(final int maxSize) {
    return Collections.newSetFromMap(LimitedMap.createSynchronizedLRU(maxSize));
  }

  /**
   * Creates a limited set.
   *
   * <p>The returned set is safe for concurrent access <strong>except iteration</strong> and evicts
   * the oldest inserted or re-added items. Reads do not refresh the eviction order. There is no
   * thread safe way to iterate this set.
   *
   * <p>Synchronized instances are generally faster than iterable versions.
   *
   * @param maxSize The maximum number of elements to keep in the set.
   * @param <T> The type of object held in the set.
   * @return A set that will evict elements when the max size is exceeded.
   */
  public static <T> Set<T> createSynchronizedNatural(final int maxSize) {
    return Collections.newSetFromMap(LimitedMap.createSynchronizedNatural(maxSize));
  }

  /**
   * Creates a limited set. The returned set is safe for all forms of concurrent access including
   * iteration and evicts the least recently used items.
   *
   * @param maxSize The maximum number of elements to keep in the set.
   * @param <T> The type of object held in the set.
   * @return A set that will evict elements when the max size is exceeded.
   */
  public static <T> Set<T> createSynchronizedIterable(final int maxSize) {
    return Collections.newSetFromMap(LimitedMap.createSynchronizedIterable(maxSize));
  }
}

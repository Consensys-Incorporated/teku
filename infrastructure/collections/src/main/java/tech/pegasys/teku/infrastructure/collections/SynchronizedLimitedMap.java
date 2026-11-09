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

/** Helper that creates a thread-safe map with a maximum capacity. */
final class SynchronizedLimitedMap<K, V> extends AbstractLimitedMap<K, V> {
  final boolean accessOrder;

  public SynchronizedLimitedMap(final int maxSize, final boolean accessOrder) {
    super(
        Collections.synchronizedMap(
            accessOrder
                ? LimitedMapBackingMaps.createLimitedMap(maxSize, true)
                : LimitedMapBackingMaps.createWriteOrderedLimitedMap(maxSize)),
        maxSize);
    this.accessOrder = accessOrder;
  }

  @Override
  public LimitedMap<K, V> copy() {
    SynchronizedLimitedMap<K, V> map = new SynchronizedLimitedMap<>(getMaxSize(), accessOrder);
    synchronized (delegate) {
      map.putAll(delegate);
    }
    return map;
  }
}

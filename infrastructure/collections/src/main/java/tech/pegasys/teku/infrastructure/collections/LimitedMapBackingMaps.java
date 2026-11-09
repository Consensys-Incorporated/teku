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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

final class LimitedMapBackingMaps {

  private LimitedMapBackingMaps() {}

  static <K, V> Map<K, V> createLimitedMap(final int maxSize, final boolean accessOrder) {
    return new LinkedHashMap<>(16, 0.75f, accessOrder) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return this.size() > maxSize;
      }
    };
  }

  static <K, V> Map<K, V> createWriteOrderedLimitedMap(final int maxSize) {
    return new LinkedHashMap<>(16, 0.75f, false) {
      @Override
      public V put(final K key, final V value) {
        if (containsKey(key)) {
          final V previousValue = remove(key);
          super.put(key, value);
          return previousValue;
        }
        return super.put(key, value);
      }

      @Override
      public void putAll(final Map<? extends K, ? extends V> map) {
        map.forEach(this::put);
      }

      @Override
      public V merge(
          final K key,
          final V value,
          final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (containsKey(key)) {
          Objects.requireNonNull(value);
          Objects.requireNonNull(remappingFunction);
          final V oldValue = get(key);
          final V newValue = oldValue == null ? value : remappingFunction.apply(oldValue, value);
          if (newValue == null) {
            remove(key);
          } else {
            put(key, newValue);
          }
          return newValue;
        }
        return super.merge(key, value, remappingFunction);
      }

      @Override
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return this.size() > maxSize;
      }
    };
  }
}

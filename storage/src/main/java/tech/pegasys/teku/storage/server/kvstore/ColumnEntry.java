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

package tech.pegasys.teku.storage.server.kvstore;

import com.google.common.base.MoreObjects;
import java.util.Map;
import java.util.Objects;

public class ColumnEntry<K, V> implements Map.Entry<K, V> {
  private final K key;
  private final V value;

  private ColumnEntry(final K key, final V value) {
    this.key = key;
    this.value = value;
  }

  public static <K, V> ColumnEntry<K, V> create(final K key, final V value) {
    return new ColumnEntry<>(key, value);
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public V getValue() {
    return value;
  }

  @Override
  public V setValue(final V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof ColumnEntry)) {
      return false;
    }
    final ColumnEntry<?, ?> other = (ColumnEntry<?, ?>) obj;
    return Objects.equals(getKey(), other.getKey()) && Objects.equals(getValue(), other.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("key", key).add("value", value).toString();
  }
}

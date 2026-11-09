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

package tech.pegasys.teku.storage.store;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Generic simple key-value store interface Both key and value are not allowed to be null
 *
 * @param <TKey> key type
 * @param <TValue> value type
 */
public interface KeyValueStore<TKey, TValue> {

  /** Puts a new value. If the value is {@code null} then the entry is removed if exist */
  void put(@NotNull TKey key, @NotNull TValue value);

  /** Removes entry with the specified key */
  void remove(@NotNull TKey key);

  /**
   * Returns a value corresponding to the key or {{@link Optional#empty()}} if entry doesn't exist
   * in the store
   */
  Optional<TValue> get(@NotNull TKey key);

  /**
   * Performs batch store update.
   *
   * <p>The implementation may override this default method and declare it to be an atomic store
   * update. Though this generic interface makes no restrictions on atomicity of this method
   */
  default void updateAll(final Iterable<EntryUpdate<? extends TKey, ? extends TValue>> data) {
    data.forEach(
        update -> {
          if (update.getType() == UpdateType.UPDATE) {
            put(update.getKey(), update.getValue());
          } else if (update.getType() == UpdateType.REMOVE) {
            remove(update.getKey());
          } else {
            throw new IllegalArgumentException("Unknown type: " + update.getType());
          }
        });
  }

  enum UpdateType {
    UPDATE,
    REMOVE
  }

  /** Represents a batched update entry */
  class EntryUpdate<K, V> {
    private final UpdateType type;
    private final K key;
    private final V value;

    public static <K, V> EntryUpdate<K, V> update(final K key, final V value) {
      return new EntryUpdate<>(UpdateType.UPDATE, key, value);
    }

    public static <K, V> EntryUpdate<K, V> remove(final K key) {
      return new EntryUpdate<>(UpdateType.REMOVE, key, null);
    }

    private EntryUpdate(final UpdateType type, final K key, final V value) {
      this.type = type;
      this.key = key;
      this.value = value;
    }

    public UpdateType getType() {
      return type;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      if (getType() != UpdateType.UPDATE) {
        throw new IllegalStateException("No value for this update");
      }
      return value;
    }
  }
}

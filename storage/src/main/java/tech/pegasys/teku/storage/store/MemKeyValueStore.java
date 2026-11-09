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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/** Simple {@link java.util.HashMap} backed {@link KeyValueStore} implementation */
public class MemKeyValueStore<K, V> implements KeyValueStore<K, V> {

  private final Map<K, V> store = new ConcurrentHashMap<>();

  @Override
  public void put(@NotNull final K key, @NotNull final V value) {
    store.put(key, value);
  }

  @Override
  public void remove(@NotNull final K key) {
    store.remove(key);
  }

  @Override
  public Optional<V> get(@NotNull final K key) {
    return Optional.ofNullable(store.get(key));
  }
}

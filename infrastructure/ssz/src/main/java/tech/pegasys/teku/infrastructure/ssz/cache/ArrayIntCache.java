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

import java.util.Arrays;
import java.util.Optional;
import java.util.function.IntFunction;
import javax.annotation.Nullable;

/**
 * Thread-safe int indexed cache
 *
 * <p>CAUTION: though the class is thread-safe it contains no synchronisation for performance
 * reasons When accessed concurrently the cache may result in extra cache misses and extra backing
 * array copying but this should be safe. In optimistic scenarios such overhead could be neglected
 *
 * <p>Modify this class carefully to not violate thread safety!
 */
public final class ArrayIntCache<V> implements IntCache<V> {
  private static final int DEFAULT_INITIAL_CACHE_SIZE = 16;
  private volatile V[] values;
  private final int initSize;

  public ArrayIntCache() {
    this(DEFAULT_INITIAL_CACHE_SIZE);
  }

  public ArrayIntCache(final int initialSize) {
    this.initSize = initialSize;
    this.values = createArray(initialSize);
  }

  private ArrayIntCache(final V[] values, final int initSize) {
    this.values = values;
    this.initSize = initSize;
  }

  @SuppressWarnings("unchecked")
  private V[] createArray(final int size) {
    return (V[]) new Object[size];
  }

  private V[] extend(final int index) {
    V[] valuesLocal = this.values;
    int newSize = valuesLocal.length;
    if (index >= newSize) {
      while (index >= newSize) {
        newSize <<= 1;
      }
      V[] newValues = Arrays.copyOf(valuesLocal, newSize);
      this.values = newValues;
      return newValues;
    }
    return valuesLocal;
  }

  @Override
  public V getInt(final int key, final IntFunction<V> fallback) {
    V[] valuesLocal = this.values;
    V val = key >= valuesLocal.length ? null : valuesLocal[key];
    if (val == null) {
      val = fallback.apply(key);
      extend(key)[key] = val;
    }
    return val;
  }

  @Override
  public Optional<V> getCached(final Integer key) {
    V[] valuesLocal = this.values;
    return key >= valuesLocal.length ? Optional.empty() : Optional.ofNullable(valuesLocal[key]);
  }

  @Override
  @Nullable
  public V getCachedInt(final int key) {
    V[] valuesLocal = this.values;
    return key >= valuesLocal.length ? null : valuesLocal[key];
  }

  @Override
  public IntCache<V> copy() {
    V[] valuesLocal = this.values;
    return new ArrayIntCache<>(Arrays.copyOf(valuesLocal, valuesLocal.length), initSize);
  }

  @Override
  public IntCache<V> transfer() {
    IntCache<V> copy = copy();
    this.values = createArray(initSize);
    return copy;
  }

  @Override
  public void invalidateWithNewValueInt(final int key, final V newValue) {
    extend(key)[key] = newValue;
  }

  @Override
  public void invalidateInt(final int key) {
    V[] valuesLocal = this.values;
    if (key < valuesLocal.length) {
      valuesLocal[key] = null;
    }
  }

  @Override
  public void clear() {
    values = createArray(initSize);
  }
}

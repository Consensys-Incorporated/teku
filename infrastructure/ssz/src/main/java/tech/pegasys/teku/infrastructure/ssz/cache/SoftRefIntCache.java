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

import java.lang.ref.SoftReference;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Keeps the delegate cache in a {@link SoftReference} to allow the cache to be GC'ed if the
 * application lacks of heap memory.
 *
 * <p>On {@link #copy()} or {@link #transfer()} also returns a {@link SoftRefIntCache} instance
 */
public class SoftRefIntCache<V> implements IntCache<V> {

  private final Supplier<IntCache<V>> cacheCtor;
  private volatile SoftReference<IntCache<V>> delegate;

  private SoftRefIntCache(
      final IntCache<V> initialDelegate, final Supplier<IntCache<V>> cacheCtor) {
    this.cacheCtor = cacheCtor;
    delegate = new SoftReference<>(initialDelegate);
  }

  public SoftRefIntCache(final Supplier<IntCache<V>> cacheCtor) {
    this(cacheCtor.get(), cacheCtor);
  }

  public IntCache<V> getDelegate() {
    IntCache<V> cache = delegate.get();
    if (cache == null) {
      cache = cacheCtor.get();
      delegate = new SoftReference<>(cache);
    }
    return cache;
  }

  @Override
  public V getInt(final int key, final IntFunction<V> fallback) {
    return getDelegate().getInt(key, fallback);
  }

  @Override
  public V get(final Integer key, final Function<Integer, V> fallback) {
    return getDelegate().get(key, fallback);
  }

  @Override
  public IntCache<V> copy() {
    return new SoftRefIntCache<>(getDelegate().copy(), cacheCtor);
  }

  @Override
  public IntCache<V> transfer() {
    return new SoftRefIntCache<>(getDelegate().transfer(), cacheCtor);
  }

  @Override
  public void invalidateInt(final int key) {
    getDelegate().invalidateInt(key);
  }

  @Override
  public void invalidate(final Integer key) {
    getDelegate().invalidate(key);
  }

  @Override
  public void invalidateWithNewValueInt(final int key, final V newValue) {
    getDelegate().invalidateWithNewValueInt(key, newValue);
  }

  @Override
  public void invalidateWithNewValue(final Integer key, final V newValue) {
    getDelegate().invalidateWithNewValue(key, newValue);
  }

  @Override
  public void clear() {
    getDelegate().clear();
  }

  @Override
  public Optional<V> getCached(final Integer key) {
    return getDelegate().getCached(key);
  }

  @Override
  @Nullable
  public V getCachedInt(final int key) {
    return getDelegate().getCachedInt(key);
  }
}

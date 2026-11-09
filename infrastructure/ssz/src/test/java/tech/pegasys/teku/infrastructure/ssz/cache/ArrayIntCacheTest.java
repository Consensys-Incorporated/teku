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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ArrayIntCacheTest {

  private final List<Thread> startedThreads = new ArrayList<>();

  @AfterEach
  void clear() {
    startedThreads.forEach(Thread::interrupt);
    startedThreads.clear();
  }

  @Test
  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  public void testThreadSafety() throws InterruptedException {
    ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    Thread t1 =
        new Thread(
            () -> {
              while (!Thread.interrupted()) {
                String val = cache.get(1024, idx -> "aaa");
                assertThat(val).isEqualTo("aaa");
                cache.invalidateWithNewValueInt(1024, "aaa");
              }
            });
    startedThreads.add(t1);
    t1.start();

    List<Thread> threads =
        IntStream.range(0, 16)
            .mapToObj(
                i ->
                    new Thread(
                        () -> {
                          while (!Thread.interrupted()) {
                            IntCache<String> cache1 = cache.transfer();
                            String val = cache1.get(1024, idx -> "aaa");
                            assertThat(val).isEqualTo("aaa");
                            for (int j = 0; j < 100; j++) {
                              cache1.invalidateWithNewValueInt(1024, "bbb");
                              String val1 = cache1.get(1024, idx -> "bbb");
                              assertThat(val1).isEqualTo("bbb");
                            }
                          }
                        }))
            .peek(Thread::start)
            .collect(Collectors.toList());
    startedThreads.addAll(threads);

    // wait a second for any threading issues
    t1.join(1000);
    assertThat(t1.isAlive()).isTrue();
    assertThat(threads).allMatch(Thread::isAlive);
  }

  @Test
  void getInt_computesViaFallbackOnceThenServesCachedValue() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    final AtomicInteger fallbackCalls = new AtomicInteger();
    final IntFunction<String> fallback =
        key -> {
          fallbackCalls.incrementAndGet();
          return "v" + key;
        };

    assertThat(cache.getInt(1, fallback)).isEqualTo("v1");
    assertThat(cache.getInt(1, fallback)).isEqualTo("v1");

    assertThat(fallbackCalls).hasValue(1);
  }

  @Test
  void getCachedInt_returnsNullWhenNotCached() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    assertThat(cache.getCachedInt(2)).isNull();
  }

  @Test
  void getCachedInt_returnsNullBeyondCapacity() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);

    assertThat(cache.getCachedInt(1000)).isNull();
    assertThat(cache.getCachedInt(1000)).isNull();
  }

  @Test
  void getCachedInt_returnsStoredValue() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.getInt(3, key -> "v3");
    assertThat(cache.getCachedInt(3)).isEqualTo("v3");
  }

  @Test
  void getCachedInt_returnsNullAfterInvalidate() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.invalidateWithNewValueInt(1, "a");
    cache.invalidateInt(1);
    assertThat(cache.getCachedInt(1)).isNull();
  }

  @Test
  void invalidateWithNewValueInt_storesAndOverwritesValue() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);

    cache.invalidateWithNewValueInt(1, "a");
    assertThat(cache.getCachedInt(1)).isEqualTo("a");

    cache.invalidateWithNewValueInt(1, "b");
    assertThat(cache.getCachedInt(1)).isEqualTo("b");
  }

  @Test
  void invalidateWithNewValueInt_extendsBackingArrayForHighIndex() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);

    // index well beyond the initial size of 4 must grow the backing array
    cache.invalidateWithNewValueInt(1000, "x");

    assertThat(cache.getCachedInt(1000)).isEqualTo("x");
    assertThat(cache.getInt(1000, key -> "recomputed")).isEqualTo("x");
  }

  @Test
  void invalidateInt_clearsSlot() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.invalidateWithNewValueInt(1, "a");

    cache.invalidateInt(1);
    assertThat(cache.getCachedInt(1)).isNull();
  }

  @Test
  void invalidateInt_ignoresIndexBeyondCapacity() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);

    cache.invalidateInt(9999);
    assertThat(cache.getCachedInt(9999)).isNull();
  }

  @Test
  void getCached_returnsValueWhenPresentAndEmptyOtherwise() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.invalidateWithNewValueInt(2, "v2");

    assertThat(cache.getCached(2)).contains("v2");
    assertThat(cache.getCached(3)).isEmpty();
    assertThat(cache.getCached(1000)).isEmpty();
  }

  @Test
  void copy_isIndependentOfOriginal() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.invalidateWithNewValueInt(1, "a");

    final IntCache<String> copy = cache.copy();
    assertThat(copy.getCachedInt(1)).isEqualTo("a");

    // mutating the copy must not affect the original ...
    copy.invalidateWithNewValueInt(1, "b");
    assertThat(cache.getCachedInt(1)).isEqualTo("a");

    // ... and mutating the original must not affect the copy
    cache.invalidateWithNewValueInt(2, "c");
    assertThat(copy.getCachedInt(2)).isNull();
  }

  @Test
  void transfer_returnsPopulatedCopyAndResetsOriginal() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.invalidateWithNewValueInt(1, "a");

    final IntCache<String> transferred = cache.transfer();

    assertThat(transferred.getCachedInt(1)).isEqualTo("a");
    assertThat(cache.getCachedInt(1)).isNull();
  }

  @Test
  void clear_emptiesCache() {
    final ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    cache.invalidateWithNewValueInt(1, "a");

    cache.clear();

    assertThat(cache.getCachedInt(1)).isNull();
  }
}

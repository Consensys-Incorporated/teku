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

package tech.pegasys.teku.infrastructure.collections.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class LRUCacheTest {

  private final int maxCacheSize = 16;
  private final LRUCache<Integer, Integer> cache = LRUCache.create(maxCacheSize);

  @Test
  void concurrencyTest() {
    Random random = new Random();
    int threadsCount = 16;
    int cacheMaxSize = 256;
    LRUCache<Integer, Integer> cache = LRUCache.create(cacheMaxSize);
    ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

    CompletableFuture<?>[] futures =
        Stream.generate(
                () ->
                    CompletableFuture.runAsync(
                        () -> {
                          while (!Thread.interrupted()) {
                            for (int i = 0; i < cacheMaxSize * 16; i++) {
                              int key = random.nextInt(cacheMaxSize * 2);
                              Integer value = cache.get(key, idx -> idx);
                              assertThat(value).isEqualTo(key);
                            }
                            assertThat(cache.size()).isLessThanOrEqualTo(cacheMaxSize);
                            for (int i = 0; i < cacheMaxSize; i++) {
                              int key = random.nextInt(cacheMaxSize * 2);
                              cache.invalidate(key);
                            }
                            assertThat(cache.size()).isLessThanOrEqualTo(cacheMaxSize);

                            if (random.nextInt(threadsCount * 2) == 0) {
                              cache.clear();
                            }

                            Cache<Integer, Integer> cache1 = cache.copy();
                            for (int i = 0; i < cacheMaxSize * 16; i++) {
                              int key = random.nextInt(cacheMaxSize * 2);
                              Integer value = cache1.get(key, idx -> idx);
                              assertThat(value).isEqualTo(key);
                            }
                            assertThat(cache1.size()).isLessThanOrEqualTo(cacheMaxSize);
                            for (int i = 0; i < cacheMaxSize; i++) {
                              int key = random.nextInt(cacheMaxSize * 2);
                              cache1.invalidate(key);
                              assertThat(cache1.size()).isLessThanOrEqualTo(cacheMaxSize);
                            }
                          }
                        },
                        executor))
            .limit(threadsCount)
            .toArray(CompletableFuture[]::new);

    CompletableFuture<Object> any = CompletableFuture.anyOf(futures);

    System.out.println("Waiting if any thread fails...");
    assertThatThrownBy(() -> any.get(5, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
    System.out.println("Shutting down...");
    executor.shutdownNow();
  }

  @Test
  void get_shouldCreateAnEntryWhenMiss() {
    Integer i = cache.get(1, __ -> 777);
    assertThat(i).isEqualTo(777);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void get_shouldReturnExistingEntryWhenHit() {
    cache.get(1, __ -> 777);
    Integer i = cache.get(1, __ -> 888);
    assertThat(i).isEqualTo(777);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void get_shouldEvictOldValues() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    for (int i = 0; i < maxCacheSize; i++) {
      cache.get(i + 1, key -> 102 + key);
    }
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    assertThat(cache.getCached(0)).isEmpty();
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void invalidate_shouldRemoveEntry() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.invalidate(0);

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.getCached(0)).isEmpty();
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void invalidate_shouldNotModifyWithNonExistingKey() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.invalidate(2);

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void invalidate_shouldNotAffectMaxCapacity() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.get(2, __ -> 102);
    cache.invalidate(1);
    for (int i = 3; i < maxCacheSize + 1; i++) {
      cache.get(i, key -> 100 + key);
    }
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache.getCached(1)).isEmpty();
    assertThat(cache.getCached(2)).contains(102);
    for (int i = 3; i < maxCacheSize + 1; i++) {
      assertThat(cache.getCached(i)).contains(100 + i);
    }

    cache.get(maxCacheSize + 1, key -> 100 + key);
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    assertThat(cache.getCached(0)).isEmpty();
    assertThat(cache.getCached(1)).isEmpty();
    assertThat(cache.getCached(2)).contains(102);
  }

  @Test
  void invalidate_shouldEvictLeastRecentlyAccessed() {
    for (int i = 0; i < maxCacheSize; i++) {
      cache.get(i, key -> key);
    }
    cache.get(0, key -> key);
    cache.get(1, key -> key);

    // should evict least recently used entry '2'
    cache.get(maxCacheSize, key -> key);
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    assertThat(cache.getCached(0)).contains(0);
    assertThat(cache.getCached(1)).contains(1);
    assertThat(cache.getCached(2)).isEmpty();
    assertThat(cache.getCached(3)).contains(3);
  }

  @Test
  void copy_shouldCreateIsolatedInstance() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);

    Cache<Integer, Integer> cache1 = cache.copy();
    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache1.size()).isEqualTo(2);
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache1.getCached(0)).contains(100);
    assertThat(cache.getCached(1)).contains(101);
    assertThat(cache1.getCached(1)).contains(101);

    cache1.invalidate(1);
    cache1.get(3, __ -> 103);
    cache1.invalidateWithNewValue(4, 104);

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache.getCached(1)).contains(101);

    assertThat(cache1.size()).isEqualTo(3);
    assertThat(cache1.getCached(0)).contains(100);
    assertThat(cache1.getCached(1)).isEmpty();
    assertThat(cache1.getCached(3)).contains(103);
    assertThat(cache1.getCached(4)).contains(104);

    cache.invalidate(0);
    cache.get(3, __ -> 203);
    cache.invalidateWithNewValue(4, 204);

    assertThat(cache.size()).isEqualTo(3);
    assertThat(cache.getCached(0)).isEmpty();
    assertThat(cache.getCached(1)).contains(101);
    assertThat(cache.getCached(3)).contains(203);
    assertThat(cache.getCached(4)).contains(204);

    assertThat(cache1.size()).isEqualTo(3);
    assertThat(cache1.getCached(0)).contains(100);
    assertThat(cache1.getCached(1)).isEmpty();
    assertThat(cache1.getCached(3)).contains(103);
    assertThat(cache1.getCached(4)).contains(104);

    cache.clear();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(cache1.size()).isEqualTo(3);
  }
}

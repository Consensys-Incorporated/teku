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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class LimitedMapTest {

  @Test
  public void createSynchronizedLRU_evictLeastRecentlyAccessed() {
    final Map<Integer, Integer> map = LimitedMap.createSynchronizedLRU(2);
    map.put(1, 1);
    assertThat(map.size()).isEqualTo(1);
    map.put(2, 2);
    assertThat(map.size()).isEqualTo(2);

    // Access element 1 then add a new element that will put us over the limit
    map.get(1);

    map.put(3, 3);
    assertThat(map.size()).isEqualTo(2);
    // Element 2 should have been evicted
    assertThat(map.containsKey(3)).isTrue();
    assertThat(map.containsKey(1)).isTrue();
  }

  @Test
  public void createSynchronizedNatural_evictNaturalOrder() {
    final Map<Integer, Integer> map = LimitedMap.createSynchronizedNatural(2);
    map.put(1, 1);
    assertThat(map.size()).isEqualTo(1);
    map.put(2, 2);
    assertThat(map.size()).isEqualTo(2);

    // Access element 1 then add a new element that will put us over the limit
    map.get(1);

    map.put(3, 3);
    assertThat(map.size()).isEqualTo(2);
    // Element 1 should have been evicted
    assertThat(map.containsKey(3)).isTrue();
    assertThat(map.containsKey(2)).isTrue();
  }

  @Test
  public void createSynchronizedNatural_shouldRefreshNaturalOrderOnWrite() {
    final Map<Integer, Integer> map = LimitedMap.createSynchronizedNatural(2);
    map.put(1, 1);
    assertThat(map.size()).isEqualTo(1);
    map.put(2, 2);
    assertThat(map.size()).isEqualTo(2);

    // Update element 1 then add a new element that will put us over the limit
    map.put(1, 1);

    map.put(3, 3);
    assertThat(map.size()).isEqualTo(2);
    // Element 2 should have been evicted
    assertThat(map.containsKey(3)).isTrue();
    assertThat(map.containsKey(1)).isTrue();
  }

  @Test
  public void createSynchronizedNatural_shouldRefreshNaturalOrderOnPutAll() {
    final Map<Integer, Integer> map = LimitedMap.createSynchronizedNatural(2);
    map.put(1, 1);
    assertThat(map.size()).isEqualTo(1);
    map.put(2, 2);
    assertThat(map.size()).isEqualTo(2);

    // Update element 1 then add a new element that will put us over the limit
    map.putAll(Map.of(1, 10));

    map.put(3, 3);
    assertThat(map.size()).isEqualTo(2);
    // Element 2 should have been evicted
    assertThat(map).containsEntry(1, 10);
    assertThat(map.containsKey(3)).isTrue();
  }

  @Test
  public void createSynchronizedNatural_shouldRefreshNaturalOrderOnMerge() {
    final Map<Integer, Integer> map = LimitedMap.createSynchronizedNatural(2);
    map.put(1, 1);
    assertThat(map.size()).isEqualTo(1);
    map.put(2, 2);
    assertThat(map.size()).isEqualTo(2);

    // Merge element 1 then add a new element that will put us over the limit
    map.merge(1, 10, Integer::sum);

    map.put(3, 3);
    assertThat(map.size()).isEqualTo(2);
    // Element 2 should have been evicted
    assertThat(map).containsEntry(1, 11);
    assertThat(map.containsKey(3)).isTrue();
  }
}

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

import java.util.Set;
import org.junit.jupiter.api.Test;

public class LimitedSetTest {

  @Test
  public void create_evictLeastRecentlyAccessed() {
    final Set<Integer> set = LimitedSet.createSynchronizedLRU(2);
    set.add(1);
    assertThat(set.size()).isEqualTo(1);
    set.add(2);
    assertThat(set.size()).isEqualTo(2);

    // Access element 1 then add a new element that will put us over the limit
    set.add(1);

    set.add(3);
    assertThat(set.size()).isEqualTo(2);
    // Element 2 should have been evicted
    assertThat(set.contains(3)).isTrue();
    assertThat(set.contains(1)).isTrue();
  }

  @Test
  public void createSynchronizedNatural_evictNaturalOrder() {
    final Set<Integer> set = LimitedSet.createSynchronizedNatural(2);
    set.add(1);
    assertThat(set.size()).isEqualTo(1);
    set.add(2);
    assertThat(set.size()).isEqualTo(2);

    // Re-add element 1 then add a new element that will refresh it
    set.add(1);

    set.add(3);
    assertThat(set.size()).isEqualTo(2);
    // Element 2 should have been evicted
    assertThat(set.contains(3)).isTrue();
    assertThat(set.contains(1)).isTrue();
  }

  @Test
  public void createSynchronizedNatural_shouldNotRefreshNaturalOrderOnRead() {
    final Set<Integer> set = LimitedSet.createSynchronizedNatural(2);
    set.add(1);
    assertThat(set.size()).isEqualTo(1);
    set.add(3);
    assertThat(set.size()).isEqualTo(2);

    // Read element 1 then add a new element that will put us over the limit
    assertThat(set.contains(1)).isTrue();

    set.add(2);
    assertThat(set.size()).isEqualTo(2);
    // Element 1 should have been evicted
    assertThat(set.contains(2)).isTrue();
    assertThat(set.contains(3)).isTrue();
  }
}

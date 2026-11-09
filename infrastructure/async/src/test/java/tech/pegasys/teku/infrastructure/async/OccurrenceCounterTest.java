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

package tech.pegasys.teku.infrastructure.async;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OccurrenceCounterTest {
  private final OccurrenceCounter counter = new OccurrenceCounter(10);

  @Test
  void startsAtZero() {
    assertThat(counter.getTotalCount()).isEqualTo(0);
  }

  @Test
  void shouldClearOverTime() {
    incrementAllSlots(1);
    assertThat(counter.getTotalCount()).isEqualTo(10);
    incrementAllSlots(0);
    assertThat(counter.getTotalCount()).isZero();
  }

  @Test
  void summaryCounterGetsAllTimePeriods() {
    incrementAllSlots(1);
    assertThat(counter.getTotalCount()).isEqualTo(10);
  }

  @Test
  void shouldGetCountForCurrentPeriod() {
    counter.increment();
    assertThat(counter.poll()).isEqualTo(1);
    counter.increment();
    counter.increment();
    assertThat(counter.getCount()).isEqualTo(2);
    assertThat(counter.poll()).isEqualTo(2);
    assertThat(counter.poll()).isZero();
  }

  @Test
  void shouldCalculateCurrentSlot() {
    int initialSlot = counter.currentSlot();
    counter.poll();
    assertThat(initialSlot + 1).isEqualTo(counter.currentSlot());
  }

  @Test
  void shouldCalculateNextSlot() {
    int currentSlot;
    int sanityCounter = 12;
    for (; sanityCounter-- > 0; counter.poll()) {
      currentSlot = counter.currentSlot();
      if (currentSlot == 9) {
        assertThat(counter.nextSlot()).isZero();
      } else {
        assertThat(counter.nextSlot()).isEqualTo(counter.currentSlot() + 1);
      }
    }
  }

  void incrementAllSlots(final int value) {
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < value; j++) {
        counter.increment();
      }
      counter.poll();
    }
  }
}

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

package tech.pegasys.teku.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer.TimingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubLabelledOperationTimerTest {

  private StubLabelledOperationTimer labelledOperationTimer;
  private Supplier<Long> timeProvider;

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    timeProvider = mock(Supplier.class);
    when(timeProvider.get()).thenReturn(System.currentTimeMillis());

    labelledOperationTimer =
        new StubLabelledOperationTimer(
            TekuMetricCategory.VALIDATOR, "test_name", "help msg", timeProvider);
  }

  @Test
  public void labelledStubOperationTimerMustMarkTimeCorrectly() {
    when(timeProvider.get()).thenReturn(1L, 2L);

    final TimingContext timingContext = labelledOperationTimer.labels("foo", "bar").startTimer();
    timingContext.stopTimer();

    assertThat(labelledOperationTimer.getAverageDuration("foo", "bar")).hasValue(1.0);
  }

  @Test
  public void shouldSupportMultipleDurationsWithinSameLabels() {
    when(timeProvider.get()).thenReturn(1L, 1L, 6L, 11L);

    final OperationTimer timer1 = labelledOperationTimer.labels("foo", "bar");
    final OperationTimer timer2 = labelledOperationTimer.labels("foo", "bar");

    final TimingContext timingContext1 = timer1.startTimer();
    final TimingContext timingContext2 = timer2.startTimer();

    timingContext1.stopTimer();
    timingContext2.stopTimer();

    assertThat(labelledOperationTimer.getDurations("foo", "bar")).contains(5L, 10L);
  }

  @Test
  public void shouldSupportMultipleTimersWithDifferentLabels() {
    when(timeProvider.get()).thenReturn(1L, 1L, 6L, 11L);

    final OperationTimer timer1 = labelledOperationTimer.labels("foo", "bar");
    final OperationTimer timer2 = labelledOperationTimer.labels("blip", "blop");

    final TimingContext timingContext1 = timer1.startTimer();
    final TimingContext timingContext2 = timer2.startTimer();

    timingContext1.stopTimer();
    timingContext2.stopTimer();

    assertThat(labelledOperationTimer.getDurations("foo", "bar")).contains(5L);
    assertThat(labelledOperationTimer.getDurations("blip", "blop")).contains(10L);
  }

  @Test
  public void shouldFailGettingTimeOfNonExistingTimer() {
    assertThatThrownBy(() -> labelledOperationTimer.getAverageDuration("nope"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReturnExistingTimerForLabels() {
    final OperationTimer timer1 = labelledOperationTimer.labels("foo", "bar");
    final OperationTimer timer2 = labelledOperationTimer.labels("foo", "bar");

    assertThat(timer1).isSameAs(timer2);
  }
}

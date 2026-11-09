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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer.TimingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubOperationTimerTest {

  private StubOperationTimer operationTimer;
  private Supplier<Long> timeProvider;

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    timeProvider = mock(Supplier.class);
    when(timeProvider.get()).thenReturn(System.currentTimeMillis());

    operationTimer =
        new StubOperationTimer(
            TekuMetricCategory.VALIDATOR, "test_timer", "help msg", timeProvider);
  }

  @Test
  public void shouldCalculateCorrectDuration() {
    // duration  6 - 1 =  5
    when(timeProvider.get()).thenReturn(1L, 6L);

    TimingContext timingContext = operationTimer.startTimer();

    assertThat(timingContext.stopTimer()).isEqualTo(5);
  }

  @Test
  public void shouldCalculateMultipleDurations() {
    // 1st duration  6 - 1 =  5
    // 2nd duration 11 - 1 = 10
    when(timeProvider.get()).thenReturn(1L, 1L, 6L, 11L);

    TimingContext timingContext1 = operationTimer.startTimer();
    TimingContext timingContext2 = operationTimer.startTimer();

    assertThat(timingContext1.stopTimer()).isEqualTo(5);
    assertThat(timingContext2.stopTimer()).isEqualTo(10);
  }

  @Test
  public void shouldCalculateCorrectDurationAverage() {
    // 1st duration  6 - 1 =  5
    // 2nd duration 11 - 1 = 10
    when(timeProvider.get()).thenReturn(1L, 1L, 6L, 11L);

    TimingContext timingContext1 = operationTimer.startTimer();
    TimingContext timingContext2 = operationTimer.startTimer();

    timingContext1.stopTimer();
    timingContext2.stopTimer();

    assertThat(operationTimer.getAverageDuration()).hasValue(7.5);
  }

  @Test
  public void shouldNotCalculateDurationIfTimerIsNotStopped() {
    // We never stop this timer
    operationTimer.startTimer();

    assertThat(operationTimer.getDurations()).isEmpty();
  }
}

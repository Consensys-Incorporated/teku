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

package tech.pegasys.teku.services.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;

class TimerServiceTest {

  private static final int TICKS_PER_SECOND = 10;
  private static final int TICK_INTERVAL_MS = 1000 / TICKS_PER_SECOND;
  private final TimeProvider systemTimeProvider = new SystemTimeProvider();

  private Service serviceUnderTest;

  public static Stream<Arguments> timeToNextTaskMs() {
    // final long millis, final int ticksPerSecond, final long expectedValue
    return Stream.of(
        Arguments.of(0, 2, 500),
        Arguments.of(1, 2, 499),
        Arguments.of(499, 2, 1),
        Arguments.of(500, 2, 500),
        Arguments.of(999, 2, 1),
        // ticksPerSecond = 3 (intervalMs = 333): non-divisible case
        Arguments.of(0, 3, 333),
        Arguments.of(333, 3, 333),
        Arguments.of(666, 3, 334),
        Arguments.of(999, 3, 1));
  }

  @AfterEach
  void tearDown() throws Exception {
    if (serviceUnderTest != null) {
      serviceUnderTest.stop().get(5, TimeUnit.SECONDS);
      serviceUnderTest = null;
    }
  }

  /**
   * Verifies that an uncaught exception thrown by the tick handler does not permanently stop the
   * timer. The scheduler must continue firing ticks after the exception.
   */
  @Test
  void exceptionInTickHandler_timerContinuesFiring() throws Exception {
    final AtomicInteger successCount = new AtomicInteger();
    final AtomicBoolean threw = new AtomicBoolean(false);
    final CountDownLatch ticksAfterException = new CountDownLatch(2);

    final TimerService service =
        new TimerService(
            () -> {
              if (threw.compareAndSet(false, true)) {
                throw new RuntimeException("simulated tick failure");
              }
              successCount.incrementAndGet();
              ticksAfterException.countDown();
            },
            TICKS_PER_SECOND,
            systemTimeProvider);

    serviceUnderTest = service;
    service.start().get(5, TimeUnit.SECONDS);
    final boolean timedOut = !ticksAfterException.await(5, TimeUnit.SECONDS);
    service.stop().get(5, TimeUnit.SECONDS);
    serviceUnderTest = null;

    assertThat(timedOut).as("timer should continue firing after exception").isFalse();
    assertThat(successCount.get())
        .as("at least 2 ticks should succeed after the exception")
        .isGreaterThanOrEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("timeToNextTaskMs")
  void nextTickDueCases(final long millis, final int ticksPerSecond, final long expectedValue) {
    assertThat(TimerService.nextTickDue(millis, ticksPerSecond)).isEqualTo(expectedValue);
  }

  private TimerService createTimerService(final TimeTickHandler handler) {
    return new TimerService(handler, TICKS_PER_SECOND, new SystemTimeProvider());
  }

  /** The service should fire a comparable number of ticks in a fixed wall-clock window. */
  @Test
  void ticksFire_withinExpectedRange() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    final TimerService service = createTimerService(counter::incrementAndGet);
    serviceUnderTest = service;

    service.start().get(5, TimeUnit.SECONDS);
    Thread.sleep(1_000);
    service.stop().get(5, TimeUnit.SECONDS);
    serviceUnderTest = null;

    // 10 ticks/sec over ~1 s → expect roughly 7–14 ticks; allow wide margin for CI jitter
    assertThat(counter.get()).as("tick count").isBetween(7, 14);
  }

  /**
   * The service must continue firing after the tick handler throws an exception. This is a
   * regression guard: raw {@code ScheduledExecutorService} silently stops on uncaught exceptions.
   */
  @Test
  void exceptionInHandler_timerContinuesFiring() throws Exception {
    final AtomicBoolean threw = new AtomicBoolean(false);
    final CountDownLatch ticksAfterException = new CountDownLatch(2);

    final TimerService service =
        createTimerService(
            () -> {
              if (threw.compareAndSet(false, true)) {
                throw new RuntimeException("simulated tick failure");
              }
              ticksAfterException.countDown();
            });
    serviceUnderTest = service;

    service.start().get(5, TimeUnit.SECONDS);
    final boolean timedOut = !ticksAfterException.await(10, TimeUnit.SECONDS);
    service.stop().get(5, TimeUnit.SECONDS);
    serviceUnderTest = null;

    assertThat(timedOut).as("timer should continue firing after exception").isFalse();
  }

  /**
   * When a tick takes longer than the interval, the service should not fire a catch-up burst of
   * missed ticks once the slow tick completes.
   */
  @Test
  void slowTick_doesNotCauseCatchupBurst() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    final AtomicBoolean first = new AtomicBoolean(true);

    final TimerService service =
        createTimerService(
            () -> {
              counter.incrementAndGet();
              if (first.compareAndSet(true, false)) {
                try {
                  // Sleep 4× the interval to force missed ticks
                  Thread.sleep(TICK_INTERVAL_MS * 4L);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
            });
    serviceUnderTest = service;

    service.start().get(5, TimeUnit.SECONDS);
    // Run for ~10 intervals; without catch-up the count stays low
    Thread.sleep(TICK_INTERVAL_MS * 10L);
    service.stop().get(5, TimeUnit.SECONDS);
    serviceUnderTest = null;

    // A burst-free implementation fires at most ~6 ticks in this window
    assertThat(counter.get()).as("no catch-up burst expected").isBetween(2, 8);
  }
}

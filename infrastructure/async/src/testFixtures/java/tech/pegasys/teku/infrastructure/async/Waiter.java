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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.awaitility.pollinterval.IterativePollInterval;

/**
 * A simpler wrapper around Awaitility that directs people towards best practices for waiting. The
 * native Awaitility wrapper has a number of "gotchas" that can lead to intermittency which this
 * wrapper aims to prevent.
 */
public class Waiter {

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final Duration INITIAL_POLL_INTERVAL = Duration.ofMillis(200);
  private static final Duration MAX_POLL_INTERVAL = Duration.ofSeconds(5);

  public static void waitFor(
      final Condition assertion, final int timeoutValue, final TimeUnit timeUnit) {
    waitFor(assertion, timeoutValue, timeUnit, true);
  }

  public static void waitFor(
      final Condition assertion,
      final int timeoutValue,
      final TimeUnit timeUnit,
      final boolean ignoreExceptions) {
    ConditionFactory conditionFactory = Awaitility.waitAtMost(timeoutValue, timeUnit);
    if (ignoreExceptions) {
      conditionFactory = conditionFactory.ignoreExceptions();
    }
    conditionFactory
        .pollInterval(
            IterativePollInterval.iterative(Waiter::nextPollInterval, INITIAL_POLL_INTERVAL))
        .untilAsserted(assertion::run);
  }

  public static void waitFor(final Condition assertion) {
    waitFor(assertion, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  private static Duration nextPollInterval(final Duration duration) {
    final Duration nextInterval = duration.multipliedBy(2);
    return nextInterval.compareTo(MAX_POLL_INTERVAL) <= 0 ? nextInterval : MAX_POLL_INTERVAL;
  }

  public static <T> T waitFor(final Future<T> future)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  public static <T> T waitFor(final Future<T> future, final Duration duration)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(duration.toSeconds(), TimeUnit.SECONDS);
  }

  public interface Condition {
    void run() throws Throwable;
  }

  public static void ensureConditionRemainsMet(
      final Condition condition, final int waitTimeInMilliseconds) throws InterruptedException {
    final long mustBeTrueUntil = System.currentTimeMillis() + waitTimeInMilliseconds;
    while (System.currentTimeMillis() < mustBeTrueUntil) {
      try {
        condition.run();
      } catch (final Throwable t) {
        throw new RuntimeException("Condition did not remain met", t);
      }
      Thread.sleep(500);
    }
  }

  public static void ensureConditionRemainsMet(final Condition condition)
      throws InterruptedException {
    ensureConditionRemainsMet(condition, 2000);
  }
}

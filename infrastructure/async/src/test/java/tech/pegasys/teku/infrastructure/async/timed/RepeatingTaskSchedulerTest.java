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

package tech.pegasys.teku.infrastructure.async.timed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFutureSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler.ExpirationTask;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler.RepeatingTask;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class RepeatingTaskSchedulerTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(500);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final RepeatingTask action = mock(RepeatingTask.class);
  private final ExpirationTask expirationAction = mock(ExpirationTask.class);

  private final RepeatingTaskScheduler eventQueue =
      new RepeatingTaskScheduler(asyncRunner, timeProvider);

  @Test
  void shouldExecuteEventImmediatelyWhenAlreadyDue() {
    scheduleRepeatingEvent(getTime(), UInt64.valueOf(12), action);
    verify(action).execute(getTime(), getTime());
  }

  @Test
  void shouldExecuteEventImmediatelyMultipleTimesWhenMultipleRepeatsAreAlreadyDue() {
    scheduleRepeatingEvent(getTime().minus(24), UInt64.valueOf(12), action);
    verify(action).execute(getTime().minus(24), getTime());
    verify(action).execute(getTime().minus(12), getTime());
    verify(action).execute(getTime(), getTime());
  }

  @Test
  void shouldDelayExecutionUntilEventIsDue() {
    scheduleRepeatingEvent(getTime().plus(100), UInt64.valueOf(12), action);

    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(action);

    // Task executes when time becomes due
    advanceTimeBy(100);
    // Delayed action fires
    asyncRunner.executeDueActionsRepeatedly();
    // Action is executed async
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(), getTime());
    verifyNoMoreInteractions(action);
  }

  @Test
  void shouldExecuteEventAgainAfterRepeatPeriod() {
    final UInt64 repeatPeriod = UInt64.valueOf(12);
    // Action executes immediately
    scheduleRepeatingEvent(getTime(), repeatPeriod, action);
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(), getTime());

    advanceTimeBy(repeatPeriod.longValue());
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(), getTime());
  }

  @Test
  void shouldInterleaveMultipleRepeatingEvents() {
    final RepeatingTask secondAction = mock(RepeatingTask.class);
    scheduleRepeatingEvent(getTime(), UInt64.valueOf(6), action);
    scheduleRepeatingEvent(getTime(), UInt64.valueOf(5), secondAction);

    // Both execute initially
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(), getTime());
    verify(secondAction).execute(getTime(), getTime());

    // Then second action is scheduled 5 seconds later
    advanceTimeBy(5);
    asyncRunner.executeDueActionsRepeatedly();
    verify(secondAction).execute(getTime(), getTime());
    verifyNoMoreInteractions(action);

    // And action one second after that
    advanceTimeBy(1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(getTime(), getTime());
    verifyNoMoreInteractions(secondAction);
  }

  @Test
  void shouldReportScheduledAndActualExecutionTimeWhenTaskIsDelayed() {
    final UInt64 scheduledTime = getTime().minus(5);
    scheduleRepeatingEvent(scheduledTime, UInt64.valueOf(10), action);

    asyncRunner.executeDueActionsRepeatedly();
    verify(action).execute(scheduledTime, getTime());
  }

  @Test
  void shouldExecuteEventAndExpireImmediatelyWhenAlreadyDueAndAlreadyExpired() {
    scheduleRepeatingEventWithExpiration(
        getTime(), UInt64.valueOf(12), action, getTime(), expirationAction);
    verify(action).execute(getTime(), getTime());
    verify(expirationAction).execute(getTime(), getTime());

    advanceTimeBy(24);

    verifyNoMoreInteractions(action);
    verifyNoMoreInteractions(expirationAction);
  }

  @Test
  void shouldExecuteEventAndExpire() {
    final UInt64 initialSchedule = getTime().plus(1);
    final UInt64 expirationTime = getTime().plus(14);
    scheduleRepeatingEventWithExpiration(
        initialSchedule, UInt64.valueOf(12), action, expirationTime, expirationAction);

    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(action);
    verifyNoInteractions(expirationAction);

    advanceTimeBy(1);
    asyncRunner.executeDueActionsRepeatedly();

    verify(action).execute(getTime(), getTime());
    verifyNoInteractions(expirationAction);

    advanceTimeBy(12);
    asyncRunner.executeDueActionsRepeatedly();

    // we are at 13, next due will be 14 which is the expiration time,
    // thus, this is the last action and expiration action should be executed.
    verify(action).execute(getTime(), getTime());
    verify(expirationAction).execute(expirationTime, getTime());

    advanceTimeBy(24);

    verifyNoMoreInteractions(action);
    verifyNoMoreInteractions(expirationAction);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExpireViaErrorHandlerWhenRunAfterDelayFails() {
    // Use a mock AsyncRunner where runAfterDelay always returns a failed future
    final AsyncRunner failingAsyncRunner = mock(AsyncRunner.class, Mockito.CALLS_REAL_METHODS);
    doReturn(SafeFuture.failedFuture(new RejectedExecutionException("Pool full")))
        .when(failingAsyncRunner)
        .runAfterDelay(any(ExceptionThrowingFutureSupplier.class), any(Duration.class));

    final RepeatingTaskScheduler scheduler =
        new RepeatingTaskScheduler(failingAsyncRunner, timeProvider);

    final UInt64 startTime = getTime();
    final UInt64 period = UInt64.valueOf(6);
    final UInt64 expirationTime = startTime.plus(12);

    // Schedule: event executes immediately at startTime via the while loop.
    // After execution, nextDue = startTime+6. Not expired (startTime+6 < startTime+12).
    // runAfterDelay fails, error handler fires:
    //   moveToNextScheduledTime() -> nextDue = startTime+12. isExpired (startTime+12 >=
    // startTime+12).
    //   expireEvent(event) called directly instead of scheduleEvent(event).
    scheduler.scheduleRepeatingEventInMillis(
        startTime, period, action, expirationTime, expirationAction);

    verify(action).execute(startTime, startTime);
    verify(expirationAction).execute(expirationTime, startTime);
    verifyNoMoreInteractions(action);
    verifyNoMoreInteractions(expirationAction);
  }

  private void scheduleRepeatingEvent(
      final UInt64 initialInvocationTime, final UInt64 repeatingPeriod, final RepeatingTask task) {
    eventQueue.scheduleRepeatingEventInMillis(initialInvocationTime, repeatingPeriod, task);
  }

  private void scheduleRepeatingEventWithExpiration(
      final UInt64 initialInvocationTime,
      final UInt64 repeatingPeriod,
      final RepeatingTask task,
      final UInt64 expirationTime,
      final ExpirationTask expirationTask) {
    eventQueue.scheduleRepeatingEventInMillis(
        initialInvocationTime, repeatingPeriod, task, expirationTime, expirationTask);
  }

  private UInt64 getTime() {
    return timeProvider.getTimeInMillis();
  }

  private void advanceTimeBy(final long time) {
    timeProvider.advanceTimeByMillis(time);
  }
}

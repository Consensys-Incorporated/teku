/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.propagateResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StubAsyncRunner implements AsyncRunner {
  private final TimeProvider timeProvider;

  private final PriorityQueue<Task> queuedActions =
      new PriorityQueue<>(Comparator.comparingLong(Task::getScheduledTimeMillis));

  public StubAsyncRunner() {
    this(() -> UInt64.ZERO);
  }

  public StubAsyncRunner(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  @Override
  public <U> SafeFuture<U> runAsync(final ExceptionThrowingFutureSupplier<U> action) {
    // Schedule for immediate execution
    return schedule(action, 0L);
  }

  private <U> SafeFuture<U> schedule(
      final ExceptionThrowingFutureSupplier<U> action, final long scheduledTimeMillis) {
    final SafeFuture<U> result = new SafeFuture<>();
    queuedActions.add(
        new Task(
            scheduledTimeMillis,
            () -> {
              try {
                propagateResult(action.get(), result);
              } catch (final Throwable t) {
                result.completeExceptionally(t);
              }
            }));
    return result;
  }

  @Override
  public <U> SafeFuture<U> runAfterDelay(
      final ExceptionThrowingFutureSupplier<U> action, final Duration delay) {
    return schedule(action, timeProvider.getTimeInMillis().longValue() + delay.toMillis());
  }

  @Override
  public void shutdown() {
    queuedActions.clear();
  }

  public void executeQueuedActions() {
    final List<Task> actionsToExecute = new ArrayList<>(queuedActions);
    queuedActions.clear();
    actionsToExecute.forEach(Task::run);
  }

  public void executeQueuedActions(final int limit) {
    final List<Task> actionsToExecute = new ArrayList<>();
    for (int i = 0; i < limit && !queuedActions.isEmpty(); i++) {
      actionsToExecute.add(queuedActions.remove());
    }
    actionsToExecute.forEach(Task::run);
  }

  public void executeDueActions() {
    final long maxScheduledTime = timeProvider.getTimeInMillis().longValue();
    final List<Task> actionsToExecute = new ArrayList<>();
    Task next = queuedActions.peek();
    while (next != null && next.getScheduledTimeMillis() <= maxScheduledTime) {
      actionsToExecute.add(queuedActions.remove());
      next = queuedActions.peek();
    }
    actionsToExecute.forEach(Task::run);
  }

  public void executeDueActionsRepeatedly() {
    int loopCounter = 0;
    final long maxScheduledTime = timeProvider.getTimeInMillis().longValue();
    Task nextTask = queuedActions.peek();
    while (nextTask != null && nextTask.getScheduledTimeMillis() <= maxScheduledTime) {
      executeDueActions();
      nextTask = queuedActions.peek();
      loopCounter++;
      checkState(loopCounter < 100, "Executed due actions 100 times and still not done");
    }
  }

  public void executeUntilDone() {
    executeRepeatedly(1000, true);
  }

  public void executeRepeatedly(final int maxRuns) {
    executeRepeatedly(maxRuns, false);
  }

  public void waitForExactly(final int count) throws InterruptedException {
    int runs = 0;
    int iterations = 0;
    while (runs < count) {
      runs += executeRepeatedly(count - runs, false);
      iterations += 1;
      if (iterations > 100) {
        throw new IllegalStateException("Too many iterations");
      }
      if (runs < count) {
        Thread.sleep(100);
      }
    }
  }

  private int executeRepeatedly(final int maxRuns, final boolean shouldFinish) {
    int iterations = 0;
    for (int i = 0; i < maxRuns; i++) {
      if (countDelayedActions() == 0) {
        return iterations;
      }
      iterations += 1;
      executeQueuedActions();
    }

    if (countDelayedActions() != 0 && shouldFinish) {
      throw new RuntimeException(
          "Unable to execute all delayed actions. "
              + countDelayedActions()
              + " actions remain unexecuted.");
    }
    return iterations;
  }

  public boolean hasDelayedActions() {
    return !queuedActions.isEmpty();
  }

  public int countDelayedActions() {
    return queuedActions.size();
  }

  private static class Task {
    private final long scheduledTimeMillis;
    private final Runnable action;

    private Task(final long scheduledTimeMillis, final Runnable action) {
      this.scheduledTimeMillis = scheduledTimeMillis;
      this.action = action;
    }

    public long getScheduledTimeMillis() {
      return scheduledTimeMillis;
    }

    public void run() {
      action.run();
    }
  }
}

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

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.function.Consumer;

public interface AsyncRunner {

  default SafeFuture<Void> runAsync(final ExceptionThrowingRunnable action) {
    return runAsync(() -> SafeFuture.fromRunnable(action));
  }

  <U> SafeFuture<U> runAsync(final ExceptionThrowingFutureSupplier<U> action);

  <U> SafeFuture<U> runAfterDelay(ExceptionThrowingFutureSupplier<U> action, final Duration delay);

  default SafeFuture<Void> runAfterDelay(
      final ExceptionThrowingRunnable action, final Duration delay) {
    return runAfterDelay(() -> SafeFuture.fromRunnable(action), delay);
  }

  default Cancellable runCancellableAfterDelay(
      final ExceptionThrowingRunnable runnable,
      final Duration delay,
      final Consumer<Throwable> exceptionHandler) {
    Cancellable cancellable = FutureUtil.createCancellable();
    FutureUtil.runAfterDelay(this, runnable, cancellable, delay, exceptionHandler);
    return cancellable;
  }

  void shutdown();

  default <U> SafeFuture<U> runAsync(final ExceptionThrowingSupplier<U> action) {
    return runAsync(() -> SafeFuture.of(action));
  }

  default SafeFuture<Void> getDelayedFuture(final Duration delay) {
    return runAfterDelay(() -> SafeFuture.COMPLETE, delay);
  }

  /**
   * Schedules the recurrent task which will be repeatedly executed with the specified delay.
   *
   * <p>The returned instance can be used to cancel the task. Note that {@link Cancellable#cancel()}
   * doesn't interrupt already running task.
   *
   * <p>Whenever the {@code runnable} throws exception it is notified to the {@code
   * exceptionHandler} and the task recurring executions are not interrupted
   */
  default Cancellable runWithFixedDelay(
      final ExceptionThrowingRunnable runnable,
      final Duration delay,
      final Consumer<Throwable> exceptionHandler) {
    return runWithFixedDelay(runnable, delay, delay, exceptionHandler);
  }

  /**
   * Schedules the recurrent task which will be repeatedly executed with the specified delay. The
   * first run will occur after {@code initialDelay}.
   *
   * <p>The returned instance can be used to cancel the task. Note that {@link Cancellable#cancel()}
   * doesn't interrupt already running task.
   *
   * <p>Whenever the {@code runnable} throws exception it is notified to the {@code
   * exceptionHandler} and the task recurring executions are not interrupted
   */
  default Cancellable runWithFixedDelay(
      final ExceptionThrowingRunnable runnable,
      final Duration initialDelay,
      final Duration delay,
      final Consumer<Throwable> exceptionHandler) {
    Preconditions.checkNotNull(exceptionHandler);

    Cancellable cancellable = FutureUtil.createCancellable();
    FutureUtil.runWithFixedDelay(
        this, runnable, cancellable, initialDelay, delay, exceptionHandler);
    return cancellable;
  }

  /**
   * Schedules the recurrent task which will be repeatedly executed with the specified delay.
   *
   * <p>The recurrent task may perform actions async and the delay between executions will only
   * begin when the returned future is completed.
   *
   * <p>The returned instance can be used to cancel the task. Note that {@link Cancellable#cancel()}
   * doesn't interrupt already running task.
   *
   * <p>Whenever the {@code runnable} throws exception it is notified to the {@code
   * exceptionHandler} and the task recurring executions are not interrupted
   */
  default <T> Cancellable runWithFixedDelay(
      final ExceptionThrowingFutureSupplier<T> runnable,
      final Duration initialDelay,
      final Duration delay,
      final Consumer<Throwable> exceptionHandler) {
    Preconditions.checkNotNull(exceptionHandler);

    Cancellable cancellable = FutureUtil.createCancellable();
    FutureUtil.runWithFixedDelay(
        this, runnable, cancellable, initialDelay, delay, exceptionHandler);
    return cancellable;
  }

  /**
   * Execute the future supplier until it completes normally up to some maximum number of retries.
   *
   * @param action The action to run
   * @param retryDelay The time to wait before retrying
   * @param maxRetries The maximum number of retries. A value of 0 means the action is run only once
   *     (no retries).
   * @param <T> The value returned by the action future
   * @return A future that resolves with the first successful result, or else an error if the
   *     maximum retries are exhausted.
   */
  default <T> SafeFuture<T> runWithRetry(
      final ExceptionThrowingFutureSupplier<T> action,
      final Duration retryDelay,
      final int maxRetries) {

    return SafeFuture.of(action)
        .exceptionallyCompose(
            err -> {
              if (maxRetries > 0) {
                // Retry after delay, decrementing the remaining available retries
                final int remainingRetries = maxRetries - 1;
                return runAfterDelay(
                    () -> runWithRetry(action, retryDelay, remainingRetries), retryDelay);
              } else {
                return SafeFuture.failedFuture(err);
              }
            });
  }
}

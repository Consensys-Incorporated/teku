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
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FutureUtil {
  private static final Logger LOG = LogManager.getLogger();

  public static <T> void ignoreFuture(final Future<T> future) {}

  static void runWithFixedDelay(
      final AsyncRunner runner,
      final ExceptionThrowingRunnable runnable,
      final Cancellable task,
      final Duration initialDelay,
      final Duration duration,
      final Consumer<Throwable> exceptionHandler) {

    runner
        .runAfterDelay(
            () -> {
              if (!task.isCancelled()) {
                try {
                  runnable.run();
                } catch (Throwable throwable) {
                  try {
                    exceptionHandler.accept(throwable);
                  } catch (Exception e) {
                    LOG.warn("Exception in exception handler", e);
                  }
                } finally {
                  runWithFixedDelay(runner, runnable, task, duration, duration, exceptionHandler);
                }
              }
            },
            initialDelay)
        .finish(() -> {}, exceptionHandler);
  }

  static void runWithFixedDelay(
      final AsyncRunner runner,
      final ExceptionThrowingFutureSupplier<?> asyncAction,
      final Cancellable task,
      final Duration initialDelay,
      final Duration duration,
      final Consumer<Throwable> exceptionHandler) {
    runner
        .runAfterDelay(
            () -> {
              if (task.isCancelled()) {
                return;
              }
              SafeFuture.of(asyncAction)
                  .handleException(
                      error -> {
                        try {
                          exceptionHandler.accept(error);
                        } catch (final Throwable t) {
                          LOG.warn("Exception in exception handler", t);
                        }
                      })
                  .always(
                      () ->
                          runWithFixedDelay(
                              runner, asyncAction, task, duration, duration, exceptionHandler));
            },
            initialDelay)
        .finish(exceptionHandler);
  }

  static void runAfterDelay(
      final AsyncRunner runner,
      final ExceptionThrowingRunnable runnable,
      final Cancellable task,
      final Duration delay,
      final Consumer<Throwable> exceptionHandler) {
    runner
        .runAfterDelay(
            () -> {
              if (!task.isCancelled()) {
                try {
                  runnable.run();
                } catch (final Throwable throwable) {
                  try {
                    exceptionHandler.accept(throwable);
                  } catch (Exception e) {
                    LOG.warn("Exception in exception handler", e);
                  }
                }
              }
            },
            delay)
        .finish(exceptionHandler);
  }

  static Cancellable createCancellable() {
    return new Cancellable() {
      private volatile boolean cancelled;

      @Override
      public void cancel() {
        cancelled = true;
      }

      @Override
      public boolean isCancelled() {
        return cancelled;
      }
    };
  }
}

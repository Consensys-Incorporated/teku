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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An AsyncRunner that uses the common ForkJoinPool so that it is guaranteed not to leak threads
 * even if the test doesn't shut it down.
 */
public class DelayedExecutorAsyncRunner implements AsyncRunner {
  private static final Logger LOG = LogManager.getLogger();
  private final ExecutorFactory executorFactory;

  private DelayedExecutorAsyncRunner(final ExecutorFactory executorFactory) {
    this.executorFactory = executorFactory;
  }

  public static DelayedExecutorAsyncRunner create() {
    return new DelayedExecutorAsyncRunner(CompletableFuture::delayedExecutor);
  }

  @Override
  public <U> SafeFuture<U> runAsync(final ExceptionThrowingFutureSupplier<U> action) {
    final Executor executor = getAsyncExecutor();
    return runAsync(action, executor);
  }

  @Override
  public <U> SafeFuture<U> runAfterDelay(
      final ExceptionThrowingFutureSupplier<U> action, final Duration delay) {
    final Executor executor = getDelayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS);
    return runAsync(action, executor);
  }

  @Override
  public void shutdown() {}

  @VisibleForTesting
  <U> SafeFuture<U> runAsync(
      final ExceptionThrowingFutureSupplier<U> action, final Executor executor) {
    final SafeFuture<U> result = new SafeFuture<>();
    try {
      executor.execute(() -> SafeFuture.of(action).propagateTo(result));
    } catch (final RejectedExecutionException ex) {
      LOG.debug("shutting down ", ex);
    } catch (final Throwable t) {
      result.completeExceptionally(t);
    }
    return result;
  }

  private Executor getAsyncExecutor() {
    return getDelayedExecutor(-1, TimeUnit.SECONDS);
  }

  private Executor getDelayedExecutor(final long delayAmount, final TimeUnit delayUnit) {
    return executorFactory.create(delayAmount, delayUnit);
  }

  private interface ExecutorFactory {
    Executor create(long delayAmount, TimeUnit delayUnit);
  }
}

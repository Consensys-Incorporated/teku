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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScheduledExecutorAsyncRunner implements AsyncRunner {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler;
  private final ExecutorService workerPool;

  ScheduledExecutorAsyncRunner(
      final ScheduledExecutorService scheduler, final ExecutorService workerPool) {
    this.scheduler = scheduler;
    this.workerPool = workerPool;
  }

  @SuppressWarnings("ThreadPriorityCheck")
  public static AsyncRunner create(
      final String name,
      final int maxThreads,
      final int maxQueueSize,
      final int threadPriority,
      final MetricTrackingExecutorFactory executorFactory) {
    final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(name + "-async-scheduler-%d")
                .setDaemon(false)
                .build());
    final ExecutorService workerPool =
        executorFactory.newCachedThreadPool(
            name,
            maxThreads,
            maxQueueSize,
            new ThreadFactoryBuilder()
                .setNameFormat(name + "-async-%d")
                .setDaemon(false)
                .setPriority(threadPriority)
                .build());

    return new ScheduledExecutorAsyncRunner(scheduler, workerPool);
  }

  @Override
  public <U> SafeFuture<U> runAsync(final ExceptionThrowingFutureSupplier<U> action) {
    if (shutdown.get()) {
      LOG.debug("Ignoring async task because shutdown is in progress");
      return new SafeFuture<>();
    }
    final SafeFuture<U> result = new SafeFuture<>();
    try {
      workerPool.execute(createRunnableForAction(action, result));
    } catch (final Throwable t) {
      handleExecutorError(result, t);
    }
    return result;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public <U> SafeFuture<U> runAfterDelay(
      final ExceptionThrowingFutureSupplier<U> action, final Duration delay) {
    if (shutdown.get()) {
      LOG.debug("Ignoring async task because shutdown is in progress");
      return new SafeFuture<>();
    }
    final SafeFuture<U> result = new SafeFuture<>();
    try {
      scheduler.schedule(
          () -> {
            try {
              workerPool.execute(createRunnableForAction(action, result));
            } catch (final Throwable t) {
              handleExecutorError(result, t);
            }
          },
          delay.toMillis(),
          TimeUnit.MILLISECONDS);
    } catch (final Throwable t) {
      handleExecutorError(result, t);
    }
    return result;
  }

  @Override
  public void shutdown() {
    // All threads are daemon threads so don't wait for them to actually stop
    shutdown.set(true);
    scheduler.shutdownNow();
    workerPool.shutdownNow();
  }

  private <U> void handleExecutorError(final SafeFuture<U> result, final Throwable t) {
    if (t instanceof RejectedExecutionException && shutdown.get()) {
      LOG.trace("Ignoring RejectedExecutionException because shutdown is in progress", t);
    } else {
      result.completeExceptionally(t);
    }
  }

  private <U> Runnable createRunnableForAction(
      final ExceptionThrowingFutureSupplier<U> action, final SafeFuture<U> result) {
    return () -> SafeFuture.of(action).propagateTo(result);
  }
}

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
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

/**
 * *** WARNING *** Make sure about the following before using this class:
 *
 * <p>DO NOT nest calls to queueTask() - it may lead to stalled task processing. If the nested
 * task(s) are queued, there might not be any inflight tasks to complete and trigger processing of
 * the queued tasks.
 *
 * <p>Task suppliers should not be CPU intensive or blocking - they will occupy a thread from the
 * executor's thread pool preventing other tasks from running. If you need to run blocking or CPU
 * intensive tasks consider using a dedicated executor and queue for those tasks.
 *
 * <p>Submitted task should eventually complete (exceptionally or successfully). If tasks remains
 * pending forever, inflight will reach the maximum and no further tasks will be started, causing
 * the queue fill up and reject new tasks.
 */
public class ThrottlingTaskQueue implements TaskQueue {
  public static final int DEFAULT_MAXIMUM_QUEUE_SIZE = 500_000;

  private static final Logger LOG = LogManager.getLogger();
  protected final Queue<Runnable> queuedTasks;
  private final int maximumConcurrentTasks;
  protected final AtomicLong rejectedTaskCount = new AtomicLong(0);
  private final AtomicInteger inflightTaskCount = new AtomicInteger(0);

  public static class QueueIsFullException extends RejectedExecutionException {
    public QueueIsFullException() {
      super("Task queue is full");
    }
  }

  public static boolean isQueueIsFullException(final Throwable error) {
    return error instanceof QueueIsFullException
        || (error.getCause() != null && isQueueIsFullException(error.getCause()));
  }

  public static ThrottlingTaskQueue create(
      final int maximumConcurrentTasks, final int maximumQueueSize) {
    return new ThrottlingTaskQueue(maximumConcurrentTasks, maximumQueueSize);
  }

  public static ThrottlingTaskQueue create(
      final int maximumConcurrentTasks,
      final int maximumQueueSize,
      final MetricsSystem metricsSystem,
      final TekuMetricCategory metricCategory,
      final String metricName,
      final String rejectedMetricName) {
    final ThrottlingTaskQueue taskQueue =
        new ThrottlingTaskQueue(maximumConcurrentTasks, maximumQueueSize);
    metricsSystem.createGauge(
        metricCategory, metricName, "Number of tasks queued", taskQueue.queuedTasks::size);
    metricsSystem
        .createLabelledSuppliedCounter(
            metricCategory, rejectedMetricName, "Number of tasks rejected by the queue")
        .labels(taskQueue.rejectedTaskCount::get);
    return taskQueue;
  }

  protected ThrottlingTaskQueue(final int maximumConcurrentTasks, final int maximumQueueSize) {
    this.maximumConcurrentTasks = maximumConcurrentTasks;
    this.queuedTasks = new LinkedBlockingQueue<>(maximumQueueSize);
    LOG.debug("Task queue maximum concurrent tasks {}", maximumConcurrentTasks);
  }

  @Override
  public <T> SafeFuture<T> queueTask(final Supplier<SafeFuture<T>> request) {
    return queueTask(request, queuedTasks, rejectedTaskCount);
  }

  protected <T> SafeFuture<T> queueTask(
      final Supplier<SafeFuture<T>> request,
      final Queue<Runnable> queue,
      final AtomicLong rejectedTaskCounter) {
    final SafeFuture<T> target = new SafeFuture<>();
    final Runnable taskToQueue = getTaskToQueue(request, target);

    if (!queue.offer(taskToQueue)) {
      rejectedTaskCounter.incrementAndGet();
      target.completeExceptionally(new QueueIsFullException());
      return target;
    }
    processQueuedTasks();
    return target;
  }

  protected <T> Runnable getTaskToQueue(
      final Supplier<SafeFuture<T>> request, final SafeFuture<T> target) {
    return () -> {
      final SafeFuture<T> requestFuture;
      try {
        requestFuture = request.get();
      } catch (final Exception e) {
        target.completeExceptionally(e);
        taskComplete();
        return;
      }
      requestFuture.propagateTo(target);
      requestFuture.always(this::taskComplete);
    };
  }

  protected Runnable getTaskToRun() {
    return queuedTasks.poll();
  }

  protected void processQueuedTasks() {
    // Loop to ensure we start as many tasks as possible up to the concurrent limit.
    while (true) {
      final int currentInflight = inflightTaskCount.get();
      if (currentInflight >= maximumConcurrentTasks) {
        // Already at capacity, no need to do anything.
        return;
      }

      if (getQueuedTasksCount() == 0) {
        return;
      }

      // Attempt to atomically increment the count, "reserving" a slot to run a task.
      if (inflightTaskCount.compareAndSet(currentInflight, currentInflight + 1)) {
        // We successfully reserved a slot. Now, pull a task and run it.
        final Runnable task = getTaskToRun();
        if (task != null) {
          task.run();
        } else {
          // There was no task to run after all (race condition), so release our reservation.
          inflightTaskCount.decrementAndGet();
        }
      }
    }
  }

  @Override
  public int getQueuedTasksCount() {
    return queuedTasks.size();
  }

  @VisibleForTesting
  @Override
  public int getInflightTaskCount() {
    return inflightTaskCount.get();
  }

  private void taskComplete() {
    inflightTaskCount.decrementAndGet();
    processQueuedTasks();
  }
}

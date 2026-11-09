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

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueueWithPriority extends ThrottlingTaskQueue {
  private final Queue<Runnable> queuedPrioritizedTasks;

  private final AtomicLong rejectedPrioritizedTaskCount = new AtomicLong(0);

  public static ThrottlingTaskQueueWithPriority create(
      final int maximumConcurrentTasks,
      final int maximumQueueSize,
      final MetricsSystem metricsSystem,
      final TekuMetricCategory metricCategory,
      final String metricName,
      final String rejectedMetricName) {
    final ThrottlingTaskQueueWithPriority taskQueue =
        new ThrottlingTaskQueueWithPriority(maximumConcurrentTasks, maximumQueueSize);
    final LabelledSuppliedMetric taskQueueGauge =
        metricsSystem.createLabelledSuppliedGauge(
            metricCategory, metricName, "Number of tasks queued", "priority");
    taskQueueGauge.labels(taskQueue.queuedTasks::size, "normal");
    taskQueueGauge.labels(taskQueue.queuedPrioritizedTasks::size, "high");
    final LabelledSuppliedMetric labelledCounter =
        metricsSystem.createLabelledSuppliedCounter(
            metricCategory,
            rejectedMetricName,
            "Number of tasks rejected by the queue",
            "priority");

    labelledCounter.labels(taskQueue.rejectedTaskCount::get, "normal");
    labelledCounter.labels(taskQueue.rejectedPrioritizedTaskCount::get, "high");
    return taskQueue;
  }

  protected ThrottlingTaskQueueWithPriority(
      final int maximumConcurrentTasks, final int maximumQueueSize) {
    super(maximumConcurrentTasks, maximumQueueSize);
    this.queuedPrioritizedTasks = new LinkedBlockingQueue<>(maximumQueueSize);
  }

  public <T> SafeFuture<T> queueTask(
      final Supplier<SafeFuture<T>> request, final boolean prioritize) {
    if (!prioritize) {
      return queueTask(request);
    }

    return queueTask(request, queuedPrioritizedTasks, rejectedPrioritizedTaskCount);
  }

  @Override
  protected Runnable getTaskToRun() {
    final Runnable taskToQueue = queuedPrioritizedTasks.poll();
    if (taskToQueue != null) {
      return taskToQueue;
    }
    return super.getTaskToRun();
  }

  @Override
  public int getQueuedTasksCount() {
    return queuedTasks.size() + queuedPrioritizedTasks.size();
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

class MetricTrackingExecutorFactoryTest {
  private final StubMetricsSystem metricSystem = new StubMetricsSystem();
  private final ThreadFactory threadFactory =
      new ThreadFactoryBuilder()
          .setNameFormat(MetricTrackingExecutorFactoryTest.class.getSimpleName() + "-%d")
          .setDaemon(true)
          .build();
  private final List<ExecutorService> executors = new ArrayList<>();

  private final MetricTrackingExecutorFactory factory =
      new MetricTrackingExecutorFactory(metricSystem);

  @AfterEach
  void tearDown() {
    executors.forEach(ExecutorService::shutdownNow);
  }

  @Test
  void shouldQueueTasksOnceAllThreadsAreFull() throws Exception {
    final Task task1 = new Task();
    final Task task2 = new Task();
    final Task task3 = new Task();
    final ExecutorService executorService = newCachedThreadPool(2, 5);
    executorService.execute(task1);
    executorService.execute(task2);
    executorService.execute(task3);

    task1.assertStarted();
    task2.assertStarted();
    assertThat(task3.isStarted()).isFalse();

    final StubLabelledGauge gauge =
        metricSystem.getLabelledGauge(TekuMetricCategory.EXECUTOR, "queue_size");
    assertThat(gauge.getValue("foo").getAsDouble()).isEqualTo(1.0);

    assertThat(
            metricSystem
                .getGauge(TekuMetricCategory.EXECUTOR, "foo_thread_active_count")
                .getValue())
        .isEqualTo(2);
    assertThat(
            metricSystem.getGauge(TekuMetricCategory.EXECUTOR, "foo_thread_pool_size").getValue())
        .isEqualTo(2);

    task1.allowCompletion();

    task3.assertStarted();
    assertThat(gauge.getValue("foo").getAsDouble()).isEqualTo(0.0);

    task2.allowCompletion();
    task3.allowCompletion();
  }

  @Test
  void shouldRejectTasksOnceQueueIsFull() {
    final Task task1 = new Task();
    final Task task2 = new Task();
    final Task task3 = new Task();
    final Task task4 = new Task();
    final ExecutorService executorService = newCachedThreadPool(1, 2);
    executorService.execute(task1);
    executorService.execute(task2);
    executorService.execute(task3);

    assertThatThrownBy(() -> executorService.execute(task4))
        .isInstanceOf(RejectedExecutionException.class);
  }

  private ExecutorService newCachedThreadPool(final int maxThreads, final int maxQueueSize) {
    final ExecutorService executorService =
        factory.newCachedThreadPool("foo", maxThreads, maxQueueSize, threadFactory);
    executors.add(executorService);
    return executorService;
  }

  private static class Task implements Runnable {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch allowCompletion = new CountDownLatch(1);

    @Override
    public void run() {
      started.countDown();
      try {
        allowCompletion.await(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // Ignore
      }
    }

    public void assertStarted() throws Exception {
      assertThat(started.await(10, TimeUnit.SECONDS)).describedAs("task started").isTrue();
    }

    public boolean isStarted() {
      return started.getCount() == 0;
    }

    public void allowCompletion() {
      allowCompletion.countDown();
    }
  }
}

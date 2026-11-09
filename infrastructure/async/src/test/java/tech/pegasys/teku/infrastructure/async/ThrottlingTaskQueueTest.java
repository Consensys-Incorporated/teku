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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.QueueIsFullException;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueueTest {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAXIMUM_CONCURRENT_TASKS = 3;

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();

  private static final String METRIC_NAME = "test_metric";
  private static final String REJECTED_METRIC_NAME = "test_rejected_metric";
  private final ThrottlingTaskQueue taskQueue =
      ThrottlingTaskQueue.create(
          MAXIMUM_CONCURRENT_TASKS,
          15,
          stubMetricsSystem,
          TekuMetricCategory.BEACON,
          METRIC_NAME,
          REJECTED_METRIC_NAME);

  @Test
  public void throttlesRequests() {
    // queue tasks to run, they shouldn't start straight away.
    final List<SafeFuture<Void>> requests =
        IntStream.range(0, 10)
            .mapToObj(
                element ->
                    taskQueue.queueTask(
                        () ->
                            stubAsyncRunner.runAsync(
                                () -> {
                                  LOG.info("Running task {}", element);
                                  assertThat(taskQueue.getInflightTaskCount())
                                      .isLessThanOrEqualTo(MAXIMUM_CONCURRENT_TASKS);
                                })))
            .toList();

    // queueTask will start tasks up to the maximum, so expect 3 to be running, 7 in a queue
    checkQueueProgress(requests, 7, 3, 0);

    // stubRunner will run whatever is active
    stubAsyncRunner.executeQueuedActions();
    checkQueueProgress(requests, 4, 3, 3);

    // run another 3 tasks
    stubAsyncRunner.executeQueuedActions();
    checkQueueProgress(requests, 1, 3, 6);

    // only 1 task left to run, so no items left in queue
    stubAsyncRunner.executeQueuedActions();
    checkQueueProgress(requests, 0, 1, 9);

    stubAsyncRunner.executeQueuedActions();
    checkQueueProgress(requests, 0, 0, 10);
  }

  @Test
  public void shouldFailTaskIfSupplierThrows() {
    final RuntimeException error = new RuntimeException("Test exception");

    final SafeFuture<Void> request =
        taskQueue.queueTask(
            () -> {
              throw error;
            });

    assertThatSafeFuture(request).isCompletedExceptionallyWith(error);
    checkQueueProgress(List.of(request), 0, 0, 1);
  }

  @Test
  public void rejectsWhenFull() {

    final int totalTasks = 20;
    final int maxQueueSize = 15;
    final int expectedRejected = totalTasks - maxQueueSize - MAXIMUM_CONCURRENT_TASKS;
    final int[] rejectedCount = {0};
    final List<SafeFuture<Void>> requests =
        IntStream.range(0, totalTasks)
            .mapToObj(
                element ->
                    taskQueue
                        .queueTask(
                            () ->
                                stubAsyncRunner.runAsync(
                                    () -> {
                                      LOG.info("Running task {}", element);
                                      assertThat(taskQueue.getInflightTaskCount())
                                          .isLessThanOrEqualTo(MAXIMUM_CONCURRENT_TASKS);
                                    }))
                        .exceptionally(
                            err -> {
                              LOG.info("Task {} was rejected", element);
                              assertThat(err).isInstanceOf(QueueIsFullException.class);
                              rejectedCount[0]++;
                              return null;
                            }))
            .toList();

    // stubRunner will run whatever is active
    stubAsyncRunner.executeQueuedActions();
    assertThat(rejectedCount[0]).isEqualTo(expectedRejected);
    checkQueueProgress(
        requests,
        maxQueueSize - MAXIMUM_CONCURRENT_TASKS,
        MAXIMUM_CONCURRENT_TASKS,
        MAXIMUM_CONCURRENT_TASKS + expectedRejected);
  }

  protected void checkQueueProgress(
      final List<SafeFuture<Void>> requests,
      final int queueSize,
      final int inFlight,
      final int done) {
    assertThat(getQueuedTasksGaugeValue()).isEqualTo(queueSize);
    assertThat(taskQueue.getInflightTaskCount()).isEqualTo(inFlight);
    assertThat(requests.stream().filter(CompletableFuture::isDone).count()).isEqualTo(done);
  }

  private double getQueuedTasksGaugeValue() {
    return stubMetricsSystem.getGauge(TekuMetricCategory.BEACON, "test_metric").getValue();
  }
}

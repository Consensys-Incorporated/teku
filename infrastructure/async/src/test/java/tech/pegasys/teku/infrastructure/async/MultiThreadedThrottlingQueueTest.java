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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.isQueueIsFullException;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

public class MultiThreadedThrottlingQueueTest {
  private static final int SIMULATION_TIME_SECONDS = 3;

  private static final int MAXIMUM_CONCURRENT_TASKS = 5;
  private static final int MAXIMUM_QUEUE_SIZE = 100;

  private static final int NUM_PRODUCERS = 3; // Number threads queuing tasks
  private static final int MIN_BURST_SIZE = 2; // Min requests per burst
  private static final int MAX_BURST_SIZE = 8; // Max requests per burst
  private static final long MIN_DELAY_BETWEEN_BURSTS_MS = 20; // Min rest time
  private static final long MAX_DELAY_BETWEEN_BURSTS_MS = 75; // Max rest time
  private static final int MAX_SLOW_TASK_DURATION_MS = 150; // Max duration of a slow task

  // based on worst case scenario: we have MAXIMUM_QUEUE_SIZE tasks all taking
  // MAX_SLOW_TASK_DURATION_MS to complete
  private static final int EXPECTED_MAX_DEQUEUING_TIME_MS =
      (MAXIMUM_QUEUE_SIZE * MAX_SLOW_TASK_DURATION_MS) / MAXIMUM_CONCURRENT_TASKS;

  private static final int EXPECTED_MAX_TEST_DURATION_MILLIS =
      (SIMULATION_TIME_SECONDS * 1000) + EXPECTED_MAX_DEQUEUING_TIME_MS;

  private static final Logger LOG = LogManager.getLogger();

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();
  private final MetricTrackingExecutorFactory metricTrackingExecutorFactory =
      new MetricTrackingExecutorFactory(stubMetricsSystem);
  private final DefaultAsyncRunnerFactory asyncRunnerFactory =
      new DefaultAsyncRunnerFactory(metricTrackingExecutorFactory);
  private final AsyncRunner asyncRunner = asyncRunnerFactory.create("slow_tasks", 10, 50_000, 5);

  private final AtomicLong rejectedTaskCount = new AtomicLong(0);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Timeout(value = EXPECTED_MAX_TEST_DURATION_MILLIS, unit = TimeUnit.MILLISECONDS)
  @SuppressWarnings("FutureReturnValueIgnored")
  public void heavyMultiThreadedTest(final boolean isPriority) throws InterruptedException {
    final ThrottlingTaskQueue limitedQueue =
        isPriority
            ? new ThrottlingTaskQueueWithPriority(MAXIMUM_CONCURRENT_TASKS, MAXIMUM_QUEUE_SIZE)
            : new ThrottlingTaskQueue(MAXIMUM_CONCURRENT_TASKS, MAXIMUM_QUEUE_SIZE);

    // We will manually close, ignoring AutoCloseable
    final ExecutorService producerPool = Executors.newFixedThreadPool(NUM_PRODUCERS);

    // Start all the producer threads
    for (int i = 0; i < NUM_PRODUCERS; i++) {
      producerPool.submit(new Producer(limitedQueue, "Producer-" + (i + 1)));
    }

    // Let the simulation run for the specified duration
    Thread.sleep(SIMULATION_TIME_SECONDS * 1000L);

    // make sure we had some rejections
    assertThat(rejectedTaskCount.get()).isGreaterThan(0);

    LOG.info("Simulation time is over. Shutting down threads...");

    // Shutdown the producer pool. They will be interrupted and stop producing.
    producerPool.shutdownNow();

    try {
      // Wait for the threads to terminate
      assertThat(producerPool.awaitTermination(1, TimeUnit.SECONDS))
          .describedAs("produced pool threads should quickly terminate")
          .isTrue();
      LOG.info("All producer threads have been shut down.");
    } catch (final InterruptedException e) {
      LOG.error("Error waiting for thread pools to terminate.");
      Thread.currentThread().interrupt();
    }

    LOG.info("All threads have been shut down.");
    LOG.info("Remaining getInflightTaskCount in queue: {}", limitedQueue.getInflightTaskCount());
    LOG.info("Remaining getQueuedTasksCount in queue: {}", limitedQueue.getQueuedTasksCount());

    LOG.info("Waiting for remaining tasks to complete...");
    while (limitedQueue.getQueuedTasksCount() > 0 || limitedQueue.getInflightTaskCount() > 0) {
      // the test has a global timeout, so we don't need to limit this loop
      Thread.sleep(200L);
    }

    LOG.info("All queued tasks have completed.");

    LOG.info("--- Simulation Finished ---");
  }

  private class Producer implements Runnable {

    private final TaskQueue sharedQueue;
    private final String name;
    private final Random random = new Random();

    // A thread-safe counter to generate unique request IDs
    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger(0);

    public Producer(final TaskQueue sharedQueue, final String name) {
      this.name = name;
      this.sharedQueue =
          new TaskQueue() {
            @Override
            public <T> SafeFuture<T> queueTask(final Supplier<SafeFuture<T>> request) {
              if (sharedQueue instanceof ThrottlingTaskQueueWithPriority) {
                return ((ThrottlingTaskQueueWithPriority) sharedQueue)
                    .queueTask(request, random.nextBoolean());
              } else {
                return sharedQueue.queueTask(request);
              }
            }

            @Override
            public int getQueuedTasksCount() {
              return sharedQueue.getQueuedTasksCount();
            }

            @Override
            public int getInflightTaskCount() {
              return sharedQueue.getInflightTaskCount();
            }
          };
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void run() {
      LOG.info("{} has started.", name);
      try {
        while (!Thread.currentThread().isInterrupted()) {
          // Decide how many requests to produce in this burst
          final int burstSize =
              random.nextInt(MAX_BURST_SIZE - MIN_BURST_SIZE + 1) + MIN_BURST_SIZE;
          LOG.info("{} starting a burst of {} requests...", name, burstSize);

          for (int i = 0; i < burstSize; i++) {
            final long id = REQUEST_COUNTER.incrementAndGet();
            LOG.info("{} producing {}", name, id);

            SafeFuture<Void> queuedTask;

            if (random.nextBoolean()) {
              // simple instant task
              queuedTask =
                  sharedQueue
                      .queueTask(() -> SafeFuture.COMPLETE)
                      .alwaysRun(() -> LOG.info("{} instant task {} - done", name, id));

            } else {
              // slow task
              queuedTask =
                  sharedQueue
                      .queueTask(
                          () -> {
                            final SafeFuture<Long> future = new SafeFuture<>();
                            LOG.info("{} delaying {}.", name, id);
                            asyncRunner.runAfterDelay(
                                () -> {
                                  LOG.info("{} {} completing", name, id);
                                  future.complete(id);
                                },
                                Duration.ofMillis(random.nextInt(MAX_SLOW_TASK_DURATION_MS)));
                            return future;
                          })
                      .thenAccept(__ -> LOG.info("{} slow task {} - done", name, id));
            }

            if (random.nextBoolean()) {
              // Chain an instant task to run after the queued task completes
              queuedTask =
                  queuedTask.thenRun(
                      () ->
                          sharedQueue
                              .queueTask(() -> SafeFuture.COMPLETE)
                              .thenRun(
                                  () -> LOG.info("{} chained instant task {} - done", name, id)));
            }

            // finish task
            queuedTask.finish(
                err -> {
                  if (isQueueIsFullException(err)) {
                    rejectedTaskCount.incrementAndGet();
                    LOG.info("{} {} - Queue is full", name, id);
                  } else if (err != null) {
                    LOG.info("{} {} - Exception: {}", name, id, err);
                  }
                });
          }

          // Calculate how long to wait before the next burst
          long interBurstDelay =
              MIN_DELAY_BETWEEN_BURSTS_MS
                  + random.nextInt(
                      (int) (MAX_DELAY_BETWEEN_BURSTS_MS - MIN_DELAY_BETWEEN_BURSTS_MS));

          LOG.info(
              "{}",
              String.format(
                  "%s finished burst. Resting for %.2f seconds...",
                  name, interBurstDelay / 1000.0));
          Thread.sleep(interBurstDelay);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.info("{} was interrupted and is stopping.", name);
      }
      LOG.info("{} has finished.", name);
    }
  }
}

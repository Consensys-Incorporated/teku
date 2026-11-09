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

package tech.pegasys.teku.dataproviders.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.generators.CachingTaskQueue.CacheableTask;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

class CachingTaskQueueTest {
  private static final int MAX_CONCURRENT_TASKS = 2;
  private static final int MAX_CACHE_SIZE = 5;
  private static final String METRICS_PREFIX = "stub";
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final CachingTaskQueue<Integer, String> taskQueue =
      new CachingTaskQueue<>(
          SYNC_RUNNER, metricsSystem, METRICS_PREFIX, () -> MAX_CONCURRENT_TASKS, MAX_CACHE_SIZE);

  @BeforeEach
  void setUp() {
    taskQueue.startMetrics();
  }

  @Test
  void shouldHaveEmptyCacheAtStart() {
    assertCacheSizeMetric(0);
    assertCacheHitCount(0);
    assertDuplicateTaskCount(0);
    assertNewTaskCount(0);
  }

  @Test
  void shouldPerformTaskWhenNotCachedOrInProgress() {
    final StubTask task = new StubTask(4);
    final SafeFuture<Optional<String>> result = taskQueue.perform(task);
    assertThat(result).isNotDone();

    task.assertPerformedWithoutRebase();

    task.completeTask();
    assertThat(result).isCompletedWithValue(task.getExpectedValue());
  }

  @Test
  void shouldCacheResultOfPreviousTask() {
    final StubTask task1 = new StubTask(4);
    final StubTask task2 = new StubTask(4);
    final SafeFuture<Optional<String>> result1 = taskQueue.perform(task1);
    task1.completeTask();
    task1.assertPerformedWithoutRebase();
    assertThat(result1).isCompletedWithValue(task1.getExpectedValue());

    final SafeFuture<Optional<String>> result = taskQueue.perform(task2);
    assertThat(result).isCompletedWithValue(task2.getExpectedValue());
    task2.assertNotPerformed();
    task2.assertNotRebased();
    assertCacheSizeMetric(1);
    assertCacheHitCount(1);
  }

  @Test
  void shouldRegenerateInParallelUpToLimit() {
    final StubTask task1 = new StubTask(1);
    final StubTask task2 = new StubTask(2);
    final StubTask task3 = new StubTask(3);
    final SafeFuture<Optional<String>> result1 = taskQueue.perform(task1);
    final SafeFuture<Optional<String>> result2 = taskQueue.perform(task2);
    final SafeFuture<Optional<String>> result3 = taskQueue.perform(task3);
    assertThat(result1).isNotDone();
    assertThat(result2).isNotDone();
    assertThat(result3).isNotDone();
    task1.assertPerformedWithoutRebase();
    task2.assertPerformedWithoutRebase();
    task3.assertNotPerformed();
    assertNewTaskCount(3);

    // Task 3 is queued until one of the previous tasks finishes
    assertQueuedTaskCount(1);
    task1.completeTask();
    task3.assertPerformedWithoutRebase();
    assertNewTaskCount(3);
  }

  @Test
  void shouldUseAlreadyQueuedTaskIfPresent() {
    final StubTask taskA = new StubTask(1);
    final StubTask taskB = new StubTask(1);
    final SafeFuture<Optional<String>> resultA = taskQueue.perform(taskA);
    final SafeFuture<Optional<String>> resultB = taskQueue.perform(taskB);
    assertThat(resultA).isNotDone();
    assertThat(resultB).isNotDone();

    taskA.assertPerformedWithoutRebase();
    taskB.assertNotPerformed();
    taskA.completeTask();

    assertThat(resultA).isCompletedWithValue(taskA.getExpectedValue());
    assertThat(resultB).isCompletedWithValue(taskB.getExpectedValue());
    taskB.assertNotPerformed();
    assertNewTaskCount(1);
    assertDuplicateTaskCount(1);
  }

  @Test
  void shouldRebaseOntoIntermediateStepsWhenPossible() {
    final StubTask taskA = new StubTask(5, 4, 3, 2, 1);
    final StubTask taskB = new StubTask(7, 6, 5, 4, 3, 2, 1);

    final SafeFuture<Optional<String>> resultA = taskQueue.perform(taskA);
    final SafeFuture<Optional<String>> resultB = taskQueue.perform(taskB);
    assertThat(resultA).isNotDone();
    assertThat(resultB).isNotDone();

    taskA.assertPerformedWithoutRebase();
    taskB.assertNotPerformed();

    taskA.completeTask();
    taskB.assertPerformedFrom(taskA.getExpectedValue().orElseThrow());
    assertThat(resultB).isNotDone();

    taskB.completeTask();
    assertThat(resultB).isCompletedWithValue(taskB.getExpectedValue());
    assertNewTaskCount(1);
    assertRebasedTaskCount(1);
  }

  @Test
  void shouldNotPerformDuplicateTasksWhenTasksAreRebased() {
    final StubTask taskA = new StubTask(1);
    final StubTask taskB = new StubTask(5, 1);
    final StubTask taskC = new StubTask(5, 1);

    final SafeFuture<Optional<String>> resultA = taskQueue.perform(taskA);
    final SafeFuture<Optional<String>> resultB = taskQueue.perform(taskB);
    final SafeFuture<Optional<String>> resultC = taskQueue.perform(taskC);

    assertPendingTaskCount(2); // B & C were de-duplicated

    taskA.assertPerformedWithoutRebase();
    // Task B will be scheduled for rebase when A completes
    // Task C should just use the pending future from B and never execute
    taskC.assertNotRebased();
    taskC.assertNotPerformed();

    taskA.completeTask();
    taskB.assertPerformedFrom(taskA.getExpectedValue().orElseThrow());
    taskC.assertNotRebased();
    taskC.assertNotPerformed();

    taskB.completeTask();

    assertThat(resultA).isCompletedWithValue(taskA.getExpectedValue());
    assertThat(resultB).isCompletedWithValue(taskB.getExpectedValue());
    assertThat(resultC).isCompletedWithValue(taskC.getExpectedValue());
    taskC.assertNotRebased();
    taskC.assertNotPerformed();
  }

  @Test
  void shouldRecordAndUpdateTaskStatusMetrics() {
    final StubTask taskA = new StubTask(1);
    final StubTask taskB = new StubTask(2);
    final StubTask taskC = new StubTask(3);
    final SafeFuture<Optional<String>> resultA = taskQueue.perform(taskA);
    final SafeFuture<Optional<String>> resultB = taskQueue.perform(taskB);
    final SafeFuture<Optional<String>> resultC = taskQueue.perform(taskC);

    assertThat(resultA).isNotDone();
    assertThat(resultB).isNotDone();
    assertThat(resultC).isNotDone();

    assertPendingTaskCount(3);
    assertActiveTaskCount(2);
    assertQueuedTaskCount(1);

    taskA.completeTask();
    taskB.completeTask();

    assertPendingTaskCount(1);
    assertActiveTaskCount(1);
    assertQueuedTaskCount(0);
  }

  @Test
  void getIfAvailable_shouldReturnValueWhenPresent() {
    final StubTask task = new StubTask(1);
    final SafeFuture<Optional<String>> result = taskQueue.perform(task);
    task.completeTask();
    assertThat(result).isCompleted();

    assertThat(taskQueue.getIfAvailable(1)).isEqualTo(task.getExpectedValue());
  }

  @Test
  void getIfAvailable_shouldReturnEmptyWhenTaskInProgress() {
    final StubTask task = new StubTask(1);
    final SafeFuture<Optional<String>> result = taskQueue.perform(task);
    assertThat(result).isNotCompleted();

    assertThat(taskQueue.getIfAvailable(1)).isEmpty();
  }

  @Test
  void getIfAvailable_shouldReturnEmptyWhenAbsent() {
    assertThat(taskQueue.getIfAvailable(1)).isEmpty();
  }

  @Test
  void cache_shouldAddItemToCache() {
    taskQueue.cache(1, "1");
    assertThat(taskQueue.getIfAvailable(1)).contains("1");
  }

  @Test
  void cacheAll_shouldAddAllItemsToCache() {
    final Map<Integer, String> items = Map.of(1, "1", 2, "2", 3, "3");
    taskQueue.cacheAll(items);
    items.forEach((key, value) -> assertThat(taskQueue.getIfAvailable(key)).contains(value));
  }

  @Test
  void remove_shouldRemoveItemFromCache() {
    taskQueue.cache(1, "1");
    taskQueue.remove(1);
    assertThat(taskQueue.getIfAvailable(1)).isEmpty();
  }

  @Test
  void remove_shouldNotCancelPendingTasks() {
    final StubTask task = new StubTask(1);
    final SafeFuture<Optional<String>> result = taskQueue.perform(task);
    assertThat(result).isNotDone();

    taskQueue.remove(task.getKey());

    assertThat(result).isNotDone();
    task.completeTask();
    assertThat(result).isCompletedWithValue(task.getExpectedValue());
    // Should have been re-added to the cache when the task completed.
    assertThat(taskQueue.getIfAvailable(task.getKey())).isEqualTo(task.getExpectedValue());
  }

  private void assertCacheSizeMetric(final int expectedSize) {
    final double value =
        metricsSystem
            .getGauge(TekuMetricCategory.STORAGE, METRICS_PREFIX + "_cache_size")
            .getValue();
    assertThat(value).isEqualTo(expectedSize);
  }

  private void assertCacheHitCount(final int expectedCount) {
    final double value =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks_total", "cached");
    assertThat(value).isEqualTo(expectedCount);
  }

  private void assertNewTaskCount(final int expectedCount) {
    final double value =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks_total", "new");
    assertThat(value).isEqualTo(expectedCount);
  }

  private void assertDuplicateTaskCount(final int expectedCount) {
    final double value =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks_total", "duplicate");
    assertThat(value).isEqualTo(expectedCount);
  }

  private void assertRebasedTaskCount(final int expectedCount) {
    final double value =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks_total", "rebase");
    assertThat(value).isEqualTo(expectedCount);
  }

  private void assertPendingTaskCount(final int expectedCount) {
    final OptionalDouble optionalValue =
        metricsSystem
            .getLabelledGauge(TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks")
            .getValue("requested");
    assertThat(optionalValue).hasValue(expectedCount);
  }

  private void assertActiveTaskCount(final int expectedCount) {
    final OptionalDouble optionalValue =
        metricsSystem
            .getLabelledGauge(TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks")
            .getValue("active");
    assertThat(optionalValue).hasValue(expectedCount);
  }

  private void assertQueuedTaskCount(final int expectedCount) {
    final OptionalDouble optionalValue =
        metricsSystem
            .getLabelledGauge(TekuMetricCategory.STORAGE, METRICS_PREFIX + "_tasks")
            .getValue("queued");
    assertThat(optionalValue).hasValue(expectedCount);
  }

  public static class StubTask implements CacheableTask<Integer, String> {
    private final SafeFuture<Optional<String>> result = new SafeFuture<>();
    private final Integer key;
    private final IntList intermediateSteps;
    private boolean regenerated = false;
    private Optional<String> rebasedTo = Optional.empty();

    public StubTask(final Integer key, final int... intermediateSteps) {
      this.key = key;
      this.intermediateSteps = IntList.of(intermediateSteps);
    }

    @Override
    public Integer getKey() {
      return key;
    }

    @Override
    public Stream<Integer> streamIntermediateSteps() {
      @SuppressWarnings("DoNotUseDeprecatedFastutilMethod")
      Stream<Integer> stream = intermediateSteps.intStream().boxed();
      return stream;
    }

    @Override
    public CacheableTask<Integer, String> rebase(final String newBaseValue) {
      rebasedTo = Optional.of(newBaseValue);
      return this;
    }

    @Override
    public SafeFuture<Optional<String>> performTask() {
      regenerated = true;
      return result;
    }

    public void assertPerformedWithoutRebase() {
      assertNotRebased();
      assertThat(regenerated).describedAs("regenerated").isTrue();
    }

    public void assertPerformedFrom(final String newBase) {
      assertThat(regenerated).describedAs("regenerated").isTrue();
      assertThat(rebasedTo).describedAs("rebased starting point").contains(newBase);
    }

    public void completeTask() {
      result.complete(getExpectedValue());
    }

    public Optional<String> getExpectedValue() {
      return Optional.of(key.toString());
    }

    public void assertNotPerformed() {
      assertThat(regenerated).describedAs("regenerated").isFalse();
    }

    public void assertNotRebased() {
      assertThat(rebasedTo).describedAs("rebased starting point").isEmpty();
    }
  }
}

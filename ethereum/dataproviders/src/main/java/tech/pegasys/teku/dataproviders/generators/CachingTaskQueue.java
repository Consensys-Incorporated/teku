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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class CachingTaskQueue<K, V> {

  private final Counter cachedTaskCounter;
  private final Counter duplicateTaskCounter;
  private final Counter newTaskCounter;
  private final Counter rebasedTaskCounter;

  private final ConcurrentMap<K, SafeFuture<Optional<V>>> pendingTasks = new ConcurrentHashMap<>();
  private final AtomicInteger activeTasks = new AtomicInteger(0);
  private final Queue<CacheableTask<K, V>> queuedTasks = new ConcurrentLinkedQueue<>();

  private final Map<K, V> cache;
  private final AsyncRunner asyncRunner;
  private final MetricsSystem metricsSystem;
  private final String metricsPrefix;
  private final IntSupplier activeTaskLimit;

  CachingTaskQueue(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final String metricsPrefix,
      final IntSupplier activeTaskLimit,
      final int maxCacheSize) {
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.metricsPrefix = metricsPrefix;
    this.activeTaskLimit = activeTaskLimit;
    this.cache = LimitedMap.createSoft(maxCacheSize);

    final LabelledMetric<Counter> labelledCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.STORAGE,
            metricsPrefix + "_tasks_total",
            "Total number of tasks requested",
            "type");
    duplicateTaskCounter = labelledCounter.labels("duplicate");
    cachedTaskCounter = labelledCounter.labels("cached");
    newTaskCounter = labelledCounter.labels("new");
    rebasedTaskCounter = labelledCounter.labels("rebase");
  }

  public static <K, V> CachingTaskQueue<K, V> create(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final String metricsPrefix,
      final int maxCacheSize) {
    return new CachingTaskQueue<>(
        asyncRunner,
        metricsSystem,
        metricsPrefix,
        () -> Math.max(2, Runtime.getRuntime().availableProcessors()),
        maxCacheSize);
  }

  public void startMetrics() {
    final LabelledSuppliedMetric taskQueueMetrics =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.STORAGE,
            metricsPrefix + "_tasks",
            "Labelled task queue metrics",
            "status");
    taskQueueMetrics.labels(pendingTasks::size, "requested");
    taskQueueMetrics.labels(activeTasks::get, "active");
    taskQueueMetrics.labels(queuedTasks::size, "queued");
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.STORAGE,
        metricsPrefix + "_cache_size",
        "Number of checkpoint states held in the in-memory store",
        cache::size);
  }

  public synchronized SafeFuture<Optional<V>> perform(final CacheableTask<K, V> task) {
    // Check if a completed result is available
    final V cachedResult = cache.get(task.getKey());
    if (cachedResult != null) {
      cachedTaskCounter.inc();
      return SafeFuture.completedFuture(Optional.of(cachedResult));
    }

    // Check if the task is already scheduled
    final SafeFuture<Optional<V>> currentPendingTask = pendingTasks.get(task.getKey());
    if (currentPendingTask != null) {
      duplicateTaskCounter.inc();
      return currentPendingTask;
    }

    final SafeFuture<Optional<V>> generationResult = new SafeFuture<>();
    pendingTasks.put(task.getKey(), generationResult);

    // Check if there's a better starting point (in cache or in progress)
    final Optional<SafeFuture<Optional<V>>> newBase =
        task.streamIntermediateSteps()
            .map(
                key ->
                    Optional.ofNullable(cache.get(key))
                        .map(value -> SafeFuture.completedFuture(Optional.of(value)))
                        .orElse(pendingTasks.get(key)))
            .filter(Objects::nonNull)
            .findFirst();
    if (newBase.isPresent()) {
      rebasedTaskCounter.inc();
      newBase
          .get()
          .thenAccept(ancestorResult -> queueTask(task.rebase(ancestorResult)))
          .finish(error -> completePendingTask(task, Optional.empty(), error));
      return generationResult;
    }

    // Schedule the task for execution
    newTaskCounter.inc();
    queueTask(task);
    return generationResult;
  }

  public Optional<V> getIfAvailable(final K key) {
    return Optional.ofNullable(cache.get(key));
  }

  private void queueTask(final CacheableTask<K, V> task) {
    queuedTasks.add(task);
    tryProcessNext();
  }

  private synchronized void tryProcessNext() {
    while (activeTasks.get() < activeTaskLimit.getAsInt() && !queuedTasks.isEmpty()) {
      processNext();
    }
  }

  private void processNext() {
    final CacheableTask<K, V> task = queuedTasks.poll();
    if (task == null) {
      activeTasks.decrementAndGet();
      return;
    }
    activeTasks.incrementAndGet();
    asyncRunner
        .runAsync(task::performTask)
        .thenPeek(result -> result.ifPresent(value -> cache(task.getKey(), value)))
        .handle(
            (result, error) -> {
              completePendingTask(task, result, error);
              // Errors are propagated to the result, so convert to a completed future here
              // Otherwise it will be reported as an unhandled exception.
              return null;
            })
        .alwaysRun(
            () -> {
              activeTasks.decrementAndGet();
              tryProcessNext();
            })
        .finishStackTrace();
  }

  private synchronized void completePendingTask(
      final CacheableTask<K, V> task, final Optional<V> result, final Throwable error) {
    final SafeFuture<Optional<V>> future = pendingTasks.remove(task.getKey());
    asyncRunner
        .runAsync(
            () -> {
              if (error != null) {
                future.completeExceptionally(error);
              } else {
                future.complete(result);
              }
            })
        .finishStackTrace();
  }

  public void cache(final K key, final V value) {
    cache.put(key, value);
  }

  public void cacheAll(final Map<K, V> values) {
    cache.putAll(values);
  }

  public void remove(final K key) {
    cache.remove(key);
  }

  public void removeIf(final Predicate<K> removalCondition) {
    cache.keySet().removeIf(removalCondition);
  }

  public void clear() {
    cache.clear();
  }

  public interface CacheableTask<K, V> {
    /**
     * The key that uniquely identifies this task. Two tasks with equal keys should also have
     * equivalent results.
     */
    K getKey();

    /**
     * Return the keys for intermediate steps that this task can be rebased on top of, in order of
     * desirability (ie the best starting point first).
     *
     * @return stream of intermediate step keys.
     */
    Stream<K> streamIntermediateSteps();

    /**
     * Return a new CacheableTask which starts from the supplied value. The value must be the value
     * generated by one of the intermediate steps returned by {@link #streamIntermediateSteps()}.
     *
     * @param newBaseValue the intermediate value to start generation from.
     * @return the new task.
     */
    CacheableTask<K, V> rebase(final V newBaseValue);

    default CacheableTask<K, V> rebase(final Optional<V> newBaseValue) {
      return newBaseValue.map(this::rebase).orElse(this);
    }

    /**
     * Perform the task to create the value.
     *
     * @return a future that contains the final task output or empty optional if the task could not
     *     access the required input data.
     */
    SafeFuture<Optional<V>> performTask();
  }
}

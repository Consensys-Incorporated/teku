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

package tech.pegasys.teku.beacon.sync.gossip;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.fetch.AbstractFetchTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.RetryDelayFunction;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.service.serviceutils.Service;

/**
 * @param <K> - the type of the key used to retrieve an object from a fetch task
 * @param <T> - the type of the task used for fetching
 * @param <R> - the type of the object that is fetched
 */
public abstract class AbstractFetchService<K, T extends AbstractFetchTask<K, R>, R>
    extends Service {

  private static final Logger LOG = LogManager.getLogger();

  public static final int DEFAULT_MAX_CONCURRENT_BLOCKS_REQUESTS = 3;

  private static final Duration WAIT_FOR_PEERS_DURATION = Duration.ofSeconds(30);
  private static final RetryDelayFunction RETRY_DELAY_FUNCTION =
      RetryDelayFunction.createExponentialRetry(2, Duration.ofSeconds(5), Duration.ofMinutes(5));

  protected final Map<K, T> allTasks = new ConcurrentHashMap<>();

  private final Queue<T> pendingTasks = new ConcurrentLinkedQueue<>();
  private final Collection<T> activeTasks = new ConcurrentLinkedQueue<>();

  private final AsyncRunner asyncRunner;
  private final int maxConcurrentRequests;

  protected AbstractFetchService(final AsyncRunner asyncRunner, final int maxConcurrentRequests) {
    this.asyncRunner = asyncRunner;
    this.maxConcurrentRequests = maxConcurrentRequests;
  }

  protected void runNextTask(final T task) {
    registerActiveTask(task);
    task.run()
        .thenAccept(res -> processFetchResult(task, res))
        .exceptionally(
            (err) -> {
              LOG.warn("Failed to run " + getTaskName(task), err);
              return null;
            })
        .always(() -> deregisterActiveTask(task));
  }

  protected void processFetchResult(final T task, final FetchResult<R> result) {
    switch (result.getStatus()) {
      case SUCCESSFUL -> processFetchedResult(task, result.getResult().orElseThrow());
      case NO_AVAILABLE_PEERS ->
          // Wait a bit and then requeue
          queueTaskWithDelay(task, WAIT_FOR_PEERS_DURATION);
      case FETCH_FAILED ->
          // Push task back onto queue to retry
          queueTaskWithRetryDelay(task);
      case CANCELLED -> {
        LOG.trace("Request for {} cancelled: {}.", getTaskName(task), task.getKey());
        removeTask(task);
      }
    }
  }

  protected void cancelRequest(final K key) {
    final T task = allTasks.get(key);
    if (task != null) {
      task.cancel();
    }
  }

  protected void removeTask(final T task) {
    // Stop tracking task
    task.cancel();
    allTasks.remove(task.getKey(), task);
  }

  protected void queueTask(final T task) {
    pendingTasks.add(task);
    checkTasks();
  }

  private synchronized void checkTasks() {
    // If we have capacity, execute the next task
    if (activeTasks.size() < maxConcurrentRequests) {
      final T nextTask = pendingTasks.poll();
      if (nextTask == null) {
        return;
      }
      runNextTask(nextTask);
    }
  }

  protected void queueTaskWithDelay(final T task, final Duration delay) {
    asyncRunner
        .getDelayedFuture(delay)
        .finish(
            () -> queueTask(task),
            (err) -> {
              task.cancel();
              LOG.error(
                  String.format(
                      "Unable to execute delayed task. Dropping task %s %s",
                      getTaskName(task), task.getKey()),
                  err);
            });
  }

  protected void queueTaskWithRetryDelay(final T task) {
    final Duration delay = RETRY_DELAY_FUNCTION.getRetryDelay(task.getNumberOfRetries());
    queueTaskWithDelay(task, delay);
  }

  public int countPendingTasks() {
    return pendingTasks.size();
  }

  public int countActiveTasks() {
    return activeTasks.size();
  }

  public int countTrackedTasks() {
    return allTasks.size();
  }

  private void registerActiveTask(final T task) {
    LOG.trace("{} {}", getTaskName(task), task.getKey());
    activeTasks.add(task);
  }

  private void deregisterActiveTask(final T task) {
    activeTasks.remove(task);
    checkTasks();
  }

  private String getTaskName(final T task) {
    return task.getClass().getSimpleName();
  }

  public abstract T createTask(K key);

  public abstract void processFetchedResult(T task, R result);
}

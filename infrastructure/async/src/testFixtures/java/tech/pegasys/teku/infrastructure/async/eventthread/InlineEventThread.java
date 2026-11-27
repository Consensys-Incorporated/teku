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

package tech.pegasys.teku.infrastructure.async.eventthread;

import com.google.common.base.Preconditions;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * An EventThread implementation that immediately executes commands given to it. Useful for tests
 * which are already single threaded.
 */
public class InlineEventThread implements EventThread {

  private final ThreadLocal<Boolean> isEventThread = ThreadLocal.withInitial(() -> false);
  private final Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

  @Override
  public void checkOnEventThread() {
    Preconditions.checkState(
        isEventThread.get(), "Attempting to access resource when not on the event thread");
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void execute(final Runnable command) {
    withEventThreadMarkerSet(command);

    // Execute any tasks run with executeLater before returning.
    executePendingTasks();
  }

  @Override
  public void executeLater(final Runnable task) {
    pendingTasks.add(task);
  }

  @Override
  public <T> SafeFuture<T> execute(final ExceptionThrowingSupplier<T> callable) {
    final SafeFuture<T> result = new SafeFuture<>();
    withEventThreadMarkerSet(() -> SafeFuture.of(callable).propagateTo(result));

    // Execute any tasks run with executeLater before returning.
    executePendingTasks();

    return result;
  }

  @Override
  public <T> SafeFuture<T> executeFuture(final Supplier<SafeFuture<T>> callable) {
    final SafeFuture<T> result = new SafeFuture<>();
    withEventThreadMarkerSet(() -> SafeFuture.of(callable::get).propagateTo(result));

    // Execute any tasks run with executeLater before returning.
    executePendingTasks();

    return result;
  }

  public void executePendingTasks() {
    withEventThreadMarkerSet(
        () -> {
          while (!pendingTasks.isEmpty()) {
            pendingTasks.remove().run();
          }
        });
  }

  /**
   * Available for testing so that tests can call methods which normally must be accessed on the
   * event thread, without having to actually pass a lambda to execute.
   */
  public void markAsOnEventThread() {
    isEventThread.set(true);
  }

  public void markAsOffEventThread() {
    isEventThread.set(false);
  }

  /**
   * Executes the specified command with a flag set to identify the current thread as the event
   * thread.
   *
   * <p>While we always stay on the event thread, we want to track that access actually went through
   * this class so mark the current thread as the event thread in a re-entrant safe way to make
   * checkOnEventThread work
   *
   * @param command the command to execute
   */
  private void withEventThreadMarkerSet(final Runnable command) {

    final Boolean originalEventThread = isEventThread.get();
    markAsOnEventThread();
    command.run();

    isEventThread.set(originalEventThread);
  }
}

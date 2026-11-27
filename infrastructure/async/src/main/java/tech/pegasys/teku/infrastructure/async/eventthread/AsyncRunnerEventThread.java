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

import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AsyncRunnerEventThread implements EventThread {
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final String name;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private AsyncRunner thread;

  /** The ID of the event thread. */
  private volatile long eventThreadId = -1;

  public AsyncRunnerEventThread(final String name, final AsyncRunnerFactory asyncRunnerFactory) {
    this.name = name;
    this.asyncRunnerFactory = asyncRunnerFactory;
  }

  @Override
  public void checkOnEventThread() {
    checkState(isEventThread(), "Attempting to access %s resource from non-event thread", name);
  }

  private boolean isEventThread() {
    return Thread.currentThread().threadId() == eventThreadId;
  }

  @Override
  public synchronized void start() {
    if (started.get()) {
      return;
    }
    thread = asyncRunnerFactory.create(name, 1);
    started.set(true);
  }

  @Override
  public synchronized void stop() {
    if (!started.compareAndSet(true, false)) {
      return;
    }
    thread.shutdown();
  }

  @Override
  public void executeLater(final Runnable task) {
    if (!started.get()) {
      throw new IllegalStateException("EventThread not started");
    }
    thread.runAsync(() -> recordEventThreadIdAndExecute(asSupplier(task))).finishStackTrace();
  }

  @Override
  public <T> SafeFuture<T> execute(final ExceptionThrowingSupplier<T> callable) {
    // Note: started is only set to true after thread has been initialized so if it is true, thread
    // must be initialized.
    if (!started.get()) {
      return SafeFuture.failedFuture(new IllegalStateException("EventThread not started"));
    }
    return doExecute(callable);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public <T> SafeFuture<T> executeFuture(final Supplier<SafeFuture<T>> task) {
    // Note: started is only set to true after thread has been initialized so if it is true, thread
    // must be initialized.
    if (!started.get()) {
      return SafeFuture.failedFuture(new IllegalStateException("EventThread not started"));
    }
    return doExecute(task::get).thenCompose(Function.identity());
  }

  @Override
  public void execute(final Runnable task) {
    if (!started.get()) {
      // If we threw an exception here, it would cause a lot of noise at
      // shutdown because started is set to false on shutdown. We may still be
      // sending tasks to the executor which we deliberately want to ignore.
      return;
    }
    doExecute(asSupplier(task)).finishStackTrace();
  }

  private <T> SafeFuture<T> doExecute(final ExceptionThrowingSupplier<T> callable) {
    // Execute immediately if we're already on the event thread.
    if (isEventThread()) {
      return SafeFuture.of(callable);
    } else {
      return thread.runAsync(() -> recordEventThreadIdAndExecute(callable));
    }
  }

  /**
   * Record the ID of the current thread as the event thread ID and execute the specified task.
   *
   * <p>While there is only one event thread, if there is no activity for some time the thread may
   * expire and shutdown, then a different thread created to handle the next event. This is still
   * thread-safe but we need to make sure we have the latest thread ID to make isEventThread work.
   *
   * @param task the task to execute.
   */
  private <T> T recordEventThreadIdAndExecute(final ExceptionThrowingSupplier<T> task)
      throws Throwable {
    eventThreadId = Thread.currentThread().threadId();
    try {
      return task.get();
    } finally {
      // Reset again to avoid problems if the thread exits and its ID is reused.
      eventThreadId = -1;
    }
  }

  private ExceptionThrowingSupplier<Object> asSupplier(final Runnable task) {
    return () -> {
      task.run();
      return null;
    };
  }
}

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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes tasks using an {@link AsyncRunner} ensuring that the order of task execution is
 * preserved.
 *
 * <p>While this could also be achieved by using an AsyncRunner with a single thread, this class
 * allows reusing an existing pool of threads to make managing work loads easier.
 */
public class OrderedAsyncRunner implements Executor {
  private final AtomicReference<SafeFuture<Void>> lastTask =
      new AtomicReference<>(SafeFuture.COMPLETE);
  private final AsyncRunner asyncRunner;

  public OrderedAsyncRunner(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
  }

  @Override
  public void execute(final Runnable task) {
    lastTask.updateAndGet(previous -> executeAfter(previous, task));
  }

  private SafeFuture<Void> executeAfter(final SafeFuture<Void> previous, final Runnable task) {
    return previous
        .exceptionally(
            error -> {
              final Thread currentThread = Thread.currentThread();
              currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, error);
              return null;
            })
        .thenCompose(__ -> asyncRunner.runAsync(task::run));
  }
}

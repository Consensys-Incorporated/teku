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

package tech.pegasys.teku.infrastructure.async.runloop;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingRunnable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AsyncRunLoop {

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final RunLoopLogic logic;
  private final AsyncRunner asyncRunner;
  private final Duration retryDelay;

  public AsyncRunLoop(
      final RunLoopLogic logic, final AsyncRunner asyncRunner, final Duration retryDelay) {
    this.logic = logic;
    this.asyncRunner = asyncRunner;
    this.retryDelay = retryDelay;
  }

  public void start() {
    logic
        .init()
        .thenCompose(__ -> nextLoopAfterDelay())
        .finish(error -> onError(error, this::start));
  }

  public void stop() {
    stopped.compareAndSet(false, true);
  }

  private SafeFuture<Void> nextLoopAfterDelay() {
    if (stopped.get()) {
      return SafeFuture.COMPLETE;
    }
    return asyncRunner.runAfterDelay(
        () -> {
          if (stopped.get()) {
            return;
          }
          nextLoop();
        },
        logic.getDelayUntilNextAdvance());
  }

  private void nextLoop() {
    logic
        .advance()
        .thenCompose(__ -> nextLoopAfterDelay())
        .finish(error -> onError(error, this::nextLoop));
  }

  private void onError(final Throwable error, final ExceptionThrowingRunnable retryAction) {
    if (error instanceof CompletionException && error.getCause() != null) {
      logic.onError(error.getCause());
    } else {
      logic.onError(error);
    }
    asyncRunner.runAfterDelay(retryAction, retryDelay).finishStackTrace();
  }
}

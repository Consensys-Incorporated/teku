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

import java.util.concurrent.Executor;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface EventThread extends Executor {

  /**
   * Asserts that the current thread is the event thread and throws an `IllegalStateException` if
   * not.
   */
  void checkOnEventThread();

  void start();

  void stop();

  /**
   * Add a task to the end of the event queue. Unlike {@link
   * EventThread#execute(java.lang.Runnable)}, guarantees that the task will not be executed
   * immediately, even if the current thread is the event thread.
   */
  void executeLater(final Runnable task);

  <T> SafeFuture<T> execute(final ExceptionThrowingSupplier<T> callable);

  <T> SafeFuture<T> executeFuture(final Supplier<SafeFuture<T>> callable);
}

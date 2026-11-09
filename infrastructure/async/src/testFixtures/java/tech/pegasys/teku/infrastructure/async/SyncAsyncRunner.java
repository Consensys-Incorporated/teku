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

import java.time.Duration;

public class SyncAsyncRunner implements AsyncRunner {
  public static final SyncAsyncRunner SYNC_RUNNER = new SyncAsyncRunner();

  private SyncAsyncRunner() {}

  @Override
  public <U> SafeFuture<U> runAsync(final ExceptionThrowingFutureSupplier<U> action) {
    return SafeFuture.of(action);
  }

  @Override
  public <U> SafeFuture<U> runAfterDelay(
      final ExceptionThrowingFutureSupplier<U> action, final Duration delay) {
    throw new UnsupportedOperationException(
        "Delayed execution not possible using " + SyncAsyncRunner.class.getName());
  }

  @Override
  public void shutdown() {}
}

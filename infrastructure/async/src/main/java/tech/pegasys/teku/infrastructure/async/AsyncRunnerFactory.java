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

import java.util.regex.Pattern;

public interface AsyncRunnerFactory {

  int DEFAULT_MAX_QUEUE_SIZE = 10_000;
  int DEFAULT_MAX_QUEUE_SIZE_ALL_SUBNETS = 40_000;
  int DEFAULT_THREAD_PRIORITY = Thread.NORM_PRIORITY;
  Pattern ASYNC_RUNNER_NAME_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

  default AsyncRunner create(final String name, final int maxThreads) {
    validateAsyncRunnerName(name);
    return create(name, maxThreads, DEFAULT_MAX_QUEUE_SIZE);
  }

  default void validateAsyncRunnerName(final String asyncRunnerName) {
    if (!ASYNC_RUNNER_NAME_PATTERN.matcher(asyncRunnerName).matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid async runner name %s. Must match the regex %s",
              asyncRunnerName, ASYNC_RUNNER_NAME_PATTERN.pattern()));
    }
  }

  default AsyncRunner create(final String name, final int maxThreads, final int maxQueueSize) {
    return create(name, maxThreads, maxQueueSize, DEFAULT_THREAD_PRIORITY);
  }

  AsyncRunner create(String name, int maxThreads, int maxQueueSize, int threadPriority);

  void shutdown();

  static DefaultAsyncRunnerFactory createDefault(
      final MetricTrackingExecutorFactory executorFactory) {
    return new DefaultAsyncRunnerFactory(executorFactory);
  }
}

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

import java.util.function.Consumer;

public class ExceptionHandlers {

  public static Runnable exceptionHandlingRunnable(
      final Runnable runnable, final SafeFuture<?> target) {
    return () -> {
      try {
        runnable.run();
      } catch (final Throwable t) {
        target.completeExceptionally(t);
      }
    };
  }

  public static <T> Consumer<T> exceptionHandlingConsumer(
      final Consumer<T> consumer, final SafeFuture<?> target) {
    return value -> {
      try {
        consumer.accept(value);
      } catch (final Throwable t) {
        target.completeExceptionally(t);
      }
    };
  }
}

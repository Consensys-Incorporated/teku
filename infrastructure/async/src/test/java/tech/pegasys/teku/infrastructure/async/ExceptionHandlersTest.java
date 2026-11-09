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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ExceptionHandlersTest {

  @Test
  void exceptionHandlingRunnable_shouldNotCompleteFutureWhenRunnableCompletesNormally() {
    final SafeFuture<Void> future = new SafeFuture<>();
    final Runnable delegate = mock(Runnable.class);
    final Runnable runnable = ExceptionHandlers.exceptionHandlingRunnable(delegate, future);
    runnable.run();

    verify(delegate).run();
    assertThat(future).isNotCompleted();
  }

  @Test
  @SuppressWarnings("unchecked")
  void exceptionHandlingConsumer_shouldPropagateExceptionInConsumer() {
    final SafeFuture<Void> future = new SafeFuture<>();
    final RuntimeException exception = new RuntimeException("oops");
    final String value = "the value";
    final Consumer<String> delegate = mock(Consumer.class);
    doThrow(exception).when(delegate).accept(value);
    final Consumer<String> consumer = ExceptionHandlers.exceptionHandlingConsumer(delegate, future);
    consumer.accept(value);
    verify(delegate).accept(value);
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::join).hasRootCause(exception);
  }

  @Test
  @SuppressWarnings("unchecked")
  void exceptionHandlingConsumer_shouldNotCompleteFutureWhenRunnableCompletesNormally() {
    final SafeFuture<Void> future = new SafeFuture<>();
    final String value = "the value";
    final Consumer<String> delegate = mock(Consumer.class);
    final Consumer<String> consumer = ExceptionHandlers.exceptionHandlingConsumer(delegate, future);
    consumer.accept(value);

    verify(delegate).accept(value);
    assertThat(future).isNotCompleted();
  }
}

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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.Thread.UncaughtExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AsyncOrderedRunnerTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final Runnable task1 = mock(Runnable.class);
  private final Runnable task2 = mock(Runnable.class);

  private final OrderedAsyncRunner orderedRunner = new OrderedAsyncRunner(asyncRunner);
  private UncaughtExceptionHandler originalUncaughtExceptionHandler;

  @BeforeEach
  void setUp() {
    originalUncaughtExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
  }

  @AfterEach
  void tearDown() {
    Thread.currentThread().setUncaughtExceptionHandler(originalUncaughtExceptionHandler);
  }

  @Test
  void shouldExecuteFirstTaskWithAsyncRunner() {
    orderedRunner.execute(task1);

    verifyNoInteractions(task1);

    asyncRunner.executeQueuedActions();

    verify(task1).run();
  }

  @Test
  void shouldNotExecuteSecondActionUntilFirstCompletes() {
    orderedRunner.execute(task1);
    orderedRunner.execute(task2);

    verifyNoInteractions(task1);
    verifyNoInteractions(task2);

    // Only task1 was scheduled
    asyncRunner.executeQueuedActions();
    verify(task1).run();
    verifyNoInteractions(task2);

    // Now task 2 is scheduled
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(task1);
    verify(task2).run();
  }

  @Test
  void shouldExecuteNextTaskAfterFirstFails() {
    final UncaughtExceptionHandler exceptionHandler = mock(UncaughtExceptionHandler.class);
    Thread.currentThread().setUncaughtExceptionHandler(exceptionHandler);
    final RuntimeException error = new RuntimeException("Bang");
    doThrow(error).when(task1).run();

    orderedRunner.execute(task1);
    orderedRunner.execute(task2);

    verifyNoInteractions(task1);
    verifyNoInteractions(task2);

    // Only task1 was scheduled
    asyncRunner.executeQueuedActions();
    verify(task1).run();
    verifyNoInteractions(task2);
    // Error should be reported to the uncaught exception handler
    final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(exceptionHandler).uncaughtException(same(Thread.currentThread()), errorCaptor.capture());
    assertThat(errorCaptor.getValue()).hasCause(error);

    // Now task 2 is scheduled
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(task1);
    verify(task2).run();
  }
}

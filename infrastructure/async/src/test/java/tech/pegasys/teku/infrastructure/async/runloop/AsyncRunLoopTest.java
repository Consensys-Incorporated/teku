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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class AsyncRunLoopTest {
  private static final Duration RETRY_DELAY = Duration.ofSeconds(10);
  private final RunLoopLogic logic = mock(RunLoopLogic.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(100_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final AsyncRunLoop runLoop = new AsyncRunLoop(logic, asyncRunner, RETRY_DELAY);

  @BeforeEach
  void setUp() {
    // By default init completes but advance doesn't
    when(logic.init()).thenReturn(SafeFuture.COMPLETE);
    when(logic.advance()).thenReturn(new SafeFuture<>());
  }

  @Test
  void shouldCallInitWhenStarted() {
    runLoop.start();

    verify(logic).init();
  }

  @Test
  void shouldRetryAfterDelayIfInitFails() {
    final Throwable error = new RuntimeException("Computer says no");
    when(logic.init()).thenReturn(SafeFuture.failedFuture(error)).thenReturn(SafeFuture.COMPLETE);

    runLoop.start();

    verify(logic).init();
    verify(logic).onError(error);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    advanceTime(RETRY_DELAY);
    verify(logic, times(2)).init();
  }

  @Test
  void shouldWaitThenCallAdvanceAfterInitCompletes() {
    final SafeFuture<Void> initResult = new SafeFuture<>();
    final Duration advanceDelay = Duration.ofSeconds(3);
    when(logic.init()).thenReturn(initResult);
    when(logic.getDelayUntilNextAdvance()).thenReturn(advanceDelay);

    runLoop.start();
    verify(logic).init();
    verifyNoMoreInteractions(logic);

    // Time advances but because init hasn't completed we are no closer to triggering advance
    advanceTime(advanceDelay);

    initResult.complete(null);
    verify(logic).getDelayUntilNextAdvance();
    verifyNoMoreInteractions(logic);

    // Delay starts after init completes and then triggers advance when due
    advanceTime(advanceDelay);

    verify(logic).advance();
    verifyNoMoreInteractions(logic);
  }

  @Test
  void shouldScheduleNextAdvanceWhenFirstAdvanceCompletes() {
    final Duration firstAdvanceDelay = Duration.ofSeconds(2);
    final Duration secondAdvanceDelay = Duration.ofSeconds(20);
    when(logic.advance()).thenReturn(SafeFuture.COMPLETE).thenReturn(new SafeFuture<>());
    when(logic.getDelayUntilNextAdvance()).thenReturn(firstAdvanceDelay, secondAdvanceDelay);

    runLoop.start();
    verify(logic).init();
    verify(logic, never()).advance();

    advanceTime(firstAdvanceDelay);
    verify(logic).advance();

    // Shouldn't use same delay for second advance so this won't wait long enough
    advanceTime(firstAdvanceDelay);
    verify(logic).advance();

    // Advance remaining time until second advance is due and confirm it is invoked
    advanceTime(secondAdvanceDelay.minus(firstAdvanceDelay));
    verify(logic, times(2)).advance();

    verify(logic, atLeastOnce()).getDelayUntilNextAdvance();
    verifyNoMoreInteractions(logic);
  }

  @Test
  void shouldNotAdvanceIfStopped() {
    final Duration advanceDelay = Duration.ofSeconds(2);
    when(logic.advance()).thenReturn(SafeFuture.COMPLETE);
    when(logic.getDelayUntilNextAdvance()).thenReturn(advanceDelay);

    runLoop.start();
    verify(logic).init();
    verify(logic, never()).advance();

    advanceTime(advanceDelay);
    verify(logic).advance();

    runLoop.stop();
    advanceTime(advanceDelay);

    // advance should have been invoked only once
    verify(logic).advance();
  }

  @Test
  void shouldRetryAfterDelayWhenAdvanceFails() {
    final Duration advanceDelay = Duration.ofSeconds(5);
    final Throwable error = new RuntimeException("Computer says no");
    when(logic.advance()).thenReturn(SafeFuture.failedFuture(error)).thenReturn(new SafeFuture<>());
    when(logic.getDelayUntilNextAdvance()).thenReturn(advanceDelay);

    runLoop.start();
    advanceTime(advanceDelay);

    verify(logic).advance();
    verify(logic).onError(error);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    advanceTime(RETRY_DELAY);

    verify(logic, times(2)).advance();
  }

  private void advanceTime(final Duration advanceDelay) {
    timeProvider.advanceTimeBy(advanceDelay);
    asyncRunner.executeDueActions();
  }
}

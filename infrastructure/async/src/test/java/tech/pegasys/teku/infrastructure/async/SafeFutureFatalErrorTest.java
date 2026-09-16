/*
 * Copyright Consensys Software Inc., 2026
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.ExitConstants;
import tech.pegasys.teku.infrastructure.exceptions.FatalErrorHandler;

/**
 * Async code across Teku catches {@link Throwable} and turns it into a failed future or a log line.
 * These tests confirm a fatal error taking those paths still shuts the node down.
 */
@SuppressWarnings("FutureReturnValueIgnored")
class SafeFutureFatalErrorTest {

  private final AtomicInteger terminations = new AtomicInteger(0);
  private final List<Integer> exitCodes = new ArrayList<>();

  @BeforeEach
  void setUp() {
    FatalErrorHandler.overrideProcessTerminator(
        (exitCode, gracefulTimeout) -> {
          terminations.incrementAndGet();
          exitCodes.add(exitCode);
        });
  }

  @AfterEach
  void tearDown() {
    FatalErrorHandler.restoreDefaultProcessTerminator();
  }

  @Test
  void shouldShutdownWhenFutureIsCompletedWithOutOfMemoryError() {
    new SafeFuture<>().completeExceptionally(new OutOfMemoryError());

    assertThat(terminations).hasValue(1);
    // Restarting is expected to recover from running out of memory
    assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  void shouldShutdownWhenActionThrowsOutOfMemoryError() {
    SafeFuture.fromRunnable(
        () -> {
          throw new OutOfMemoryError();
        });

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldShutdownWhenOutOfMemoryErrorIsPassedToAnExceptionHandler() {
    failedWith(new OutOfMemoryError()).handleException(error -> {});

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldShutdownWhenOutOfMemoryErrorIsOnlyLogged() {
    failedWith(new OutOfMemoryError()).finishError(LogManager.getLogger());

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldShutdownWhenOutOfMemoryErrorIsReplacedWithAFallbackValue() {
    failedWith(new OutOfMemoryError()).exceptionally(error -> null);

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldShutdownWhenOutOfMemoryErrorIsPassedToAFinishErrorHandler() {
    failedWith(new OutOfMemoryError()).finish(error -> {});

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldShutdownWhenOutOfMemoryErrorIsObservedByWhenComplete() {
    failedWith(new OutOfMemoryError()).whenComplete((result, error) -> {});

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldShutdownWhenOutOfMemoryErrorIsNotOneOfTheIgnoredExceptions() {
    failedWith(new OutOfMemoryError()).ignoreExceptions(IOException.class);

    assertThat(terminations).hasValue(1);
  }

  @Test
  void shouldNotShutdownForOrdinaryFailures() {
    failedWith(new IOException("nope")).handleException(error -> {});
    failedWith(new IOException("nope")).finishError(LogManager.getLogger());
    failedWith(new IOException("nope")).exceptionally(error -> null);
    failedWith(new IOException("nope")).finish(error -> {});
    failedWith(new IOException("nope")).whenComplete((result, error) -> {});

    assertThat(terminations).hasValue(0);
  }

  /**
   * Returns a future failed by a task throwing, so the error is wrapped in a {@link
   * java.util.concurrent.CompletionException} exactly as it would be in production and doesn't pass
   * through {@link SafeFuture#completeExceptionally(Throwable)}.
   */
  private SafeFuture<Void> failedWith(final Throwable error) {
    return SafeFuture.COMPLETE.thenRun(
        () -> {
          throw new AssertionError("wrapper", error);
        });
  }
}

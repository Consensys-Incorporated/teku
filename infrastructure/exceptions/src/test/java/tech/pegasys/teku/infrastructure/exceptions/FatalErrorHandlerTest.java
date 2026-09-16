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

package tech.pegasys.teku.infrastructure.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FatalErrorHandlerTest {

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
  void isFatalError_shouldDetectDirectOutOfMemoryError() {
    assertThat(FatalErrorHandler.isFatalError(new OutOfMemoryError("Java heap space"))).isTrue();
  }

  @Test
  void isFatalError_shouldDetectOutOfMemoryErrorWrappedByAsyncFramework() {
    final Throwable error =
        new CompletionException(
            new ExecutionException(new IllegalStateException(new OutOfMemoryError())));

    assertThat(FatalErrorHandler.isFatalError(error)).isTrue();
  }

  @Test
  void isFatalError_shouldDetectSuppressedOutOfMemoryError() {
    // SafeFuture.allOf reports the additional failures as suppressed errors
    final Throwable error = new CompletionException(new IOException("first failure"));
    error.addSuppressed(new OutOfMemoryError());

    assertThat(FatalErrorHandler.isFatalError(error)).isTrue();
  }

  @Test
  void isFatalError_shouldDetectSubclassesOfOutOfMemoryError() {
    // For example Netty's OutOfDirectMemoryError
    assertThat(FatalErrorHandler.isFatalError(new RuntimeException(new CustomOutOfMemoryError())))
        .isTrue();
  }

  @Test
  void isFatalError_shouldNotDetectOrdinaryErrors() {
    assertThat(FatalErrorHandler.isFatalError(new CompletionException(new IOException("nope"))))
        .isFalse();
    assertThat(FatalErrorHandler.isFatalError(new IllegalStateException())).isFalse();
    assertThat(FatalErrorHandler.isFatalError(null)).isFalse();
  }

  @Test
  void isFatalError_shouldNotLoopForeverWhenCausesAreCyclic() {
    final RuntimeException a = new RuntimeException();
    final RuntimeException b = new RuntimeException(a);
    a.addSuppressed(b);

    assertThat(FatalErrorHandler.isFatalError(b)).isFalse();
  }

  @Test
  void shutdownIfFatalError_shouldTerminateWithErrorExitCodeWhenFatal() {
    assertThat(
            FatalErrorHandler.shutdownIfFatalError(
                new CompletionException(new OutOfMemoryError()), "test"))
        .isTrue();

    assertThat(terminations).hasValue(1);
    assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  void shutdownIfFatalError_shouldDoNothingWhenNotFatal() {
    assertThat(FatalErrorHandler.shutdownIfFatalError(new IOException("nope"), "test")).isFalse();

    assertThat(terminations).hasValue(0);
  }

  @Test
  void shutdownIfFatalError_shouldOnlyTerminateOnceWhenMultipleErrorsAreReported() {
    for (int i = 0; i < 5; i++) {
      assertThat(FatalErrorHandler.shutdownIfFatalError(new OutOfMemoryError(), "test")).isTrue();
    }

    assertThat(terminations).hasValue(1);
  }

  @Test
  void terminate_shouldUseTheSuppliedExitCode() {
    FatalErrorHandler.terminate(ExitConstants.FATAL_EXIT_CODE);

    assertThat(exitCodes).containsExactly(ExitConstants.FATAL_EXIT_CODE);
  }

  @Test
  void terminate_shouldOnlyTerminateOnce() {
    FatalErrorHandler.terminate(ExitConstants.FATAL_EXIT_CODE);
    FatalErrorHandler.terminate(ExitConstants.ERROR_EXIT_CODE);
    FatalErrorHandler.shutdownIfFatalError(new OutOfMemoryError(), "test");

    assertThat(terminations).hasValue(1);
  }

  /**
   * Reproduces the shape of a real hang: an out of memory error triggered {@link System#exit(int)},
   * which ran the shutdown hook, which blocked forever stopping the REST API (Jetty waits on an
   * untimed latch). The JVM stayed alive for days with every other thread that called exit blocked
   * on the {@code Shutdown} class monitor.
   */
  @Test
  void terminateProcess_shouldHaltWhenAShutdownHookNeverReturns() throws Exception {
    final CountDownLatch halted = new CountDownLatch(1);
    final CountDownLatch wedgedHook = new CountDownLatch(1);

    FatalErrorHandler.terminateProcess(
        Duration.ofMillis(100),
        () -> {
          // System.exit never returns because a shutdown hook is stuck
          try {
            wedgedHook.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        },
        halted::countDown);

    assertThat(halted.await(30, TimeUnit.SECONDS)).isTrue();
    wedgedHook.countDown();
  }

  @Test
  void terminateProcess_shouldNotBlockTheReportingThread() {
    final CountDownLatch wedgedHook = new CountDownLatch(1);

    // Returns rather than blocking with the wedged exit call, unlike calling System.exit directly
    FatalErrorHandler.terminateProcess(
        Duration.ofHours(1),
        () -> {
          try {
            wedgedHook.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        },
        () -> {});

    wedgedHook.countDown();
  }

  @Test
  void terminateProcess_shouldUseDaemonThreadsSoTheyCannotKeepTheJvmAlive() {
    FatalErrorHandler.terminateProcess(Duration.ofHours(1), () -> {}, () -> {});

    assertThat(findThreads("fatal-error-"))
        .isNotEmpty()
        .allSatisfy(thread -> assertThat(thread.isDaemon()).isTrue());
  }

  private List<Thread> findThreads(final String namePrefix) {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> thread.getName().startsWith(namePrefix))
        .toList();
  }

  @Test
  void shutdown_shouldPassAGracefulShutdownTimeoutToTheTerminator() {
    final List<Duration> timeouts = new ArrayList<>();
    FatalErrorHandler.overrideProcessTerminator(
        (exitCode, gracefulTimeout) -> timeouts.add(gracefulTimeout));

    FatalErrorHandler.shutdown(new OutOfMemoryError(), "test");

    assertThat(timeouts).containsExactly(FatalErrorHandler.GRACEFUL_SHUTDOWN_TIMEOUT);
  }

  // Static so that this Serializable class doesn't reference the non serializable test class
  private static class CustomOutOfMemoryError extends OutOfMemoryError {}
}

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

package tech.pegasys.teku;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.ExitConstants;
import tech.pegasys.teku.infrastructure.exceptions.FatalErrorHandler;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.services.beaconchain.EphemeryLifecycleException;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

class TekuDefaultExceptionHandlerTest {

  private final StatusLogger log = mock(StatusLogger.class);
  private final TekuDefaultExceptionHandler exceptionHandler = new TekuDefaultExceptionHandler(log);
  private final List<Integer> exitCodes = new ArrayList<>();

  @BeforeEach
  void setUp() {
    FatalErrorHandler.overrideProcessTerminator(
        (exitCode, gracefulTimeout) -> exitCodes.add(exitCode));
  }

  @AfterEach
  void tearDown() {
    FatalErrorHandler.restoreDefaultProcessTerminator();
  }

  @Test
  void logWarningIfAssertFails() {
    final IllegalArgumentException exception = new IllegalArgumentException("whoops");
    exceptionHandler.uncaughtException(Thread.currentThread(), exception);

    verify(log).specificationFailure(any(), eq(exception));
  }

  @Test
  void logFatalIfNonAssertExceptionThrown() {
    final RuntimeException exception = new RuntimeException("test");

    exceptionHandler.uncaughtException(Thread.currentThread(), exception);

    verify(log).unexpectedFailure(anyString(), eq(exception));
  }

  @Test
  void shouldNotShutdownForOrdinaryFailures() {
    exceptionHandler.uncaughtException(Thread.currentThread(), new IOException("nope"));

    assertThat(exitCodes).isEmpty();
  }

  // Out of memory is expected to be recoverable by a restart, so it must not use the exit code
  // which tells operators not to restart
  @Test
  void shouldExitWithErrorExitCodeWhenOutOfMemoryErrorIsThrown() {
    final OutOfMemoryError error = new OutOfMemoryError();

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    verify(log).fatalError(anyString(), eq(error));
    assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  void shouldExitWithErrorExitCodeWhenOutOfMemoryErrorIsWrappedInAnotherException() {
    final Throwable error =
        new CompletionException(new IllegalArgumentException(new OutOfMemoryError()));

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    verify(log).fatalError(anyString(), eq(error));
    verify(log, never()).specificationFailure(anyString(), any());
    assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  void shouldExitWithFatalExitCodeWhenAServiceFailsFatally() {
    final Throwable error =
        new CompletionException(
            new FatalServiceFailureException(
                TekuDefaultExceptionHandlerTest.class, new IOException()));

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    assertThat(exitCodes).containsExactly(ExitConstants.FATAL_EXIT_CODE);
  }

  @Test
  void shouldExitWithFatalExitCodeWhenStorageIsUnrecoverable() {
    final Throwable error =
        new CompletionException(
            DatabaseStorageException.unrecoverable("corrupt", new IOException()));

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    assertThat(exitCodes).containsExactly(ExitConstants.FATAL_EXIT_CODE);
  }

  // A fatal service failure means a restart won't help, even when it was caused by running out of
  // memory, so it keeps precedence over the out of memory check
  @Test
  void shouldExitWithFatalExitCodeWhenAFatalServiceFailureWasCausedByOutOfMemory() {
    final Throwable error =
        new CompletionException(
            new FatalServiceFailureException(
                TekuDefaultExceptionHandlerTest.class, new OutOfMemoryError()));

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    assertThat(exitCodes).containsExactly(ExitConstants.FATAL_EXIT_CODE);
  }

  @Test
  void shouldExitWithErrorExitCodeWhenEphemeryLifecycleEnds() {
    exceptionHandler.uncaughtException(
        Thread.currentThread(), new EphemeryLifecycleException("reset required"));

    assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }
}

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.ExitConstants;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;

class TekuDefaultExceptionHandlerTest {

  private final StatusLogger log = mock(StatusLogger.class);
  private final List<Integer> haltedWith = new ArrayList<>();
  private final TekuDefaultExceptionHandler exceptionHandler =
      new TekuDefaultExceptionHandler(log, haltedWith::add);

  @Test
  void logWarningIfAssertFails() {
    final IllegalArgumentException exception = new IllegalArgumentException("whoops");
    exceptionHandler.uncaughtException(Thread.currentThread(), exception);

    verify(log).specificationFailure(any(), eq(exception));
    assertThat(haltedWith).isEmpty();
  }

  @Test
  void logFatalIfNonAssertExceptionThrown() {
    final RuntimeException exception = new RuntimeException("test");

    exceptionHandler.uncaughtException(Thread.currentThread(), exception);

    verify(log).unexpectedFailure(anyString(), eq(exception));
    assertThat(haltedWith).isEmpty();
  }

  @Test
  void shouldHaltOnDirectBufferMemoryError() {
    // The form NIO uses when the direct memory limit is reached. Not covered by
    // -XX:+ExitOnOutOfMemoryError because it is thrown by Java code rather than raised by the VM.
    final OutOfMemoryError error =
        new OutOfMemoryError("Cannot reserve 1048576 bytes of direct buffer memory");

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    verify(log).fatalError(anyString(), eq(error));
    // ERROR_EXIT_CODE because restarting is expected to recover
    assertThat(haltedWith).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  void shouldHaltWhenOutOfMemoryErrorIsWrappedInAnotherException() {
    // How RpcHandler reports a failure from a Netty channel, via a failed future
    final Throwable error =
        new CompletionException(
            new IllegalStateException("Channel exception", new OutOfDirectMemoryError()));

    exceptionHandler.uncaughtException(Thread.currentThread(), error);

    verify(log).fatalError(anyString(), eq(error));
    verify(log, never()).unexpectedFailure(anyString(), any());
    assertThat(haltedWith).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  void shouldHaltEvenWhenReportingTheErrorFails() {
    final OutOfMemoryError error = new OutOfMemoryError("direct buffer memory");
    doThrow(new OutOfMemoryError("failed to log")).when(log).fatalError(anyString(), any());

    assertThatThrownBy(() -> exceptionHandler.uncaughtException(Thread.currentThread(), error))
        .isInstanceOf(OutOfMemoryError.class);

    assertThat(haltedWith).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  // Stands in for Netty's OutOfDirectMemoryError, which is not a dependency of this module.
  // Static so that this Serializable class doesn't reference the non serializable test class.
  private static class OutOfDirectMemoryError extends OutOfMemoryError {}
}

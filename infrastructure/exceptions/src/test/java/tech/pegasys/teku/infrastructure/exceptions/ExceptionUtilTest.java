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
import java.io.InvalidClassException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

public class ExceptionUtilTest {

  @Test
  public void getCause_noChainOfCauses() {
    final RuntimeException err = new RuntimeException();

    // Exact match
    assertThat(ExceptionUtil.getCause(err, RuntimeException.class)).contains(err);
    // Superclass match
    assertThat(ExceptionUtil.getCause(err, Exception.class)).contains(err);

    // Subclass does not match
    assertThat(ExceptionUtil.getCause(err, CustomRuntimeException.class)).isEmpty();
    // Other error does not match
    assertThat(ExceptionUtil.getCause(err, IllegalStateException.class)).isEmpty();
  }

  @Test
  public void getCause_withChainOfCauses() {
    final IllegalArgumentException rootCause = new IllegalArgumentException("Wrong argument");
    final IllegalStateException intermediateCause = new IllegalStateException(rootCause);
    final RuntimeException err = new RuntimeException(intermediateCause);

    // Match error
    assertThat(ExceptionUtil.getCause(err, RuntimeException.class)).contains(err);
    // Superclass match
    assertThat(ExceptionUtil.getCause(err, Exception.class)).contains(err);

    // Match intermediate cause
    assertThat(ExceptionUtil.getCause(err, IllegalStateException.class))
        .contains(intermediateCause);

    // Match root cause
    assertThat(ExceptionUtil.getCause(err, IllegalArgumentException.class)).contains(rootCause);
  }

  @Test
  public void getCause_withChainOfSameType() {
    final RuntimeException rootCause = new RuntimeException("Oops");
    final RuntimeException err = new RuntimeException(rootCause);

    // Match error
    assertThat(ExceptionUtil.getCause(err, RuntimeException.class)).contains(err);
  }

  @Test
  void hasCause_shouldBeTrueWhenAnyCauseIsFound() {
    final IllegalStateException rootCause = new IllegalStateException("Oops");
    final RuntimeException err = new RuntimeException(rootCause);
    assertThat(ExceptionUtil.hasCause(err, IOException.class, IllegalStateException.class))
        .isTrue();
  }

  @Test
  void hasCause_shouldBeTrueWhenExceptionIsOneOfTargetTypes() {
    final Exception rootCause = new IOException("Oops");
    final RuntimeException err = new IllegalStateException(rootCause);
    assertThat(ExceptionUtil.hasCause(err, IOException.class, IllegalStateException.class))
        .isTrue();
  }

  @Test
  void hasCause_shouldBeFalseWhenNoCauseMatchesTargetTypes() {
    final Exception rootCause = new IOException("Oops");
    final RuntimeException err = new RuntimeException(rootCause);
    assertThat(
            ExceptionUtil.hasCause(err, InvalidClassException.class, IllegalStateException.class))
        .isFalse();
  }

  @Test
  public void escalateIfOutOfMemory_reportsOutOfMemoryErrorToTheUncaughtExceptionHandler() {
    final OutOfMemoryError error =
        new OutOfMemoryError("Cannot reserve 1048576 bytes of direct buffer memory");

    assertThat(escalated(error)).containsExactly(error);
  }

  @Test
  public void escalateIfOutOfMemory_reportsWrappedOutOfMemoryError() {
    // The form a Netty channel error takes by the time it is handled
    final Throwable error =
        new CompletionException(
            new IllegalStateException("Channel exception", new OutOfMemoryError()));

    assertThat(escalated(error)).containsExactly(error);
  }

  @Test
  public void escalateIfOutOfMemory_ignoresOtherErrors() {
    assertThat(escalated(new IOException("nope"))).isEmpty();
    assertThat(escalated(new CompletionException(new IllegalStateException()))).isEmpty();
  }

  /** Runs the escalation with a capturing uncaught exception handler installed. */
  private List<Throwable> escalated(final Throwable error) {
    final List<Throwable> captured = new ArrayList<>();
    final Thread thread = Thread.currentThread();
    final UncaughtExceptionHandler original = thread.getUncaughtExceptionHandler();
    thread.setUncaughtExceptionHandler((t, e) -> captured.add(e));
    try {
      ExceptionUtil.escalateIfOutOfMemory(error);
    } finally {
      thread.setUncaughtExceptionHandler(original);
    }
    return captured;
  }

  private static class CustomRuntimeException extends RuntimeException {}
}

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

import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ExceptionUtil {

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> Optional<T> getCause(
      final Throwable err, final Class<? extends T> targetType) {
    return ExceptionUtils.getThrowableList(err).stream()
        .filter(targetType::isInstance)
        .map(e -> (T) e)
        .findFirst();
  }

  public static <T extends Throwable> boolean hasCause(
      final Throwable err, final Class<? extends T> targetType) {
    return getCause(err, targetType).isPresent();
  }

  /**
   * Hands an {@link OutOfMemoryError} to the thread's uncaught exception handler, so that a node
   * which cannot allocate memory any more is shut down instead of being left running in a broken
   * state. Does nothing for any other error.
   *
   * <p>Call this where an error would otherwise be swallowed, typically a {@code catch (Throwable)}
   * that logs and carries on. Out of memory errors raised by the VM never reach such code, as
   * {@code -XX:+ExitOnOutOfMemoryError} terminates the JVM where the error is thrown. This is for
   * the ones thrown by Java code, which that flag does not detect: Netty's {@code
   * OutOfDirectMemoryError} and NIO's "Cannot reserve ... direct buffer memory".
   *
   * <p>Interception is therefore best effort: it only works where Teku code sees the error. An
   * error caught and logged inside a library, such as Netty's pipeline tail or a Jetty thread pool,
   * is still swallowed.
   */
  public static void escalateIfOutOfMemory(final Throwable err) {
    if (err instanceof OutOfMemoryError || hasCause(err, OutOfMemoryError.class)) {
      final Thread currentThread = Thread.currentThread();
      currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, err);
    }
  }

  public static String getRootCauseMessage(final Throwable err) {
    return Optional.ofNullable(ExceptionUtils.getRootCause(err))
        .map(ExceptionUtil::getMessageOrSimpleName)
        .orElse("");
  }

  @SafeVarargs
  public static boolean hasCause(
      final Throwable err, final Class<? extends Throwable>... targetTypes) {

    return ExceptionUtils.getThrowableList(err).stream()
        .anyMatch(cause -> isAnyOf(cause, targetTypes));
  }

  @SafeVarargs
  private static boolean isAnyOf(
      final Throwable cause, final Class<? extends Throwable>... targetTypes) {
    for (Class<? extends Throwable> targetType : targetTypes) {
      if (targetType.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }

  public static String getMessageOrSimpleName(final Throwable throwable) {
    return Optional.ofNullable(throwable.getMessage()).orElse(throwable.getClass().getSimpleName());
  }
}

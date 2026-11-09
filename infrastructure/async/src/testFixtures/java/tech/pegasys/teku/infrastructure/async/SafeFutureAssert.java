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

import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.assertj.core.api.AbstractCompletableFutureAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.assertj.core.api.ThrowableAssert;

public class SafeFutureAssert<T> extends AbstractCompletableFutureAssert<SafeFutureAssert<T>, T> {

  private SafeFutureAssert(final SafeFuture<T> actual) {
    super(actual, SafeFutureAssert.class);
  }

  public static <T> SafeFutureAssert<T> assertThatSafeFuture(final SafeFuture<T> actual) {
    return new SafeFutureAssert<>(actual);
  }

  public void isCompletedExceptionallyWith(final Throwable t) {
    isCompletedExceptionally();
    Assertions.assertThatThrownBy(actual::join)
        .describedAs(info.description())
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(t);
  }

  public void isCompletedExceptionallyWithSuppressed(final Throwable[] suppressed) {
    isCompletedExceptionally();
    Assertions.assertThatThrownBy(actual::join)
        .extracting(Throwable::getCause)
        .extracting(Throwable::getSuppressed)
        .isEqualTo(suppressed);
  }

  public void isCompletedExceptionallyWithCauseAndSuppressed(
      final Throwable cause, final Throwable... suppressed) {
    isCompletedExceptionallyWith(cause);
    isCompletedExceptionallyWithSuppressed(suppressed);
  }

  public <X extends Throwable> ThrowableAssert<X> isCompletedExceptionallyWith(
      final Class<X> exceptionType) {
    isCompletedExceptionally();
    return Assertions.assertThatThrownBy(actual::join)
        .describedAs(info.description())
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isInstanceOf(exceptionType)
        .asInstanceOf(
            new InstanceOfAssertFactory<X, ThrowableAssert<X>>(
                exceptionType, ThrowableAssert::new));
  }

  public void isCompletedExceptionallyWithMessage(final String expectedMessage) {
    isCompletedExceptionally();
    Assertions.assertThatThrownBy(actual::join)
        .describedAs(info.description())
        .extracting(Throwable::getMessage)
        .matches(m -> m.contains(expectedMessage));
  }

  public void isCompletedWithEmptyOptional() {
    isCompleted();
    assertThat(actual.join()).describedAs(info.description()).isEqualTo(Optional.empty());
  }

  public void isCompletedWithNonEmptyOptional() {
    isCompleted();
    T result = actual.join();
    assertThat(result).describedAs(info.description()).isInstanceOf(Optional.class);
    assertThat((Optional<?>) result).describedAs(info.description()).isNotEmpty();
  }

  @SuppressWarnings("unchecked")
  public <X> void isCompletedWithOptionalContaining(final X value) {
    isCompleted();
    T result = actual.join();
    assertThat(result).describedAs(info.description()).isInstanceOf(Optional.class);
    assertThat(((Optional<T>) result)).describedAs(info.description()).contains((T) value);
  }

  public T joinsImmediately() {
    isDone();
    return actual.join();
  }

  public static <X> X safeJoin(final SafeFuture<X> future) {
    return assertThatSafeFuture(future).joinsImmediately();
  }
}

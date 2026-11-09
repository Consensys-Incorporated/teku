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

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RootCauseExceptionHandlerTest {
  @SuppressWarnings("unchecked")
  private final Consumer<Throwable> defaultHandler = mock(Consumer.class);

  @Test
  public void shouldUseDefaultHandlerWhenNoOthersMatch() {
    final RootCauseExceptionHandler handler =
        RootCauseExceptionHandler.builder()
            .addCatch(IllegalArgumentException.class, e -> fail("Wrong handler used"))
            .defaultCatch(defaultHandler);

    final Exception exception = new IllegalStateException("Oh no");
    handler.accept(exception);
    verify(defaultHandler).accept(exception);
  }

  @Test
  public void shouldUseSpecifiedHandlerWhenExceptionWithoutCauseMatches() {
    final Consumer<IllegalArgumentException> illegalArgumentHandler = mockHandler();
    final RootCauseExceptionHandler handler =
        RootCauseExceptionHandler.builder()
            .addCatch(IllegalArgumentException.class, illegalArgumentHandler)
            .defaultCatch(defaultHandler);

    final IllegalArgumentException exception = new IllegalArgumentException("Yup");
    handler.accept(exception);
    verify(illegalArgumentHandler).accept(exception);
    verifyNoInteractions(defaultHandler);
  }

  @Test
  public void shouldUseSpecifiedHandlerWhenRootCauseMatches() {
    final Consumer<IllegalArgumentException> illegalArgumentHandler = mockHandler();
    final RootCauseExceptionHandler handler =
        RootCauseExceptionHandler.builder()
            .addCatch(IllegalArgumentException.class, illegalArgumentHandler)
            .defaultCatch(defaultHandler);

    final IllegalArgumentException exception = new IllegalArgumentException("Yup");
    handler.accept(new CompletionException(exception));
    verify(illegalArgumentHandler).accept(exception);
    verifyNoInteractions(defaultHandler);
  }

  @Test
  public void shouldUseSpecifiedHandlerWhenSuperclassOfExceptionTypeMatches() {
    final Consumer<IOException> ioExceptionHandler = mockHandler();

    final RootCauseExceptionHandler handler =
        RootCauseExceptionHandler.builder()
            .addCatch(IOException.class, ioExceptionHandler)
            .defaultCatch(defaultHandler);

    final FileNotFoundException exception = new FileNotFoundException("No file");
    handler.accept(exception);
    verify(ioExceptionHandler).accept(exception);
    verifyNoInteractions(defaultHandler);
  }

  @SuppressWarnings("unchecked")
  private <T extends Throwable> Consumer<T> mockHandler() {
    return mock(Consumer.class);
  }
}

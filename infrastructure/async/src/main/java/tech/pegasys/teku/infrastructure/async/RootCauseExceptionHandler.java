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

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RootCauseExceptionHandler implements Consumer<Throwable> {

  private final List<ExceptionHandler<?>> handlers;

  private RootCauseExceptionHandler(final List<ExceptionHandler<?>> handlers) {
    this.handlers = handlers;
  }

  public static RootCauseExceptionHandler.Builder builder() {
    return new RootCauseExceptionHandler.Builder();
  }

  @Override
  public void accept(final Throwable t) {
    final Throwable rootCause = Throwables.getRootCause(t);
    for (ExceptionHandler<?> handler : handlers) {
      if (handler.attemptHandle(rootCause)) {
        return;
      }
    }
    throw new IllegalStateException(
        "No handler specified for exception type " + rootCause.getClass(), t);
  }

  public static class Builder {
    private final List<ExceptionHandler<? extends Throwable>> handlers = new ArrayList<>();

    public <T extends Throwable> Builder addCatch(
        final Class<T> rootCause, final Consumer<T> handler) {
      handlers.add(new ExceptionHandler<>(rootCause, handler));
      return this;
    }

    public RootCauseExceptionHandler defaultCatch(final Consumer<Throwable> defaultHandler) {
      handlers.add(new ExceptionHandler<>(Throwable.class, defaultHandler));
      return new RootCauseExceptionHandler(handlers);
    }
  }

  private static class ExceptionHandler<T extends Throwable> {
    private final Class<T> exceptionType;
    private final Consumer<T> handler;

    private ExceptionHandler(final Class<T> exceptionType, final Consumer<T> handler) {
      this.exceptionType = exceptionType;
      this.handler = handler;
    }

    @SuppressWarnings("unchecked")
    public boolean attemptHandle(final Throwable rootCause) {
      if (exceptionType.isInstance(rootCause)) {
        handler.accept((T) rootCause);
        return true;
      }
      return false;
    }
  }
}

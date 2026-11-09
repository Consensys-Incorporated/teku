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

package tech.pegasys.teku.service.serviceutils;

import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public abstract class Service implements ServiceFacade {
  enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  @Override
  public SafeFuture<?> start() {
    if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
      return SafeFuture.failedFuture(
          new IllegalStateException("Attempt to start an already started service."));
    }
    return doStart();
  }

  protected abstract SafeFuture<?> doStart();

  @Override
  public SafeFuture<?> stop() {
    if (state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return doStop();
    } else {
      // Return a successful future if there's nothing to do at this point
      return SafeFuture.COMPLETE;
    }
  }

  protected abstract SafeFuture<?> doStop();

  @Override
  public boolean isRunning() {
    return state.get() == State.RUNNING;
  }

  protected void assertIsRunning(final String action) {
    checkState(isRunning(), "Service must be running to execute action '%s'", action);
  }
}

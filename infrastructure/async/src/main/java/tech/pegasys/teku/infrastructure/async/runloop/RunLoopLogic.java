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

package tech.pegasys.teku.infrastructure.async.runloop;

import java.time.Duration;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Implements the logic to be used inside an {@link AsyncRunLoop}.
 *
 * <p>While multiple threads may be used to call into this class, it is guaranteed that only one
 * method will be executing at any given time, including waiting for any returned future to
 * complete. Additionally happens-before memory semantics are guaranteed so that any variable
 * changes performed in one method will be visible to subsequently invoked methods.
 */
public interface RunLoopLogic {
  /** Called when the run loop first starts to perform any initial setup. */
  SafeFuture<Void> init();

  /** Called each time around the run loop. */
  SafeFuture<Void> advance();

  /**
   * Determine the delay from current time before {@link #advance()} is to be called again.
   *
   * <p>While generally this will be called only once per loop, with {@link #advance()} then being
   * called after the specified delay, it may be called multiple times between calls to {@link
   * #advance()} if required and should adjust the returned duration to account for any time that
   * has passed.
   */
  Duration getDelayUntilNextAdvance();

  /**
   * Called to notify of an exception that occurred either while executing one of the methods of
   * this class or when a returned future completes exceptionally.
   *
   * <p>The run loop will automatically retry after a delay, this method only needs to update any
   * internal state in response to the error message and log it if appropriate.
   *
   * @param t the error that occurred
   */
  void onError(Throwable t);
}

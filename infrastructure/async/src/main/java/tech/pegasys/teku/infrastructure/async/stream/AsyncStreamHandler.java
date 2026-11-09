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

package tech.pegasys.teku.infrastructure.async.stream;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Internal {@link AsyncStream} implementation interface which is analogous to {@link
 * java.util.concurrent.Flow.Subscriber} in RX world
 */
public interface AsyncStreamHandler<T> {

  SafeFuture<Boolean> TRUE_FUTURE = SafeFuture.completedFuture(true);
  SafeFuture<Boolean> FALSE_FUTURE = SafeFuture.completedFuture(false);

  /**
   * Called when next element is available
   *
   * @return The promise to the upstream handler if the further elements are expected. When the
   *     future is completed with false the only next call expected is {@link #onComplete()}
   */
  SafeFuture<Boolean> onNext(T t);

  /**
   * Called when the stream is complete. No other calls are expected after this call
   *
   * <p>An implementation MUST NOT call this method prior to {@link SafeFuture<Boolean>} returned
   * from the last {@link #onNext(Object)} call is completed.
   */
  void onComplete();

  /** Called when upstream error happened Basically no other calls are expected after this */
  void onError(Throwable t);
}

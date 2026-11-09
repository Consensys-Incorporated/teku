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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Handler that merges elements from two AsyncStreams, emitting elements from both as they become
 * available.
 */
public class MergeStreamHandler<T> implements AsyncStreamHandler<T> {

  private final AsyncStreamHandler<T> downstream;
  private final AtomicInteger completedStreams = new AtomicInteger(0);
  private final AtomicBoolean errorOccurred = new AtomicBoolean(false);

  public MergeStreamHandler(
      final AsyncStreamHandler<T> downstream, final AsyncStream<T> otherStream) {
    this.downstream = downstream;

    // Start consuming the other stream
    otherStream.consume(
        new AsyncStreamHandler<T>() {
          @Override
          public SafeFuture<Boolean> onNext(final T t) {
            if (!errorOccurred.get()) {
              return downstream.onNext(t);
            }
            return SafeFuture.completedFuture(false);
          }

          @Override
          public void onComplete() {
            if (completedStreams.incrementAndGet() == 2 && !errorOccurred.get()) {
              downstream.onComplete();
            }
          }

          @Override
          public void onError(final Throwable error) {
            if (errorOccurred.compareAndSet(false, true)) {
              downstream.onError(error);
            }
          }
        });
  }

  @Override
  public SafeFuture<Boolean> onNext(final T t) {
    if (!errorOccurred.get()) {
      return downstream.onNext(t);
    }
    return SafeFuture.completedFuture(false);
  }

  @Override
  public void onComplete() {
    if (completedStreams.incrementAndGet() == 2 && !errorOccurred.get()) {
      downstream.onComplete();
    }
  }

  @Override
  public void onError(final Throwable error) {
    if (errorOccurred.compareAndSet(false, true)) {
      downstream.onError(error);
    }
  }
}

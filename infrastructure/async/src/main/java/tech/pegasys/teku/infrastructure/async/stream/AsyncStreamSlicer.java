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

import static tech.pegasys.teku.infrastructure.async.stream.AsyncStreamSlicer.SliceResult.CONTINUE;
import static tech.pegasys.teku.infrastructure.async.stream.AsyncStreamSlicer.SliceResult.INCLUDE_AND_STOP;
import static tech.pegasys.teku.infrastructure.async.stream.AsyncStreamSlicer.SliceResult.SKIP_AND_STOP;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public interface AsyncStreamSlicer<T> {

  enum SliceResult {
    CONTINUE,
    INCLUDE_AND_STOP,
    SKIP_AND_STOP
  }

  SliceResult slice(final T element);

  static <T> AsyncStreamSlicer<T> limit(final long count) {
    return new AsyncStreamSlicer<>() {
      private final AtomicLong remainCount = new AtomicLong(count);

      @Override
      public SliceResult slice(final T element) {
        return remainCount.decrementAndGet() > 0 ? CONTINUE : INCLUDE_AND_STOP;
      }
    };
  }

  static <T> AsyncStreamSlicer<T> takeWhile(final Predicate<T> condition) {
    return t -> condition.test(t) ? CONTINUE : SKIP_AND_STOP;
  }

  default AsyncStreamSlicer<T> then(final AsyncStreamSlicer<T> nextSlicer) {
    return new AsyncStreamSlicer<>() {
      private boolean thisSlicerCompleted = false;

      @Override
      public SliceResult slice(final T element) {
        if (thisSlicerCompleted) {
          return nextSlicer.slice(element);
        } else {
          final SliceResult result = AsyncStreamSlicer.this.slice(element);
          return switch (result) {
            case CONTINUE -> result;
            case SKIP_AND_STOP -> {
              thisSlicerCompleted = true;
              yield nextSlicer.slice(element);
            }
            case INCLUDE_AND_STOP -> {
              thisSlicerCompleted = true;
              yield CONTINUE;
            }
          };
        }
      }
    };
  }
}

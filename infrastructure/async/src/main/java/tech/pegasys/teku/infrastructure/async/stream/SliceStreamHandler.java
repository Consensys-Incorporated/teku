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

class SliceStreamHandler<T> extends AbstractDelegatingStreamHandler<T, T> {

  private final AsyncStreamSlicer<T> slicer;

  protected SliceStreamHandler(
      final AsyncStreamHandler<T> delegate, final AsyncStreamSlicer<T> slicer) {
    super(delegate);
    this.slicer = slicer;
  }

  @Override
  public SafeFuture<Boolean> onNext(final T t) {
    AsyncStreamSlicer.SliceResult sliceResult = slicer.slice(t);
    return switch (sliceResult) {
      case CONTINUE -> delegate.onNext(t);
      case SKIP_AND_STOP -> {
        delegate.onComplete();
        yield FALSE_FUTURE;
      }
      case INCLUDE_AND_STOP -> {
        SafeFuture<Boolean> ret = delegate.onNext(t).thenApply(__ -> false);
        delegate.onComplete();
        yield ret;
      }
    };
  }
}

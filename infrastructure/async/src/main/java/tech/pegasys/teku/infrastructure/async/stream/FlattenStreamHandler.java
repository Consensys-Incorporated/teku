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

class FlattenStreamHandler<TCol extends AsyncStream<T>, T>
    extends AbstractDelegatingStreamHandler<T, TCol> {

  protected FlattenStreamHandler(final AsyncStreamHandler<T> delegate) {
    super(delegate);
  }

  @Override
  public SafeFuture<Boolean> onNext(final TCol asyncIterator) {
    final SafeFuture<Boolean> ret = new SafeFuture<>();
    asyncIterator.consume(
        new AsyncStreamHandler<T>() {
          @Override
          public SafeFuture<Boolean> onNext(final T t) {
            SafeFuture<Boolean> proceedFuture = delegate.onNext(t);

            return proceedFuture.thenPeek(
                proceed -> {
                  if (!proceed) {
                    ret.complete(false);
                  }
                });
          }

          @Override
          public void onComplete() {
            ret.complete(true);
          }

          @Override
          public void onError(final Throwable t) {
            ret.complete(false);
            delegate.onError(t);
          }
        });
    return ret;
  }
}

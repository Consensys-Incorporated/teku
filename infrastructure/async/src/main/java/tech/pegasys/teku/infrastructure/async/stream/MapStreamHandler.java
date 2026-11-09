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

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class MapStreamHandler<T, S> extends AbstractDelegatingStreamHandler<T, S> {

  private final Function<S, T> mapper;

  protected MapStreamHandler(final AsyncStreamHandler<T> delegate, final Function<S, T> mapper) {
    super(delegate);
    this.mapper = mapper;
  }

  @Override
  public SafeFuture<Boolean> onNext(final S s) {
    try {
      return delegate.onNext(mapper.apply(s));
    } catch (Exception e) {
      delegate.onError(e);
      return FALSE_FUTURE;
    }
  }
}

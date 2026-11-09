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

abstract class AsyncIterator<T> implements AsyncStream<T> {

  abstract void iterate(AsyncStreamHandler<T> callback);

  @Override
  public <R> AsyncIterator<R> transform(final AsyncStreamTransformer<T, R> transformer) {
    return new TransformAsyncIterator<>(this, transformer);
  }

  @Override
  public void consume(final AsyncStreamHandler<T> consumer) {
    iterate(consumer);
  }
}

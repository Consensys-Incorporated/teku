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

import static tech.pegasys.teku.infrastructure.async.stream.Util.noCallBinaryOperator;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/** Similar to {@link Stream} but may perform async operations */
public interface AsyncStream<T> extends AsyncStreamBase<T> {

  static <T> AsyncStream<T> empty() {
    return of();
  }

  static <T> AsyncStream<T> exceptional(final Throwable error) {
    final AsyncStreamPublisher<T> ret = createPublisher(1);
    ret.onError(error);
    return ret;
  }

  @SafeVarargs
  static <T> AsyncStream<T> of(final T... elements) {
    return createUnsafe(List.of(elements).iterator());
  }

  /**
   * Creates Async stream which is not thread-safe. Be sure to guarantee thread safety on provided
   * iterator by using concurrent-friendly iterator, otherwise you may encounter concurrency issues.
   */
  static <T> AsyncStream<T> createUnsafe(final Iterator<T> iterator) {
    return new SyncToAsyncIteratorImpl<>(iterator);
  }

  static <T> AsyncStream<T> create(final CompletionStage<T> future) {
    return new FutureAsyncIteratorImpl<>(future);
  }

  static <T> AsyncStreamPublisher<T> createPublisher(final int maxBufferSize) {
    return new BufferingStreamPublisher<>(maxBufferSize);
  }

  // transformation

  default <R> AsyncStream<R> flatMap(final Function<T, AsyncStream<R>> toStreamMapper) {
    return map(toStreamMapper).transform(FlattenStreamHandler::new);
  }

  default <R> AsyncStream<R> map(final Function<T, R> mapper) {
    return transform(sourceCallback -> new MapStreamHandler<>(sourceCallback, mapper));
  }

  default AsyncStream<T> filter(final Predicate<T> filter) {
    return transform(sourceCallback -> new FilteringStreamHandler<>(sourceCallback, filter));
  }

  default AsyncStream<T> peek(final AsyncStreamVisitor<T> visitor) {
    return transform(src -> new VisitorHandler<>(src, visitor));
  }

  default <R> AsyncStream<R> mapAsync(final Function<T, SafeFuture<R>> mapper) {
    return flatMap(e -> AsyncStream.create(mapper.apply(e)));
  }

  default AsyncStream<T> merge(final AsyncStream<T> other) {
    return transform(sourceCallback -> new MergeStreamHandler<>(sourceCallback, other));
  }

  // slicing

  default AsyncStream<T> slice(final AsyncStreamSlicer<T> slicer) {
    return transform(sourceCallback -> new SliceStreamHandler<>(sourceCallback, slicer));
  }

  default AsyncStream<T> limit(final long count) {
    return slice(AsyncStreamSlicer.limit(count));
  }

  default AsyncStream<T> takeWhile(final Predicate<T> whileCondition) {
    return slice(AsyncStreamSlicer.takeWhile(whileCondition));
  }

  default AsyncStream<T> takeUntil(final Predicate<T> untilCondition, final boolean includeLast) {
    AsyncStreamSlicer<T> whileSlicer = AsyncStreamSlicer.takeWhile(untilCondition.negate());
    AsyncStreamSlicer<T> untilSlicer =
        includeLast ? whileSlicer.then(AsyncStreamSlicer.limit(1)) : whileSlicer;
    return slice(untilSlicer);
  }

  // consuming

  default <A, R> SafeFuture<R> collect(final Collector<T, A, R> collector) {
    final AsyncIteratorCollector<T, A, R> asyncIteratorCollector =
        new AsyncIteratorCollector<>(collector);
    consume(asyncIteratorCollector);
    return asyncIteratorCollector.getPromise();
  }

  default SafeFuture<Optional<T>> findFirst() {
    return this.limit(1)
        .toList()
        .thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst()));
  }

  default SafeFuture<Void> forEach(final Consumer<T> consumer) {
    return collect(Collector.of(() -> null, (a, t) -> consumer.accept(t), noCallBinaryOperator()));
  }

  default <C extends Collection<T>> SafeFuture<C> collect(final C targetCollection) {
    return collect(Collectors.toCollection(() -> targetCollection));
  }

  default SafeFuture<List<T>> toList() {
    return collect(Collectors.toUnmodifiableList());
  }

  default SafeFuture<Optional<T>> findLast() {
    return collectLast(1)
        .thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst()));
  }

  default SafeFuture<List<T>> collectLast(final int count) {
    return collect(CircularBuf.createCollector(count));
  }
}

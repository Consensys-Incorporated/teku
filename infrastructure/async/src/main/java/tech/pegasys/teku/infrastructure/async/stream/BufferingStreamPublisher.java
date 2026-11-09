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

class BufferingStreamPublisher<T> extends AsyncIterator<T> implements AsyncStreamPublisher<T> {

  private final AsyncQueue<Event<T>> eventQueue;

  private boolean isDone = false;

  BufferingStreamPublisher(final int maxBufferSize) {
    this.eventQueue = new LimitedAsyncQueue<>(maxBufferSize);
  }

  sealed interface Event<T> {
    boolean isTerminal();
  }

  record ItemEvent<T>(T item, SafeFuture<Boolean> nextReturn) implements Event<T> {
    @Override
    public boolean isTerminal() {
      return false;
    }
  }

  record CompleteEvent<T>() implements Event<T> {
    @Override
    public boolean isTerminal() {
      return true;
    }
  }

  record ErrorEvent<T>(Throwable error) implements Event<T> {
    @Override
    public boolean isTerminal() {
      return true;
    }
  }

  private synchronized void putNext(final Event<T> event) {
    if (isDone) {
      throw new IllegalStateException("Stream has been done already");
    }
    isDone = event.isTerminal();
    eventQueue.put(event);
  }

  private SafeFuture<Event<T>> takeNext() {
    return eventQueue.take();
  }

  @Override
  void iterate(final AsyncStreamHandler<T> delegate) {
    SafeFuture.asyncDoWhile(
            () ->
                takeNext()
                    .thenCompose(
                        event ->
                            switch (event) {
                              case ItemEvent<T> item -> {
                                delegate.onNext(item.item()).propagateTo(item.nextReturn());
                                yield item.nextReturn();
                              }
                              case CompleteEvent<T> ignored -> {
                                delegate.onComplete();
                                yield FALSE_FUTURE;
                              }
                              case ErrorEvent<T> errorEvent -> {
                                delegate.onError(errorEvent.error());
                                yield FALSE_FUTURE;
                              }
                            }))
        .finish(delegate::onError);
  }

  @Override
  public SafeFuture<Boolean> onNext(final T t) {
    SafeFuture<Boolean> ret = new SafeFuture<>();
    try {
      putNext(new ItemEvent<>(t, ret));
    } catch (Exception e) {
      ret.completeExceptionally(e);
    }
    return ret;
  }

  @Override
  public void onComplete() {
    putNext(new CompleteEvent<>());
  }

  @Override
  public synchronized void onError(final Throwable t) {
    putNext(new ErrorEvent<>(t));
  }
}

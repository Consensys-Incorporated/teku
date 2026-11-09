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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class LimitedAsyncQueue<T> implements AsyncQueue<T> {

  private final int maxSize;

  private final Queue<T> items = new ArrayDeque<>();
  private final Queue<SafeFuture<T>> takers = new ArrayDeque<>();

  public LimitedAsyncQueue(final int maxSize) {
    this.maxSize = maxSize;
  }

  // Adds an item to the queue
  @Override
  public void put(final T item) {
    final CompletableFuture<T> maybeTaker;
    synchronized (this) {
      if (!takers.isEmpty()) {
        // If there are pending takers, complete one with the item
        maybeTaker = takers.poll();
      } else {
        // Otherwise, add the item to the items queue
        if (items.size() >= maxSize) {
          throw new IllegalStateException("Buffer size overflow: " + maxSize);
        }
        items.offer(item);
        maybeTaker = null;
      }
    }
    if (maybeTaker != null) {
      maybeTaker.complete(item);
    }
  }

  // Returns a CompletableFuture that will be completed when an item is available
  @Override
  public synchronized SafeFuture<T> take() {
    if (!items.isEmpty()) {
      // If items are available, return a completed future
      final T item = items.poll();
      return SafeFuture.completedFuture(item);
    } else {
      // If no items, create a new CompletableFuture and add it to takers
      final SafeFuture<T> future = new SafeFuture<>();
      takers.offer(future);
      return future;
    }
  }
}

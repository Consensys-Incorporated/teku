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

package tech.pegasys.teku.storage.server.rocksdb;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.AbstractNativeReference;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import tech.pegasys.teku.storage.server.ShuttingDownException;

class RocksDbIterator<T> implements Iterator<T>, AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  private final RocksIterator rocksIterator;
  private final Function<RocksIterator, T> readEntry;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Supplier<Boolean> isDatabaseClosed;
  // Native resources the iterator depends on (e.g. its ReadOptions), closed after the iterator
  private final List<AbstractNativeReference> resources;

  private RocksDbIterator(
      final RocksIterator rocksIterator,
      final Function<RocksIterator, T> readEntry,
      final Supplier<Boolean> isDatabaseClosed,
      final List<AbstractNativeReference> resources) {
    this.rocksIterator = rocksIterator;
    this.readEntry = readEntry;
    this.isDatabaseClosed = isDatabaseClosed;
    this.resources = resources;
  }

  @MustBeClosed
  public static <T> RocksDbIterator<T> create(
      final RocksIterator rocksIt,
      final Function<RocksIterator, T> readEntry,
      final Supplier<Boolean> isDatabaseClosed,
      final List<AbstractNativeReference> resources) {
    return new RocksDbIterator<>(rocksIt, readEntry, isDatabaseClosed, resources);
  }

  @Override
  public boolean hasNext() {
    assertOpen();
    return rocksIterator.isValid();
  }

  @Override
  public T next() {
    assertOpen();
    try {
      rocksIterator.status();
    } catch (final RocksDBException e) {
      LOG.error("RocksDbIterator encountered a problem while iterating.", e);
    }
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    final T entry = readEntry.apply(rocksIterator);
    rocksIterator.next();
    return entry;
  }

  @MustBeClosed
  public Stream<T> toStream() {
    assertOpen();
    final Spliterator<T> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false).onClose(this::close);
  }

  private void assertOpen() {
    if (this.isDatabaseClosed.get()) {
      throw new ShuttingDownException();
    }
    if (closed.get()) {
      throw new IllegalStateException(
          "Attempt to update a closed " + this.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      rocksIterator.close();
      resources.forEach(AbstractNativeReference::close);
    }
  }
}

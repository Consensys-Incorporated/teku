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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.MustBeClosed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.AbstractNativeReference;
import org.rocksdb.AbstractRocksIterator;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.TransactionDB;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class RocksDbInstance implements KvStoreAccessor {

  private static final Logger LOG = LogManager.getLogger();

  private final TransactionDB db;
  private final ColumnFamilyHandle defaultHandle;
  private volatile ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandles;
  private final Map<KvStoreColumn<?, ?>, ColumnFamilyDescriptor> columnDescriptors;
  private final List<AutoCloseable> resources;
  private final Set<RocksDbTransaction> openTransactions = new HashSet<>();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  RocksDbInstance(
      final TransactionDB db,
      final ColumnFamilyHandle defaultHandle,
      final ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandles,
      final Map<KvStoreColumn<?, ?>, ColumnFamilyDescriptor> columnDescriptors,
      final List<AutoCloseable> resources) {
    this.db = db;
    this.defaultHandle = defaultHandle;
    this.columnHandles = columnHandles;
    this.columnDescriptors = columnDescriptors;
    this.resources = resources;
  }

  @Override
  public <T> Optional<T> get(final KvStoreVariable<T> variable) {
    return getRaw(variable).map(data -> variable.getSerializer().deserialize(data.toArrayUnsafe()));
  }

  @Override
  public Optional<Bytes> getRaw(final KvStoreVariable<?> variable) {
    assertOpen();
    try {
      return Optional.ofNullable(db.get(defaultHandle, variable.getId().toArrayUnsafe()))
          .map(Bytes::wrap);
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to get value", e);
    }
  }

  @Override
  public <K, V> Optional<V> get(final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    final ColumnFamilyHandle handle = getColumnHandle(column);
    if (handle == null) {
      return Optional.empty();
    }
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    try {
      return Optional.ofNullable(db.get(handle, keyBytes))
          .map(data -> column.getValueSerializer().deserialize(data));
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to get value", e);
    }
  }

  @Override
  public long size(final KvStoreColumn<?, ?> column) {
    assertOpen();
    try (final Stream<?> rawStream = streamRaw(column)) {
      return rawStream.count();
    }
  }

  @Override
  public <K, V> Map<K, V> getAll(final KvStoreColumn<K, V> column) {
    assertOpen();
    try (final Stream<ColumnEntry<K, V>> stream = stream(column)) {
      return stream.collect(Collectors.toMap(ColumnEntry::getKey, ColumnEntry::getValue));
    }
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(
      final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    try (final Stream<ColumnEntry<K, V>> stream =
        createStream(column, Optional.empty(), it -> it.seekForPrev(keyBytes), readEntry(column))) {
      return stream.findFirst();
    }
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(final KvStoreColumn<K, V> column) {
    assertOpen();
    try (final Stream<ColumnEntry<K, V>> stream =
        createStream(
            column, Optional.empty(), AbstractRocksIterator::seekToFirst, readEntry(column))) {
      return stream.findFirst();
    }
  }

  @Override
  public <K, V> Optional<K> getLastKey(final KvStoreColumn<K, V> column) {
    assertOpen();
    final ColumnFamilyHandle handle = getColumnHandle(column);
    if (handle == null) {
      return Optional.empty();
    }
    try (final RocksIterator rocksDbIterator = db.newIterator(handle)) {
      rocksDbIterator.seekToLast();
      return rocksDbIterator.isValid()
          ? Optional.of(column.getKeySerializer().deserialize(rocksDbIterator.key()))
          : Optional.empty();
    }
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<ColumnEntry<K, V>> stream(final KvStoreColumn<K, V> column) {
    assertOpen();
    return createStream(column, Optional.empty(), RocksIterator::seekToFirst, readEntry(column));
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<K> streamKeys(final KvStoreColumn<K, V> column) {
    assertOpen();
    return createStream(column, Optional.empty(), RocksIterator::seekToFirst, readKey(column));
  }

  @Override
  @MustBeClosed
  public Stream<ColumnEntry<Bytes, Bytes>> streamRaw(final KvStoreColumn<?, ?> column) {
    assertOpen();
    return createStream(
        column,
        Optional.empty(),
        RocksIterator::seekToFirst,
        it -> ColumnEntry.create(Bytes.wrap(it.key()), Bytes.wrap(it.value())));
  }

  @Override
  @MustBeClosed
  public Stream<Bytes> streamKeysRaw(final KvStoreColumn<?, ?> column) {
    return createStream(
        column, Optional.empty(), RocksIterator::seekToFirst, it -> Bytes.wrap(it.key()));
  }

  @Override
  public <K, V> Optional<Bytes> getRaw(final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    final ColumnFamilyHandle handle = getColumnHandle(column);
    if (handle == null) {
      return Optional.empty();
    }
    final byte[] keyBytes = column.getKeySerializer().serialize(key);
    try {
      return Optional.ofNullable(db.get(handle, keyBytes)).map(Bytes::wrap);
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to get value", e);
    }
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    assertOpen();
    return createStream(
        column,
        Optional.of(column.getKeySerializer().serialize(to)),
        iter -> iter.seek(column.getKeySerializer().serialize(from)),
        readEntry(column));
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<K> streamKeys(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    assertOpen();
    return createStream(
        column,
        Optional.of(column.getKeySerializer().serialize(to)),
        iter -> iter.seek(column.getKeySerializer().serialize(from)),
        readKey(column));
  }

  @Override
  @MustBeClosed
  public synchronized KvStoreTransaction startTransaction() {
    assertOpen();
    final RocksDbTransaction tx =
        new RocksDbTransaction(
            db,
            defaultHandle,
            this::getColumnHandleForWrite,
            this::getColumnHandle,
            openTransactions::remove);
    openTransactions.add(tx);
    return tx;
  }

  private static <K, V> Function<RocksIterator, ColumnEntry<K, V>> readEntry(
      final KvStoreColumn<K, V> column) {
    return it ->
        ColumnEntry.create(
            column.getKeySerializer().deserialize(it.key()),
            column.getValueSerializer().deserialize(it.value()));
  }

  private static <K, V> Function<RocksIterator, K> readKey(final KvStoreColumn<K, V> column) {
    return it -> column.getKeySerializer().deserialize(it.key());
  }

  /**
   * Streams the column from the position {@code setupIterator} seeks to. When {@code maybeLastKey}
   * is present the stream ends after that key, and RocksDB enforces it as an upper bound so a seek
   * stops at the end of the range instead of stepping over every deletion tombstone up to the next
   * live key. Without the bound a seek into a pruned range has to walk all the tombstones left by
   * pruning, however small the requested range is.
   */
  @SuppressWarnings("MustBeClosedChecker")
  @MustBeClosed
  private <T> Stream<T> createStream(
      final KvStoreColumn<?, ?> column,
      final Optional<byte[]> maybeLastKey,
      final Consumer<RocksIterator> setupIterator,
      final Function<RocksIterator, T> readEntry) {
    assertOpen();
    final ColumnFamilyHandle handle = getColumnHandle(column);
    if (handle == null) {
      return Stream.empty();
    }
    final IteratorWithResources iterator;
    if (maybeLastKey.isPresent()) {
      iterator =
          createBoundedIterator(
              maybeLastKey.get(),
              readOptions -> db.newIterator(handle, readOptions),
              setupIterator);
    } else {
      final RocksIterator rocksIterator = db.newIterator(handle);
      setupIterator.accept(rocksIterator);
      iterator = new IteratorWithResources(rocksIterator, List.of());
    }
    return RocksDbIterator.create(
            iterator.rocksIterator(), readEntry, closed::get, iterator.resources())
        .toStream();
  }

  /**
   * Creates an iterator bounded just above {@code lastKey} and runs {@code setupIterator} on it.
   * The bound and its {@link ReadOptions} are native resources that must outlive the iterator, so
   * they are handed back to be closed after it, or closed here if the iterator cannot be set up.
   */
  @VisibleForTesting
  static IteratorWithResources createBoundedIterator(
      final byte[] lastKey,
      final Function<ReadOptions, RocksIterator> newIterator,
      final Consumer<RocksIterator> setupIterator) {
    final Slice upperBound = new Slice(exclusiveUpperBound(lastKey));
    final ReadOptions readOptions = new ReadOptions().setIterateUpperBound(upperBound);
    RocksIterator rocksIterator = null;
    try {
      rocksIterator = newIterator.apply(readOptions);
      setupIterator.accept(rocksIterator);
      return new IteratorWithResources(rocksIterator, List.of(readOptions, upperBound));
    } catch (final RuntimeException e) {
      if (rocksIterator != null) {
        rocksIterator.close();
      }
      readOptions.close();
      upperBound.close();
      throw e;
    }
  }

  /**
   * RocksDB's upper bound is exclusive. With the default bytewise comparator the smallest key
   * greater than {@code lastKey} is {@code lastKey} followed by a zero byte.
   */
  static byte[] exclusiveUpperBound(final byte[] lastKey) {
    return Arrays.copyOf(lastKey, lastKey.length + 1);
  }

  record IteratorWithResources(
      RocksIterator rocksIterator, List<AbstractNativeReference> resources) {}

  // Compaction is a costly operation, so we run it sequentially for each column family
  // to avoid impacting read performance by blocking all access to the database while compaction is
  // running. Compaction is handled by rocksdb in general this is a method used for debug tools ONLY
  @Override
  public void compact() {
    assertOpen();
    final List<ColumnFamilyHandle> handles = new ArrayList<>(columnHandles.values());
    handles.add(defaultHandle);
    LOG.info("Compacting {} RocksDB column families", handles.size());
    int compacted = 0;
    for (final ColumnFamilyHandle handle : handles) {
      try {
        final long startTime = System.currentTimeMillis();
        db.compactRange(handle);
        LOG.info(
            "Compacted column family {} of {} in {} ms",
            ++compacted,
            handles.size(),
            System.currentTimeMillis() - startTime);
      } catch (final RocksDBException e) {
        throw RocksDbExceptionUtil.wrapException("Failed to compact RocksDB column family", e);
      }
    }
  }

  @Override
  public synchronized void close() throws Exception {
    if (closed.compareAndSet(false, true)) {
      for (RocksDbTransaction openTransaction : openTransactions) {
        openTransaction.closeViaDatabase();
      }
      db.syncWal();
      for (final AutoCloseable resource : resources) {
        resource.close();
      }
    }
  }

  private void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
  }

  private ColumnFamilyHandle getColumnHandle(final KvStoreColumn<?, ?> column) {
    return columnHandles.get(column);
  }

  private synchronized ColumnFamilyHandle getColumnHandleForWrite(
      final KvStoreColumn<?, ?> column) {
    assertOpen();
    final ColumnFamilyHandle existingHandle = columnHandles.get(column);
    if (existingHandle != null) {
      return existingHandle;
    }

    final ColumnFamilyDescriptor descriptor = columnDescriptors.get(column);
    if (descriptor == null) {
      throw DatabaseStorageException.unrecoverable("Column is not defined in the RocksDB schema");
    }

    try {
      final ColumnFamilyHandle columnHandle = db.createColumnFamily(descriptor);
      resources.add(Math.max(0, resources.size() - 1), columnHandle);
      columnHandles =
          ImmutableMap.<KvStoreColumn<?, ?>, ColumnFamilyHandle>builder()
              .putAll(columnHandles)
              .put(column, columnHandle)
              .build();
      return columnHandle;
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException("Failed to create column family", e);
    }
  }
}

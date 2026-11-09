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

package tech.pegasys.teku.storage.server.leveldb;

import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.asColumnEntry;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.asOptionalColumnEntry;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.deserializeKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getColumnKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getKeyAfterColumn;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.getVariableKey;
import static tech.pegasys.teku.storage.server.leveldb.LevelDbUtils.isFromColumn;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteBatch;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

/**
 * Implements {@link KvStoreAccessor} using LevelDB to store the data.
 *
 * <p>The primary difference between RocksDB and LevelDB is that LevelDB doesn't support columns. To
 * support the same interface and generally make it easy to work with the database, columns are
 * implemented by prefixing all keys with the column ID. As a result, each column has a unique
 * keyspace and column entries are sorted together. Iterators can then be used to walk through the
 * column with the only caveat being that we need to manually check if the next entry is from a
 * different column and stop iterating.
 */
public class LevelDbInstance implements KvStoreAccessor {
  private static final Logger LOG = LogManager.getLogger();

  private final Set<LevelDbTransaction> openTransactions = new HashSet<>();
  private final Set<DBIterator> openIterators = new HashSet<>();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final DB db;
  private final Counter openedTransactionsCounter;
  private final Counter closedTransactionsCounter;
  private final Counter openedIteratorsCounter;
  private final Counter closedIteratorsCounter;

  public LevelDbInstance(
      final DB db, final MetricsSystem metricsSystem, final MetricCategory metricCategory) {
    this.db = db;
    openedTransactionsCounter =
        metricsSystem.createCounter(
            metricCategory, "opened_transactions_total", "Total number of opened transactions");
    closedTransactionsCounter =
        metricsSystem.createCounter(
            metricCategory, "closed_transactions_total", "Total number of closed transactions");
    openedIteratorsCounter =
        metricsSystem.createCounter(
            metricCategory, "opened_iterators_total", "Total number of opened iterators");
    closedIteratorsCounter =
        metricsSystem.createCounter(
            metricCategory, "closed_iterators_total", "Total number of closed iterators");
  }

  @Override
  public <T> Optional<T> get(final KvStoreVariable<T> variable) {
    return getRaw(variable)
        .map(value -> variable.getSerializer().deserialize(value.toArrayUnsafe()));
  }

  @Override
  public Optional<Bytes> getRaw(final KvStoreVariable<?> variable) {
    assertOpen();
    return Optional.ofNullable(db.get(getVariableKey(variable))).map(Bytes::wrap);
  }

  @Override
  public <K, V> Optional<V> get(final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    return Optional.ofNullable(db.get(getColumnKey(column, key)))
        .map(column.getValueSerializer()::deserialize);
  }

  @Override
  public long size(final KvStoreColumn<?, ?> column) {
    assertOpen();
    try (final Stream<?> rawStream = streamKeysRaw(column)) {
      return rawStream.count();
    }
  }

  @Override
  public <K, V> Map<K, V> getAll(final KvStoreColumn<K, V> column) {
    return withIterator(
        iterator -> {
          iterator.seek(column.getId().toArrayUnsafe());
          final Map<K, V> values = new HashMap<>();
          while (iterator.hasNext()) {
            final Map.Entry<byte[], byte[]> entry = iterator.next();
            if (!isFromColumn(column, entry.getKey())) {
              break;
            }
            values.put(
                deserializeKey(column, entry.getKey()),
                column.getValueSerializer().deserialize(entry.getValue()));
          }
          return values;
        });
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(
      final KvStoreColumn<K, V> column, final K key) {
    return withIterator(
        iterator -> {
          final byte[] matchingKey = getColumnKey(column, key);
          iterator.seek(matchingKey);
          if (!iterator.hasNext()) {
            return getLastDatabaseEntryIfFromColumn(column, iterator)
                .map(entry -> asColumnEntry(column, entry));
          }

          // Check if an exact match was found
          final Map.Entry<byte[], byte[]> next = iterator.peekNext();
          if (Arrays.equals(next.getKey(), matchingKey)) {
            return Optional.of(
                ColumnEntry.create(key, column.getValueSerializer().deserialize(next.getValue())));
          }

          // Otherwise check if the previous item is in our column.
          if (iterator.hasPrev()) {
            final Map.Entry<byte[], byte[]> prev = iterator.peekPrev();
            if (isFromColumn(column, prev.getKey())) {
              return asOptionalColumnEntry(column, prev);
            }
          }
          // No entry in this column at or prior to the specified key
          return Optional.empty();
        });
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(final KvStoreColumn<K, V> column) {
    return withIterator(
        iterator -> {
          iterator.seek(column.getId().toArrayUnsafe());
          if (iterator.hasNext()) {
            return Optional.of(iterator.peekNext())
                .filter(entry -> isFromColumn(column, entry.getKey()))
                .map(entry -> asColumnEntry(column, entry));
          }
          return Optional.empty();
        });
  }

  @Override
  public <K, V> Optional<K> getLastKey(final KvStoreColumn<K, V> column) {
    return withIterator(
        iterator -> {
          final byte[] keyAfterColumn = getKeyAfterColumn(column);
          iterator.seek(keyAfterColumn);
          if (!iterator.hasNext()) {
            return getLastDatabaseEntryIfFromColumn(column, iterator)
                .map(entry -> deserializeKey(column, entry.getKey()));
          }
          if (iterator.hasPrev()) {
            return Optional.of(iterator.peekPrev())
                .filter(entry -> isFromColumn(column, entry.getKey()))
                .map(entry -> deserializeKey(column, entry.getKey()));
          }
          return Optional.empty();
        });
  }

  /**
   * LevelDB iterators have a slightly odd property where if you seek to an item that is after the
   * last entry in the database, the iterator is invalid (no next or previous item).
   *
   * <p>We work around that by explicitly seeking the last item, and checking if it's part of the
   * column we're interested in.
   */
  private <K, V> Optional<Map.Entry<byte[], byte[]>> getLastDatabaseEntryIfFromColumn(
      final KvStoreColumn<K, V> column, final DBIterator iterator) {
    iterator.seekToLast();
    if (!iterator.hasNext()) {
      // Empty database
      return Optional.empty();
    }
    return Optional.of(iterator.peekNext()).filter(entry -> isFromColumn(column, entry.getKey()));
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<ColumnEntry<K, V>> stream(final KvStoreColumn<K, V> column) {
    // Note that the "to" key is actually after the end of the column and iteration is inclusive.
    // Fortunately, we know that the "to" key can't exist because it is just the column ID with an
    // empty item key and empty item keys are not allowed.
    return stream(column, column.getId().toArrayUnsafe(), getKeyAfterColumn(column));
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<K> streamKeys(final KvStoreColumn<K, V> column) {
    // Note that the "to" key is actually after the end of the column and iteration is inclusive.
    // Fortunately, we know that the "to" key can't exist because it is just the column ID with an
    // empty item key and empty item keys are not allowed.
    return streamKeys(column, column.getId().toArrayUnsafe(), getKeyAfterColumn(column));
  }

  @Override
  @MustBeClosed
  public Stream<ColumnEntry<Bytes, Bytes>> streamRaw(final KvStoreColumn<?, ?> column) {
    return streamRaw(column, column.getId().toArrayUnsafe(), getKeyAfterColumn(column))
        .map(entry -> ColumnEntry.create(Bytes.wrap(entry.getKey()), Bytes.wrap(entry.getValue())));
  }

  @Override
  @MustBeClosed
  public Stream<Bytes> streamKeysRaw(final KvStoreColumn<?, ?> column) {
    return streamKeysRaw(column, column.getId().toArrayUnsafe(), getKeyAfterColumn(column))
        .map(Bytes::wrap);
  }

  @Override
  public <K, V> Optional<Bytes> getRaw(final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    return Optional.ofNullable(db.get(getColumnKey(column, key))).map(Bytes::wrap);
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    final byte[] fromBytes = getColumnKey(column, from);
    final byte[] toBytes = getColumnKey(column, to);
    return stream(column, fromBytes, toBytes);
  }

  @Override
  @MustBeClosed
  public <K extends Comparable<K>, V> Stream<K> streamKeys(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    final byte[] fromBytes = getColumnKey(column, from);
    final byte[] toBytes = getColumnKey(column, to);
    return streamKeys(column, fromBytes, toBytes);
  }

  @MustBeClosed
  private <K, V> Stream<ColumnEntry<K, V>> stream(
      final KvStoreColumn<K, V> column, final byte[] fromBytes, final byte[] toBytes) {
    return streamRaw(column, fromBytes, toBytes)
        .map(
            entry ->
                ColumnEntry.create(
                    column.getKeySerializer().deserialize(entry.getKey()),
                    column.getValueSerializer().deserialize(entry.getValue())));
  }

  @MustBeClosed
  private <K, V> Stream<K> streamKeys(
      final KvStoreColumn<K, V> column, final byte[] fromBytes, final byte[] toBytes) {
    return streamKeysRaw(column, fromBytes, toBytes)
        .map(entry -> column.getKeySerializer().deserialize(entry));
  }

  @MustBeClosed
  private Stream<ColumnEntry<byte[], byte[]>> streamRaw(
      final KvStoreColumn<?, ?> column, final byte[] fromBytes, final byte[] toBytes) {
    assertOpen();
    final CustomDBIterator iterator = createIterator();
    iterator.seek(fromBytes);
    return new LevelDbIterator<>(this, iterator, column, toBytes)
        .toStream()
        .onClose(() -> closeIterator(iterator));
  }

  @MustBeClosed
  private Stream<byte[]> streamKeysRaw(
      final KvStoreColumn<?, ?> column, final byte[] fromBytes, final byte[] toBytes) {
    assertOpen();
    final CustomDBIterator iterator = createIterator();
    iterator.seek(fromBytes);
    return new LevelDbKeyIterator<>(this, iterator, column, toBytes)
        .toStream()
        .onClose(() -> closeIterator(iterator));
  }

  @Override
  @MustBeClosed
  public synchronized KvStoreTransaction startTransaction() {
    assertOpen();
    openedTransactionsCounter.inc();
    final WriteBatch writeBatch = db.createWriteBatch();
    final LevelDbTransaction transaction = new LevelDbTransaction(this, db, writeBatch);
    openTransactions.add(transaction);
    return transaction;
  }

  @Override
  public void compact() {
    assertOpen();
    LOG.info("Compacting LevelDB");
    final long startTime = System.currentTimeMillis();
    // Passing null bounds compacts the entire key range.
    db.compactRange(null, null);
    LOG.info("LevelDB compaction completed in {} ms", System.currentTimeMillis() - startTime);
  }

  @Override
  public void close() throws Exception {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    List<DBIterator> openIteratorsSnapshot;
    List<LevelDbTransaction> openTransactionsSnapshot;
    synchronized (this) {
      openIteratorsSnapshot = new ArrayList<>(openIterators);
      openTransactionsSnapshot = new ArrayList<>(openTransactions);
    }
    openIteratorsSnapshot.forEach(this::closeIterator);
    openTransactionsSnapshot.forEach(LevelDbTransaction::close);
    db.close();
  }

  private synchronized <T> T withIterator(final Function<DBIterator, T> action) {
    assertOpen();
    final DBIterator iterator = createIterator();
    try {
      return action.apply(iterator);
    } catch (final DBException e) {
      throw DatabaseStorageException.unrecoverable("Failed to create iterator", e);
    } finally {
      closeIterator(iterator);
    }
  }

  private synchronized void closeIterator(final DBIterator iterator) {
    if (!openIterators.remove(iterator)) {
      return;
    }
    closedIteratorsCounter.inc();
    try {
      iterator.close();
    } catch (final IOException e) {
      LOG.error("Failed to close leveldb iterator", e);
    }
  }

  synchronized void onTransactionClosed(final LevelDbTransaction transaction) {
    if (openTransactions.remove(transaction)) {
      closedTransactionsCounter.inc();
    }
  }

  private CustomDBIterator createIterator() {
    final CustomDBIterator iterator =
        (CustomDBIterator) db.iterator(new ReadOptions().fillCache(false));
    openIterators.add(iterator);
    openedIteratorsCounter.inc();
    return iterator;
  }

  void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
  }
}

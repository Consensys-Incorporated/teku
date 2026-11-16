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

package tech.pegasys.teku.storage.server.kvstore;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public interface KvStoreAccessor extends AutoCloseable {

  <T> Optional<T> get(KvStoreVariable<T> variable);

  Optional<Bytes> getRaw(KvStoreVariable<?> variable);

  <K, V> Optional<V> get(KvStoreColumn<K, V> column, K key);

  /**
   * Returns the exact number of entries in the given column by performing a full sequential scan.
   * O(N) — may take minutes on columns with millions of entries (e.g. data column sidecars). Do not
   * call on hot paths or in tight loops; use only for periodic metrics updates or offline tooling.
   */
  long size(KvStoreColumn<?, ?> column);

  <K, V> Map<K, V> getAll(KvStoreColumn<K, V> column);

  /**
   * Returns the last entry with a key less than or equal to the given key.
   *
   * @param column The column we want to query
   * @param key The requested key
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last entry with a key less than or equal to the given {@code key}
   */
  <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(KvStoreColumn<K, V> column, K key);

  /**
   * Returns the first entry in the given column.
   *
   * @param column The column we want to query
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The first entry in this column - the entry with the lowest key value
   */
  <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(KvStoreColumn<K, V> column);

  /**
   * Returns the last key in the given column without loading the associated value.
   *
   * @param column The column we want to query
   * @param <K> The key type of the column
   * @param <V> The value type of the column
   * @return The last key in this column - the key with the greatest value
   */
  <K, V> Optional<K> getLastKey(KvStoreColumn<K, V> column);

  @MustBeClosed
  <K, V> Stream<ColumnEntry<K, V>> stream(KvStoreColumn<K, V> column);

  @MustBeClosed
  <K, V> Stream<K> streamKeys(KvStoreColumn<K, V> column);

  /**
   * WARNING: should only be used to migrate data between database instances
   *
   * @param column
   * @return
   */
  @MustBeClosed
  Stream<ColumnEntry<Bytes, Bytes>> streamRaw(KvStoreColumn<?, ?> column);

  @MustBeClosed
  Stream<Bytes> streamKeysRaw(final KvStoreColumn<?, ?> column);

  /**
   * WARNING: should only be used to migrate data between tables
   *
   * @param column column to get value from
   * @param key key of the data to retrieve
   * @return Bytes representing the value found at key
   */
  <K, V> Optional<Bytes> getRaw(KvStoreColumn<K, V> column, K key);

  /**
   * Stream entries from a column between keys from and to fully inclusive.
   *
   * @param column the column to stream entries from
   * @param from the first key to return
   * @param to the last key to return
   * @param <K> the key type of the column
   * @param <V> the value type of the column
   * @return a Stream of entries between from and to (fully inclusive).
   */
  @MustBeClosed
  <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      KvStoreColumn<K, V> column, K from, K to);

  @MustBeClosed
  <K extends Comparable<K>, V> Stream<K> streamKeys(KvStoreColumn<K, V> column, K from, K to);

  KvStoreTransaction startTransaction();

  /**
   * Triggers a full compaction of every column family, rewriting all levels and physically dropping
   * deleted keys and their tombstones. This reclaims disk space left behind by pruning that
   * automatic background compaction has not yet reclaimed. It is an expensive, blocking, I/O-heavy
   * operation and is intended to be run offline (e.g. from the debug CLI), not on a running node.
   */
  void compact();

  /**
   * Compacts the keys of a column between from and to fully inclusive, physically dropping deleted
   * keys and their tombstones in that range.
   *
   * <p>Pruning deletes keys one at a time and each delete leaves a tombstone until compaction
   * removes it. A seek that lands among them has to step over every tombstone before it reaches a
   * live key, so compacting a range once it has been pruned keeps later reads of it cheap. This is
   * a blocking, I/O-heavy call: run it off the storage thread. If the database is closed while it
   * runs, it stops early and throws {@code ShuttingDownException}.
   *
   * @param column the column to compact
   * @param from the first key to compact
   * @param to the last key to compact
   * @param <K> the key type of the column
   * @param <V> the value type of the column
   */
  <K, V> void compactRange(KvStoreColumn<K, V> column, K from, K to);

  /**
   * Counts the deletion tombstones that compaction has not dropped yet, in the data files that hold
   * keys of a column between from and to.
   *
   * <p>Counts every tombstone in each data file that overlaps the range, so it can include a few
   * from just outside it. Deletes that have not been flushed to a data file yet are not counted.
   * RocksDB loads the counts for files written before the database was opened a few at a time, so
   * it can undercount for a while after startup.
   *
   * @param column the column to count tombstones in
   * @param from the first key of the range
   * @param to the last key of the range
   * @param <K> the key type of the column
   * @param <V> the value type of the column
   * @return the number of tombstones, or empty if the database cannot report it
   */
  <K, V> OptionalLong countTombstones(KvStoreColumn<K, V> column, K from, K to);

  interface KvStoreTransaction extends AutoCloseable {

    <T> void put(KvStoreVariable<T> variable, T value);

    /**
     * Write raw bytes to a specified variable.
     *
     * <p>WARNING: should only be used to migrate data between database instances
     */
    <T> void putRaw(KvStoreVariable<T> variable, Bytes value);

    <K, V> void put(KvStoreColumn<K, V> column, K key, V value);

    /**
     * Write raw bytes to a column for a given key.
     *
     * <p>WARNING: should only be used to migrate data between database instances
     */
    <K, V> void putRaw(KvStoreColumn<K, V> column, Bytes key, Bytes value);

    <K, V> void put(KvStoreColumn<K, V> column, Map<K, V> data);

    <K, V> void delete(KvStoreColumn<K, V> column, K key);

    <T> void delete(KvStoreVariable<T> variable);

    void commit();

    void rollback();

    @Override
    void close();
  }
}

/*
 * Copyright Consensys Software Inc., 2026
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

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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class MockKvStoreInstance implements KvStoreAccessor {
  private final Set<KvStoreColumn<?, ?>> columns;
  private final Set<KvStoreVariable<?>> variables;

  private final Map<KvStoreColumn<?, ?>, NavigableMap<Bytes, Bytes>> columnData;
  private final Map<KvStoreVariable<?>, Bytes> variableData;

  private final AtomicBoolean closed = new AtomicBoolean(false);

  public MockKvStoreInstance(
      final Collection<KvStoreColumn<?, ?>> columns,
      final Collection<KvStoreVariable<?>> variables,
      final Map<KvStoreColumn<?, ?>, NavigableMap<Bytes, Bytes>> columnData,
      final Map<KvStoreVariable<?>, Bytes> variableData) {
    this.columns = new HashSet<>(columns);
    this.variables = new HashSet<>(variables);
    this.columnData = columnData;
    this.variableData = variableData;
  }

  public MockKvStoreInstance reopen() {
    if (closed.get()) {
      return new MockKvStoreInstance(columns, variables, columnData, variableData);
    } else {
      return this;
    }
  }

  public static MockKvStoreInstance createEmpty(
      final Collection<KvStoreColumn<?, ?>> columns,
      final Collection<KvStoreVariable<?>> variables) {
    checkArgument(columns.size() > 0, "No columns attached to schema");

    final Map<KvStoreColumn<?, ?>, NavigableMap<Bytes, Bytes>> columnData =
        columns.stream()
            .collect(Collectors.toConcurrentMap(col -> col, __ -> new ConcurrentSkipListMap<>()));
    final Map<KvStoreVariable<?>, Bytes> variableData = new ConcurrentHashMap<>();
    return new MockKvStoreInstance(columns, variables, columnData, variableData);
  }

  @Override
  public <T> Optional<T> get(final KvStoreVariable<T> variable) {
    return getRaw(variable).map(Bytes::toArrayUnsafe).map(variable.getSerializer()::deserialize);
  }

  @Override
  public Optional<Bytes> getRaw(final KvStoreVariable<?> variable) {
    assertOpen();
    assertValidVariable(variable);
    return Optional.ofNullable(variableData.get(variable));
  }

  @Override
  public <K, V> Optional<V> get(final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    assertValidColumn(column);
    final Bytes keyBytes = keyToBytes(column, key);
    final Bytes valueBytes = columnData.get(column).get(keyBytes);
    return columnValue(column, valueBytes);
  }

  @Override
  public long size(final KvStoreColumn<?, ?> column) {
    assertOpen();
    assertValidColumn(column);
    return columnData.get(column).size();
  }

  @Override
  public <K, V> Map<K, V> getAll(final KvStoreColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    return stream(column).collect(Collectors.toMap(ColumnEntry::getKey, ColumnEntry::getValue));
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFloorEntry(
      final KvStoreColumn<K, V> column, final K key) {
    assertOpen();
    assertValidColumn(column);
    final Bytes keyBytes = keyToBytes(column, key);
    return Optional.ofNullable(columnData.get(column).floorEntry(keyBytes))
        .map(e -> columnEntry(column, e));
  }

  @Override
  public <K, V> Optional<ColumnEntry<K, V>> getFirstEntry(final KvStoreColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    final NavigableMap<Bytes, Bytes> values = columnData.get(column);
    return values.isEmpty()
        ? Optional.empty()
        : Optional.of(values.firstEntry()).map(e -> columnEntry(column, e));
  }

  @Override
  public <K, V> Optional<K> getLastKey(final KvStoreColumn<K, V> column) {
    assertOpen();
    assertValidColumn(column);
    final NavigableMap<Bytes, Bytes> values = columnData.get(column);
    return values.isEmpty()
        ? Optional.empty()
        : Optional.of(values.lastKey()).map(data -> columnKey(column, data));
  }

  @Override
  public <K, V> Stream<ColumnEntry<K, V>> stream(final KvStoreColumn<K, V> column) {
    return streamRaw(column)
        .map(
            entry ->
                ColumnEntry.create(
                    column.getKeySerializer().deserialize(entry.getKey().toArrayUnsafe()),
                    column.getValueSerializer().deserialize(entry.getValue().toArrayUnsafe())));
  }

  @Override
  public <K, V> Stream<K> streamKeys(final KvStoreColumn<K, V> column) {
    return streamKeysRaw(column)
        .map(entry -> column.getKeySerializer().deserialize(entry.toArrayUnsafe()));
  }

  @Override
  public Stream<ColumnEntry<Bytes, Bytes>> streamRaw(final KvStoreColumn<?, ?> column) {
    assertOpen();
    assertValidColumn(column);
    return columnData.get(column).entrySet().stream()
        .peek(value -> assertOpen())
        .map(entry -> columnEntry(entry));
  }

  @Override
  public Stream<Bytes> streamKeysRaw(final KvStoreColumn<?, ?> column) {
    assertOpen();
    assertValidColumn(column);
    return columnData.get(column).keySet().stream();
  }

  @Override
  public <K, V> Optional<Bytes> getRaw(final KvStoreColumn<K, V> column, final K key) {
    final Bytes keyBytes = keyToBytes(column, key);
    return Optional.ofNullable(columnData.get(column).get(keyBytes));
  }

  @Override
  public <K extends Comparable<K>, V> Stream<ColumnEntry<K, V>> stream(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    assertOpen();
    return columnData
        .get(column)
        .subMap(keyToBytes(column, from), true, keyToBytes(column, to), true)
        .entrySet()
        .stream()
        .peek(value -> assertOpen())
        .map(e -> columnEntry(column, e));
  }

  @Override
  public <K extends Comparable<K>, V> Stream<K> streamKeys(
      final KvStoreColumn<K, V> column, final K from, final K to) {
    assertOpen();
    return columnData
        .get(column)
        .subMap(keyToBytes(column, from), true, keyToBytes(column, to), true)
        .keySet()
        .stream()
        .peek(value -> assertOpen())
        .map(e -> columnKey(column, e));
  }

  @Override
  public KvStoreTransaction startTransaction() {
    assertOpen();
    return new MockKvStoreTransaction(this);
  }

  private <K, V> Optional<V> columnValue(final KvStoreColumn<K, V> column, final Bytes bytes) {
    return Optional.ofNullable(bytes)
        .map(Bytes::toArrayUnsafe)
        .map(column.getValueSerializer()::deserialize);
  }

  private <K, V> K columnKey(final KvStoreColumn<K, V> column, final Bytes bytes) {
    return column.getKeySerializer().deserialize(bytes.toArrayUnsafe());
  }

  private <K, V> ColumnEntry<K, V> columnEntry(
      final KvStoreColumn<K, V> column, final Map.Entry<Bytes, Bytes> entry) {
    final K key = columnKey(column, entry.getKey());
    final V value = columnValue(column, entry.getValue()).orElse(null);
    return ColumnEntry.create(key, value);
  }

  private ColumnEntry<Bytes, Bytes> columnEntry(final Map.Entry<Bytes, Bytes> entry) {
    return ColumnEntry.create(entry.getKey(), entry.getValue());
  }

  private <K, V> Bytes keyToBytes(final KvStoreColumn<K, V> column, final K key) {
    return Bytes.wrap(column.getKeySerializer().serialize(key));
  }

  private <K, V> Bytes valueToBytes(final KvStoreColumn<K, V> column, final V value) {
    return Bytes.wrap(column.getValueSerializer().serialize(value));
  }

  @Override
  public void compact() {
    // No-op for the in-memory mock instance.
  }

  @Override
  public void close() {
    closed.set(true);
  }

  private void assertValidVariable(final KvStoreVariable<?> variable) {
    checkArgument(variables.contains(variable), "Unknown RocksDbVariable supplied");
  }

  private void assertValidColumn(final KvStoreColumn<?, ?> column) {
    checkArgument(columns.contains(column), "Unknown RocksDbColumn %s supplied", column.getId());
  }

  private void assertOpen() {
    if (closed.get()) {
      throw new ShuttingDownException();
    }
  }

  private static class MockKvStoreTransaction implements KvStoreTransaction {

    private final MockKvStoreInstance dbInstance;
    private final Map<KvStoreColumn<?, ?>, Map<Bytes, Bytes>> columnUpdates = new HashMap<>();
    private final Map<KvStoreColumn<?, ?>, Set<Bytes>> deletedColumnKeys = new HashMap<>();
    private final Map<KvStoreVariable<?>, Optional<Bytes>> variableUpdates = new HashMap<>();
    private boolean closed = false;

    public MockKvStoreTransaction(final MockKvStoreInstance mockRocksDbInstance) {
      this.dbInstance = mockRocksDbInstance;
    }

    @Override
    public <T> void put(final KvStoreVariable<T> variable, final T value) {
      final Bytes valueBytes = Bytes.wrap(variable.getSerializer().serialize(value));
      putRaw(variable, valueBytes);
    }

    @Override
    public <T> void putRaw(final KvStoreVariable<T> variable, final Bytes value) {
      assertOpen();
      dbInstance.assertValidVariable(variable);
      variableUpdates.put(variable, Optional.of(value));
    }

    @Override
    public <K, V> void put(final KvStoreColumn<K, V> column, final K key, final V value) {
      final Bytes keyBytes = dbInstance.keyToBytes(column, key);
      final Bytes valueBytes = dbInstance.valueToBytes(column, value);
      putRaw(column, keyBytes, valueBytes);
    }

    @Override
    public <K, V> void putRaw(
        final KvStoreColumn<K, V> column, final Bytes keyBytes, final Bytes valueBytes) {
      assertOpen();
      dbInstance.assertValidColumn(column);
      final Map<Bytes, Bytes> updates =
          columnUpdates.computeIfAbsent(column, (col) -> new HashMap<>());
      updates.put(keyBytes, valueBytes);
    }

    @Override
    public <K, V> void put(final KvStoreColumn<K, V> column, final Map<K, V> data) {
      assertOpen();
      dbInstance.assertValidColumn(column);
      data.forEach((key, value) -> put(column, key, value));
    }

    @Override
    public <K, V> void delete(final KvStoreColumn<K, V> column, final K key) {
      assertOpen();
      dbInstance.assertValidColumn(column);
      final Bytes keyBytes = dbInstance.keyToBytes(column, key);
      final Map<Bytes, Bytes> updates = columnUpdates.get(column);
      if (updates != null) {
        updates.remove(keyBytes);
      }

      final Set<Bytes> deletedKeys =
          deletedColumnKeys.computeIfAbsent(column, __ -> new HashSet<>());
      deletedKeys.add(keyBytes);
    }

    @Override
    public <T> void delete(final KvStoreVariable<T> variable) {
      assertOpen();
      dbInstance.assertValidVariable(variable);
      variableUpdates.put(variable, Optional.empty());
    }

    @Override
    public void commit() {
      assertOpen();
      dbInstance.assertOpen();
      variableUpdates.forEach(
          (key, value) -> {
            if (value.isPresent()) {
              dbInstance.variableData.put(key, value.get());
            } else {
              dbInstance.variableData.remove(key);
            }
          });
      columnUpdates.forEach(
          (col, updates) -> {
            final NavigableMap<Bytes, Bytes> targetColumn = dbInstance.columnData.get(col);
            targetColumn.putAll(updates);
          });
      deletedColumnKeys.forEach(
          (col, deletedKeys) -> {
            final NavigableMap<Bytes, Bytes> targetColumn = dbInstance.columnData.get(col);
            deletedKeys.forEach(targetColumn::remove);
          });
    }

    private void assertOpen() {
      checkState(!closed, "Attempt to modify a closed transaction");
      if (dbInstance.closed.get()) {
        throw new ShuttingDownException();
      }
    }

    @Override
    public void rollback() {
      closed = true;
      variableUpdates.clear();
      columnUpdates.clear();
      deletedColumnKeys.clear();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.rocksdb.AbstractNativeReference;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.PerfLevel;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.Schema;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbInstance.IteratorWithResources;

class RocksDbInstanceTest {

  private static final int KEY_COUNT = 10_000;
  // Keys below this are deleted, leaving one tombstone each, as data column sidecar pruning does
  private static final int FIRST_LIVE_KEY = 9_900;

  private final KvStoreColumn<UInt64, UInt64> column =
      KvStoreColumn.create(1, UINT64_SERIALIZER, UINT64_SERIALIZER);

  @TempDir Path databaseDir;

  private TransactionDB db;
  private ColumnFamilyHandle columnHandle;
  private RocksDbInstance instance;

  @BeforeEach
  void setUp() throws Exception {
    RocksDB.loadLibrary();
    final DBOptions dbOptions =
        new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
    final TransactionDBOptions txOptions = new TransactionDBOptions();
    final ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
    final ColumnFamilyDescriptor columnDescriptor =
        new ColumnFamilyDescriptor(column.getId().toArrayUnsafe(), columnFamilyOptions);
    final List<ColumnFamilyHandle> handles = new ArrayList<>();
    db =
        TransactionDB.open(
            dbOptions,
            txOptions,
            databaseDir.toString(),
            List.of(
                new ColumnFamilyDescriptor(
                    Schema.DEFAULT_COLUMN_ID.toArrayUnsafe(), columnFamilyOptions),
                columnDescriptor),
            handles);
    columnHandle = handles.get(1);
    final List<AutoCloseable> resources = new ArrayList<>(handles);
    resources.addAll(List.of(db, dbOptions, txOptions, columnFamilyOptions));
    instance =
        new RocksDbInstance(
            db,
            handles.get(0),
            ImmutableMap.of(column, columnHandle),
            Map.of(column, columnDescriptor),
            resources);

    try (final KvStoreTransaction transaction = instance.startTransaction()) {
      for (int i = 0; i < KEY_COUNT; i++) {
        transaction.put(column, UInt64.valueOf(i), UInt64.valueOf(i * 2L));
      }
      transaction.commit();
    }
    flush();
    try (final KvStoreTransaction transaction = instance.startTransaction()) {
      for (int i = 0; i < FIRST_LIVE_KEY; i++) {
        transaction.delete(column, UInt64.valueOf(i));
      }
      transaction.commit();
    }
    // Keep the live keys and the tombstones in separate files on disk, as after pruning
    flush();
  }

  @AfterEach
  void tearDown() throws Exception {
    // The perf level is per thread, so don't leave counting on for other tests on this thread
    db.setPerfLevel(PerfLevel.DISABLE);
    instance.close();
  }

  @Test
  void streamKeys_shouldNotStepOverTombstonesBeyondTheRange() {
    db.setPerfLevel(PerfLevel.ENABLE_COUNT);

    db.getPerfContext().reset();
    try (final Stream<UInt64> keys = instance.streamKeys(column)) {
      // Control: an unbounded seek into the deleted block walks every tombstone
      assertThat(keys.findFirst()).contains(UInt64.valueOf(FIRST_LIVE_KEY));
    }
    assertThat(db.getPerfContext().getInternalDeleteSkippedCount())
        .isGreaterThanOrEqualTo(FIRST_LIVE_KEY);

    db.getPerfContext().reset();
    try (final Stream<UInt64> keys =
        instance.streamKeys(column, UInt64.valueOf(100), UInt64.valueOf(109))) {
      assertThat(keys).isEmpty();
    }
    // Only the tombstones of the requested range are visited
    assertThat(db.getPerfContext().getInternalDeleteSkippedCount()).isLessThanOrEqualTo(10);
  }

  @Test
  void stream_shouldNotStepOverTombstonesBeyondTheRange() {
    db.setPerfLevel(PerfLevel.ENABLE_COUNT);
    db.getPerfContext().reset();

    try (final Stream<ColumnEntry<UInt64, UInt64>> entries =
        instance.stream(column, UInt64.valueOf(100), UInt64.valueOf(109))) {
      assertThat(entries).isEmpty();
    }
    assertThat(db.getPerfContext().getInternalDeleteSkippedCount()).isLessThanOrEqualTo(10);
  }

  @Test
  void streamKeys_shouldIncludeLastKeyOfRange() {
    try (final Stream<UInt64> keys =
        instance.streamKeys(column, UInt64.valueOf(9_950), UInt64.valueOf(9_955))) {
      assertThat(keys).containsExactlyElementsOf(range(9_950, 9_955));
    }
  }

  @Test
  void stream_shouldIncludeLastKeyOfRange() {
    try (final Stream<ColumnEntry<UInt64, UInt64>> entries =
        instance.stream(column, UInt64.valueOf(9_950), UInt64.valueOf(9_955))) {
      assertThat(entries)
          .containsExactlyElementsOf(
              range(9_950, 9_955).stream()
                  .map(key -> ColumnEntry.create(key, key.times(2)))
                  .toList());
    }
  }

  @Test
  void streamKeys_shouldReturnOnlyLiveKeysWhenRangeStraddlesDeletedBlock() {
    try (final Stream<UInt64> keys =
        instance.streamKeys(
            column, UInt64.valueOf(FIRST_LIVE_KEY - 50), UInt64.valueOf(FIRST_LIVE_KEY + 5))) {
      assertThat(keys).containsExactlyElementsOf(range(FIRST_LIVE_KEY, FIRST_LIVE_KEY + 5));
    }
  }

  @Test
  void streamKeys_shouldReturnLiveKeysUpToLastKeyInColumn() {
    try (final Stream<UInt64> keys =
        instance.streamKeys(column, UInt64.valueOf(KEY_COUNT - 3), UInt64.MAX_VALUE)) {
      assertThat(keys).containsExactlyElementsOf(range(KEY_COUNT - 3, KEY_COUNT - 1));
    }
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  void iterator_shouldCloseIteratorBeforeItsResourcesWhenClosedEarly() {
    final RocksIterator rocksIterator = mock(RocksIterator.class);
    final AbstractNativeReference readOptions = mock(AbstractNativeReference.class);
    final AbstractNativeReference upperBound = mock(AbstractNativeReference.class);
    when(rocksIterator.isValid()).thenReturn(true);
    when(rocksIterator.key()).thenReturn(UINT64_SERIALIZER.serialize(UInt64.ONE));

    try (final Stream<byte[]> keys =
        RocksDbIterator.create(
                rocksIterator, RocksIterator::key, () -> false, List.of(readOptions, upperBound))
            .toStream()) {
      assertThat(keys.findFirst()).isPresent();
    }

    final InOrder inOrder = inOrder(rocksIterator, readOptions, upperBound);
    inOrder.verify(rocksIterator).close();
    inOrder.verify(readOptions).close();
    inOrder.verify(upperBound).close();
  }

  @Test
  void createBoundedIterator_shouldSetExclusiveUpperBoundAndHandBackResources() {
    final RocksIterator rocksIterator = mock(RocksIterator.class);
    final AtomicReference<ReadOptions> readOptions = new AtomicReference<>();

    final IteratorWithResources iterator =
        RocksDbInstance.createBoundedIterator(
            new byte[] {1, 2},
            options -> {
              readOptions.set(options);
              return rocksIterator;
            },
            it -> it.seek(new byte[] {1}));

    verify(rocksIterator).seek(new byte[] {1});
    assertThat(iterator.rocksIterator()).isSameAs(rocksIterator);
    assertThat(readOptions.get().iterateUpperBound().data()).containsExactly(1, 2, 0);
    assertThat(iterator.resources()).hasSize(2).first().isSameAs(readOptions.get());
    iterator.resources().forEach(AbstractNativeReference::close);
  }

  @Test
  void createBoundedIterator_shouldCloseEverythingWhenSetupFails() {
    final RocksIterator rocksIterator = mock(RocksIterator.class);
    final AtomicReference<ReadOptions> readOptions = new AtomicReference<>();
    final RuntimeException error = new RuntimeException("seek failed");

    assertThatThrownBy(
            () ->
                RocksDbInstance.createBoundedIterator(
                    new byte[] {1},
                    options -> {
                      readOptions.set(options);
                      return rocksIterator;
                    },
                    it -> {
                      throw error;
                    }))
        .isSameAs(error);

    verify(rocksIterator).close();
    assertThat(readOptions.get().isOwningHandle()).isFalse();
  }

  @Test
  void createBoundedIterator_shouldCloseReadOptionsWhenIteratorCannotBeCreated() {
    final AtomicReference<ReadOptions> readOptions = new AtomicReference<>();
    final RuntimeException error = new RuntimeException("newIterator failed");

    assertThatThrownBy(
            () ->
                RocksDbInstance.createBoundedIterator(
                    new byte[] {1},
                    options -> {
                      readOptions.set(options);
                      throw error;
                    },
                    it -> {}))
        .isSameAs(error);

    assertThat(readOptions.get().isOwningHandle()).isFalse();
  }

  @Test
  void exclusiveUpperBound_shouldBeSmallestKeyAboveLastKey() {
    assertThat(RocksDbInstance.exclusiveUpperBound(new byte[] {1, (byte) 0xff}))
        .containsExactly(1, 0xff, 0);
  }

  private void flush() throws Exception {
    try (final FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
      db.flush(flushOptions, columnHandle);
    }
  }

  private static List<UInt64> range(final long firstInclusive, final long lastInclusive) {
    return LongStream.rangeClosed(firstInclusive, lastInclusive).mapToObj(UInt64::valueOf).toList();
  }
}

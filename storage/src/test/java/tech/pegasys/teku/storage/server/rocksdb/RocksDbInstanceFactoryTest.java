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
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn.asColumnId;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.Schema;

class RocksDbInstanceFactoryTest {

  private final KvStoreColumn<UInt64, UInt64> firstColumn =
      KvStoreColumn.create(1, UINT64_SERIALIZER, UINT64_SERIALIZER);
  private final KvStoreColumn<UInt64, UInt64> secondColumn =
      KvStoreColumn.create(2, UINT64_SERIALIZER, UINT64_SERIALIZER);

  @TempDir Path databaseDir;

  @Test
  void shouldNotCreateUnusedColumnFamiliesOnOpen() throws Exception {
    try (final KvStoreAccessor db = createDatabase(List.of(firstColumn, secondColumn))) {
      assertThat(db.get(firstColumn, UInt64.ONE)).isEmpty();
      assertThat(db.size(secondColumn)).isZero();
    }

    assertThat(existingColumnFamilyIds()).containsExactlyInAnyOrder(Schema.DEFAULT_COLUMN_ID);
  }

  @Test
  void shouldOpenDatabaseWithUnknownExistingColumnFamily() throws Exception {
    try (final KvStoreAccessor db = createDatabase(List.of(firstColumn, secondColumn));
        final KvStoreAccessor.KvStoreTransaction transaction = db.startTransaction()) {
      transaction.put(secondColumn, UInt64.ONE, UInt64.valueOf(2));
      transaction.commit();
    }

    assertThat(existingColumnFamilyIds())
        .containsExactlyInAnyOrder(Schema.DEFAULT_COLUMN_ID, asColumnId(2));

    try (final KvStoreAccessor db = createDatabase(List.of(firstColumn))) {
      assertThat(db.get(firstColumn, UInt64.ONE)).isEmpty();
    }
  }

  @Test
  void shouldReadWithoutAcquiringDatabaseMonitor() throws Exception {
    try (final KvStoreAccessor db = createDatabase(List.of(firstColumn))) {
      synchronized (db) {
        final CompletableFuture<Optional<UInt64>> readFuture =
            CompletableFuture.supplyAsync(() -> db.get(firstColumn, UInt64.ONE));

        assertThat(readFuture)
            .succeedsWithin(Duration.ofSeconds(1))
            .satisfies(result -> assertThat(result).isEmpty());
      }
    }
  }

  private KvStoreAccessor createDatabase(final Collection<KvStoreColumn<?, ?>> columns) {
    return RocksDbInstanceFactory.create(
        new StubMetricsSystem(),
        STORAGE,
        KvStoreConfiguration.v6SingleDefaults().withDatabaseDir(databaseDir),
        columns,
        List.of(),
        List.of(),
        List.of());
  }

  private Set<Bytes> existingColumnFamilyIds() throws Exception {
    RocksDB.loadLibrary();
    try (final Options options = new Options()) {
      return RocksDB.listColumnFamilies(options, databaseDir.toString()).stream()
          .map(Bytes::wrap)
          .collect(Collectors.toSet());
    }
  }
}

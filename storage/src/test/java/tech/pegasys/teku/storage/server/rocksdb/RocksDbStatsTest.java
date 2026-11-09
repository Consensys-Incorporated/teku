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
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;

class RocksDbStatsTest {

  @BeforeAll
  static void setUp() {
    assumeThat(DatabaseVersion.tryLoadRocksdbLibrary())
        .describedAs("RocksDB support required")
        .isTrue();
  }

  private final RocksDB database = mock(RocksDB.class);

  @Test
  void shouldNotCrashIfMetricsRequestedAfterClose() throws Exception {
    final ObservableMetricsSystem metricsSystem =
        new PrometheusMetricsSystem(Set.of(TekuMetricCategory.STORAGE_HOT_DB), true);

    try (RocksDbStats stats =
        new RocksDbStats(
            metricsSystem,
            TekuMetricCategory.STORAGE_HOT_DB,
            KvStoreConfiguration.v6SingleDefaults())) {
      stats.registerMetrics(database);

      when(database.getLongProperty(any())).thenThrow(new RocksDBException("Database shutdown"));
      final List<Observation> metrics =
          metricsSystem.streamObservations().collect(Collectors.toList());
      assertThat(metrics).isNotEmpty();
    }
  }
}

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

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.ExternalSummary;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.rocksdb.HistogramData;
import org.rocksdb.HistogramType;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.TickerType;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;

/**
 * Taken from
 * https://github.com/hyperledger/besu/blob/3d867532deb893fd267fcd5d4e6bf0d42a76e59b/metrics/rocksdb/src/main/java/org/hyperledger/besu/metrics/rocksdb/RocksDBStats.java#L137
 */
public class RocksDbStats implements AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  // Tickers - RocksDB equivalent of counters
  static final TickerType[] TICKERS = {
    TickerType.BLOCK_CACHE_ADD,
    TickerType.BLOCK_CACHE_HIT,
    TickerType.BLOCK_CACHE_ADD_FAILURES,
    TickerType.BLOCK_CACHE_INDEX_MISS,
    TickerType.BLOCK_CACHE_INDEX_HIT,
    TickerType.BLOCK_CACHE_INDEX_ADD,
    TickerType.BLOCK_CACHE_INDEX_BYTES_INSERT,
    TickerType.BLOCK_CACHE_FILTER_MISS,
    TickerType.BLOCK_CACHE_FILTER_HIT,
    TickerType.BLOCK_CACHE_FILTER_ADD,
    TickerType.BLOCK_CACHE_FILTER_BYTES_INSERT,
    TickerType.BLOCK_CACHE_DATA_MISS,
    TickerType.BLOCK_CACHE_DATA_HIT,
    TickerType.BLOCK_CACHE_DATA_ADD,
    TickerType.BLOCK_CACHE_DATA_BYTES_INSERT,
    TickerType.BLOCK_CACHE_BYTES_READ,
    TickerType.BLOCK_CACHE_BYTES_WRITE,
    TickerType.BLOOM_FILTER_USEFUL,
    TickerType.PERSISTENT_CACHE_HIT,
    TickerType.PERSISTENT_CACHE_MISS,
    TickerType.SIM_BLOCK_CACHE_HIT,
    TickerType.SIM_BLOCK_CACHE_MISS,
    TickerType.MEMTABLE_HIT,
    TickerType.MEMTABLE_MISS,
    TickerType.GET_HIT_L0,
    TickerType.GET_HIT_L1,
    TickerType.GET_HIT_L2_AND_UP,
    TickerType.COMPACTION_KEY_DROP_NEWER_ENTRY,
    TickerType.COMPACTION_KEY_DROP_OBSOLETE,
    TickerType.COMPACTION_KEY_DROP_RANGE_DEL,
    TickerType.COMPACTION_KEY_DROP_USER,
    TickerType.COMPACTION_RANGE_DEL_DROP_OBSOLETE,
    TickerType.NUMBER_KEYS_WRITTEN,
    TickerType.NUMBER_KEYS_READ,
    TickerType.NUMBER_KEYS_UPDATED,
    TickerType.BYTES_WRITTEN,
    TickerType.BYTES_READ,
    TickerType.NUMBER_DB_SEEK,
    TickerType.NUMBER_DB_NEXT,
    TickerType.NUMBER_DB_PREV,
    TickerType.NUMBER_DB_SEEK_FOUND,
    TickerType.NUMBER_DB_NEXT_FOUND,
    TickerType.NUMBER_DB_PREV_FOUND,
    TickerType.ITER_BYTES_READ,
    TickerType.NO_FILE_OPENS,
    TickerType.NO_FILE_ERRORS,
    // TickerType.STALL_L0_SLOWDOWN_MICROS,
    // TickerType.STALL_MEMTABLE_COMPACTION_MICROS,
    // TickerType.STALL_L0_NUM_FILES_MICROS,
    TickerType.STALL_MICROS,
    TickerType.DB_MUTEX_WAIT_MICROS,
    TickerType.NUMBER_MULTIGET_BYTES_READ,
    TickerType.NUMBER_MULTIGET_KEYS_READ,
    TickerType.NUMBER_MULTIGET_CALLS,
    TickerType.NUMBER_MERGE_FAILURES,
    TickerType.BLOOM_FILTER_PREFIX_CHECKED,
    TickerType.BLOOM_FILTER_PREFIX_USEFUL,
    TickerType.NUMBER_OF_RESEEKS_IN_ITERATION,
    TickerType.GET_UPDATES_SINCE_CALLS,
    TickerType.WAL_FILE_SYNCED,
    TickerType.WAL_FILE_BYTES,
    TickerType.WRITE_DONE_BY_SELF,
    TickerType.WRITE_DONE_BY_OTHER,
    TickerType.WRITE_WITH_WAL,
    TickerType.COMPACT_READ_BYTES,
    TickerType.COMPACT_WRITE_BYTES,
    TickerType.FLUSH_WRITE_BYTES,
    TickerType.NUMBER_DIRECT_LOAD_TABLE_PROPERTIES,
    TickerType.NUMBER_SUPERVERSION_ACQUIRES,
    TickerType.NUMBER_SUPERVERSION_RELEASES,
    TickerType.NUMBER_SUPERVERSION_CLEANUPS,
    TickerType.NUMBER_BLOCK_COMPRESSED,
    TickerType.NUMBER_BLOCK_DECOMPRESSED,
    TickerType.MERGE_OPERATION_TOTAL_TIME,
    TickerType.FILTER_OPERATION_TOTAL_TIME,
    TickerType.ROW_CACHE_HIT,
    TickerType.ROW_CACHE_MISS,
    TickerType.READ_AMP_ESTIMATE_USEFUL_BYTES,
    TickerType.READ_AMP_TOTAL_READ_BYTES,
    TickerType.NUMBER_RATE_LIMITER_DRAINS,
    TickerType.NUMBER_ITER_SKIP,
    TickerType.NUMBER_MULTIGET_KEYS_FOUND,
    // Blob DB metrics for measuring blobdb performance improvements
    TickerType.BLOB_DB_NUM_PUT,
    TickerType.BLOB_DB_NUM_GET,
    TickerType.BLOB_DB_BYTES_WRITTEN,
    TickerType.BLOB_DB_BYTES_READ,
    TickerType.BLOB_DB_BLOB_FILE_BYTES_READ,
    TickerType.BLOB_DB_BLOB_FILE_BYTES_WRITTEN,
    TickerType.BLOB_DB_GC_NUM_FILES,
    TickerType.BLOB_DB_GC_NUM_NEW_FILES,
    TickerType.BLOB_DB_GC_NUM_KEYS_RELOCATED,
    TickerType.BLOB_DB_GC_BYTES_RELOCATED,
    TickerType.BLOB_DB_CACHE_MISS,
    TickerType.BLOB_DB_CACHE_HIT,
    TickerType.BLOB_DB_CACHE_ADD,
    TickerType.BLOB_DB_CACHE_BYTES_READ,
    TickerType.BLOB_DB_CACHE_BYTES_WRITE,
  };

  // Histograms - treated as prometheus summaries
  static final HistogramType[] HISTOGRAMS = {
    HistogramType.DB_GET,
    HistogramType.DB_WRITE,
    HistogramType.COMPACTION_TIME,
    HistogramType.SUBCOMPACTION_SETUP_TIME,
    HistogramType.TABLE_SYNC_MICROS,
    HistogramType.COMPACTION_OUTFILE_SYNC_MICROS,
    HistogramType.WAL_FILE_SYNC_MICROS,
    HistogramType.MANIFEST_FILE_SYNC_MICROS,
    HistogramType.TABLE_OPEN_IO_MICROS,
    HistogramType.DB_MULTIGET,
    HistogramType.READ_BLOCK_COMPACTION_MICROS,
    HistogramType.READ_BLOCK_GET_MICROS,
    HistogramType.WRITE_RAW_BLOCK_MICROS,
    HistogramType.NUM_FILES_IN_SINGLE_COMPACTION,
    HistogramType.DB_SEEK,
    HistogramType.WRITE_STALL,
    HistogramType.SST_READ_MICROS,
    HistogramType.NUM_SUBCOMPACTIONS_SCHEDULED,
    HistogramType.BYTES_PER_READ,
    HistogramType.BYTES_PER_WRITE,
    HistogramType.BYTES_PER_MULTIGET,
    HistogramType.COMPRESSION_TIMES_NANOS,
    HistogramType.DECOMPRESSION_TIMES_NANOS,
    HistogramType.READ_NUM_MERGE_OPERANDS,
  };

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Statistics stats;
  private final MetricsSystem metricsSystem;
  private final MetricCategory category;
  private final KvStoreConfiguration configuration;

  public RocksDbStats(
      final MetricsSystem metricsSystem,
      final MetricCategory category,
      final KvStoreConfiguration configuration) {
    this.stats = new Statistics();
    this.metricsSystem = metricsSystem;
    this.category = category;
    this.configuration = configuration;
  }

  public Statistics getStats() {
    return stats;
  }

  public void registerMetrics(final RocksDB database) {
    metricsSystem.createLongGauge(
        category,
        "estimated_table_readers_memory",
        "Estimated memory used by index and filter blocks",
        () -> getLongProperty(database, "rocksdb.estimate-table-readers-mem"));
    metricsSystem.createLongGauge(
        category,
        "current_size_all_mem_tables",
        "Current size of all RocksDB mem tables combined",
        () -> getLongProperty(database, "rocksdb.cur-size-all-mem-tables"));

    // Blob DB size metrics for measuring space savings
    if (configuration.blobDbEnabled()) {
      metricsSystem.createLongGauge(
          category,
          "total_blob_file_size",
          "Total size of all blob files in bytes",
          () -> getLongProperty(database, "rocksdb.total-blob-file-size"));
      metricsSystem.createLongGauge(
          category,
          "live_blob_file_size",
          "Size of live blob files in bytes (excluding garbage)",
          () -> getLongProperty(database, "rocksdb.live-blob-file-size"));
      metricsSystem.createLongGauge(
          category,
          "num_blob_files",
          "Number of blob files",
          () -> getLongProperty(database, "rocksdb.num-blob-files"));
    }

    for (final TickerType ticker : TICKERS) {
      final String promCounterName = ticker.name().toLowerCase(Locale.ROOT);
      metricsSystem.createLongGauge(
          category,
          promCounterName,
          "RocksDB reported statistics for " + ticker.name(),
          () -> ifOpen(() -> stats.getTickerCount(ticker), 0L));
    }

    if (metricsSystem instanceof PrometheusMetricsSystem) {
      for (final HistogramType histogram : HISTOGRAMS) {
        metricsSystem.createSummary(
            category,
            category.getApplicationPrefix().orElse("")
                + category.getName()
                + "_"
                + histogram.name().toLowerCase(Locale.ROOT),
            "RocksDB histogram for " + histogram.name(),
            () -> provideExternalSummary(histogram));
      }
    }
  }

  private ExternalSummary provideExternalSummary(final HistogramType histogramType) {
    return ifOpen(
        () -> {
          final HistogramData data = stats.getHistogramData(histogramType);

          return new ExternalSummary(
              data.getCount(),
              data.getSum(),
              List.of(
                  new ExternalSummary.Quantile(0.0, data.getMin()),
                  new ExternalSummary.Quantile(0.5, data.getMedian()),
                  new ExternalSummary.Quantile(0.95, data.getPercentile95()),
                  new ExternalSummary.Quantile(0.99, data.getPercentile99()),
                  new ExternalSummary.Quantile(1.0, data.getMax())));
        },
        null);
  }

  private long getLongProperty(final RocksDB database, final String name) {
    return ifOpen(
        () -> {
          try {
            return database.getLongProperty(name);
          } catch (RocksDBException e) {
            LOG.warn("Failed to load " + name + " property for RocksDB metrics");
            return 0L;
          }
        },
        0L);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      stats.close();
    }
  }

  private <T> T ifOpen(final Supplier<T> supplier, final T defaultValue) {
    if (closed.get()) {
      return defaultValue;
    }
    return supplier.get();
  }
}

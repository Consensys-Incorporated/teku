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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

@SuppressWarnings("JavaCase")
// RocksDB subcommand helper methods.
public class RocksDbHelper {
  private static final Logger LOG = LoggerFactory.getLogger(RocksDbHelper.class);

  public static void forEachColumnFamily(
      final String dbPath,
      final Eth2NetworkConfiguration networkConfig,
      final BiConsumer<RocksDB, ColumnFamilyHandle> task) {
    RocksDB.loadLibrary();
    final Options options = new Options();
    options.setCreateIfMissing(true);

    // Open the RocksDB database with multiple column families
    List<byte[]> cfNames;
    try {
      cfNames = RocksDB.listColumnFamilies(options, dbPath);
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
    final List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
    final List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    for (byte[] cfName : cfNames) {
      cfDescriptors.add(new ColumnFamilyDescriptor(cfName));
    }
    try (final RocksDB rocksdb = RocksDB.openReadOnly(dbPath, cfDescriptors, cfHandles)) {
      for (ColumnFamilyHandle cfHandle : cfHandles) {
        task.accept(rocksdb, cfHandle);
      }
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    } finally {
      for (ColumnFamilyHandle cfHandle : cfHandles) {
        cfHandle.close();
      }
    }
  }

  private static long parseLongOrDefault(final String str) {
    try {
      return Long.parseLong(str);
    } catch (NumberFormatException e) {
      LOG.debug(String.format("Failed to parse %s as a long", str), e);
      return 0L;
    }
  }

  public static void printStatsForColumnFamily(
      final RocksDB rocksdb,
      final ColumnFamilyHandle cfHandle,
      final SubCommandLogger out,
      final Optional<String> maybeFilter,
      final Function<byte[], String> nameFromIdFn)
      throws RocksDBException {
    final String size = rocksdb.getProperty(cfHandle, "rocksdb.estimate-live-data-size");
    final String numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys");
    final long sizeLong = parseLongOrDefault(size);
    final long numberOfKeysLong = parseLongOrDefault(numberOfKeys);
    if (!size.isBlank()
        && !numberOfKeys.isBlank()
        && isPopulatedColumnFamily(sizeLong, numberOfKeysLong)) {
      final String nameById = nameFromIdFn.apply(cfHandle.getName());
      if (nameById != null) {
        if (maybeFilter.isPresent() && !nameById.contains(maybeFilter.get())) {
          return;
        }
        out.display("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        out.display("Column Family: " + nameById);

        final String prefix = "rocksdb.";
        final String cfstats = "cfstats";
        final String cfstats_no_file_histogram = "cfstats-no-file-histogram";
        final String cf_file_histogram = "cf-file-histogram";
        final String cf_write_stall_stats = "cf-write-stall-stats";
        final String dbstats = "dbstats";
        final String db_write_stall_stats = "db-write-stall-stats";
        final String levelstats = "levelstats";
        final String block_cache_entry_stats = "block-cache-entry-stats";
        final String fast_block_cache_entry_stats = "fast-block-cache-entry-stats";
        final String num_immutable_mem_table = "num-immutable-mem-table";
        final String num_immutable_mem_table_flushed = "num-immutable-mem-table-flushed";
        final String mem_table_flush_pending = "mem-table-flush-pending";
        final String compaction_pending = "compaction-pending";
        final String background_errors = "background-errors";
        final String cur_size_active_mem_table = "cur-size-active-mem-table";
        final String cur_size_all_mem_tables = "cur-size-all-mem-tables";
        final String size_all_mem_tables = "size-all-mem-tables";
        final String num_entries_active_mem_table = "num-entries-active-mem-table";
        final String num_entries_imm_mem_tables = "num-entries-imm-mem-tables";
        final String num_deletes_active_mem_table = "num-deletes-active-mem-table";
        final String num_deletes_imm_mem_tables = "num-deletes-imm-mem-tables";
        final String estimate_num_keys = "estimate-num-keys";
        final String estimate_table_readers_mem = "estimate-table-readers-mem";
        final String is_file_deletions_enabled = "is-file-deletions-enabled";
        final String num_snapshots = "num-snapshots";
        final String oldest_snapshot_time = "oldest-snapshot-time";
        final String oldest_snapshot_sequence = "oldest-snapshot-sequence";
        final String num_live_versions = "num-live-versions";
        final String current_version_number = "current-super-version-number";
        final String estimate_live_data_size = "estimate-live-data-size";
        final String min_log_number_to_keep_str = "min-log-number-to-keep";
        final String min_obsolete_sst_number_to_keep_str = "min-obsolete-sst-number-to-keep";
        final String base_level_str = "base-level";
        final String total_sst_files_size = "total-sst-files-size";
        final String live_sst_files_size = "live-sst-files-size";
        final String obsolete_sst_files_size = "obsolete-sst-files-size";
        final String live_sst_files_size_at_temperature = "live-sst-files-size-at-temperature";
        final String estimate_pending_comp_bytes = "estimate-pending-compaction-bytes";
        final String aggregated_table_properties = "aggregated-table-properties";
        final String num_running_compactions = "num-running-compactions";
        final String num_running_flushes = "num-running-flushes";
        final String actual_delayed_write_rate = "actual-delayed-write-rate";
        final String is_write_stopped = "is-write-stopped";
        final String estimate_oldest_key_time = "estimate-oldest-key-time";
        final String block_cache_capacity = "block-cache-capacity";
        final String block_cache_usage = "block-cache-usage";
        final String block_cache_pinned_usage = "block-cache-pinned-usage";
        final String options_statistics = "options-statistics";
        final String num_blob_files = "num-blob-files";
        final String blob_stats = "blob-stats";
        final String total_blob_file_size = "total-blob-file-size";
        final String live_blob_file_size = "live-blob-file-size";
        final String live_blob_file_garbage_size = "live-blob-file-garbage-size";
        final String blob_cache_capacity = "blob-cache-capacity";
        final String blob_cache_usage = "blob-cache-usage";
        final String blob_cache_pinned_usage = "blob-cache-pinned-usage";
        Stream.of(
                cfstats,
                cfstats_no_file_histogram,
                cf_file_histogram,
                cf_write_stall_stats,
                dbstats,
                db_write_stall_stats,
                levelstats,
                block_cache_entry_stats,
                fast_block_cache_entry_stats,
                num_immutable_mem_table,
                num_immutable_mem_table_flushed,
                mem_table_flush_pending,
                compaction_pending,
                background_errors,
                cur_size_active_mem_table,
                cur_size_all_mem_tables,
                size_all_mem_tables,
                num_entries_active_mem_table,
                num_entries_imm_mem_tables,
                num_deletes_active_mem_table,
                num_deletes_imm_mem_tables,
                estimate_num_keys,
                estimate_table_readers_mem,
                is_file_deletions_enabled,
                num_snapshots,
                oldest_snapshot_time,
                oldest_snapshot_sequence,
                num_live_versions,
                current_version_number,
                estimate_live_data_size,
                min_log_number_to_keep_str,
                min_obsolete_sst_number_to_keep_str,
                base_level_str,
                total_sst_files_size,
                live_sst_files_size,
                obsolete_sst_files_size,
                live_sst_files_size_at_temperature,
                estimate_pending_comp_bytes,
                aggregated_table_properties,
                num_running_compactions,
                num_running_flushes,
                actual_delayed_write_rate,
                is_write_stopped,
                estimate_oldest_key_time,
                block_cache_capacity,
                block_cache_usage,
                block_cache_pinned_usage,
                options_statistics,
                num_blob_files,
                blob_stats,
                total_blob_file_size,
                live_blob_file_size,
                live_blob_file_garbage_size,
                blob_cache_capacity,
                blob_cache_usage,
                blob_cache_pinned_usage)
            .forEach(
                prop -> {
                  try {
                    final String value = rocksdb.getProperty(cfHandle, prefix + prop);
                    if (!value.isBlank()) {
                      out.display(prop + ": " + value);
                    }
                  } catch (RocksDBException e) {
                    LOG.debug("couldn't get property {}", prop);
                  }
                });
        out.display("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
      } else {

        out.error(
            "*** No rocksdb properties found for " + Bytes.of(cfHandle.getName()).toHexString());
      }
    }
  }

  public static ColumnFamilyUsage getAndPrintUsageForColumnFamily(
      final RocksDB rocksdb,
      final ColumnFamilyHandle cfHandle,
      final SubCommandLogger out,
      final Function<byte[], String> nameFromIdFn)
      throws RocksDBException, NumberFormatException {
    final String numberOfKeys = rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys");
    if (!numberOfKeys.isBlank()) {
      try {
        final long numberOfKeysLong = Long.parseLong(numberOfKeys);
        final String totalSstFilesSize =
            rocksdb.getProperty(cfHandle, "rocksdb.total-sst-files-size");
        final long totalSstFilesSizeLong =
            !totalSstFilesSize.isBlank() ? Long.parseLong(totalSstFilesSize) : 0;

        final String totalBlobFilesSize =
            rocksdb.getProperty(cfHandle, "rocksdb.total-blob-file-size");
        final long totalBlobFilesSizeLong =
            !totalBlobFilesSize.isBlank() ? Long.parseLong(totalBlobFilesSize) : 0;

        final long totalFilesSize = totalSstFilesSizeLong + totalBlobFilesSizeLong;
        if (isPopulatedColumnFamily(0, numberOfKeysLong)) {
          printLine(
              out,
              nameFromIdFn.apply(cfHandle.getName()),
              rocksdb.getProperty(cfHandle, "rocksdb.estimate-num-keys"),
              formatOutputSize(totalFilesSize),
              formatOutputSize(totalSstFilesSizeLong),
              formatOutputSize(totalBlobFilesSizeLong));
        }
        return new ColumnFamilyUsage(
            nameFromIdFn.apply(cfHandle.getName()),
            numberOfKeysLong,
            totalFilesSize,
            totalSstFilesSizeLong,
            totalBlobFilesSizeLong);
      } catch (NumberFormatException e) {
        LOG.error("Failed to parse string into long: " + e.getMessage());
      }
    }
    // return empty usage on error
    return new ColumnFamilyUsage(nameFromIdFn.apply(cfHandle.getName()), 0, 0, 0, 0);
  }

  public static void printTotals(
      final SubCommandLogger out, final List<ColumnFamilyUsage> columnFamilyUsages) {
    final long totalKeys = columnFamilyUsages.stream().mapToLong(ColumnFamilyUsage::keys).sum();
    final long totalSize =
        columnFamilyUsages.stream().mapToLong(ColumnFamilyUsage::totalSize).sum();
    final long totalSsts =
        columnFamilyUsages.stream().mapToLong(ColumnFamilyUsage::sstFilesSize).sum();
    final long totalBlobs =
        columnFamilyUsages.stream().mapToLong(ColumnFamilyUsage::blobFilesSize).sum();
    printSeparator(out);
    printLine(
        out,
        "ESTIMATED TOTAL",
        String.valueOf(totalKeys),
        formatOutputSize(totalSize),
        formatOutputSize(totalSsts),
        formatOutputSize(totalBlobs));
    printSeparator(out);
  }

  private static boolean isPopulatedColumnFamily(final long size, final long numberOfKeys) {
    return size != 0 || numberOfKeys != 0;
  }

  static String formatOutputSize(final long size) {
    if (size > (1024 * 1024 * 1024)) {
      long sizeInGiB = size / (1024 * 1024 * 1024);
      return sizeInGiB + " GiB";
    } else if (size > (1024 * 1024)) {
      long sizeInMiB = size / (1024 * 1024);
      return sizeInMiB + " MiB";
    } else if (size > 1024) {
      long sizeInKiB = size / 1024;
      return sizeInKiB + " KiB";
    } else {
      return size + " B";
    }
  }

  public static void printTableHeader(final SubCommandLogger out) {
    printSeparator(out);
    printLine(out, "Column Family", "Keys", "Total Size", "SST Files Size", "Blob Files Size");
    printSeparator(out);
  }

  private static void printSeparator(final SubCommandLogger out) {
    printLine(out, "-".repeat(50), "-".repeat(15), "-".repeat(11), "-".repeat(15), "-".repeat(16));
  }

  @SuppressWarnings("InlineFormatString")
  static void printLine(
      final SubCommandLogger out,
      final String cfName,
      final String keys,
      final String totalFilesSize,
      final String sstFilesSize,
      final String blobFilesSize) {
    final String format = "| %-50s | %-15s | %-11s | %-15s | %-16s |";

    out.display(String.format(format, cfName, keys, totalFilesSize, sstFilesSize, blobFilesSize));
  }

  public record ColumnFamilyUsage(
      String name, long keys, long totalSize, long sstFilesSize, long blobFilesSize) {}
}

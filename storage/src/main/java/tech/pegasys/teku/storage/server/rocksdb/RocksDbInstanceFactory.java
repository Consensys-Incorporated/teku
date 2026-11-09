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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration.EXPECTED_WAL_FILE_SIZE;
import static tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration.NUMBER_OF_LOG_FILES_TO_KEEP;
import static tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration.ROCKSDB_BLOCK_SIZE;
import static tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration.TIME_TO_ROLL_LOG_FILE;
import static tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration.WAL_MAX_TOTAL_SIZE;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.Schema;

public class RocksDbInstanceFactory {
  static {
    RocksDbUtil.loadNativeLibrary();
  }

  public static KvStoreAccessor create(
      final MetricsSystem metricsSystem,
      final MetricCategory metricCategory,
      final KvStoreConfiguration configuration,
      final Collection<KvStoreColumn<?, ?>> columns,
      final Collection<Bytes> deletedColumns,
      final Collection<KvStoreVariable<?>> variables,
      final Collection<Bytes> deletedVariables)
      throws DatabaseStorageException {
    // Track resources that need to be closed
    checkArgument(
        Stream.concat(columns.stream().map(KvStoreColumn::getId), deletedColumns.stream())
                .distinct()
                .count()
            == columns.size() + deletedColumns.size(),
        "Column IDs are not distinct");

    checkArgument(
        Stream.concat(variables.stream().map(KvStoreVariable::getId), deletedVariables.stream())
                .distinct()
                .count()
            == variables.size() + deletedVariables.size(),
        "Variable IDs are not distinct");

    // Create options
    final TransactionDBOptions txOptions = new TransactionDBOptions();
    final RocksDbStats rocksDbStats =
        new RocksDbStats(metricsSystem, metricCategory, configuration);
    final DBOptions dbOptions = createDBOptions(configuration, rocksDbStats.getStats());
    final LRUCache blockCache = new LRUCache(configuration.getCacheCapacity());
    final ColumnFamilyOptions columnFamilyOptions =
        createColumnFamilyOptions(configuration, blockCache);
    final List<AutoCloseable> resources =
        new ArrayList<>(
            List.of(txOptions, dbOptions, columnFamilyOptions, rocksDbStats, blockCache));

    final ColumnFamilyDescriptors columnFamilyDescriptors =
        createColumnFamilyDescriptors(
            configuration, columns, deletedColumns, columnFamilyOptions, resources);
    final List<ColumnFamilyDescriptor> columnDescriptors =
        getExistingColumnIds(configuration).stream()
            .map(
                id ->
                    columnFamilyDescriptors
                        .byId()
                        .getOrDefault(
                            id,
                            new ColumnFamilyDescriptor(id.toArrayUnsafe(), columnFamilyOptions)))
            .collect(Collectors.toCollection(ArrayList::new));
    Map<Bytes, KvStoreColumn<?, ?>> columnsById =
        columns.stream().collect(Collectors.toMap(KvStoreColumn::getId, Function.identity()));

    try {
      // columnHandles will be filled when the db is opened
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      final TransactionDB db =
          TransactionDB.open(
              dbOptions,
              txOptions,
              configuration.getDatabaseDir().toString(),
              columnDescriptors,
              columnHandles);

      final ImmutableMap.Builder<KvStoreColumn<?, ?>, ColumnFamilyHandle> builder =
          ImmutableMap.builder();
      for (ColumnFamilyHandle columnHandle : columnHandles) {
        final Bytes columnId = Bytes.wrap(columnHandle.getName());
        final KvStoreColumn<?, ?> column = columnsById.get(columnId);
        if (column != null) {
          // We need to check for null because the default column will not match a RocksDbColumn
          builder.put(column, columnHandle);
        }
        resources.add(columnHandle);
      }
      final ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyHandle> columnHandlesMap =
          builder.build();
      final ColumnFamilyHandle defaultHandle = getDefaultHandle(columnHandles);
      resources.add(db);

      rocksDbStats.registerMetrics(db);

      return new RocksDbInstance(
          db, defaultHandle, columnHandlesMap, columnFamilyDescriptors.byColumn(), resources);
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException(
          "Failed to open database at path: " + configuration.getDatabaseDir(), e);
    }
  }

  private static ColumnFamilyHandle getDefaultHandle(final List<ColumnFamilyHandle> columnHandles) {
    return columnHandles.stream()
        .filter(
            handle -> {
              try {
                return Bytes.wrap(handle.getName()).equals(Schema.DEFAULT_COLUMN_ID);
              } catch (RocksDBException e) {
                throw RocksDbExceptionUtil.wrapException(
                    "Unable to retrieve default column handle", e);
              }
            })
        .findFirst()
        .orElseThrow(() -> DatabaseStorageException.unrecoverable("No default column defined"));
  }

  private static DBOptions createDBOptions(
      final KvStoreConfiguration configuration, final Statistics stats) {
    final DBOptions options =
        new DBOptions()
            .setCreateIfMissing(true)
            .setIncreaseParallelism(Runtime.getRuntime().availableProcessors())
            .setMaxBackgroundJobs(configuration.getMaxBackgroundJobs())
            .setDbWriteBufferSize(configuration.getWriteBufferCapacity())
            .setMaxOpenFiles(configuration.getMaxOpenFiles())
            .setLogFileTimeToRoll(TIME_TO_ROLL_LOG_FILE)
            .setKeepLogFileNum(NUMBER_OF_LOG_FILES_TO_KEEP)
            .setEnv(Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()))
            .setStatistics(stats)
            .setMaxTotalWalSize(WAL_MAX_TOTAL_SIZE)
            .setRecycleLogFileNum(WAL_MAX_TOTAL_SIZE / EXPECTED_WAL_FILE_SIZE);

    // Java docs suggests this if db is under 1GB, nearly impossible atm
    if (configuration.optimizeForSmallDb()) {
      options.optimizeForSmallDb();
    }

    return options;
  }

  private static ColumnFamilyOptions createColumnFamilyOptions(
      final KvStoreConfiguration configuration, final Cache cache) {
    return new ColumnFamilyOptions()
        .setCompressionType(configuration.getCompressionType())
        .setBottommostCompressionType(configuration.getBottomMostCompressionType())
        .setLevelCompactionDynamicLevelBytes(true)
        .setTableFormatConfig(createBlockBasedTableConfig(cache));
  }

  private static ColumnFamilyDescriptors createColumnFamilyDescriptors(
      final KvStoreConfiguration configuration,
      final Collection<KvStoreColumn<?, ?>> columns,
      final Collection<Bytes> deletedColumns,
      final ColumnFamilyOptions columnFamilyOptions,
      final List<AutoCloseable> resources) {

    final ColumnFamilyOptions columnFamilyOptionsWithBlobDb =
        new ColumnFamilyOptions(columnFamilyOptions);
    resources.add(columnFamilyOptionsWithBlobDb);
    if (configuration.blobDbEnabled()) {
      columnFamilyOptionsWithBlobDb
          .setEnableBlobFiles(true)
          .setMinBlobSize(100)
          .setBlobCompressionType(CompressionType.LZ4_COMPRESSION)
          .setEnableBlobGarbageCollection(true);
    }

    final Map<Bytes, ColumnFamilyDescriptor> descriptorsById = new HashMap<>();
    final Map<KvStoreColumn<?, ?>, ColumnFamilyDescriptor> descriptorsByColumn = new HashMap<>();
    for (KvStoreColumn<?, ?> column : columns) {
      final ColumnFamilyDescriptor descriptor =
          new ColumnFamilyDescriptor(
              column.getId().toArrayUnsafe(),
              configuration.blobDbEnabled() && column.containsStaticData()
                  ? columnFamilyOptionsWithBlobDb
                  : columnFamilyOptions);
      descriptorsById.put(column.getId(), descriptor);
      descriptorsByColumn.put(column, descriptor);
    }
    for (Bytes deletedColumn : deletedColumns) {
      descriptorsById.put(
          deletedColumn,
          new ColumnFamilyDescriptor(deletedColumn.toArrayUnsafe(), columnFamilyOptions));
    }
    descriptorsById.put(
        Schema.DEFAULT_COLUMN_ID,
        new ColumnFamilyDescriptor(Schema.DEFAULT_COLUMN_ID.toArrayUnsafe(), columnFamilyOptions));
    return new ColumnFamilyDescriptors(
        ImmutableMap.copyOf(descriptorsById), ImmutableMap.copyOf(descriptorsByColumn));
  }

  private static List<Bytes> getExistingColumnIds(final KvStoreConfiguration configuration) {
    if (!configuration.getDatabaseDir().resolve("CURRENT").toFile().exists()) {
      return List.of(Schema.DEFAULT_COLUMN_ID);
    }

    try (final Options options = new Options()) {
      final List<Bytes> existingColumnIds =
          RocksDB.listColumnFamilies(options, configuration.getDatabaseDir().toString()).stream()
              .map(Bytes::wrap)
              .collect(Collectors.toCollection(ArrayList::new));
      return existingColumnIds.isEmpty() ? List.of(Schema.DEFAULT_COLUMN_ID) : existingColumnIds;
    } catch (RocksDBException e) {
      throw RocksDbExceptionUtil.wrapException(
          "Failed to list column families at path: " + configuration.getDatabaseDir(), e);
    }
  }

  private static BlockBasedTableConfig createBlockBasedTableConfig(final Cache cache) {
    return new BlockBasedTableConfig()
        .setFormatVersion(5)
        .setBlockCache(cache)
        .setFilterPolicy(new BloomFilter(10, false))
        .setPartitionFilters(true)
        .setCacheIndexAndFilterBlocks(false)
        .setBlockSize(ROCKSDB_BLOCK_SIZE);
  }

  private record ColumnFamilyDescriptors(
      ImmutableMap<Bytes, ColumnFamilyDescriptor> byId,
      ImmutableMap<KvStoreColumn<?, ?>, ColumnFamilyDescriptor> byColumn) {}
}

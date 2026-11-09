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

package tech.pegasys.teku.cli.subcommand.storage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bouncycastle.util.Arrays;
import org.rocksdb.RocksDBException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedTreeState;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbHelper;

@Command(
    name = "rocksdb",
    description = "Print RocksDB information",
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class)
public class RocksDbCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @Command(
      name = "usage",
      description = "Print disk usage",
      mixinStandardHelpOptions = true,
      versionProvider = PicoCliVersionProvider.class)
  public int usage(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions) {
    final DatabaseVersion dbVersion = beaconNodeDataOptions.parseDatabaseVersion();
    switch (dbVersion) {
      case V4, V5 -> {
        getUsage(beaconNodeDataOptions, eth2NetworkOptions, "archive");
        getUsage(beaconNodeDataOptions, eth2NetworkOptions, "db");
      }
      case V6, ROCKSDB_TREE -> getUsage(beaconNodeDataOptions, eth2NetworkOptions, "db");
      default -> {
        SUB_COMMAND_LOG.error("LevelDB is not supported in this command");
        return 1;
      }
    }
    return 0;
  }

  private void getUsage(
      final BeaconNodeDataOptions beaconNodeDataOptions,
      final Eth2NetworkOptions eth2NetworkOptions,
      final String folder) {
    SUB_COMMAND_LOG.display("\nDisplaying column usage from RocksDB folder /" + folder);
    final DataConfig dataConfig = beaconNodeDataOptions.getDataConfig();
    final String dbPath =
        DataDirLayout.createFrom(dataConfig).getBeaconDataDirectory() + "/" + folder;
    final DatabaseVersion dbVersion = beaconNodeDataOptions.parseDatabaseVersion();
    RocksDbHelper.printTableHeader(SUB_COMMAND_LOG);
    final Eth2NetworkConfiguration networkConfiguration =
        eth2NetworkOptions.getNetworkConfiguration();

    final Spec spec = networkConfiguration.getSpec();
    final List<RocksDbHelper.ColumnFamilyUsage> columnFamilyUsages = new ArrayList<>();
    RocksDbHelper.forEachColumnFamily(
        dbPath,
        networkConfiguration,
        (rocksdb, cfHandle) -> {
          try {
            columnFamilyUsages.add(
                RocksDbHelper.getAndPrintUsageForColumnFamily(
                    rocksdb,
                    cfHandle,
                    SUB_COMMAND_LOG,
                    findColumnNameInDatabaseColumns(folder, dbVersion, spec)));
          } catch (final RocksDBException e) {
            throw new RuntimeException(e);
          }
        });
    RocksDbHelper.printTotals(SUB_COMMAND_LOG, columnFamilyUsages);
  }

  @Command(
      name = "stats",
      description = "Print rocksdb stats",
      mixinStandardHelpOptions = true,
      versionProvider = PicoCliVersionProvider.class)
  public int stats(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @CommandLine.Option(
              names = {"--filter"},
              description = "Only count the columns that contain the specified filter.")
          final String filter) {

    final Optional<String> maybeFilter = Optional.ofNullable(filter);
    final DatabaseVersion dbVersion = beaconNodeDataOptions.parseDatabaseVersion();
    switch (dbVersion) {
      case V4, V5 -> {
        getStats(beaconNodeDataOptions, eth2NetworkOptions, maybeFilter, "archive");
        getStats(beaconNodeDataOptions, eth2NetworkOptions, maybeFilter, "db");
      }
      case V6, ROCKSDB_TREE ->
          getStats(beaconNodeDataOptions, eth2NetworkOptions, maybeFilter, "db");
      default -> {
        SUB_COMMAND_LOG.error("LevelDB is not supported in this command");
        return 1;
      }
    }
    return 0;
  }

  private void getStats(
      final BeaconNodeDataOptions beaconNodeDataOptions,
      final Eth2NetworkOptions eth2NetworkOptions,
      final Optional<String> maybeFilter,
      final String folder) {
    final DatabaseVersion dbVersion = beaconNodeDataOptions.parseDatabaseVersion();
    final String dbPath =
        DataDirLayout.createFrom(beaconNodeDataOptions.getDataConfig()).getBeaconDataDirectory()
            + "/"
            + folder;
    final Eth2NetworkConfiguration networkConfiguration =
        eth2NetworkOptions.getNetworkConfiguration();
    final Spec spec = networkConfiguration.getSpec();
    SUB_COMMAND_LOG.display("Column Family Stats...");
    RocksDbHelper.forEachColumnFamily(
        dbPath,
        networkConfiguration,
        (rocksdb, cfHandle) -> {
          try {
            RocksDbHelper.printStatsForColumnFamily(
                rocksdb,
                cfHandle,
                SUB_COMMAND_LOG,
                maybeFilter,
                findColumnNameInDatabaseColumns(folder, dbVersion, spec));
          } catch (final RocksDBException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private Function<byte[], String> findColumnNameInDatabaseColumns(
      final String folder, final DatabaseVersion dbVersion, final Spec spec) {
    Map<String, KvStoreColumn<?, ?>> columnMap;
    switch (dbVersion) {
      case V4, V5 -> {
        if (Objects.equals(folder, "db")) {
          columnMap = V6SchemaCombinedSnapshot.createV4(spec).asSchemaHot().getColumnMap();
        } else {
          columnMap = V6SchemaCombinedSnapshot.createV4(spec).asSchemaFinalized().getColumnMap();
        }
      }
      case ROCKSDB_TREE -> columnMap = new V6SchemaCombinedTreeState(spec).getColumnMap();
      default -> columnMap = V6SchemaCombinedSnapshot.createV6(spec).getColumnMap();
    }

    return (byte[] id) -> {
      for (Map.Entry<String, KvStoreColumn<?, ?>> entry : columnMap.entrySet()) {
        if (Arrays.areEqual(entry.getValue().getId().toArray(), id)) {
          return entry.getKey();
        }
      }
      try {
        return new String(id, StandardCharsets.UTF_8);
      } catch (Throwable e) {
        SUB_COMMAND_LOG.error("failed to decode column name: " + e.getMessage());
      }
      return null;
    };
  }
}

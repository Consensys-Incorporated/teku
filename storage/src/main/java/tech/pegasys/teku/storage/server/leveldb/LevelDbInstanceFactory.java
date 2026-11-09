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

package tech.pegasys.teku.storage.server.leveldb;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class LevelDbInstanceFactory {
  public static KvStoreAccessor create(
      final MetricsSystem metricsSystem,
      final MetricCategory metricCategory,
      final KvStoreConfiguration configuration,
      final Collection<KvStoreColumn<?, ?>> columns,
      final Collection<Bytes> deletedColumns,
      final Collection<KvStoreVariable<?>> variables,
      final Collection<Bytes> deletedVariables)
      throws DatabaseStorageException {
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
    final Options options =
        new Options()
            .createIfMissing(true)
            .maxOpenFiles(configuration.getLeveldbMaxOpenFiles())
            .blockSize(configuration.getLeveldbBlockSize())
            .writeBufferSize(configuration.getLeveldbWriteBufferSize());

    try {
      final DB db =
          CustomJniDBFactory.FACTORY.open(configuration.getDatabaseDir().toFile(), options);
      return new LevelDbInstance(db, metricsSystem, metricCategory);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to open database", e);
    }
  }
}

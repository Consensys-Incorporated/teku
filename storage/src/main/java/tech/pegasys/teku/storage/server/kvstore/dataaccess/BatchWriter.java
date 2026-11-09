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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;

class BatchWriter implements AutoCloseable {
  private static final int OUTPUT_PER_TRANSACTION_COUNT = 15;
  final KvStoreAccessor db;

  private final Consumer<String> logger;
  private final long targetBatchSize;
  private KvStoreAccessor.KvStoreTransaction transaction = null;

  private long transactionCounter = 0;
  private long entryCounter = 0;
  private long bytes = 0;
  private final Optional<UInt64> maybeExpectedCount;

  BatchWriter(final int targetBatchSize, final Consumer<String> logger, final KvStoreAccessor db) {
    this(targetBatchSize, logger, db, Optional.empty());
  }

  /**
   * @param targetBatchSize target size (MB) for a batch
   * @param db database accessor object
   * @param maybeExpectedCount the expected number of objects across all objects
   */
  BatchWriter(
      final int targetBatchSize,
      final Consumer<String> logger,
      final KvStoreAccessor db,
      final Optional<UInt64> maybeExpectedCount) {
    // target batch size comes in MB, can store in Bytes to make life simpler for comparison
    this.targetBatchSize = targetBatchSize * 1_000_000L;
    this.logger = logger;
    this.db = db;
    this.maybeExpectedCount = maybeExpectedCount;
  }

  void startTransaction() {
    if (transaction == null) {
      transaction = db.startTransaction();
    }
  }

  KvStoreAccessor.KvStoreTransaction getTransaction() {
    return transaction;
  }

  void commit() {
    if (transaction != null) {
      transaction.commit();
      transaction.close();
      transaction = null;
    }
  }

  void add(final KvStoreColumn<?, ?> column, final ColumnEntry<Bytes, Bytes> entry) {
    startTransaction();

    bytes += entry.getKey().size() + entry.getValue().size();
    getTransaction().putRaw(column, entry.getKey(), entry.getValue());
    entryCounter++;
    if (bytes >= targetBatchSize) {
      commit();
      bytes = 0;
      transactionCounter++;
      if (transactionCounter % OUTPUT_PER_TRANSACTION_COUNT == 0L) {
        maybeExpectedCount.ifPresentOrElse(
            (expectedTotal) ->
                logger.accept(
                    String.format(
                        " -- %,d (%d %%)...",
                        entryCounter, (entryCounter * 100) / expectedTotal.longValue())),
            () -> logger.accept(String.format(" -- %,d...", entryCounter)));
      }
    }
  }

  @Override
  public void close() {
    try {
      commit();
      if (entryCounter > 0) {
        logger.accept(String.format(" => Inserted %,d entries...", entryCounter));
      }
    } catch (Exception ex) {
      logger.accept("Failed to commit transaction on close: " + ex.getCause());
    }
  }
}

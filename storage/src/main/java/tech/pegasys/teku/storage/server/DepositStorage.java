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

package tech.pegasys.teku.storage.server;

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;

public class DepositStorage implements Eth1DepositStorageChannel {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEPOSIT_REMOVAL_CHUNK = 1000;

  private final Database database;
  private final Supplier<SafeFuture<Boolean>> removeDepositsResult;

  private DepositStorage(final Database database) {
    this.database = database;
    this.removeDepositsResult = Suppliers.memoize(() -> SafeFuture.of(this::removeDeposits));
  }

  public static DepositStorage create(final Database database) {
    return new DepositStorage(database);
  }

  @Override
  public SafeFuture<Boolean> removeDepositEvents() {
    return removeDepositsResult.get();
  }

  private boolean removeDeposits() {
    LOG.debug("Pruning deposit events in database");
    boolean depositsRemoved = false;
    try (Stream<DepositsFromBlockEvent> eventStream = database.streamDepositsFromBlocks()) {
      Iterator<DepositsFromBlockEvent> eventIterator = eventStream.iterator();
      while (eventIterator.hasNext()) {
        final List<UInt64> blockNumbers = new ArrayList<>();
        for (int i = 0; i < DEPOSIT_REMOVAL_CHUNK && eventIterator.hasNext(); ++i) {
          blockNumbers.add(eventIterator.next().getBlockNumber());
        }
        if (!blockNumbers.isEmpty()) {
          depositsRemoved = true;
          database.removeDepositsFromBlockEvents(blockNumbers);
        }
      }
    }
    if (depositsRemoved) {
      LOG.info("Deposit events were successfully pruned in database");
    }
    return true;
  }
}

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

package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

/** Applies */
public class TickProcessor {

  private final Spec spec;
  private final RecentChainData recentChainData;

  private UInt64 highestProcessedTime = UInt64.ZERO;
  private UInt64 highestPendingTime = UInt64.ZERO;
  private SafeFuture<Void> lastUpdate = SafeFuture.COMPLETE;
  private SafeFuture<Void> nextUpdate = new SafeFuture<>();
  private boolean nextUpdateScheduled = false;

  public TickProcessor(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public synchronized SafeFuture<Void> onTick(final UInt64 currentTimeMillis) {
    if (currentTimeMillis.isLessThanOrEqualTo(highestProcessedTime)) {
      return lastUpdate;
    }
    highestPendingTime = highestPendingTime.max(currentTimeMillis);
    if (nextUpdateScheduled) {
      return nextUpdate;
    }

    nextUpdate = new SafeFuture<>();
    nextUpdateScheduled = true;
    lastUpdate.thenCompose(__ -> processOnTick()).propagateTo(nextUpdate);
    return nextUpdate;
  }

  private synchronized SafeFuture<Void> processOnTick() {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    spec.onTick(transaction, highestPendingTime);
    highestProcessedTime = highestPendingTime;
    nextUpdateScheduled = false;
    lastUpdate = transaction.commit();
    return lastUpdate;
  }
}

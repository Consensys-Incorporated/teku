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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class StoredLatestCanonicalBlockUpdater implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  final long slotsPerEpoch;

  private final RecentChainData recentChainData;

  public StoredLatestCanonicalBlockUpdater(final RecentChainData recentChainData, final Spec spec) {
    this.recentChainData = recentChainData;
    slotsPerEpoch = spec.getGenesisSpec().getSlotsPerEpoch();
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (!slot.mod(slotsPerEpoch).equals(ONE)) {
      return;
    }
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    recentChainData.getBestBlockRoot().ifPresent(transaction::setLatestCanonicalBlockRoot);
    transaction
        .commit()
        .finish(error -> LOG.error("Failed to store latest canonical block root", error));
  }

  long getSlotsPerEpoch() {
    return slotsPerEpoch;
  }
}

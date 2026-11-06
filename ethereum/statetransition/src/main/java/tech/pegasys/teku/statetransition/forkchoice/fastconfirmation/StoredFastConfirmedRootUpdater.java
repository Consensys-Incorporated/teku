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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

/**
 * Persists the confirmed root so that {@link FastConfirmationTracker} can restore it as {@code
 * get_root_confirmed_before_restart} after a restart.
 *
 * <p>Only writes when the root changes, and never writes the finalized root: a restarted node
 * starts from at least its finalized block anyway, and until the node is in sync and the root is
 * restored the rule reports the finalized root, which would otherwise overwrite the root confirmed
 * before the restart.
 */
public class StoredFastConfirmedRootUpdater implements FastConfirmationEventChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final AtomicReference<Bytes32> lastStoredRoot = new AtomicReference<>();

  public StoredFastConfirmedRootUpdater(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  @Override
  public void onFastConfirmation(
      final Bytes32 confirmedRoot, final UInt64 confirmedSlot, final UInt64 currentSlot) {
    final boolean isFinalizedRoot =
        recentChainData
            .getFinalizedCheckpoint()
            .map(checkpoint -> checkpoint.getRoot().equals(confirmedRoot))
            .orElse(false);
    if (isFinalizedRoot || confirmedRoot.equals(lastStoredRoot.get())) {
      return;
    }
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setFastConfirmedRoot(confirmedRoot);
    transaction.commit().finish(error -> LOG.error("Failed to store fast confirmed root", error));
    lastStoredRoot.set(confirmedRoot);
  }
}

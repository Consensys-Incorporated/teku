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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import java.util.Optional;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.beacon.sync.forward.multipeer.Sync.SyncProgress;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SinglePeerSyncService extends Service implements ForwardSyncService {

  private final SyncManager syncManager;
  private final RecentChainData recentChainData;

  public SinglePeerSyncService(
      final SyncManager syncManager, final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
    this.syncManager = syncManager;
  }

  @Override
  protected SafeFuture<?> doStart() {
    // We shouldn't start syncing until we have reached genesis.
    // There are also no valid blocks until we've reached genesis so no point in gossipping and
    // queuing them
    recentChainData.subscribeStoreInitialized(() -> syncManager.start().finishStackTrace());
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return syncManager.stop();
  }

  @Override
  public SyncingStatus getSyncStatus() {
    return syncManager.getSyncStatus();
  }

  @Override
  public SafeFuture<Optional<SyncProgress>> getSyncProgress() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public boolean isSyncActive() {
    return syncManager.isSyncActive();
  }

  @Override
  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return syncManager.subscribeToSyncChanges(subscriber);
  }

  @Override
  public void unsubscribeFromSyncChanges(final long subscriberId) {
    syncManager.unsubscribeFromSyncChanges(subscriberId);
  }
}

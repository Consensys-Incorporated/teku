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

package tech.pegasys.teku.api;

import java.util.function.IntSupplier;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;

public class SyncDataProvider {

  private final SyncService syncService;
  private final IntSupplier rejectedExecutionSupplier;

  public SyncDataProvider(
      final SyncService syncService, final IntSupplier rejectedExecutionSupplier) {
    this.syncService = syncService;
    this.rejectedExecutionSupplier = rejectedExecutionSupplier;
  }

  public SyncingStatus getSyncingStatus() {
    return syncService.getSyncStatus();
  }

  public int getRejectedExecutionCount() {
    return rejectedExecutionSupplier.getAsInt();
  }

  public long subscribeToSyncStateChanges(final SyncStateProvider.SyncStateSubscriber subscriber) {
    return syncService.subscribeToSyncStateChanges(subscriber);
  }

  public boolean unsubscribeFromSyncStateChanges(final long subscriberId) {
    return syncService.unsubscribeFromSyncStateChanges(subscriberId);
  }

  // Sync state can be influenced by needing to sync from nodes, but also from
  // starting up and having a lack of peers. Consider both 'syncing' and 'startup'
  // as 'isSyncing', as both of these states will mean that you can't perform duties
  public boolean isSyncing() {
    return !syncService.getCurrentSyncState().isInSync();
  }

  public SyncState getCurrentSyncState() {
    return syncService.getCurrentSyncState();
  }
}

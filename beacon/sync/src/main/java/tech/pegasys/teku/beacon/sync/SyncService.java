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

package tech.pegasys.teku.beacon.sync;

import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetcher;
import tech.pegasys.teku.beacon.sync.gossip.executionpayloads.RecentExecutionPayloadsFetcher;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;

public interface SyncService extends SyncStateProvider {
  SafeFuture<?> start();

  SafeFuture<?> stop();

  ForwardSync getForwardSync();

  OptimisticHeadSubscriber getOptimisticSyncSubscriber();

  RecentBlocksFetcher getRecentBlocksFetcher();

  RecentBlobSidecarsFetcher getRecentBlobSidecarsFetcher();

  RecentExecutionPayloadsFetcher getRecentExecutionPayloadsFetcher();

  default boolean isSyncActive() {
    return getForwardSync().isSyncActive();
  }

  default SyncingStatus getSyncStatus() {
    return getForwardSync().getSyncStatus();
  }
}

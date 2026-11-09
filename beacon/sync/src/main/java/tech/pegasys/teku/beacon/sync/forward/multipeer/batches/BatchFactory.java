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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;

public class BatchFactory {

  private final EventThread eventThread;
  private final Spec spec;
  private final BlobSidecarManager blobSidecarManager;
  private final ConflictResolutionStrategy conflictResolutionStrategy;

  public BatchFactory(
      final EventThread eventThread,
      final Spec spec,
      final BlobSidecarManager blobSidecarManager,
      final ConflictResolutionStrategy conflictResolutionStrategy) {
    this.eventThread = eventThread;
    this.spec = spec;
    this.blobSidecarManager = blobSidecarManager;
    this.conflictResolutionStrategy = conflictResolutionStrategy;
  }

  public Batch createBatch(final TargetChain chain, final UInt64 start, final UInt64 count) {
    eventThread.checkOnEventThread();
    final SyncSourceSelector syncSourceProvider = chain::selectRandomPeer;
    return new EventThreadOnlyBatch(
        eventThread,
        new SyncSourceBatch(
            eventThread,
            spec,
            blobSidecarManager,
            syncSourceProvider,
            conflictResolutionStrategy,
            chain,
            start,
            count));
  }
}

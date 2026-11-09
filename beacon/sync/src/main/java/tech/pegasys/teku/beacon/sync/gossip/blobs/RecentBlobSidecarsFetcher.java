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

package tech.pegasys.teku.beacon.sync.gossip.blobs;

import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.ServiceFacade;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;

public interface RecentBlobSidecarsFetcher extends ServiceFacade {

  RecentBlobSidecarsFetcher NOOP =
      new RecentBlobSidecarsFetcher() {
        @Override
        public void subscribeBlobSidecarFetched(BlobSidecarSubscriber subscriber) {}

        @Override
        public void requestRecentBlobSidecar(BlobIdentifier blobIdentifier) {}

        @Override
        public void cancelRecentBlobSidecarRequest(BlobIdentifier blobIdentifier) {}

        @Override
        public SafeFuture<?> start() {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<?> stop() {
          return SafeFuture.COMPLETE;
        }

        @Override
        public boolean isRunning() {
          return false;
        }
      };

  static RecentBlobSidecarsFetcher create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ForwardSyncService forwardSyncService,
      final FetchTaskFactory fetchTaskFactory) {
    final RecentBlobSidecarsFetcher recentBlobSidecarsFetcher;
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      recentBlobSidecarsFetcher =
          RecentBlobSidecarsFetchService.create(
              asyncRunner,
              blockBlobSidecarsTrackersPool,
              forwardSyncService,
              fetchTaskFactory,
              spec);
    } else {
      recentBlobSidecarsFetcher = RecentBlobSidecarsFetcher.NOOP;
    }

    return recentBlobSidecarsFetcher;
  }

  void subscribeBlobSidecarFetched(BlobSidecarSubscriber subscriber);

  void requestRecentBlobSidecar(BlobIdentifier blobIdentifier);

  void cancelRecentBlobSidecarRequest(BlobIdentifier blobIdentifier);
}

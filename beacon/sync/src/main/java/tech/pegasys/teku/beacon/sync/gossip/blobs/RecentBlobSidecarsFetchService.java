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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlobSidecarTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;

public class RecentBlobSidecarsFetchService
    extends AbstractFetchService<BlobIdentifier, FetchBlobSidecarTask, BlobSidecar>
    implements RecentBlobSidecarsFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ForwardSync forwardSync;
  private final FetchTaskFactory fetchTaskFactory;

  private final Subscribers<BlobSidecarSubscriber> blobSidecarSubscribers =
      Subscribers.create(true);

  RecentBlobSidecarsFetchService(
      final AsyncRunner asyncRunner,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final int maxConcurrentRequests) {
    super(asyncRunner, maxConcurrentRequests);
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.forwardSync = forwardSync;
    this.fetchTaskFactory = fetchTaskFactory;
  }

  static RecentBlobSidecarsFetchService create(
      final AsyncRunner asyncRunner,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final Spec spec) {
    final int maxConcurrentRequests =
        DEFAULT_MAX_CONCURRENT_BLOCKS_REQUESTS
            * spec.getMaxBlobsPerBlockForHighestMilestone().orElse(1);
    return new RecentBlobSidecarsFetchService(
        asyncRunner,
        blockBlobSidecarsTrackersPool,
        forwardSync,
        fetchTaskFactory,
        maxConcurrentRequests);
  }

  @Override
  protected SafeFuture<?> doStart() {
    setupSubscribers();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public void subscribeBlobSidecarFetched(final BlobSidecarSubscriber subscriber) {
    blobSidecarSubscribers.subscribe(subscriber);
  }

  @Override
  public void requestRecentBlobSidecar(final BlobIdentifier blobIdentifier) {
    if (forwardSync.isSyncActive()) {
      // Forward sync already in progress, assume it will fetch any missing blob sidecars
      return;
    }
    if (blockBlobSidecarsTrackersPool.containsBlobSidecar(blobIdentifier)) {
      // We've already got this blob sidecar
      return;
    }
    final FetchBlobSidecarTask task = createTask(blobIdentifier);
    if (allTasks.putIfAbsent(blobIdentifier, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue blob sidecar to be fetched: {}", blobIdentifier);
    queueTask(task);
  }

  @Override
  public void cancelRecentBlobSidecarRequest(final BlobIdentifier blobIdentifier) {
    cancelRequest(blobIdentifier);
  }

  private void setupSubscribers() {
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecar(this::requestRecentBlobSidecar);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecarDropped(
        this::cancelRecentBlobSidecarRequest);
    forwardSync.subscribeToSyncChanges(this::onSyncStatusChanged);
  }

  private void onSyncStatusChanged(final boolean syncActive) {
    if (syncActive) {
      return;
    }
    // Ensure we are requesting the blob sidecars not already filled in by the sync
    // We may have ignored these requested blob sidecars while the sync was in progress
    blockBlobSidecarsTrackersPool
        .getAllRequiredBlobSidecars()
        .forEach(this::requestRecentBlobSidecar);
  }

  @Override
  public FetchBlobSidecarTask createTask(final BlobIdentifier key) {
    return fetchTaskFactory.createFetchBlobSidecarTask(key);
  }

  @Override
  public void processFetchedResult(final FetchBlobSidecarTask task, final BlobSidecar result) {
    LOG.trace("Successfully fetched blob sidecar: {}", result);
    blobSidecarSubscribers.forEach(s -> s.onBlobSidecar(result));
    // After retrieved blob sidecar has been processed, stop tracking it
    removeTask(task);
  }
}

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

package tech.pegasys.teku.beacon.sync.gossip.blocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;
import tech.pegasys.teku.statetransition.util.PendingPool;

public class RecentBlocksFetchService
    extends AbstractFetchService<Bytes32, FetchBlockTask, SignedBeaconBlock>
    implements RecentBlocksFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private final ForwardSync forwardSync;
  private final PendingPool<SignedBeaconBlock> pendingBlockPool;
  private final PendingPool<ValidatableAttestation> pendingAttestationsPool;
  private final PendingPool<PayloadAttestationMessage> pendingPayloadAttestationsPool;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final FetchTaskFactory fetchTaskFactory;
  private final Subscribers<BlockSubscriber> blockSubscribers = Subscribers.create(true);
  private final DasSamplerBasic dasBasicSampler;

  RecentBlocksFetchService(
      final AsyncRunner asyncRunner,
      final PendingPool<SignedBeaconBlock> pendingBlockPool,
      final PendingPool<ValidatableAttestation> pendingAttestationsPool,
      final PendingPool<PayloadAttestationMessage> pendingPayloadAttestationsPool,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final DasSamplerBasic dasBasicSampler,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final int maxConcurrentRequests) {
    super(asyncRunner, maxConcurrentRequests);
    this.forwardSync = forwardSync;
    this.pendingBlockPool = pendingBlockPool;
    this.pendingAttestationsPool = pendingAttestationsPool;
    this.pendingPayloadAttestationsPool = pendingPayloadAttestationsPool;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.dasBasicSampler = dasBasicSampler;
    this.fetchTaskFactory = fetchTaskFactory;
  }

  public static RecentBlocksFetchService create(
      final AsyncRunner asyncRunner,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final PendingPool<ValidatableAttestation> pendingAttestations,
      final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final DasSamplerBasic dasBasicSampler,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory) {
    return new RecentBlocksFetchService(
        asyncRunner,
        pendingBlocksPool,
        pendingAttestations,
        pendingPayloadAttestations,
        blockBlobSidecarsTrackersPool,
        dasBasicSampler,
        forwardSync,
        fetchTaskFactory,
        DEFAULT_MAX_CONCURRENT_BLOCKS_REQUESTS);
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
  public void subscribeBlockFetched(final BlockSubscriber subscriber) {
    blockSubscribers.subscribe(subscriber);
  }

  @Override
  public void requestRecentBlock(final Bytes32 blockRoot) {
    if (forwardSync.isSyncActive()) {
      // Forward sync already in progress, assume it will fetch any missing blocks
      return;
    }
    if (pendingBlockPool.contains(blockRoot)) {
      // We've already got this block
      return;
    }
    if (blockBlobSidecarsTrackersPool.containsBlock(blockRoot)) {
      // We already have this block, waiting for blobs
      return;
    }
    if (dasBasicSampler.containsBlock(blockRoot)) {
      // We already have this block in DAS sampler
      return;
    }
    final FetchBlockTask task = createTask(blockRoot);
    if (allTasks.putIfAbsent(blockRoot, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue block to be fetched: {}", blockRoot);
    queueTask(task);
  }

  @Override
  public void cancelRecentBlockRequest(final Bytes32 blockRoot) {
    cancelRequest(blockRoot);
  }

  @Override
  public FetchBlockTask createTask(final Bytes32 key) {
    return fetchTaskFactory.createFetchBlockTask(key);
  }

  @Override
  public void processFetchedResult(final FetchBlockTask task, final SignedBeaconBlock block) {
    LOG.trace("Successfully fetched block: {}", block);
    blockSubscribers.forEach(s -> s.onBlock(block));
    // After retrieved block has been processed, stop tracking it
    removeTask(task);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    cancelRecentBlockRequest(block.getRoot());
  }

  private void setupSubscribers() {
    pendingAttestationsPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    pendingAttestationsPool.subscribeRequiredBlockRootDropped(this::cancelRecentBlockRequest);
    pendingBlockPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    pendingBlockPool.subscribeRequiredBlockRootDropped(this::cancelRecentBlockRequest);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlockRootDropped(this::cancelRecentBlockRequest);
    pendingPayloadAttestationsPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    pendingPayloadAttestationsPool.subscribeRequiredBlockRootDropped(
        this::cancelRecentBlockRequest);
    forwardSync.subscribeToSyncChanges(this::onSyncStatusChanged);
  }

  private void onSyncStatusChanged(final boolean syncActive) {
    if (syncActive) {
      return;
    }
    // Ensure we are requesting the parents of any pending blocks not already filled in by the sync
    // We may have ignored these requested blocks while the sync was in progress
    pendingBlockPool.getAllRequiredBlockRoots().forEach(this::requestRecentBlock);
  }
}

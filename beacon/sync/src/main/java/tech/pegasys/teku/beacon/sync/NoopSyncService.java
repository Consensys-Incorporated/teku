/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.multipeer.Sync.SyncProgress;
import tech.pegasys.teku.beacon.sync.gossip.blobs.BlobSidecarSubscriber;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.BlockSubscriber;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetcher;
import tech.pegasys.teku.beacon.sync.gossip.executionpayloads.ExecutionPayloadSubscriber;
import tech.pegasys.teku.beacon.sync.gossip.executionpayloads.RecentExecutionPayloadsFetcher;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;

public class NoopSyncService
    implements ForwardSync,
        RecentBlocksFetcher,
        RecentBlobSidecarsFetcher,
        RecentExecutionPayloadsFetcher,
        SyncService,
        OptimisticHeadSubscriber {

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public SafeFuture<?> stop() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public ForwardSync getForwardSync() {
    return this;
  }

  @Override
  public RecentBlocksFetcher getRecentBlocksFetcher() {
    return this;
  }

  @Override
  public RecentBlobSidecarsFetcher getRecentBlobSidecarsFetcher() {
    return this;
  }

  @Override
  public RecentExecutionPayloadsFetcher getRecentExecutionPayloadsFetcher() {
    return this;
  }

  @Override
  public SyncingStatus getSyncStatus() {
    return new SyncingStatus(false, ZERO);
  }

  @Override
  public SafeFuture<Optional<SyncProgress>> getSyncProgress() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public boolean isSyncActive() {
    return false;
  }

  @Override
  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return 0;
  }

  @Override
  public long subscribeToSyncStateChangesAndUpdate(final SyncStateSubscriber subscriber) {
    final long subscriptionId = subscribeToSyncStateChanges(subscriber);
    subscriber.onSyncStateChange(getCurrentSyncState());
    return subscriptionId;
  }

  @Override
  public void unsubscribeFromSyncChanges(final long subscriberId) {}

  @Override
  public void subscribeBlockFetched(final BlockSubscriber subscriber) {}

  @Override
  public void requestRecentBlock(final Bytes32 blockRoot) {
    // No-op
  }

  @Override
  public void cancelRecentBlockRequest(final Bytes32 blockRoot) {
    // No-op
  }

  @Override
  public void subscribeBlobSidecarFetched(final BlobSidecarSubscriber subscriber) {
    // No-op
  }

  @Override
  public void requestRecentBlobSidecar(final BlobIdentifier blobIdentifier) {
    // No-op
  }

  @Override
  public void cancelRecentBlobSidecarRequest(final BlobIdentifier blobIdentifier) {
    // No-op
  }

  @Override
  public void subscribeExecutionPayloadFetched(final ExecutionPayloadSubscriber subscriber) {
    // No-op
  }

  @Override
  public void requestRecentExecutionPayload(final Bytes32 beaconBlockRoot) {
    // No-op
  }

  @Override
  public void cancelRecentExecutionPayloadRequest(final Bytes32 beaconBlockRoot) {
    // No-op
  }

  @Override
  public void onOptimisticHeadChanged(final boolean isSyncingOptimistically) {
    // No-op
  }

  @Override
  public OptimisticHeadSubscriber getOptimisticSyncSubscriber() {
    return this;
  }

  @Override
  public SyncState getCurrentSyncState() {
    return SyncState.IN_SYNC;
  }

  @Override
  public long subscribeToSyncStateChanges(final SyncStateSubscriber subscriber) {
    return 0;
  }

  @Override
  public boolean unsubscribeFromSyncStateChanges(final long subscriberId) {
    return false;
  }

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // No-op
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    // No-op
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {
    // No-op
  }

  @Override
  public void onExecutionPayloadAvailable(final SignedExecutionPayloadEnvelope executionPayload) {
    // No-op
  }

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    // No-op
  }
}

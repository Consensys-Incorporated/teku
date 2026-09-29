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

package tech.pegasys.teku.storage.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.TaskQueue;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public class ThrottlingStorageQueryChannel implements StorageQueryChannel {

  private final StorageQueryChannel delegate;
  private final TaskQueue taskQueue;

  public ThrottlingStorageQueryChannel(
      final StorageQueryChannel delegate,
      final int maxConcurrentQueries,
      final int maximumQueueSize,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        ThrottlingTaskQueue.create(
            maxConcurrentQueries,
            maximumQueueSize,
            metricsSystem,
            TekuMetricCategory.STORAGE,
            "throttling_storage_query_queue_size",
            "throttling_storage_query_rejected");
  }

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    return taskQueue.queueTask(delegate::onStoreRequest);
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return taskQueue.queueTask(delegate::getWeakSubjectivityState);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return taskQueue.queueTask(delegate::getEarliestAvailableBlockSlot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return taskQueue.queueTask(delegate::getEarliestAvailableBlock);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getBlockByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getHotBlockAndStateByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getHotStateAndBlockSummaryByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return taskQueue.queueTask(() -> delegate.getHotBlocksByRoot(blockRoots));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>>
      getBlindedExecutionPayloadEnvelopesByBlockRoot(final Set<Bytes32> blockRoots) {
    return taskQueue.queueTask(
        () -> delegate.getBlindedExecutionPayloadEnvelopesByBlockRoot(blockRoots));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getBlobSidecarsBySlotAndBlockRoot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return taskQueue.queueTask(() -> delegate.getBlobSidecarsBySlotAndBlockRoot(slotAndBlockRoot));
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return taskQueue.queueTask(() -> delegate.getSlotAndBlockRootByStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getLatestFinalizedStateAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getLatestAvailableFinalizedState(slot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByBlockRoot(final Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedSlotByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedStateByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedSlotByStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<Bytes32>> getLatestCanonicalBlockRoot() {
    return taskQueue.queueTask(delegate::getLatestCanonicalBlockRoot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getCustodyGroupCount() {
    return taskQueue.queueTask(delegate::getCustodyGroupCount);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getNonCanonicalBlockByRoot(
      final Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getNonCanonicalBlockByRoot(blockRoot));
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getNonCanonicalBlocksBySlot(slot));
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return taskQueue.queueTask(delegate::getAnchor);
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return taskQueue.queueTask(delegate::getFinalizedDepositSnapshot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return taskQueue.queueTask(delegate::getEarliestAvailableBlobSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return taskQueue.queueTask(() -> delegate.getBlobSidecar(key));
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getNonCanonicalBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key) {
    return taskQueue.queueTask(() -> delegate.getNonCanonicalBlobSidecar(key));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getBlobSidecarKeys(slot));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getAllBlobSidecarKeys(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getAllBlobSidecarKeys(slot));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot, final long limit) {
    return taskQueue.queueTask(() -> delegate.getBlobSidecarKeys(startSlot, endSlot, limit));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return taskQueue.queueTask(() -> delegate.getBlobSidecarKeys(slotAndBlockRoot));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return taskQueue.queueTask(() -> delegate.getArchivedBlobSidecars(slotAndBlockRoot));
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getArchivedBlobSidecars(slot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return taskQueue.queueTask(delegate::getFirstCustodyIncompleteSlot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot() {
    return taskQueue.queueTask(delegate::getEarliestAvailableDataColumnSlot);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return taskQueue.queueTask(() -> delegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return taskQueue.queueTask(() -> delegate.getNonCanonicalSidecar(identifier));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getDataColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getNonCanonicalDataColumnIdentifiers(
      final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getNonCanonicalDataColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot, final UInt64 limit) {
    return taskQueue.queueTask(() -> delegate.getDataColumnIdentifiers(startSlot, endSlot, limit));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestDataColumnSidecarSlot() {
    return taskQueue.queueTask(delegate::getEarliestDataColumnSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<List<List<KZGProof>>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getDataColumnSidecarsProofs(slot));
  }

  @Override
  public SafeFuture<List<StoredLightClientUpdate>> getBestLightClientUpdates() {
    return taskQueue.queueTask(delegate::getBestLightClientUpdates);
  }
}

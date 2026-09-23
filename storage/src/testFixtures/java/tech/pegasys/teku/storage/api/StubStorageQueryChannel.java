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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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

public class StubStorageQueryChannel implements StorageQueryChannel {

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return SafeFuture.completedFuture(WeakSubjectivityState.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return SafeFuture.completedFuture(Collections.emptyMap());
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(final UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<Bytes32>> getLatestCanonicalBlockRoot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getCustodyGroupCount() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getNonCanonicalBlockByRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    return SafeFuture.completedFuture(new ArrayList<>());
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getNonCanonicalBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(final UInt64 slot) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getAllBlobSidecarKeys(final UInt64 slot) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot, final long limit) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(final UInt64 slot) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<List<BlobSidecar>> getBlobSidecarsBySlotAndBlockRoot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(final UInt64 slot) {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getNonCanonicalDataColumnIdentifiers(
      final UInt64 slot) {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot, final UInt64 limit) {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestDataColumnSidecarSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<List<List<KZGProof>>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>>
      getBlindedExecutionPayloadEnvelopesByBlockRoot(final Set<Bytes32> blockRoots) {
    return SafeFuture.completedFuture(Collections.emptyMap());
  }

  @Override
  public SafeFuture<List<StoredLightClientUpdate>> getBestLightClientUpdates() {
    return SafeFuture.completedFuture(List.of());
  }
}

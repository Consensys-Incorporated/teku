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

package tech.pegasys.teku.storage.server.noop;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.server.Database;

public class NoOpDatabase implements Database {

  @Override
  public void storeInitialAnchor(final AnchorPoint initialAnchor) {}

  @Override
  public UpdateResult update(final StorageUpdate event) {
    return new UpdateResult(Optional.empty());
  }

  @Override
  public void storeFinalizedBlocks(
      final Collection<SignedBeaconBlock> blocks,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {}

  @Override
  public void storeReconstructedFinalizedState(final BeaconState state, final Bytes32 blockRoot) {}

  @Override
  public void updateWeakSubjectivityState(final WeakSubjectivityUpdate weakSubjectivityUpdate) {}

  @Override
  public Optional<OnDiskStoreData> createMemoryStore() {
    return Optional.empty();
  }

  @Override
  public WeakSubjectivityState getWeakSubjectivityState() {
    return WeakSubjectivityState.empty();
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return Collections.emptyMap();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes32> getLatestCanonicalBlockRoot() {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getCustodyGroupCount() {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getLastAvailableFinalizedBlock() {
    return Optional.empty();
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes32> getFinalizedBlockRootBySlot(final UInt64 slot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return Optional.empty();
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return Optional.empty();
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return Collections.emptyMap();
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloadEnvelope(
      final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloadEnvelopes(
      final Set<Bytes32> blockRoots) {
    return Collections.emptyMap();
  }

  @Override
  public Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz() {
    return Stream.empty();
  }

  @Override
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return Stream.empty();
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return Optional.empty();
  }

  @Override
  public Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints() {
    return Stream.empty();
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    return Collections.emptyList();
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {}

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return Optional.empty();
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {}

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return Optional.empty();
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots() {
    return Stream.empty();
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getNonCanonicalBlockByRoot(final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return new ArrayList<>();
  }

  @Override
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return Stream.empty();
  }

  @Override
  public Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return Stream.empty();
  }

  @Override
  public Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot() {
    return Optional.empty();
  }

  @Override
  public void setFinalizedDepositSnapshot(final DepositTreeSnapshot finalizedDepositSnapshot) {}

  @Override
  public UInt64 pruneFinalizedBlocks(
      final UInt64 lastSlotToPrune, final int pruneLimit, final UInt64 checkpointInitialSlot) {
    return lastSlotToPrune;
  }

  @Override
  public Optional<UInt64> pruneFinalizedStates(
      final Optional<UInt64> lastPrunedSlot,
      final UInt64 lastSlotToPruneStateFor,
      final long pruneLimit) {
    return Optional.of(lastSlotToPruneStateFor);
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {}

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {}

  @Override
  public void removeDepositsFromBlockEvents(final List<UInt64> blockNumbers) {}

  @Override
  public void storeVotes(final Map<UInt64, VoteTracker> votes) {}

  @Override
  public Map<String, Long> getColumnCounts(final Optional<String> maybeColumnFilter) {
    return new HashMap<>();
  }

  @Override
  public Map<String, Optional<String>> getVariables() {
    return new HashMap<>();
  }

  @Override
  public long getBlobSidecarColumnCount() {
    return 0L;
  }

  @Override
  public long getSidecarColumnCount() {
    return 0;
  }

  @Override
  public long getNonCanonicalBlobSidecarColumnCount() {
    return 0L;
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return Optional.empty();
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return Optional.empty();
  }

  @Override
  public void deleteHotBlocks(final Set<Bytes32> blockRootsToDelete) {}

  @Override
  public void storeBlobSidecar(final BlobSidecar blobSidecar) {}

  @Override
  public void storeNonCanonicalBlobSidecar(final BlobSidecar blobSidecar) {}

  @Override
  public Optional<BlobSidecar> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return Optional.empty();
  }

  @Override
  public Optional<BlobSidecar> getNonCanonicalBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return Optional.empty();
  }

  @Override
  public boolean pruneOldestBlobSidecars(
      final UInt64 lastSlotToPrune,
      final int pruneLimit,
      final BlobSidecarsArchiver blobSidecarsArchiver) {
    return false;
  }

  @Override
  public boolean pruneOldestNonCanonicalBlobSidecars(
      final UInt64 lastSlotToPrune,
      final int pruneLimit,
      final BlobSidecarsArchiver blobSidecarsArchiver) {
    return false;
  }

  @Override
  public Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return Stream.empty();
  }

  @Override
  public Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return Stream.empty();
  }

  @Override
  public Stream<BlobSidecar> streamBlobSidecars(final SlotAndBlockRoot slotAndBlockRoot) {
    return Stream.empty();
  }

  @Override
  public List<SlotAndBlockRootAndBlobIndex> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return Collections.emptyList();
  }

  @Override
  public Optional<UInt64> getEarliestBlobSidecarSlot() {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getFirstCustodyIncompleteSlot() {
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecar> getSidecar(final DataColumnSlotAndIdentifier identifier) {
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecar> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return Optional.empty();
  }

  @Override
  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    return Stream.empty();
  }

  @Override
  public Stream<DataColumnSlotAndIdentifier> streamNonCanonicalDataColumnIdentifiers(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    return Stream.empty();
  }

  @Override
  public Optional<UInt64> getEarliestDataColumnSidecarSlot() {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getEarliestAvailableDataColumnSlot() {
    return Optional.empty();
  }

  @Override
  public void setEarliestAvailableDataColumnSlot(final UInt64 slot) {}

  @Override
  public Optional<UInt64> getLastDataColumnSidecarsProofsSlot() {
    return Optional.empty();
  }

  @Override
  public Optional<List<List<KZGProof>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return Optional.empty();
  }

  @Override
  public void setFirstCustodyIncompleteSlot(final UInt64 slot) {}

  @Override
  public void addSidecar(final DataColumnSidecar sidecar) {}

  @Override
  public void addNonCanonicalSidecar(final DataColumnSidecar sidecar) {}

  @Override
  public void pruneAllSidecars(final UInt64 tillSlotInclusive, final int pruneLimit) {}

  @Override
  public void compactStorage() {}

  @Override
  public void archiveSidecarsProofs(final UInt64 startSlot, final UInt64 tillSlotInclusive) {}

  @Override
  public void close() {}
}

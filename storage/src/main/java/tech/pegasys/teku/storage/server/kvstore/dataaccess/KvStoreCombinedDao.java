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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;

public interface KvStoreCombinedDao extends AutoCloseable {

  Bytes32 MIN_BLOCK_ROOT = Bytes32.ZERO;
  Bytes32 MAX_BLOCK_ROOT = Bytes32.ZERO.not();

  @MustBeClosed
  HotUpdater hotUpdater();

  @MustBeClosed
  FinalizedUpdater finalizedUpdater();

  @MustBeClosed
  CombinedUpdater combinedUpdater();

  Optional<SignedBeaconBlock> getHotBlock(Bytes32 root);

  Optional<Bytes> getHotBlockAsSsz(Bytes32 root);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamHotBlocks();

  Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz();

  Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(UInt64 slot);

  Optional<UInt64> getEarliestFinalizedBlockSlot();

  Optional<SignedBeaconBlock> getEarliestFinalizedBlock();

  Optional<UInt64> getEarliestFinalizedStateSlot();

  Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(UInt64 slot);

  List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(UInt64 slot);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamFinalizedBlocks(UInt64 startSlot, UInt64 endSlot);

  Optional<UInt64> getSlotForFinalizedBlockRoot(Bytes32 blockRoot);

  Optional<UInt64> getSlotForFinalizedStateRoot(Bytes32 stateRoot);

  Optional<Bytes32> getLatestCanonicalBlockRoot();

  Optional<UInt64> getCustodyGroupCount();

  Optional<SignedBeaconBlock> getNonCanonicalBlock(Bytes32 root);

  void ingest(KvStoreCombinedDao dao, int batchSize, Consumer<String> logger);

  Optional<UInt64> getGenesisTime();

  Optional<Checkpoint> getAnchor();

  Optional<Checkpoint> getJustifiedCheckpoint();

  Optional<Checkpoint> getBestJustifiedCheckpoint();

  Optional<Checkpoint> getFinalizedCheckpoint();

  // In hot dao because it must be in sync with the finalized checkpoint
  Optional<BeaconState> getLatestFinalizedState();

  Optional<Checkpoint> getWeakSubjectivityCheckpoint();

  Optional<BlockCheckpoints> getHotBlockCheckpointEpochs(Bytes32 root);

  Optional<BeaconState> getHotState(Bytes32 root);

  Optional<SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloadEnvelope(Bytes32 root);

  List<Bytes32> getStateRootsBeforeSlot(UInt64 slot);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(Bytes32 stateRoot);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(Bytes32 stateRoot);

  Map<UInt64, VoteTracker> getVotes();

  @MustBeClosed
  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  @MustBeClosed
  Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints();

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  Optional<BeaconState> getLatestAvailableFinalizedState(UInt64 maxSlot);

  @MustBeClosed
  Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots();

  @MustBeClosed
  Stream<Map.Entry<Bytes32, UInt64>> getFinalizedBlockRoots();

  Set<Bytes32> getNonCanonicalBlockRootsAtSlot(UInt64 slot);

  Optional<UInt64> getOptimisticTransitionBlockSlot();

  Optional<Bytes> getBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  Optional<Bytes> getNonCanonicalBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  @MustBeClosed
  Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<Bytes> streamBlobSidecars(SlotAndBlockRoot slotAndBlockRoot);

  List<SlotAndBlockRootAndBlobIndex> getBlobSidecarKeys(SlotAndBlockRoot slotAndBlockRoot);

  List<SlotAndBlockRootAndBlobIndex> getNonCanonicalBlobSidecarKeys(
      SlotAndBlockRoot slotAndBlockRoot);

  Optional<UInt64> getEarliestBlobSidecarSlot();

  Map<String, Long> getColumnCounts(final Optional<String> maybeColumnFilter);

  Map<String, Optional<String>> getVariables();

  long getBlobSidecarColumnCount();

  long getSidecarColumnCount();

  long getNonCanonicalBlobSidecarColumnCount();

  @MustBeClosed
  Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot);

  Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot();

  Optional<UInt64> getFirstCustodyIncompleteSlot();

  Optional<Bytes> getSidecar(DataColumnSlotAndIdentifier identifier);

  Optional<Bytes> getNonCanonicalSidecar(DataColumnSlotAndIdentifier identifier);

  @MustBeClosed
  Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(UInt64 startSlot, UInt64 endSlot);

  @MustBeClosed
  Stream<DataColumnSlotAndIdentifier> streamNonCanonicalDataColumnIdentifiers(
      UInt64 startSlot, UInt64 endSlot);

  List<DataColumnSlotAndIdentifier> getDataColumnIdentifiers(SlotAndBlockRoot slotAndBlockRoot);

  Optional<UInt64> getEarliestAvailableDataColumnSlot();

  Optional<UInt64> getLastDataColumnSidecarPrunedSlot();

  Optional<UInt64> getLastDataColumnSidecarsProofsSlot();

  Optional<List<List<KZGProof>>> getDataColumnSidecarsProofs(UInt64 slot);

  @MustBeClosed
  Stream<ColumnEntry<UInt64, LightClientUpdate>> streamBestLightClientUpdates();

  @MustBeClosed
  Stream<UInt64> streamBestLightClientUpdatePeriods();

  Optional<Bytes32> getBestLightClientUpdateSignatureBlockRoot(UInt64 period);

  /** Triggers a full, blocking compaction of the underlying storage to reclaim freed space. */
  void compact();

  interface CombinedUpdater extends HotUpdater, FinalizedUpdater {}

  interface HotUpdater extends AutoCloseable {
    void addHotBlock(BlockAndCheckpoints blockAndCheckpointEpochs);

    default void addHotBlocks(final Map<Bytes32, BlockAndCheckpoints> blocks) {
      blocks.values().forEach(this::addHotBlock);
    }

    void deleteHotBlock(Bytes32 blockRoot);

    void deleteHotBlockOnly(Bytes32 blockRoot);

    void setGenesisTime(UInt64 genesisTime);

    void setAnchor(Checkpoint anchorPoint);

    void setJustifiedCheckpoint(Checkpoint checkpoint);

    void setBestJustifiedCheckpoint(Checkpoint checkpoint);

    void setFinalizedCheckpoint(Checkpoint checkpoint);

    void setLatestCanonicalBlockRoot(Bytes32 canonicalBlockRoot);

    void setCustodyGroupCount(UInt64 custodyGroupCount);

    void addBestLightClientUpdate(
        UInt64 period, LightClientUpdate update, Bytes32 signatureBlockRoot);

    void removeBestLightClientUpdate(UInt64 period);

    void setWeakSubjectivityCheckpoint(Checkpoint checkpoint);

    void clearWeakSubjectivityCheckpoint();

    void setLatestFinalizedState(BeaconState state);

    void addHotState(Bytes32 blockRoot, BeaconState state);

    default void addHotStates(final Map<Bytes32, BeaconState> states) {
      states.forEach(this::addHotState);
    }

    void addVotes(Map<UInt64, VoteTracker> states);

    void addHotStateRoots(Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap);

    void pruneHotStateRoots(List<Bytes32> stateRoots);

    void deleteHotState(Bytes32 blockRoot);

    void setFinalizedDepositSnapshot(DepositTreeSnapshot finalizedDepositSnapshot);

    void commit();

    void cancel();

    @Override
    void close();

    void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

    void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);

    void removeDepositsFromBlockEvent(UInt64 blockNumber);
  }

  interface FinalizedUpdater extends AutoCloseable {

    void addFinalizedBlock(final SignedBeaconBlock block);

    void addFinalizedBlockRaw(UInt64 slot, Bytes32 blockRoot, Bytes blockBytes);

    void addBlindedExecutionPayloadEnvelope(
        Bytes32 blockRoot,
        SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope);

    void addNonCanonicalBlock(final SignedBeaconBlock block);

    void deleteFinalizedBlock(final UInt64 slot, final Bytes32 blockRoot);

    void deleteBlindedExecutionPayloadEnvelope(final Bytes32 blockRoot);

    void deleteNonCanonicalBlockOnly(final Bytes32 blockRoot);

    void addFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void deleteFinalizedState(final UInt64 slot);

    void addReconstructedFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot);

    void deleteFinalizedStateRoot(final Bytes32 stateRoot);

    void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot);

    void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots);

    void addBlobSidecar(BlobSidecar blobSidecar);

    void addNonCanonicalBlobSidecar(BlobSidecar blobSidecar);

    void addNonCanonicalBlobSidecarRaw(Bytes blobSidecarBytes, SlotAndBlockRootAndBlobIndex key);

    void removeBlobSidecar(SlotAndBlockRootAndBlobIndex key);

    void removeNonCanonicalBlobSidecar(SlotAndBlockRootAndBlobIndex key);

    void setEarliestBlobSidecarSlot(UInt64 slot);

    void setEarliestBlockSlot(UInt64 slot);

    void setEarliestAvailableDataColumnSlot(UInt64 slot);

    void setLastDataColumnSidecarPrunedSlot(UInt64 slot);

    void deleteEarliestBlockSlot();

    void setFirstCustodyIncompleteSlot(UInt64 slot);

    void addSidecar(DataColumnSidecar sidecar);

    void addNonCanonicalSidecar(DataColumnSidecar sidecar);

    void removeSidecar(DataColumnSlotAndIdentifier identifier);

    void removeNonCanonicalSidecar(DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier);

    void addDataColumnSidecarsProofs(UInt64 slot, List<List<KZGProof>> kzgProofs);

    void removeDataColumnSidecarsProofs(UInt64 slot);

    void commit();

    void cancel();

    @Override
    void close();
  }
}

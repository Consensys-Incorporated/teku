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
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
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

public interface StorageQueryChannel extends ChannelInterface {

  SafeFuture<Optional<OnDiskStoreData>> onStoreRequest();

  SafeFuture<WeakSubjectivityState> getWeakSubjectivityState();

  /**
   * @return The earliest available finalized block's slot
   */
  SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot();

  /**
   * @return The earliest available finalized block
   */
  SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock();

  SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(Bytes32 blockRoot);

  SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(Bytes32 blockRoot);

  SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      Bytes32 blockRoot);

  /**
   * Returns "hot" blocks - the latest finalized block or blocks that descend from the latest
   * finalized block
   *
   * @param blockRoots The roots of blocks to look up
   * @return A map from root too block of any found blocks
   */
  SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(Set<Bytes32> blockRoots);

  SafeFuture<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>>
      getBlindedExecutionPayloadEnvelopesByBlockRoot(Set<Bytes32> blockRoots);

  SafeFuture<List<BlobSidecar>> getBlobSidecarsBySlotAndBlockRoot(
      SlotAndBlockRoot slotAndBlockRoot);

  SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(Bytes32 stateRoot);

  SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(UInt64 slot);

  SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(UInt64 slot);

  SafeFuture<Optional<UInt64>> getFinalizedSlotByBlockRoot(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(Bytes32 blockRoot);

  SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(Bytes32 stateRoot);

  SafeFuture<Optional<Bytes32>> getLatestCanonicalBlockRoot();

  SafeFuture<Optional<UInt64>> getCustodyGroupCount();

  SafeFuture<Optional<SignedBeaconBlock>> getNonCanonicalBlockByRoot(Bytes32 blockRoot);

  SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(UInt64 slot);

  SafeFuture<Optional<Checkpoint>> getAnchor();

  SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot();

  /**
   * @return The earliest available finalized blob sidecar's slot
   */
  SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot();

  SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot();

  SafeFuture<Optional<BlobSidecar>> getBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  SafeFuture<Optional<BlobSidecar>> getNonCanonicalBlobSidecar(SlotAndBlockRootAndBlobIndex key);

  /** This method could return non-canonical blob sidecar keys if the slot is not finalized */
  SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(UInt64 slot);

  SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getAllBlobSidecarKeys(UInt64 slot);

  SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      UInt64 startSlot, UInt64 endSlot, long limit);

  SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      SlotAndBlockRoot slotAndBlockRoot);

  // Methods for retrieving archived blobs (when enabled)
  SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(SlotAndBlockRoot slotAndBlockRoot);

  SafeFuture<List<BlobSidecar>> getArchivedBlobSidecars(UInt64 slot);

  SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot();

  SafeFuture<Optional<DataColumnSidecar>> getSidecar(DataColumnSlotAndIdentifier identifier);

  SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalSidecar(
      DataColumnSlotAndIdentifier identifier);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(UInt64 slot);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getNonCanonicalDataColumnIdentifiers(UInt64 slot);

  SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(
      UInt64 startSlot, UInt64 endSlot, UInt64 limit);

  SafeFuture<Optional<UInt64>> getEarliestDataColumnSidecarSlot();

  SafeFuture<Optional<List<List<KZGProof>>>> getDataColumnSidecarsProofs(UInt64 slot);

  SafeFuture<List<StoredLightClientUpdate>> getBestLightClientUpdates();
}

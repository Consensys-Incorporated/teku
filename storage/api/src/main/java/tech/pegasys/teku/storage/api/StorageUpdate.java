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

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StorageUpdate {

  private final Optional<UInt64> genesisTime;
  private final Optional<FinalizedChainData> finalizedChainData;
  private final Optional<Checkpoint> justifiedCheckpoint;
  private final Optional<Checkpoint> bestJustifiedCheckpoint;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Map<Bytes32, BlockAndCheckpoints> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads;
  private final Optional<Bytes32> unsatisfiedInclusionListBlockRoot;
  private final Map<Bytes32, UInt64> deletedHotBlocks;
  private final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars;
  private final Optional<UInt64> maybeEarliestBlobSidecarSlot;
  private final boolean optimisticTransitionBlockRootSet;
  private final Optional<Bytes32> optimisticTransitionBlockRoot;
  private final Optional<Bytes32> latestCanonicalBlockRoot;
  private final Optional<UInt64> custodyGroupCount;
  private final boolean blobSidecarsEnabled;
  private final boolean sidecarsEnabled;
  private final boolean executionPayloadEnvelopesEnabled;
  private final boolean isEmpty;

  public StorageUpdate(
      final Optional<UInt64> genesisTime,
      final Optional<FinalizedChainData> finalizedChainData,
      final Optional<Checkpoint> justifiedCheckpoint,
      final Optional<Checkpoint> bestJustifiedCheckpoint,
      final Map<Bytes32, BlockAndCheckpoints> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Optional<Bytes32> unsatisfiedInclusionListBlockRoot,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot,
      final Map<Bytes32, UInt64> deletedHotBlocks,
      final Map<Bytes32, SlotAndBlockRoot> stateRoots,
      final boolean optimisticTransitionBlockRootSet,
      final Optional<Bytes32> optimisticTransitionBlockRoot,
      final Optional<Bytes32> latestCanonicalBlockRoot,
      final Optional<UInt64> custodyGroupCount,
      @NonUpdating final boolean blobSidecarsEnabled,
      @NonUpdating final boolean sidecarsEnabled,
      @NonUpdating final boolean executionPayloadEnvelopesEnabled) {
    this.genesisTime = genesisTime;
    this.finalizedChainData = finalizedChainData;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.blindedExecutionPayloads = blindedExecutionPayloads;
    this.unsatisfiedInclusionListBlockRoot = unsatisfiedInclusionListBlockRoot;
    this.blobSidecars = blobSidecars;
    this.maybeEarliestBlobSidecarSlot = maybeEarliestBlobSidecarSlot;
    this.deletedHotBlocks = deletedHotBlocks;
    this.stateRoots = stateRoots;
    this.optimisticTransitionBlockRootSet = optimisticTransitionBlockRootSet;
    this.optimisticTransitionBlockRoot = optimisticTransitionBlockRoot;
    this.latestCanonicalBlockRoot = latestCanonicalBlockRoot;
    this.custodyGroupCount = custodyGroupCount;
    this.blobSidecarsEnabled = blobSidecarsEnabled;
    this.sidecarsEnabled = sidecarsEnabled;
    this.executionPayloadEnvelopesEnabled = executionPayloadEnvelopesEnabled;
    checkArgument(
        optimisticTransitionBlockRootSet || optimisticTransitionBlockRoot.isEmpty(),
        "Can't have optimisticTransitionBlockRoot present but not set");

    this.isEmpty =
        genesisTime.isEmpty()
            && justifiedCheckpoint.isEmpty()
            && finalizedChainData.isEmpty()
            && bestJustifiedCheckpoint.isEmpty()
            && hotBlocks.isEmpty()
            && hotStates.isEmpty()
            && blindedExecutionPayloads.isEmpty()
            && unsatisfiedInclusionListBlockRoot.isEmpty()
            && deletedHotBlocks.isEmpty()
            && stateRoots.isEmpty()
            && blobSidecars.isEmpty()
            && maybeEarliestBlobSidecarSlot.isEmpty()
            && latestCanonicalBlockRoot.isEmpty()
            && custodyGroupCount.isEmpty()
            && !optimisticTransitionBlockRootSet;
  }

  public boolean isEmpty() {
    return isEmpty;
  }

  public Optional<UInt64> getGenesisTime() {
    return genesisTime;
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return finalizedChainData.map(FinalizedChainData::getFinalizedCheckpoint);
  }

  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  public Map<Bytes32, BlockAndCheckpoints> getHotBlocks() {
    return hotBlocks;
  }

  public Map<SlotAndBlockRoot, List<BlobSidecar>> getBlobSidecars() {
    return blobSidecars;
  }

  public Optional<UInt64> getEarliestBlobSidecarSlot() {
    return maybeEarliestBlobSidecarSlot;
  }

  public Map<Bytes32, BeaconState> getHotStates() {
    return hotStates;
  }

  public Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> getBlindedExecutionPayloads() {
    return blindedExecutionPayloads;
  }

  public Optional<Bytes32> getUnsatisfiedInclusionListBlockRoot() {
    return unsatisfiedInclusionListBlockRoot;
  }

  public Map<Bytes32, UInt64> getDeletedHotBlocks() {
    return deletedHotBlocks;
  }

  public Map<Bytes32, Bytes32> getFinalizedChildToParentMap() {
    return finalizedChainData
        .map(FinalizedChainData::getFinalizedChildToParentMap)
        .orElse(Collections.emptyMap());
  }

  public Map<Bytes32, SignedBeaconBlock> getFinalizedBlocks() {
    return finalizedChainData.map(FinalizedChainData::getBlocks).orElse(Collections.emptyMap());
  }

  public Map<Bytes32, BeaconState> getFinalizedStates() {
    return finalizedChainData.map(FinalizedChainData::getStates).orElse(Collections.emptyMap());
  }

  public Optional<BeaconState> getLatestFinalizedState() {
    return finalizedChainData.map(FinalizedChainData::getLatestFinalizedState);
  }

  public boolean isFinalizedOptimisticTransitionBlockRootSet() {
    return optimisticTransitionBlockRootSet;
  }

  public Optional<Bytes32> getOptimisticTransitionBlockRoot() {
    return optimisticTransitionBlockRoot;
  }

  public Optional<Bytes32> getLatestCanonicalBlockRoot() {
    return latestCanonicalBlockRoot;
  }

  public Optional<UInt64> getCustodyGroupCount() {
    return custodyGroupCount;
  }

  public Map<Bytes32, SlotAndBlockRoot> getStateRoots() {
    return stateRoots;
  }

  public boolean isBlobSidecarsEnabled() {
    return blobSidecarsEnabled;
  }

  public boolean isSidecarsEnabled() {
    return sidecarsEnabled;
  }

  public boolean isExecutionPayloadEnvelopesEnabled() {
    return executionPayloadEnvelopesEnabled;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface NonUpdating {}
}

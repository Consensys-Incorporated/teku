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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface MutableStore extends ReadOnlyStore {

  /**
   * Stores recent block and corresponding data
   *
   * @param block Block
   * @param state Corresponding state
   * @param checkpoints Checkpoints
   * @param blobSidecars Optional.empty() for pre-Deneb blocks or out of availability window,
   *     otherwise actual data
   * @param earliestBlobSidecarSlot not required even post-Deneb, saved only if the new value is
   *     smaller
   */
  void putBlockAndState(
      SignedBeaconBlock block,
      BeaconState state,
      BlockCheckpoints checkpoints,
      Optional<List<BlobSidecar>> blobSidecars,
      Optional<UInt64> earliestBlobSidecarSlot);

  default void putBlockAndState(
      final SignedBlockAndState blockAndState, final BlockCheckpoints checkpoints) {
    putBlockAndState(
        blockAndState.getBlock(),
        blockAndState.getState(),
        checkpoints,
        Optional.empty(),
        Optional.empty());
  }

  default void putBlockAndState(
      final SignedBlockAndState blockAndState,
      final List<BlobSidecar> blobSidecars,
      final BlockCheckpoints checkpoints) {
    putBlockAndState(
        blockAndState.getBlock(),
        blockAndState.getState(),
        checkpoints,
        Optional.of(blobSidecars),
        Optional.empty());
  }

  /**
   * Stores recent execution payload
   *
   * @param executionPayload Execution payload
   */
  void putExecutionPayload(
      SignedExecutionPayloadEnvelope executionPayload, boolean executionOptimistic);

  void putStateRoot(Bytes32 stateRoot, SlotAndBlockRoot slotAndBlockRoot);

  void pullUpBlockCheckpoints(Bytes32 blockRoot);

  void setTimeMillis(UInt64 timeMillis);

  void setGenesisTime(UInt64 genesisTime);

  void setJustifiedCheckpoint(Checkpoint justifiedCheckpoint);

  void setFinalizedCheckpoint(Checkpoint finalizedCheckpoint, boolean fromOptimisticBlock);

  void setBestJustifiedCheckpoint(Checkpoint bestJustifiedCheckpoint);

  void setProposerBoostRoot(Bytes32 boostedBlockRoot);

  void setLatestCanonicalBlockRoot(Bytes32 latestCanonicalBlockRoot);

  void setCustodyGroupCount(UInt64 custodyGroupCount);

  /**
   * Persists the fast confirmed root, to be loaded as {@link #getFastConfirmedRootBeforeRestart()}
   * on the next start.
   */
  void setFastConfirmedRoot(Bytes32 fastConfirmedRoot);

  void removeProposerBoostRoot();

  void removeFinalizedOptimisticTransitionPayload();
}

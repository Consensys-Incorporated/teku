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

package tech.pegasys.teku.storage.client;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.SlotAndForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ChainHead implements MinimalBeaconBlockSummary {

  private final MinimalBeaconBlockSummary blockData;
  private final ForkChoiceNode forkChoiceNode;
  private final Bytes32 executionPayloadBlockHash;
  private final boolean isOptimistic;
  private final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture;

  private ChainHead(
      final MinimalBeaconBlockSummary blockData,
      final Bytes32 executionPayloadBlockHash,
      final boolean isOptimistic,
      final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture,
      final ForkChoicePayloadStatus payloadStatus) {
    this.blockData = blockData;
    this.forkChoiceNode = new ForkChoiceNode(blockData.getRoot(), payloadStatus);
    this.executionPayloadBlockHash = executionPayloadBlockHash;
    this.isOptimistic = isOptimistic;
    this.stateAndBlockSummaryFuture = stateAndBlockSummaryFuture;
  }

  @VisibleForTesting
  public static ChainHead create(final StateAndBlockSummary stateAndBlockSummary) {
    return new ChainHead(
        stateAndBlockSummary.getBlockSummary(),
        stateAndBlockSummary.getExecutionBlockHash().orElse(Bytes32.ZERO),
        false,
        SafeFuture.completedFuture(stateAndBlockSummary),
        ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
  }

  @VisibleForTesting
  public static ChainHead create(final SignedBlockAndState blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(),
        blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
        false,
        SafeFuture.completedFuture(blockAndState),
        ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
  }

  public static ChainHead create(
      final ProtoNodeData blockData,
      final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture) {
    return new ChainHead(
        blockData,
        blockData.getExecutionBlockHash(),
        blockData.isOptimistic(),
        stateAndBlockSummaryFuture,
        blockData.getPayloadStatus());
  }

  public SafeFuture<BeaconState> getState() {
    return stateAndBlockSummaryFuture.thenApply(StateAndBlockSummary::getState);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlock() {
    return stateAndBlockSummaryFuture.thenApply(StateAndBlockSummary::getSignedBeaconBlock);
  }

  public SafeFuture<StateAndBlockSummary> asStateAndBlockSummary() {
    return stateAndBlockSummaryFuture;
  }

  public boolean isOptimistic() {
    return isOptimistic;
  }

  @Override
  public UInt64 getSlot() {
    return blockData.getSlot();
  }

  @Override
  public Bytes32 getParentRoot() {
    return blockData.getParentRoot();
  }

  @Override
  public Bytes32 getStateRoot() {
    return blockData.getStateRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return blockData.getRoot();
  }

  public Bytes32 getExecutionBlockHash() {
    return executionPayloadBlockHash;
  }

  public ForkChoicePayloadStatus getPayloadStatus() {
    return forkChoiceNode.payloadStatus();
  }

  public ForkChoiceNode getForkChoiceNode() {
    return forkChoiceNode;
  }

  public boolean isSameHeadAs(final Optional<ChainHead> other) {
    return other.filter(this::equals).isPresent();
  }

  public boolean isSameHeadAs(final SlotAndForkChoiceNode other) {
    return blockData.getSlot().equals(other.slot()) && forkChoiceNode.equals(other.node());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ChainHead chainHead = (ChainHead) o;
    return isOptimistic == chainHead.isOptimistic
        && Objects.equals(forkChoiceNode, chainHead.forkChoiceNode)
        && Objects.equals(executionPayloadBlockHash, chainHead.executionPayloadBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isOptimistic, forkChoiceNode, executionPayloadBlockHash);
  }
}

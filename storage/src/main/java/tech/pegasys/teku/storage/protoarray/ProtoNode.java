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

package tech.pegasys.teku.storage.protoarray;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ProtoNode {

  public static final UInt64 NO_EXECUTION_BLOCK_NUMBER = UInt64.ZERO;
  public static final Bytes32 NO_EXECUTION_BLOCK_HASH = Bytes32.ZERO;
  public static final UInt64 NO_EXECUTION_GAS_LIMIT = UInt64.ZERO;

  private static final Logger LOG = LogManager.getLogger();

  private final ForkChoiceNode forkChoiceNode;
  private final UInt64 blockSlot;
  private final Bytes32 stateRoot;
  private final Bytes32 parentRoot;
  private BlockCheckpoints checkpoints;

  /**
   * The block number from the execution payload.
   *
   * <p>{@link ProtoNode#NO_EXECUTION_BLOCK_NUMBER} if the block does not have an execution payload
   * or uses the default payload.
   */
  private final UInt64 executionBlockNumber;

  /**
   * The block hash from the execution payload.
   *
   * <p>{@link ProtoNode#NO_EXECUTION_BLOCK_HASH} if the block does not have an execution payload or
   * uses the default payload.
   */
  private final Bytes32 executionBlockHash;

  private final UInt64 executionGasLimit;

  private UInt64 weight;
  private Optional<Integer> parentIndex;
  private Optional<Integer> bestChildIndex;
  private Optional<Integer> bestDescendantIndex;
  private ProtoNodeValidationStatus validationStatus;

  ProtoNode(
      final ForkChoiceNode forkChoiceNode,
      final UInt64 blockSlot,
      final Bytes32 stateRoot,
      final Bytes32 parentRoot,
      final Optional<Integer> parentIndex,
      final BlockCheckpoints checkpoints,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final UInt64 executionGasLimit,
      final UInt64 weight,
      final Optional<Integer> bestChildIndex,
      final Optional<Integer> bestDescendantIndex,
      final ProtoNodeValidationStatus validationStatus) {
    this.forkChoiceNode = forkChoiceNode;
    this.blockSlot = blockSlot;
    this.stateRoot = stateRoot;
    this.parentRoot = parentRoot;
    this.parentIndex = parentIndex;
    this.checkpoints = checkpoints;
    this.executionBlockNumber = executionBlockNumber;
    this.executionBlockHash = executionBlockHash;
    this.executionGasLimit = executionGasLimit;
    this.weight = weight;
    this.bestChildIndex = bestChildIndex;
    this.bestDescendantIndex = bestDescendantIndex;
    this.validationStatus = validationStatus;
  }

  public void adjustWeight(final long delta) {
    if (delta < 0) {
      final long absoluteDelta = -delta;
      try {
        weight = weight.minus(absoluteDelta);
      } catch (final ArithmeticException __) {
        LOG.error(
            "PLEASE FIX OR REPORT ProtoArray adjustWeight bug: Delta to be subtracted causes uint64 underflow for block {} ({}). Attempting to subtract {} from {}",
            getBlockRoot(),
            blockSlot,
            absoluteDelta,
            weight);
        weight = UInt64.ZERO;
      }

    } else {
      try {
        weight = weight.plus(delta);
      } catch (final ArithmeticException __) {
        LOG.error(
            "PLEASE FIX OR REPORT ProtoArray adjustWeight bug: Delta to be added causes uint64 overflow for block {} ({}). Attempting to add {} to {}",
            getBlockRoot(),
            blockSlot,
            delta,
            weight);
        weight = UInt64.MAX_VALUE;
      }
    }
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  public UInt64 getWeight() {
    return weight;
  }

  public UInt64 getBlockSlot() {
    return blockSlot;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public Bytes32 getBlockRoot() {
    return forkChoiceNode.blockRoot();
  }

  public ForkChoiceNode getForkChoiceNode() {
    return forkChoiceNode;
  }

  public Optional<Integer> getParentIndex() {
    return parentIndex;
  }

  public Checkpoint getJustifiedCheckpoint() {
    return checkpoints.getJustifiedCheckpoint();
  }

  public Checkpoint getFinalizedCheckpoint() {
    return checkpoints.getFinalizedCheckpoint();
  }

  public Checkpoint getUnrealizedJustifiedCheckpoint() {
    return checkpoints.getUnrealizedJustifiedCheckpoint();
  }

  public Checkpoint getUnrealizedFinalizedCheckpoint() {
    return checkpoints.getUnrealizedFinalizedCheckpoint();
  }

  public UInt64 getExecutionBlockNumber() {
    return executionBlockNumber;
  }

  public Bytes32 getExecutionBlockHash() {
    return executionBlockHash;
  }

  public UInt64 getExecutionGasLimit() {
    return executionGasLimit;
  }

  public BlockCheckpoints getBlockCheckpoints() {
    return checkpoints;
  }

  public void pullUpCheckpoints() {
    checkpoints = checkpoints.realizeNextEpoch();
  }

  public void setParentIndex(final Optional<Integer> parentIndex) {
    this.parentIndex = parentIndex;
  }

  public Optional<Integer> getBestChildIndex() {
    return bestChildIndex;
  }

  public void setBestChildIndex(final Optional<Integer> bestChildIndex) {
    this.bestChildIndex = bestChildIndex;
  }

  public Optional<Integer> getBestDescendantIndex() {
    return bestDescendantIndex;
  }

  public void setBestDescendantIndex(final Optional<Integer> bestDescendantIndex) {
    this.bestDescendantIndex = bestDescendantIndex;
  }

  public boolean isFullyValidated() {
    return validationStatus == ProtoNodeValidationStatus.VALID;
  }

  public boolean isInvalid() {
    return validationStatus == ProtoNodeValidationStatus.INVALID;
  }

  public boolean isOptimistic() {
    return validationStatus == ProtoNodeValidationStatus.OPTIMISTIC;
  }

  public void setValidationStatus(final ProtoNodeValidationStatus validationStatus) {
    checkState(
        this.validationStatus == ProtoNodeValidationStatus.OPTIMISTIC
            || this.validationStatus == validationStatus,
        "Cannot change node validity from %s to %s",
        this.validationStatus,
        validationStatus);
    this.validationStatus = validationStatus;
  }

  public ForkChoicePayloadStatus getPayloadStatus() {
    return forkChoiceNode.payloadStatus();
  }

  public ProtoNodeData getBlockData() {
    return new ProtoNodeData(
        blockSlot,
        getBlockRoot(),
        parentRoot,
        stateRoot,
        executionBlockNumber,
        executionBlockHash,
        executionGasLimit,
        validationStatus,
        checkpoints,
        weight,
        getPayloadStatus());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProtoNode protoNode = (ProtoNode) o;
    return Objects.equals(forkChoiceNode, protoNode.forkChoiceNode)
        && Objects.equals(blockSlot, protoNode.blockSlot)
        && Objects.equals(stateRoot, protoNode.stateRoot)
        && Objects.equals(parentRoot, protoNode.parentRoot)
        && Objects.equals(checkpoints, protoNode.checkpoints)
        && Objects.equals(executionBlockNumber, protoNode.executionBlockNumber)
        && Objects.equals(executionBlockHash, protoNode.executionBlockHash)
        && Objects.equals(executionGasLimit, protoNode.executionGasLimit)
        && Objects.equals(weight, protoNode.weight)
        && Objects.equals(parentIndex, protoNode.parentIndex)
        && Objects.equals(bestChildIndex, protoNode.bestChildIndex)
        && Objects.equals(bestDescendantIndex, protoNode.bestDescendantIndex)
        && validationStatus == protoNode.validationStatus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        forkChoiceNode,
        blockSlot,
        stateRoot,
        parentRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        executionGasLimit,
        weight,
        parentIndex,
        bestChildIndex,
        bestDescendantIndex,
        validationStatus);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkChoiceNode", forkChoiceNode)
        .add("blockSlot", blockSlot)
        .add("stateRoot", stateRoot)
        .add("parentRoot", parentRoot)
        .add("justifiedCheckpoint", getJustifiedCheckpoint())
        .add("finalizedCheckpoint", getFinalizedCheckpoint())
        .add("unrealizedJustifiedCheckpoint", getUnrealizedJustifiedCheckpoint())
        .add("unrealizedFinalizedCheckpoint", getUnrealizedFinalizedCheckpoint())
        .add("executionBlockNumber", executionBlockNumber)
        .add("executionBlockHash", executionBlockHash)
        .add("executionGasLimit", executionGasLimit)
        .add("weight", weight)
        .add("parentIndex", parentIndex)
        .add("bestChildIndex", bestChildIndex)
        .add("bestDescendantIndex", bestDescendantIndex)
        .add("validationStatus", validationStatus)
        .toString();
  }

  public String toLogString() {
    return LogFormatter.formatBlock(blockSlot, getBlockRoot()) + " " + getPayloadStatus();
  }
}

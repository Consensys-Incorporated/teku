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

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;

public class ProtoNodeData implements MinimalBeaconBlockSummary {

  private final UInt64 slot;
  private final Bytes32 root;
  private final Bytes32 parentRoot;
  private final Bytes32 stateRoot;
  private final UInt64 executionBlockNumber;
  private final Bytes32 executionBlockHash;
  private final UInt64 executionGasLimit;
  private final ProtoNodeValidationStatus validationStatus;
  private final BlockCheckpoints checkpoints;
  private final UInt64 weight;
  private final ForkChoicePayloadStatus payloadStatus;

  public ProtoNodeData(
      final UInt64 slot,
      final Bytes32 root,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final UInt64 executionGasLimit,
      final ProtoNodeValidationStatus validationStatus,
      final BlockCheckpoints checkpoints,
      final UInt64 weight,
      final ForkChoicePayloadStatus payloadStatus) {
    this.slot = slot;
    this.root = root;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.executionBlockNumber = executionBlockNumber;
    this.executionBlockHash = executionBlockHash;
    this.executionGasLimit = executionGasLimit;
    this.validationStatus = validationStatus;
    this.checkpoints = checkpoints;
    this.weight = weight;
    this.payloadStatus = payloadStatus;
  }

  @Override
  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public Bytes32 getRoot() {
    return root;
  }

  @Override
  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  @Override
  public Bytes32 getStateRoot() {
    return stateRoot;
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

  public boolean isOptimistic() {
    return validationStatus == ProtoNodeValidationStatus.OPTIMISTIC;
  }

  public ProtoNodeValidationStatus getValidationStatus() {
    return validationStatus;
  }

  public BlockCheckpoints getCheckpoints() {
    return checkpoints;
  }

  public UInt64 getWeight() {
    return weight;
  }

  public ForkChoicePayloadStatus getPayloadStatus() {
    return payloadStatus;
  }

  public ForkChoiceNode getForkChoiceNode() {
    return new ForkChoiceNode(root, payloadStatus);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProtoNodeData that = (ProtoNodeData) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(root, that.root)
        && Objects.equals(parentRoot, that.parentRoot)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(executionBlockNumber, that.executionBlockNumber)
        && Objects.equals(executionBlockHash, that.executionBlockHash)
        && Objects.equals(executionGasLimit, that.executionGasLimit)
        && validationStatus == that.validationStatus
        && Objects.equals(checkpoints, that.checkpoints)
        && Objects.equals(weight, that.weight)
        && payloadStatus == that.payloadStatus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        slot,
        root,
        parentRoot,
        stateRoot,
        executionBlockNumber,
        executionBlockHash,
        executionGasLimit,
        validationStatus,
        checkpoints,
        weight,
        payloadStatus);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("root", root)
        .add("parentRoot", parentRoot)
        .add("stateRoot", stateRoot)
        .add("executionBlockNumber", executionBlockNumber)
        .add("executionBlockHash", executionBlockHash)
        .add("executionGasLimit", executionGasLimit)
        .add("validationStatus", validationStatus)
        .add("checkpoints", checkpoints)
        .add("weight", weight)
        .add("payloadStatus", payloadStatus)
        .toString();
  }
}

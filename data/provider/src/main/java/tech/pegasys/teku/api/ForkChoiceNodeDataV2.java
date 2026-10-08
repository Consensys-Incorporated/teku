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

package tech.pegasys.teku.api;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;

public class ForkChoiceNodeDataV2 {
  private final ForkChoicePayloadStatus payloadStatus;
  private final ProtoNodeData node;
  private final Bytes32 parentRoot;
  private final Optional<ForkChoicePayloadStatus> parentPayloadStatus;
  private final UInt64 payloadAttesterCount;
  private final UInt64 payloadAvailabilityYesCount;
  private final UInt64 payloadDataAvailabilityYesCount;

  public ForkChoiceNodeDataV2(
      final ForkChoicePayloadStatus payloadStatus,
      final ProtoNodeData node,
      final Bytes32 parentRoot,
      final Optional<ForkChoicePayloadStatus> parentPayloadStatus,
      final UInt64 payloadAttesterCount,
      final UInt64 payloadAvailabilityYesCount,
      final UInt64 payloadDataAvailabilityYesCount) {
    this.payloadStatus = payloadStatus;
    this.node = node;
    this.parentRoot = parentRoot;
    this.parentPayloadStatus = parentPayloadStatus;
    this.payloadAttesterCount = payloadAttesterCount;
    this.payloadAvailabilityYesCount = payloadAvailabilityYesCount;
    this.payloadDataAvailabilityYesCount = payloadDataAvailabilityYesCount;
  }

  public ForkChoicePayloadStatus getPayloadStatus() {
    return payloadStatus;
  }

  public ProtoNodeData getNode() {
    return node;
  }

  /**
   * The block root of the parent fork choice node: for Gloas {@code EMPTY} and {@code FULL} nodes
   * this is the node's own block root (its parent is the same block's {@code PENDING} node),
   * otherwise the beacon block's parent root.
   */
  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  /** The payload status of the parent fork choice node, or empty if the parent isn't retained. */
  public Optional<ForkChoicePayloadStatus> getParentPayloadStatus() {
    return parentPayloadStatus;
  }

  public UInt64 getPayloadAttesterCount() {
    return payloadAttesterCount;
  }

  public UInt64 getPayloadAvailabilityYesCount() {
    return payloadAvailabilityYesCount;
  }

  public UInt64 getPayloadDataAvailabilityYesCount() {
    return payloadDataAvailabilityYesCount;
  }
}

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

import org.apache.tuweni.bytes.Bytes32;

/**
 * Identifies a node in the fork-choice tree. In the Gloas three-state model, each block produces
 * multiple nodes (PENDING, EMPTY, FULL), so a block root alone is not sufficient to identify a
 * node. This record combines the block root with the payload status to form a complete node
 * identity.
 *
 * <p>Spec reference: ForkChoiceNode(block_root, payload_status)
 */
public record ForkChoiceNode(Bytes32 blockRoot, ForkChoicePayloadStatus payloadStatus) {

  public static ForkChoiceNode createBase(final Bytes32 blockRoot) {
    return new ForkChoiceNode(blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
  }

  public static ForkChoiceNode createEmpty(final Bytes32 blockRoot) {
    return new ForkChoiceNode(blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  public static ForkChoiceNode createFull(final Bytes32 blockRoot) {
    return new ForkChoiceNode(blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
  }

  public ForkChoiceNode toFull() {
    if (payloadStatus == ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL) {
      return this;
    }
    return new ForkChoiceNode(blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
  }

  public ForkChoiceNode toEmpty() {
    if (payloadStatus == ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY) {
      return this;
    }
    return new ForkChoiceNode(blockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }
}

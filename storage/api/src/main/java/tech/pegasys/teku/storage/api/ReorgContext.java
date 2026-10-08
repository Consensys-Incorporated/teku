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

import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;

/**
 * Describes a reorganization of the chain head.
 *
 * <p>A block reorg ({@link #isBlockReorg()}) changes the head block: the old head is no longer
 * canonical and operations from the orphaned blocks need to be handled. From Gloas, the common
 * ancestor block may also carry a different payload status on each branch, which is reported
 * through {@link #commonAncestorPayloadStatusOnOldBranch()} and {@link
 * #commonAncestorPayloadStatusOnNewBranch()}.
 *
 * <p>A payload reorg ({@link #isPayloadReorg()}, Gloas) keeps the same head block but its payload
 * is no longer canonical: the fork-choice payload status of the head changed from FULL to EMPTY. No
 * block left the canonical chain, so pools have nothing to do; only the execution head changed.
 *
 * <p>The two are told apart by content: in a payload reorg the old head is the common ancestor,
 * whereas in a block reorg it never is (a new head descending from the old head is a chain
 * extension, not a reorg).
 *
 * <p>From Gloas a payload status is a property of a block on a branch, not of the block itself:
 * each child block's bid decides whether its parent counts as FULL or EMPTY. The common ancestor
 * block is shared by both branches but may be FULL on one and EMPTY on the other, in which case the
 * reorg also reverted (or adopted) the ancestor's payload. Pre-Gloas every payload status is
 * PENDING.
 *
 * @param oldBestBlockRoot root of the previous head block
 * @param oldBestBlockSlot slot of the previous head block
 * @param oldBestStateRoot state root of the previous head block
 * @param oldBestPayloadStatus fork-choice payload status of the previous head
 * @param oldBestExecutionBlockHash execution block hash of the previous head
 * @param newBestExecutionBlockHash execution block hash of the new head
 * @param commonAncestorSlot slot of the common ancestor block
 * @param commonAncestorRoot root of the common ancestor block
 * @param commonAncestorPayloadStatusOnOldBranch payload status of the common ancestor block along
 *     the branch of the old head
 * @param commonAncestorPayloadStatusOnNewBranch payload status of the common ancestor block along
 *     the branch of the new head
 */
public record ReorgContext(
    Bytes32 oldBestBlockRoot,
    UInt64 oldBestBlockSlot,
    Bytes32 oldBestStateRoot,
    ForkChoicePayloadStatus oldBestPayloadStatus,
    Bytes32 oldBestExecutionBlockHash,
    Bytes32 newBestExecutionBlockHash,
    UInt64 commonAncestorSlot,
    Bytes32 commonAncestorRoot,
    ForkChoicePayloadStatus commonAncestorPayloadStatusOnOldBranch,
    ForkChoicePayloadStatus commonAncestorPayloadStatusOnNewBranch) {

  /** Block reorg without payload information (pre-Gloas). */
  public ReorgContext(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot) {
    this(
        oldBestBlockRoot,
        oldBestBlockSlot,
        oldBestStateRoot,
        PAYLOAD_STATUS_PENDING,
        Bytes32.ZERO,
        Bytes32.ZERO,
        commonAncestorSlot,
        commonAncestorRoot,
        PAYLOAD_STATUS_PENDING,
        PAYLOAD_STATUS_PENDING);
  }

  public static ReorgContext blockReorg(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final ForkChoicePayloadStatus oldBestPayloadStatus,
      final Bytes32 oldBestExecutionBlockHash,
      final Bytes32 newBestExecutionBlockHash,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot,
      final ForkChoicePayloadStatus commonAncestorPayloadStatusOnOldBranch,
      final ForkChoicePayloadStatus commonAncestorPayloadStatusOnNewBranch) {
    return new ReorgContext(
        oldBestBlockRoot,
        oldBestBlockSlot,
        oldBestStateRoot,
        oldBestPayloadStatus,
        oldBestExecutionBlockHash,
        newBestExecutionBlockHash,
        commonAncestorSlot,
        commonAncestorRoot,
        commonAncestorPayloadStatusOnOldBranch,
        commonAncestorPayloadStatusOnNewBranch);
  }

  /**
   * Payload reorg: the head block is unchanged, its payload status changed from FULL to EMPTY. The
   * head block is both the old best block and the common ancestor.
   */
  public static ReorgContext payloadReorg(
      final Bytes32 headRoot,
      final UInt64 headSlot,
      final Bytes32 headStateRoot,
      final Bytes32 oldExecutionBlockHash,
      final Bytes32 newExecutionBlockHash) {
    return new ReorgContext(
        headRoot,
        headSlot,
        headStateRoot,
        PAYLOAD_STATUS_FULL,
        oldExecutionBlockHash,
        newExecutionBlockHash,
        headSlot,
        headRoot,
        PAYLOAD_STATUS_FULL,
        PAYLOAD_STATUS_EMPTY);
  }

  public boolean isBlockReorg() {
    return !isPayloadReorg();
  }

  public boolean isPayloadReorg() {
    return oldBestBlockRoot.equals(commonAncestorRoot);
  }

  public static Optional<ReorgContext> of(
      final Bytes32 oldBestBlockRoot,
      final UInt64 oldBestBlockSlot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot,
      final Bytes32 commonAncestorRoot) {
    return Optional.of(
        new ReorgContext(
            oldBestBlockRoot,
            oldBestBlockSlot,
            oldBestStateRoot,
            commonAncestorSlot,
            commonAncestorRoot));
  }

  public static Optional<ReorgContext> empty() {
    return Optional.empty();
  }
}

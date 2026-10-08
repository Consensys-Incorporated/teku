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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public interface ReadOnlyForkChoiceStrategy {

  Optional<UInt64> blockSlot(Bytes32 blockRoot);

  Optional<Bytes32> blockParentRoot(Bytes32 blockRoot);

  Optional<UInt64> executionBlockNumber(Bytes32 blockRoot);

  Optional<Bytes32> executionBlockHash(Bytes32 blockRoot);

  default Optional<UInt64> getExecutionGasLimitForBlockRootAndHash(
      final Bytes32 blockRoot, final Bytes32 blockHash) {
    return Optional.empty();
  }

  Optional<Bytes32> getAncestor(Bytes32 blockRoot, UInt64 slot);

  /**
   * Walks the parent chain from {@code node} to find the ancestor node at the given slot.
   *
   * <p>This mirrors the {@code get_ancestor(store, node, slot)} helper, which takes and returns a
   * {@link ForkChoiceNode} identity. The starting node's payload status selects which parent
   * variant to follow, and the node itself is returned once its block slot is at or before {@code
   * slot}.
   *
   * <p>This is the payload-status-aware counterpart of {@link #getAncestor(Bytes32, UInt64)} and is
   * intentionally abstract: unlike a block-root walk it cannot be derived from {@link
   * #getAncestor(Bytes32, UInt64)}, because that would discard each ancestor's payload status and
   * always yield {@linkplain ForkChoiceNode#createBase(Bytes32) base (PENDING)} nodes — which
   * silently breaks the Gloas ancestry checks in {@code ForkChoiceUtilGloas.isAncestor}. Every
   * implementation must therefore resolve payload status explicitly. Implementations that do not
   * model the Gloas three-state tree may resolve ancestry by block root and return base (PENDING)
   * nodes.
   */
  Optional<ForkChoiceNode> getAncestorNode(ForkChoiceNode node, UInt64 slot);

  /**
   * Resolves a latest-message vote to the fork-choice node that it supports.
   *
   * <p>{@code currentSlot} selects the active fork-choice model, matching score application.
   * Pre-Gloas this is the base node for {@code voteRoot}. In Gloas, {@code voteSlot} and {@code
   * payloadPresent} select the PENDING, EMPTY, or FULL variant according to {@code
   * get_supported_node(store, message)}.
   */
  default Optional<ForkChoiceNode> getSupportedNode(
      final UInt64 currentSlot,
      final Bytes32 voteRoot,
      final UInt64 voteSlot,
      final boolean payloadPresent) {
    return Optional.of(ForkChoiceNode.createBase(voteRoot));
  }

  Optional<ForkChoiceNode> getParentBeaconBlockNode(ForkChoiceNode node);

  Optional<SlotAndBlockRoot> findCommonAncestor(Bytes32 blockRoot1, Bytes32 blockRoot2);

  List<Bytes32> getBlockRootsAtSlot(UInt64 slot);

  /**
   * Returns the current terminal fork-choice heads.
   *
   * <p>This is a node-facing API. In pre-Gloas it returns the base block nodes. In the Gloas
   * three-state tree it may return EMPTY/FULL nodes for the same block root, because those are the
   * actual candidate heads selected by the modified {@code get_head(...)} spec logic.
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-get_head
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_node_children
   */
  default List<ProtoNodeData> getChainHeads() {
    return getChainHeads(false);
  }

  List<ProtoNodeData> getChainHeads(boolean includeNonViableHeads);

  Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(Bytes32 head);

  List<ProtoNodeData> getBlockData();

  /**
   * Returns the data for every fork choice node, each paired with the data of its parent fork
   * choice node when the parent is retained. Implementations that don't track parent nodes report
   * no parent.
   */
  default List<ProtoNodeDataWithParent> getBlockDataWithParent() {
    return getBlockData().stream()
        .map(node -> new ProtoNodeDataWithParent(node, Optional.empty()))
        .toList();
  }

  boolean contains(Bytes32 blockRoot);

  Optional<Boolean> isOptimistic(Bytes32 blockRoot);

  boolean isFullyValidated(final Bytes32 blockRoot);

  Optional<ProtoNodeData> getBlockData(Bytes32 blockRoot);

  default Optional<ProtoNodeData> getNodeData(final ForkChoiceNode node) {
    return getBlockData(node.blockRoot(), node.payloadStatus());
  }

  /**
   * Gets block data for a specific node identity (blockRoot + payloadStatus). In the Gloas
   * three-state tree, the same blockRoot may have multiple nodes (PENDING, EMPTY, FULL). This
   * method resolves to the correct node based on the payload status.
   *
   * <p>This is the read-side mirror of the Gloas spec helpers that distinguish the canonical block
   * root from the EMPTY/FULL child returned by `get_node_children(...)` and selected by
   * `get_head(...)`:
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_node_children
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-get_head
   *
   * <p>Default: delegates to {@link #getBlockData(Bytes32)}, ignoring payloadStatus.
   */
  default Optional<ProtoNodeData> getBlockData(
      final Bytes32 blockRoot, final ForkChoicePayloadStatus payloadStatus) {
    return getBlockData(blockRoot);
  }

  /**
   * Returns whether block production should extend the parent execution payload branch or fall back
   * to the EMPTY path.
   *
   * <p>This is a forkchoice-owned decision. Pre-Gloas follows the current master behavior based on
   * local payload availability, while Gloas can override it with the model-specific EMPTY/FULL
   * selection rules. The slotAndBlockRoot slot is supplied with the root so implementations can
   * select fork-aware logic without resolving the slot from the root.
   */
  boolean shouldExtendPayload(ReadOnlyStore store, SlotAndBlockRoot slotAndBlockRoot);

  /**
   * Returns whether block production at {@code slot} should build on the FULL variant of {@code
   * head}.
   *
   * <p>Pre-Gloas follows the existing {@link #shouldExtendPayload(ReadOnlyStore, SlotAndBlockRoot)}
   * decision. Gloas overrides this to account for PTC votes that signal data unavailability or an
   * untimely payload.
   */
  boolean shouldBuildOnFull(
      final ReadOnlyStore store, final UInt64 slot, final ForkChoiceNode head);

  default Optional<Boolean> getPayloadTimelinessVote(
      final Bytes32 blockRoot, final int ptcPosition) {
    return Optional.empty();
  }

  default Optional<Boolean> getPayloadDataAvailabilityVote(
      final Bytes32 blockRoot, final int ptcPosition) {
    return Optional.empty();
  }

  default UInt64 getPayloadAttesterCount(final Bytes32 blockRoot) {
    return UInt64.ZERO;
  }

  default UInt64 getPayloadAvailabilityYesCount(final Bytes32 blockRoot) {
    return UInt64.ZERO;
  }

  default UInt64 getPayloadDataAvailabilityYesCount(final Bytes32 blockRoot) {
    return UInt64.ZERO;
  }

  Optional<UInt64> getWeight(Bytes32 blockRoot);
}

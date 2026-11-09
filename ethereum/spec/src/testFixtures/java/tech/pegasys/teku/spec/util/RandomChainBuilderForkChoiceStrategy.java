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

package tech.pegasys.teku.spec.util;

import static java.util.stream.Collectors.toList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;

public class RandomChainBuilderForkChoiceStrategy implements ReadOnlyForkChoiceStrategy {

  private final RandomChainBuilder chainBuilder;
  private UInt64 prunePriorToSlot = UInt64.ZERO;

  public RandomChainBuilderForkChoiceStrategy(final RandomChainBuilder chainBuilder) {
    this.chainBuilder = chainBuilder;
  }

  /**
   * Prune available block prior to the given slot
   *
   * @param slot
   */
  public void prune(final UInt64 slot) {
    this.prunePriorToSlot = slot;
  }

  @Override
  public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
    return getBlock(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
    return getBlock(blockRoot).map(SignedBeaconBlock::getParentRoot);
  }

  @Override
  public Optional<UInt64> executionBlockNumber(final Bytes32 blockRoot) {
    return getExecutionPayload(blockRoot).map(ExecutionPayload::getBlockNumber);
  }

  @Override
  public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
    return getExecutionPayload(blockRoot).map(ExecutionPayload::getBlockHash);
  }

  @Override
  public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
    if (getBlock(blockRoot).isEmpty()) {
      return Optional.empty();
    }
    return getBlock(slot).map(SignedBeaconBlock::getRoot);
  }

  @Override
  public Optional<ForkChoiceNode> getAncestorNode(final ForkChoiceNode node, final UInt64 slot) {
    // This fixture models only the pre-Gloas (single-variant) tree, so ancestry is resolved by
    // block root and always yields base (PENDING) nodes.
    return getAncestor(node.blockRoot(), slot).map(ForkChoiceNode::createBase);
  }

  @Override
  public Optional<ForkChoiceNode> getParentBeaconBlockNode(final ForkChoiceNode node) {
    // Single-variant tree: the parent beacon block node is the base (PENDING) node of the parent
    return getBlock(node.blockRoot())
        .flatMap(block -> getBlock(block.getParentRoot()))
        .map(parent -> ForkChoiceNode.createBase(parent.getRoot()));
  }

  @Override
  public Optional<SlotAndBlockRoot> findCommonAncestor(
      final Bytes32 blockRoot1, final Bytes32 blockRoot2) {
    return Optional.empty();
  }

  @Override
  public List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
    final Optional<Bytes32> maybeRoot = getBlock(slot).map(SignedBeaconBlock::getRoot);
    return maybeRoot.map(List::of).orElse(Collections.emptyList());
  }

  @Override
  public List<ProtoNodeData> getChainHeads(final boolean includeNonViableHeads) {
    return chainBuilder
        .getChainHead()
        .map(h -> List.of(asProtoNodeData(h)))
        .orElse(Collections.emptyList());
  }

  private static ProtoNodeData asProtoNodeData(final SignedBlockAndState blockAndState) {
    return new ProtoNodeData(
        blockAndState.getSlot(),
        blockAndState.getRoot(),
        blockAndState.getParentRoot(),
        blockAndState.getStateRoot(),
        blockAndState.getExecutionBlockNumber().orElse(UInt64.ZERO),
        blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
        UInt64.ZERO,
        ProtoNodeValidationStatus.VALID,
        new BlockCheckpoints(
            blockAndState.getState().getCurrentJustifiedCheckpoint(),
            blockAndState.getState().getFinalizedCheckpoint(),
            blockAndState.getState().getCurrentJustifiedCheckpoint(),
            blockAndState.getState().getFinalizedCheckpoint()),
        UInt64.ZERO,
        ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
  }

  @Override
  public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
    return Optional.empty();
  }

  @Override
  public List<ProtoNodeData> getBlockData() {
    return chainBuilder.getChain().stream()
        .map(RandomChainBuilderForkChoiceStrategy::asProtoNodeData)
        .collect(toList());
  }

  @Override
  public boolean contains(final Bytes32 blockRoot) {
    return getBlock(blockRoot).isPresent();
  }

  @Override
  public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
    return Optional.of(false);
  }

  @Override
  public boolean isFullyValidated(final Bytes32 blockRoot) {
    return true;
  }

  @Override
  public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
    return chainBuilder
        .getBlockAndState(blockRoot)
        .map(RandomChainBuilderForkChoiceStrategy::asProtoNodeData);
  }

  @Override
  public boolean shouldExtendPayload(
      final ReadOnlyStore store, final SlotAndBlockRoot slotAndBlockRoot) {
    return store.getExecutionPayloadIfAvailable(slotAndBlockRoot.getBlockRoot()).isPresent();
  }

  @Override
  public boolean shouldBuildOnFull(
      final ReadOnlyStore store, final UInt64 slot, final ForkChoiceNode head) {
    return shouldExtendPayload(store, new SlotAndBlockRoot(slot, head.blockRoot()));
  }

  @Override
  public Optional<UInt64> getWeight(final Bytes32 blockRoot) {
    // We don't track weight so return 0 for all known blocks.
    return getBlock(blockRoot).map(block -> UInt64.ZERO);
  }

  private Optional<SignedBeaconBlock> getBlock(final Bytes32 root) {
    return chainBuilder
        .getBlock(root)
        .filter(b -> b.getSlot().isGreaterThanOrEqualTo(prunePriorToSlot));
  }

  private Optional<SignedBeaconBlock> getBlock(final UInt64 slot) {
    return chainBuilder
        .getBlock(slot)
        .filter(b -> b.getSlot().isGreaterThanOrEqualTo(prunePriorToSlot));
  }

  private Optional<ExecutionPayload> getExecutionPayload(final Bytes32 blockRoot) {
    return getBlock(blockRoot)
        .map(
            block -> {
              final BeaconBlockBody blockBody = block.getMessage().getBody();
              return blockBody instanceof BeaconBlockBodyBellatrix
                  ? ((BeaconBlockBodyBellatrix) blockBody).getExecutionPayload()
                  : null;
            });
  }
}

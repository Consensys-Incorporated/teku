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

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;

/** Phase0 single-node-per-block model used across the pre-Gloas milestones. */
class ForkChoiceModelPhase0 implements ForkChoiceModel {

  static final ForkChoiceModelPhase0 INSTANCE = new ForkChoiceModelPhase0();

  private ForkChoiceModelPhase0() {}

  @Override
  public void processBlock(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash,
      final Optional<UInt64> executionGasLimit,
      final boolean optimisticallyProcessed) {
    final ForkChoiceNode baseNode = ForkChoiceNode.createBase(blockRoot);
    protoArray.addNode(
        baseNode,
        blockSlot,
        parentRoot,
        blockNodeIndex.getBaseNode(parentRoot),
        stateRoot,
        checkpoints,
        executionBlockNumber.orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
        executionBlockHash.orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH),
        executionGasLimit.orElse(ProtoNode.NO_EXECUTION_GAS_LIMIT),
        optimisticallyProcessed);
    blockNodeIndex.putBaseNode(blockRoot, blockSlot, baseNode);
  }

  @Override
  public void processAnchorBlock(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash,
      final Optional<UInt64> executionGasLimit,
      final boolean optimisticallyProcessed) {
    processBlock(
        protoArray,
        blockNodeIndex,
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        executionGasLimit,
        optimisticallyProcessed);
  }

  @Override
  public void onExecutionPayload(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final UInt64 executionGasLimit,
      final boolean isOptimistic) {
    // No-op
  }

  @Override
  public void rebuildBlockNodesFromMetadata(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final StoredBlockMetadata block,
      final boolean optimisticallyProcessed) {
    processBlock(
        protoArray,
        blockNodeIndex,
        block.getBlockSlot(),
        block.getBlockRoot(),
        block.getParentRoot(),
        block.getStateRoot(),
        block.getCheckpointEpochs().orElseThrow(),
        block.getExecutionBlockNumber(),
        block.getExecutionBlockHash(),
        block.getExecutionGasLimit(),
        optimisticallyProcessed);
  }

  @Override
  public Optional<ForkChoiceNode> getSupportedNode(
      final Bytes32 voteRoot,
      final UInt64 voteSlot,
      final boolean payloadPresent,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex) {
    return blockNodeIndex.getBaseNode(voteRoot);
  }

  @Override
  public int compareViableChildren(
      final ProtoNode candidateChild,
      final ProtoNode currentBestChild,
      final ProtoNode parent,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    return 0;
  }

  @Override
  public Optional<ProtoNodeData> getBaseNodeData(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    return blockNodeIndex
        .getBaseNode(blockRoot)
        .flatMap(protoArray::getNode)
        .map(ProtoNode::getBlockData);
  }

  @Override
  public Optional<ProtoNode> getParentBeaconBlockNode(
      final ProtoArray protoArray, final ForkChoiceNode node) {
    return protoArray
        .getNode(node)
        .flatMap(current -> current.getParentIndex().map(protoArray::getNodeByIndex));
  }

  @Override
  public boolean shouldExtendPayload(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final ReadOnlyStore store,
      final Bytes32 blockRoot) {
    return false;
  }

  @Override
  public boolean shouldBuildOnFull(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 slot,
      final ForkChoiceNode head) {
    return false;
  }

  @Override
  public boolean isHeadCandidate(final ProtoNode node) {
    return true;
  }

  @Override
  public ProtoNode resolveBestDescendant(
      final ProtoNode candidate,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    // Phase0 has a single node per block — no sibling structure, no redirection needed.
    return candidate;
  }

  @Override
  public void onExecutionPayloadResult(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot,
      final ExecutionPayloadStatus status,
      final Optional<Bytes32> latestValidHash,
      final boolean verifiedInvalidTransition,
      final HeadSelectionContext headSelectionContext) {
    if (status.isValid()) {
      blockNodeIndex.getBaseNode(blockRoot).ifPresent(protoArray::markNodeValid);
    } else if (status.isInvalid()) {
      if (verifiedInvalidTransition) {
        blockNodeIndex
            .getBaseNode(blockRoot)
            .ifPresent(
                node -> protoArray.markNodeInvalid(node, latestValidHash, headSelectionContext));
      } else {
        blockNodeIndex
            .getBaseNode(blockRoot)
            .ifPresent(
                node ->
                    protoArray.markParentChainInvalid(node, latestValidHash, headSelectionContext));
      }
    }
  }

  @Override
  public Optional<ForkChoiceNode> resolveBaseNode(
      final BlockNodeVariantsIndex blockNodeIndex, final Bytes32 blockRoot) {
    return blockNodeIndex.getBaseNode(blockRoot);
  }

  @Override
  public Optional<ProtoNodeData> getExecutionNodeData(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    return getBaseNodeData(protoArray, blockNodeIndex, blockRoot);
  }

  @Override
  public Optional<UInt64> getExecutionGasLimitForBlockRootAndHash(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot,
      final Bytes32 blockHash) {
    return blockNodeIndex
        .getBaseNode(blockRoot)
        .flatMap(protoArray::getNode)
        .filter(node -> node.getExecutionBlockHash().equals(blockHash))
        .map(ProtoNode::getExecutionGasLimit)
        .filter(gasLimit -> !gasLimit.equals(ProtoNode.NO_EXECUTION_GAS_LIMIT));
  }

  @Override
  public void pullUpBlockCheckpoints(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    blockNodeIndex
        .getVariants(blockRoot)
        .ifPresent(variants -> variants.allNodes().forEach(protoArray::pullUpCheckpoints));
  }

  @Override
  public void onPtcVote(
      final Bytes32 blockRoot,
      final IntSet ptcPositions,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    // No-op
  }

  @Override
  public Optional<Boolean> getPayloadTimelinessVote(
      final Bytes32 blockRoot, final int ptcPosition) {
    return Optional.empty();
  }

  @Override
  public Optional<Boolean> getPayloadDataAvailabilityVote(
      final Bytes32 blockRoot, final int ptcPosition) {
    return Optional.empty();
  }

  @Override
  public void onRemovedBlockRoot(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Bytes32 blockRoot) {
    blockNodeIndex
        .getVariants(blockRoot)
        .ifPresent(variants -> variants.allNodes().forEach(protoArray::removeNode));
    blockNodeIndex.remove(blockRoot);
  }

  @Override
  public void onPrunedBlocks(final BlockNodeVariantsIndex blockNodeIndex) {
    // not used in phase0
  }

  @Override
  public void onForkChoiceUpdatedResult(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final ForkChoiceNode node,
      final ExecutionPayloadStatus status,
      final Optional<Bytes32> latestValidHash,
      final boolean verifiedInvalidTransition,
      final HeadSelectionContext headSelectionContext) {
    onExecutionPayloadResult(
        protoArray,
        blockNodeIndex,
        node.blockRoot(),
        status,
        latestValidHash,
        verifiedInvalidTransition,
        headSelectionContext);
  }
}

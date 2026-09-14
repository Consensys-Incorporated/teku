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

package tech.pegasys.teku.storage.protoarray;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.SlotAndForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.storage.client.ChainHead;

public class ForkChoiceStrategy implements BlockMetadataStore, ReadOnlyForkChoiceStrategy {
  private static final Logger LOG = LogManager.getLogger();

  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final Spec spec;
  private final ProtoArray protoArray;
  private final BlockNodeVariantsIndex blockNodeIndex;
  private final ForkChoiceModelFactory forkChoiceModelFactory;
  private List<UInt64> balances;

  private Optional<ForkChoiceNode> proposerBoostNode = Optional.empty();
  private UInt64 proposerBoostAmount = UInt64.ZERO;
  private HeadSelectionContext headSelectionContext;

  private ForkChoiceStrategy(
      final Spec spec,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final List<UInt64> balances) {
    this.spec = spec;
    this.protoArray = protoArray;
    this.blockNodeIndex = blockNodeIndex;
    this.forkChoiceModelFactory = new ForkChoiceModelFactory(spec);
    this.balances = balances;
    this.headSelectionContext =
        forkChoiceModelFactory.createHeadSelectionContext(
            UInt64.ZERO, blockNodeIndex, Optional.empty());
  }

  private ForkChoiceModel getForkChoiceModel(final UInt64 slot) {
    return forkChoiceModelFactory.forSlot(slot);
  }

  private Optional<ForkChoiceModel> getForkChoiceModelForRoot(final Bytes32 blockRoot) {
    return blockNodeIndex.getSlot(blockRoot).map(this::getForkChoiceModel);
  }

  public static ForkChoiceStrategy initialize(final Spec spec, final ProtoArray protoArray) {
    final BlockNodeVariantsIndex blockNodeIndex = BlockNodeVariantsIndex.fromProtoArray(protoArray);
    return new ForkChoiceStrategy(spec, protoArray, blockNodeIndex, new ArrayList<>());
  }

  public SlotAndForkChoiceNode findHead(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    protoArrayLock.readLock().lock();
    try {
      return findHeadImpl(currentEpoch, justifiedCheckpoint, finalizedCheckpoint);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  private SlotAndForkChoiceNode findHeadImpl(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    final ProtoNode bestNode =
        protoArray.findOptimisticHead(
            currentEpoch, justifiedCheckpoint, finalizedCheckpoint, headSelectionContext);
    return new SlotAndForkChoiceNode(bestNode.getBlockSlot(), bestNode.getForkChoiceNode());
  }

  /**
   * Applies the weighting changes from any updated votes then finds and returns the best chain
   * head.
   *
   * @param voteUpdater the vote updater to access and update pending votes from
   * @param proposerBoostRoot the block root to apply proposer boost to
   * @param currentEpoch the current epoch based on Store time.
   * @param finalizedCheckpoint the current finalized checkpoint
   * @param justifiedCheckpoint the current justified checkpoint
   * @param justifiedStateEffectiveBalances the effective validator balances at the justified
   *     checkpoint
   * @return the best chain head as a slot-bearing forkchoice node result
   */
  public SlotAndForkChoiceNode applyPendingVotes(
      final VoteUpdater voteUpdater,
      final Optional<Bytes32> proposerBoostRoot,
      final UInt64 currentSlot,
      final UInt64 currentEpoch,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedStateEffectiveBalances,
      final UInt64 proposerBoostAmount) {
    protoArrayLock.writeLock().lock();
    votesLock.writeLock().lock();
    balancesLock.writeLock().lock();
    try {
      final ForkChoiceModel forkChoiceModel = getForkChoiceModel(currentSlot);
      final HeadSelectionContext headSelectionContext =
          forkChoiceModelFactory.createHeadSelectionContext(
              currentSlot, blockNodeIndex, proposerBoostRoot);
      final Optional<ForkChoiceNode> nextProposerBoostNode =
          proposerBoostRoot.flatMap(blockNodeIndex::getBaseNode);
      LongList deltas =
          ProtoArrayScoreCalculator.computeDeltas(
              voteUpdater,
              getTotalTrackedNodeCount(),
              protoArray::getNodeIndex,
              this.proposerBoostNode,
              nextProposerBoostNode,
              balances,
              justifiedStateEffectiveBalances,
              this.proposerBoostAmount,
              proposerBoostAmount,
              protoArray,
              blockNodeIndex,
              forkChoiceModel);

      protoArray.applyScoreChanges(
          deltas, currentEpoch, justifiedCheckpoint, finalizedCheckpoint, headSelectionContext);
      balances = justifiedStateEffectiveBalances;
      this.proposerBoostNode = nextProposerBoostNode;
      this.proposerBoostAmount = proposerBoostAmount;
      this.headSelectionContext = headSelectionContext;

      return findHeadImpl(currentEpoch, justifiedCheckpoint, finalizedCheckpoint);
    } finally {
      protoArrayLock.writeLock().unlock();
      votesLock.writeLock().unlock();
      balancesLock.writeLock().unlock();
    }
  }

  public void onAttestation(
      final VoteUpdater voteUpdater, final IndexedAttestationLight attestation) {
    votesLock.writeLock().lock();
    try {
      final UInt64 attestationSlot = attestation.data().getSlot();
      final ForkChoiceUtil forkChoiceUtil = spec.atSlot(attestationSlot).getForkChoiceUtil();
      final boolean fullPayloadHint =
          forkChoiceUtil.getFullPayloadVoteHint(attestation.data().getIndex());
      for (final UInt64 validatorIndex : attestation.attestingIndices()) {
        processAttestation(
            voteUpdater,
            validatorIndex,
            attestation.data().getBeaconBlockRoot(),
            attestation.data().getTarget().getEpoch(),
            attestationSlot,
            fullPayloadHint,
            forkChoiceUtil);
      }
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  public void applyDeferredAttestations(final VoteUpdater voteUpdater, final DeferredVotes votes) {
    final UInt64 targetEpoch = spec.computeEpochAtSlot(votes.getSlot());
    final UInt64 slot = votes.getSlot();
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(slot).getForkChoiceUtil();
    votesLock.writeLock().lock();
    try {
      votes.forEachDeferredVote(
          (blockRoot, validatorIndex, fullPayloadHint) ->
              processAttestation(
                  voteUpdater,
                  validatorIndex,
                  blockRoot,
                  targetEpoch,
                  slot,
                  fullPayloadHint,
                  forkChoiceUtil));
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  /**
   * Returns terminal fork-choice heads using the fork-aware model to decide which node variants are
   * eligible heads.
   *
   * <p>Pre-Gloas only exposes base nodes here. In Gloas this returns EMPTY/FULL leaves, matching
   * the modified {@code get_head(...)} semantics of the three-state tree.
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-get_head
   */
  @Override
  public List<ProtoNodeData> getChainHeads(final boolean includeNonViableHeads) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream()
          .filter(
              protoNode ->
                  protoNode.getBestChildIndex().isEmpty()
                      && getForkChoiceModel(protoNode.getBlockSlot()).isHeadCandidate(protoNode)
                      && (includeNonViableHeads || protoArray.nodeIsViableForHead(protoNode)))
          .map(ProtoNode::getBlockData)
          .toList();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public ForkChoiceState getForkChoiceState(
      final Optional<ChainHead> proposingOnHead,
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Optional<Bytes32> fastConfirmationSafeBlockRoot) {
    protoArrayLock.readLock().lock();
    try {
      final ProtoNode headNode =
          proposingOnHead
              .map(head -> protoArray.getNode(head.getForkChoiceNode()).orElseThrow())
              .orElseGet(
                  () ->
                      protoArray.findOptimisticHead(
                          currentEpoch,
                          justifiedCheckpoint,
                          finalizedCheckpoint,
                          headSelectionContext));
      final UInt64 headExecutionBlockNumber = headNode.getExecutionBlockNumber();
      final Bytes32 headExecutionBlockHash = headNode.getExecutionBlockHash();
      // FCU safe/finalized hashes use BASE data, not the execution/FULL variant.
      // Pre-Gloas has only BASE, so this is the normal execution payload block hash.
      // In Gloas, BASE/EMPTY carry the effective EL parent hash from the bid
      // parent_block_hash; FULL carries the revealed payload hash for the selected head.
      //
      // safe_block_hash implements get_safe_execution_block_hash(fcr_store): when fast
      // confirmation is enabled it is the confirmed block's hash, otherwise the justified block's
      // hash (the pre-fast-confirmation default). The BASE-node hash already matches the spec per
      // fork: pre-Gloas the confirmed block's execution_payload.block_hash, in Gloas its
      // signed_execution_payload_bid.message.parent_block_hash.
      //
      // Spec divergence: get_safe_execution_block_hash unconditionally dereferences
      // store.blocks[confirmed_root]. Teku diverges in two ways: (1) when fast confirmation is
      // disabled the supplier is empty and we fall back to the justified block (the spec has no
      // such fallback); (2) the hash is read from protoarray's BASE ProtoNodeData rather than
      // store.blocks[...].body, and protoarray only retains finalized-onward blocks, so a pruned
      // confirmed/justified root yields Bytes32.ZERO here instead of the spec's KeyError.
      final Bytes32 safeExecutionHash =
          fastConfirmationSafeBlockRoot
              .flatMap(this::getBaseNodeData)
              .or(() -> getBaseNodeData(justifiedCheckpoint.getRoot()))
              .map(ProtoNodeData::getExecutionBlockHash)
              .orElse(Bytes32.ZERO);
      final Bytes32 finalizedExecutionHash =
          getBaseNodeData(finalizedCheckpoint.getRoot())
              .map(ProtoNodeData::getExecutionBlockHash)
              .orElse(Bytes32.ZERO);
      return new ForkChoiceState(
          headNode.getForkChoiceNode(),
          headNode.getBlockSlot(),
          headExecutionBlockNumber,
          headExecutionBlockHash,
          safeExecutionHash,
          finalizedExecutionHash,
          headNode.isOptimistic() || !protoArray.nodeIsViableForHead(headNode));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getBaseNode(head)
          .flatMap(protoArray::findOptimisticallySyncedMergeTransitionBlock)
          .map(ProtoNode::getBlockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @VisibleForTesting
  void processAttestation(
      final VoteUpdater voteUpdater,
      final UInt64 validatorIndex,
      final Bytes32 blockRoot,
      final UInt64 targetEpoch) {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(targetEpoch);
    processAttestation(
        voteUpdater,
        validatorIndex,
        blockRoot,
        targetEpoch,
        attestationSlot,
        false,
        spec.atSlot(attestationSlot).getForkChoiceUtil());
  }

  void processAttestation(
      final VoteUpdater voteUpdater,
      final UInt64 validatorIndex,
      final Bytes32 blockRoot,
      final UInt64 targetEpoch,
      final UInt64 attestationSlot,
      final boolean fullPayloadHint,
      final ForkChoiceUtil forkChoiceUtil) {
    VoteTracker vote = voteUpdater.getVote(validatorIndex);
    // Not updating anything for equivocated validators
    if (vote.isEquivocating()) {
      return;
    }

    if (forkChoiceUtil.shouldUpdateVote(vote, targetEpoch, attestationSlot)) {
      VoteTracker newVote =
          new VoteTracker(
              vote.getCurrentRoot(),
              blockRoot,
              false,
              false,
              attestationSlot,
              fullPayloadHint,
              vote.getCurrentSlot(),
              vote.isCurrentFullPayloadHint());
      voteUpdater.putVote(validatorIndex, newVote);
    }
  }

  public void setPruneThreshold(final int pruneThreshold) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.setPruneThreshold(pruneThreshold);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public int getTotalTrackedNodeCount() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getTotalTrackedNodeCount();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean contains(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex.containsBlock(blockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getBaseNode(blockRoot)
          .flatMap(protoArray::getNode)
          .map(ProtoNode::getBlockSlot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> executionBlockNumber(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getExecutionNodeData(blockRoot).map(ProtoNodeData::getExecutionBlockNumber);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getExecutionNodeData(blockRoot).map(ProtoNodeData::getExecutionBlockHash);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> getExecutionGasLimitForBlockRootAndHash(
      final Bytes32 blockRoot, final Bytes32 blockHash) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModelForRoot(blockRoot)
          .flatMap(
              model ->
                  model.getExecutionGasLimitForBlockRootAndHash(
                      protoArray, blockNodeIndex, blockRoot, blockHash));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getBaseNode(blockRoot)
          .flatMap(protoArray::getNode)
          .map(ProtoNode::getParentRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean isFullyValidated(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getBaseNode(blockRoot)
          .flatMap(protoArray::getNode)
          .map(ProtoNode::isFullyValidated)
          .orElse(false);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModelForRoot(blockRoot)
          .flatMap(forkChoiceModel -> getBaseNodeData(forkChoiceModel, blockRoot));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<ProtoNodeData> getBlockData(
      final Bytes32 blockRoot, final ForkChoicePayloadStatus payloadStatus) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getNode(blockRoot, payloadStatus)
          .flatMap(protoArray::getNode)
          .map(ProtoNode::getBlockData);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean shouldExtendPayload(
      final ReadOnlyStore store, final SlotAndBlockRoot slotAndBlockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModel(slotAndBlockRoot.getSlot())
          .shouldExtendPayload(protoArray, blockNodeIndex, store, slotAndBlockRoot.getBlockRoot());
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean shouldBuildOnFull(
      final ReadOnlyStore store, final UInt64 slot, final ForkChoiceNode head) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModel(slot).shouldBuildOnFull(protoArray, blockNodeIndex, slot, head);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> getWeight(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getBaseNode(blockRoot)
          .flatMap(protoArray::getNode)
          .map(ProtoNode::getWeight);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @VisibleForTesting
  void processExecutionPayload(
      final Bytes32 blockRoot,
      final UInt64 blockSlot,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final UInt64 executionGasLimit,
      final boolean isOptimistic) {
    getForkChoiceModel(blockSlot)
        .onExecutionPayload(
            protoArray,
            blockNodeIndex,
            blockRoot,
            executionBlockNumber,
            executionBlockHash,
            executionGasLimit,
            isOptimistic);
    updateParentBestChildAndDescendantForBlockVariants(blockRoot);
  }

  /**
   * Records the latest payload-attestation vote for PTC positions on a given block root.
   *
   * <p>Spec mapping: `on_payload_attestation_message(...)` updates the PTC vote material later
   * consumed by `notify_ptc_messages(...)` and the Gloas head-selection helpers.
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-on_payload_attestation_message
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-notify_ptc_messages
   */
  public void onPtcVote(
      final Bytes32 blockRoot,
      final IntSet ptcPositions,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    protoArrayLock.readLock().lock();
    try {
      getForkChoiceModelForRoot(blockRoot)
          .ifPresent(
              forkChoiceModel ->
                  forkChoiceModel.onPtcVote(
                      blockRoot, ptcPositions, payloadPresent, blobDataAvailable));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex
          .getBaseNode(blockRoot)
          .flatMap(protoArray::getNode)
          .map(ProtoNode::isOptimistic);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
    return getAncestorProtoNode(ForkChoiceNode.createBase(blockRoot), slot)
        .map(ProtoNode::getBlockRoot);
  }

  @Override
  public Optional<ForkChoiceNode> getAncestorNode(final ForkChoiceNode node, final UInt64 slot) {
    return getAncestorProtoNode(node, slot).map(ProtoNode::getForkChoiceNode);
  }

  @Override
  public Optional<ForkChoiceNode> getSupportedNode(
      final UInt64 currentSlot,
      final Bytes32 voteRoot,
      final UInt64 voteSlot,
      final boolean payloadPresent) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModel(currentSlot)
          .getSupportedNode(voteRoot, voteSlot, payloadPresent, protoArray, blockNodeIndex);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<ForkChoiceNode> getParentBeaconBlockNode(final ForkChoiceNode node) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModelForRoot(node.blockRoot())
          .flatMap(forkChoiceModel -> forkChoiceModel.getParentBeaconBlockNode(protoArray, node))
          .map(ProtoNode::getForkChoiceNode);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Boolean> getPayloadTimelinessVote(
      final Bytes32 blockRoot, final int ptcPosition) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModelForRoot(blockRoot)
          .flatMap(
              forkChoiceModel -> forkChoiceModel.getPayloadTimelinessVote(blockRoot, ptcPosition));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Boolean> getPayloadDataAvailabilityVote(
      final Bytes32 blockRoot, final int ptcPosition) {
    protoArrayLock.readLock().lock();
    try {
      return getForkChoiceModelForRoot(blockRoot)
          .flatMap(
              forkChoiceModel ->
                  forkChoiceModel.getPayloadDataAvailabilityVote(blockRoot, ptcPosition));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public UInt64 getPayloadAttesterCount(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return UInt64.valueOf(
          getForkChoiceModelForRoot(blockRoot)
              .map(forkChoiceModel -> forkChoiceModel.getPayloadAttesterCount(blockRoot))
              .orElse(0));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public UInt64 getPayloadAvailabilityYesCount(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return UInt64.valueOf(
          getForkChoiceModelForRoot(blockRoot)
              .map(forkChoiceModel -> forkChoiceModel.getPayloadAvailabilityYesCount(blockRoot))
              .orElse(0));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public UInt64 getPayloadDataAvailabilityYesCount(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return UInt64.valueOf(
          getForkChoiceModelForRoot(blockRoot)
              .map(forkChoiceModel -> forkChoiceModel.getPayloadDataAvailabilityYesCount(blockRoot))
              .orElse(0));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  private Optional<ProtoNode> getAncestorProtoNode(
      final ForkChoiceNode startNode, final UInt64 slot) {
    protoArrayLock.readLock().lock();
    try {
      // Note: This code could be more succinct if currentNode were an Optional and we used flatMap
      // and map but during long periods of finality this becomes a massive hot spot in the code and
      // our performance is dominated by the time taken to create Optional instances within the map
      // calls.
      final Optional<ProtoNode> startingNode = protoArray.getNode(startNode);
      if (startingNode.isEmpty()) {
        return Optional.empty();
      }
      ProtoNode currentNode = startingNode.get();
      while (currentNode.getBlockSlot().isGreaterThan(slot)) {
        final Optional<Integer> parentIndex = currentNode.getParentIndex();
        if (parentIndex.isEmpty()) {
          return Optional.empty();
        }
        currentNode = protoArray.getNodes().get(parentIndex.get());
      }
      return Optional.of(currentNode);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
    protoArrayLock.readLock().lock();
    try {
      return blockNodeIndex.getBlockRootsAtSlot(slot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  /**
   * Process each beacon block in the chain defined by {@code head}.
   *
   * <p>This block-facing API traverses the model's base-node variant mapping, not the internal
   * EMPTY/FULL nodes of the Gloas three-state tree.
   */
  @Override
  public void processBeaconBlockChain(final Bytes32 head, final BeaconBlockProcessor processor) {
    processBeaconBlockChainWhile(
        head, HaltableBeaconBlockProcessor.fromBeaconBlockProcessor(processor));
  }

  /**
   * Process roots in the chain defined by {@code head}. Stops processing when {@code
   * shouldContinue} returns false.
   *
   * @param head The root defining the head of the chain to construct
   * @param beaconBlockProcessor The callback receiving block roots and determining whether to
   *     continue processing
   */
  @Override
  public void processBeaconBlockChainWhile(
      final Bytes32 head, final HaltableBeaconBlockProcessor beaconBlockProcessor) {
    protoArrayLock.readLock().lock();
    try {
      final Optional<ForkChoiceNode> startingNode = blockNodeIndex.getBaseNode(head);
      if (startingNode.isEmpty()) {
        throw new IllegalArgumentException("Unknown root supplied: " + head);
      }
      Optional<ForkChoiceNode> currentNode = startingNode;
      while (currentNode.isPresent()) {
        final ProtoNode baseNode = protoArray.getNode(currentNode.get()).orElseThrow();
        final boolean shouldContinue =
            beaconBlockProcessor.process(
                baseNode.getBlockRoot(), baseNode.getBlockSlot(), baseNode.getParentRoot());
        if (!shouldContinue) {
          break;
        }
        currentNode = blockNodeIndex.getBaseNode(baseNode.getParentRoot());
      }
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public void processAllBeaconBlocksInOrder(final BeaconBlockProcessor beaconBlockProcessor) {
    protoArrayLock.readLock().lock();
    try {
      for (final ProtoNode node : protoArray.getNodes()) {
        if (!isBaseNode(node)) {
          continue;
        }
        beaconBlockProcessor.process(
            node.getBlockRoot(), node.getBlockSlot(), node.getParentRoot());
      }
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public List<ProtoNodeData> getBlockData() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream().map(ProtoNode::getBlockData).toList();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public void applyUpdate(
      final Collection<BlockAndCheckpoints> newBlocks,
      final Map<Bytes32, ExecutionPayloadUpdate> newExecutionPayloads,
      final Collection<Bytes32> pulledUpBlocks,
      final Map<Bytes32, UInt64> removedBlockRoots,
      final Checkpoint finalizedCheckpoint) {
    applyUpdate(
        newBlocks,
        newExecutionPayloads,
        pulledUpBlocks,
        removedBlockRoots,
        finalizedCheckpoint,
        Optional.empty());
  }

  /**
   * Applies hot fork-choice updates, optionally anchoring the finalized execution-payload boundary
   * block before normal block processing.
   *
   * <p>The boundary block is only supplied for Gloas finalization. It lets protoarray keep the
   * finalized block's EMPTY/FULL variants available for hot descendants while pruning older
   * finalized nodes.
   */
  public void applyUpdate(
      final Collection<BlockAndCheckpoints> newBlocks,
      final Map<Bytes32, ExecutionPayloadUpdate> executionPayloads,
      final Collection<Bytes32> pulledUpBlocks,
      final Map<Bytes32, UInt64> removedBlockRoots,
      final Checkpoint finalizedCheckpoint,
      final Optional<BlockAndCheckpoints> finalizedExecutionPayloadBoundaryBlock) {
    protoArrayLock.writeLock().lock();
    try {
      final Map<Bytes32, ExecutionPayloadUpdate> executionPayloadsByRoot =
          new HashMap<>(executionPayloads);
      finalizedExecutionPayloadBoundaryBlock.ifPresent(
          boundaryBlock -> {
            processFinalizedBoundaryBlock(boundaryBlock);
            processExecutionPayload(executionPayloadsByRoot.remove(boundaryBlock.getRoot()));
          });
      newBlocks.stream()
          .sorted(Comparator.comparing(BlockAndCheckpoints::getSlot))
          .filter(
              block ->
                  finalizedExecutionPayloadBoundaryBlock
                      .map(boundaryBlock -> !boundaryBlock.getRoot().equals(block.getRoot()))
                      .orElse(true))
          .forEach(
              block -> {
                processBlock(
                    block.getSlot(),
                    block.getRoot(),
                    block.getParentRoot(),
                    block.getStateRoot(),
                    block.getBlockCheckpoints(),
                    block.getExecutionBlockNumber(),
                    block.getExecutionBlockHash(),
                    block.getExecutionGasLimit());
                processExecutionPayload(executionPayloadsByRoot.remove(block.getRoot()));
              });
      executionPayloadsByRoot.values().forEach(this::processExecutionPayload);
      removedBlockRoots.forEach(
          (root, blockSlot) ->
              getForkChoiceModel(blockSlot).onRemovedBlockRoot(protoArray, blockNodeIndex, root));
      pulledUpBlocks.forEach(
          root ->
              getForkChoiceModelForRoot(root)
                  .ifPresent(
                      forkChoiceModel ->
                          forkChoiceModel.pullUpBlockCheckpoints(
                              protoArray, blockNodeIndex, root)));
      final int sizeBefore = protoArray.getTotalTrackedNodeCount();
      protoArray.maybePrune(ForkChoiceNode.createBase(finalizedCheckpoint.getRoot()));
      blockNodeIndex.removeIf(
          root -> blockNodeIndex.getBaseNode(root).flatMap(protoArray::getNode).isEmpty());
      if (protoArray.getTotalTrackedNodeCount() < sizeBefore) {
        forkChoiceModelFactory.onPrunedBlocks(blockNodeIndex);
      }
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  private void processExecutionPayload(final ExecutionPayloadUpdate executionPayloadUpdate) {
    if (executionPayloadUpdate == null) {
      return;
    }
    final var envelope = executionPayloadUpdate.executionPayload();
    processExecutionPayload(
        envelope.getBeaconBlockRoot(),
        envelope.getSlot(),
        envelope.getMessage().getPayload().getBlockNumber(),
        envelope.getMessage().getPayload().getBlockHash(),
        envelope.getMessage().getPayload().getGasLimit(),
        executionPayloadUpdate.isOptimistic());
  }

  private void processFinalizedBoundaryBlock(final BlockAndCheckpoints block) {
    // A finalized Gloas block can be the last beacon block before execution payload data becomes
    // available to its descendants. Re-adding it as an anchor preserves the fork-choice variants
    // descendants need while still allowing older finalized nodes to be pruned.
    getForkChoiceModel(block.getSlot())
        .processAnchorBlock(
            protoArray,
            blockNodeIndex,
            block.getSlot(),
            block.getRoot(),
            block.getParentRoot(),
            block.getStateRoot(),
            block.getBlockCheckpoints(),
            block.getExecutionBlockNumber(),
            block.getExecutionBlockHash(),
            block.getExecutionGasLimit(),
            spec.isBlockProcessorOptimistic(block.getSlot()));
    updateParentBestChildAndDescendantForBlockVariants(block.getRoot());
  }

  @Override
  public Optional<SlotAndBlockRoot> findCommonAncestor(final Bytes32 root1, final Bytes32 root2) {
    protoArrayLock.readLock().lock();
    try {
      Optional<ProtoNode> chainHead1 =
          blockNodeIndex.getBaseNode(root1).flatMap(protoArray::getNode);
      Optional<ProtoNode> chainHead2 =
          blockNodeIndex.getBaseNode(root2).flatMap(protoArray::getNode);
      while (chainHead1.isPresent() && chainHead2.isPresent()) {
        final ProtoNode node1 = chainHead1.get();
        final ProtoNode node2 = chainHead2.get();
        if (node1.getBlockSlot().isGreaterThan(node2.getBlockSlot())) {
          // Chain 1 is longer than chain 2 so need to move further up chain 2
          chainHead1 =
              blockNodeIndex.getBaseNode(node1.getParentRoot()).flatMap(protoArray::getNode);
        } else if (node2.getBlockSlot().isGreaterThan(node1.getBlockSlot())) {
          // Chain 2 is longer than chain 1 so need to move further up chain 1
          chainHead2 =
              blockNodeIndex.getBaseNode(node2.getParentRoot()).flatMap(protoArray::getNode);
        } else {
          // At the same slot, check if this is the common ancestor
          if (node1.getBlockRoot().equals(node2.getBlockRoot())) {
            return Optional.of(new SlotAndBlockRoot(node1.getBlockSlot(), node1.getBlockRoot()));
          }
          // Nope, need to move further up both chains
          chainHead1 =
              blockNodeIndex.getBaseNode(node1.getParentRoot()).flatMap(protoArray::getNode);
          chainHead2 =
              blockNodeIndex.getBaseNode(node2.getParentRoot()).flatMap(protoArray::getNode);
        }
      }
      // Reached the start of protoarray without finding a common ancestor
      return Optional.empty();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @VisibleForTesting
  void processBlock(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash) {
    processBlock(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        Optional.empty());
  }

  private void processBlock(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final Optional<UInt64> executionBlockNumber,
      final Optional<Bytes32> executionBlockHash,
      final Optional<UInt64> executionGasLimit) {
    getForkChoiceModel(blockSlot)
        .processBlock(
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
            spec.isBlockProcessorOptimistic(blockSlot));
    updateParentBestChildAndDescendantForBlockVariants(blockRoot);
  }

  private void updateParentBestChildAndDescendantForBlockVariants(final Bytes32 blockRoot) {
    blockNodeIndex
        .getVariants(blockRoot)
        .ifPresent(
            variants ->
                variants
                    .allNodes()
                    .forEach(
                        nodeIdentity ->
                            protoArray.updateBestChildAndDescendantOfParent(
                                nodeIdentity, headSelectionContext)));
  }

  private Optional<ProtoNodeData> getExecutionNodeData(final Bytes32 blockRoot) {
    return getForkChoiceModelForRoot(blockRoot)
        .flatMap(model -> model.getExecutionNodeData(protoArray, blockNodeIndex, blockRoot));
  }

  private Optional<ProtoNodeData> getBaseNodeData(final Bytes32 blockRoot) {
    return getForkChoiceModelForRoot(blockRoot)
        .flatMap(forkChoiceModel -> getBaseNodeData(forkChoiceModel, blockRoot));
  }

  private Optional<ProtoNodeData> getBaseNodeData(
      final ForkChoiceModel forkChoiceModel, final Bytes32 blockRoot) {
    return forkChoiceModel.getBaseNodeData(protoArray, blockNodeIndex, blockRoot);
  }

  private boolean isBaseNode(final ProtoNode node) {
    return blockNodeIndex.isBaseNode(node.getForkChoiceNode());
  }

  public void onExecutionPayloadResult(
      final Bytes32 blockRoot,
      final PayloadStatus result,
      final boolean verifiedInvalidTransition) {
    if (result.hasFailedExecution()) {
      LOG.warn(
          "Unable to execute Payload for block root {}, Execution Engine is offline",
          blockRoot,
          result.getFailureCause().orElseThrow());
      return;
    }
    final ExecutionPayloadStatus status = result.getStatus().orElseThrow();
    if (status.isNotValidated()) {
      return;
    }
    protoArrayLock.writeLock().lock();
    try {
      getForkChoiceModelForRoot(blockRoot)
          .ifPresent(
              forkChoiceModel -> {
                if (status.isInvalid()) {
                  LOG.warn(
                      "Payload for {} block root {} marked as invalid by Execution Client",
                      verifiedInvalidTransition ? "" : "child of",
                      blockRoot);
                }
                forkChoiceModel.onExecutionPayloadResult(
                    protoArray,
                    blockNodeIndex,
                    blockRoot,
                    status,
                    result.getLatestValidHash(),
                    verifiedInvalidTransition,
                    headSelectionContext);
              });
      if (status.isInvalid()) {
        blockNodeIndex.removeIf(
            root -> blockNodeIndex.getBaseNode(root).flatMap(protoArray::getNode).isEmpty());
      }
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public void onForkChoiceUpdatedResult(
      final SlotAndForkChoiceNode slotAndForkChoiceNode,
      final PayloadStatus result,
      final boolean verifiedInvalidTransition) {
    if (result.hasFailedExecution()) {
      LOG.warn(
          "Unable to execute Payload for {}, Execution Engine is offline",
          slotAndForkChoiceNode,
          result.getFailureCause().orElseThrow());
      return;
    }
    final ExecutionPayloadStatus status = result.getStatus().orElseThrow();
    if (status.isNotValidated()) {
      return;
    }
    protoArrayLock.writeLock().lock();
    try {
      final ForkChoiceModel forkChoiceModel = getForkChoiceModel(slotAndForkChoiceNode.slot());
      if (status.isInvalid()) {
        LOG.warn(
            "Payload for {} {} marked as invalid by Execution Client",
            verifiedInvalidTransition ? "" : "child of",
            slotAndForkChoiceNode.node());
      }
      forkChoiceModel.onForkChoiceUpdatedResult(
          protoArray,
          blockNodeIndex,
          slotAndForkChoiceNode.node(),
          status,
          result.getLatestValidHash(),
          verifiedInvalidTransition,
          headSelectionContext);
      if (status.isInvalid()) {
        blockNodeIndex.removeIf(
            root -> blockNodeIndex.getBaseNode(root).flatMap(protoArray::getNode).isEmpty());
      }
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }
}

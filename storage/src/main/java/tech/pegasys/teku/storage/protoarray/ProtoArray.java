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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.INVALID;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.OPTIMISTIC;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.VALID;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ProtoArray {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private int pruneThreshold;

  private UInt64 currentEpoch;
  private Checkpoint justifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;
  // The epoch of our initial startup state
  // When starting from genesis, this value is zero (genesis epoch)
  private final UInt64 initialEpoch;
  private final StatusLogger statusLog;

  /**
   * Lists all the known nodes. It is guaranteed that a node will be after its parent in the list.
   *
   * <p>The list may contain nodes which have been removed from the indices collection either
   * because they are now before the finalized checkpoint but pruning has not yet occurred or
   * because they extended from a now-invalid chain and were removed. This avoids having to update
   * the indices to entries in the list too often.
   */
  private final List<ProtoNode> nodes = new ArrayList<>();

  /**
   * protoArrayIndices allows root lookup to retrieve indices of protoNodes without looking through
   * the nodes list
   *
   * <p>Needs to be Maintained when nodes are added or removed from the nodes list.
   */
  private final ProtoArrayIndices indices = new ProtoArrayIndices();

  ProtoArray(
      final Spec spec,
      final int pruneThreshold,
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final UInt64 initialEpoch,
      final StatusLogger statusLog) {
    this.spec = spec;
    this.pruneThreshold = pruneThreshold;
    this.currentEpoch = currentEpoch;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.initialEpoch = initialEpoch;
    this.statusLog = statusLog;
  }

  public static ProtoArrayBuilder builder() {
    return new ProtoArrayBuilder();
  }

  public boolean containsNode(final ForkChoiceNode node) {
    return indices.contains(node);
  }

  public Optional<Integer> getNodeIndex(final ForkChoiceNode node) {
    return indices.get(node);
  }

  public Optional<ProtoNode> getNode(final ForkChoiceNode node) {
    return indices
        .get(node)
        .flatMap(
            nodeIndex -> {
              if (nodeIndex < getTotalTrackedNodeCount()) {
                return Optional.of(getNodeByIndex(nodeIndex));
              }
              return Optional.empty();
            });
  }

  public List<ProtoNode> getNodes() {
    return nodes;
  }

  public void setPruneThreshold(final int pruneThreshold) {
    this.pruneThreshold = pruneThreshold;
  }

  /**
   * Add a node to the fork-choice tree using explicit node identities.
   *
   * <p>The fork-aware model layer is responsible for deciding which node identity to create and
   * which parent node identity it should attach to.
   */
  public void addNode(
      final ForkChoiceNode nodeIdentity,
      final UInt64 blockSlot,
      final Bytes32 parentRoot,
      final Optional<ForkChoiceNode> parentNodeIdentity,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final boolean optimisticallyProcessed) {
    addNode(
        nodeIdentity,
        blockSlot,
        parentRoot,
        parentNodeIdentity,
        stateRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        ProtoNode.NO_EXECUTION_GAS_LIMIT,
        optimisticallyProcessed);
  }

  public void addNode(
      final ForkChoiceNode nodeIdentity,
      final UInt64 blockSlot,
      final Bytes32 parentRoot,
      final Optional<ForkChoiceNode> parentNodeIdentity,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash,
      final UInt64 executionGasLimit,
      final boolean optimisticallyProcessed) {
    if (indices.contains(nodeIdentity)) {
      return;
    }

    int nodeIndex = getTotalTrackedNodeCount();

    ProtoNode node =
        new ProtoNode(
            nodeIdentity,
            blockSlot,
            stateRoot,
            parentRoot,
            parentNodeIdentity.flatMap(indices::get),
            checkpoints,
            executionBlockNumber,
            executionBlockHash,
            executionGasLimit,
            UInt64.ZERO,
            Optional.empty(),
            Optional.empty(),
            optimisticallyProcessed && !executionBlockHash.isZero() ? OPTIMISTIC : VALID);

    indices.add(nodeIdentity, nodeIndex);
    nodes.add(node);
  }

  public void updateBestChildAndDescendantOfParent(
      final ForkChoiceNode nodeIdentity, final HeadSelectionContext headSelectionContext) {
    getNodeIndex(nodeIdentity)
        .ifPresent(
            nodeIndex ->
                updateBestChildAndDescendantOfParent(
                    getNodeByIndex(nodeIndex), nodeIndex, headSelectionContext));
  }

  public void setInitialCanonicalBlockRoot(
      final Bytes32 initialCanonicalBlockRoot, final HeadSelectionContext headSelectionContext) {
    final Optional<ProtoNode> initialCanonicalProtoNode =
        getNode(ForkChoiceNode.createBase(initialCanonicalBlockRoot));
    if (initialCanonicalProtoNode.isEmpty()) {
      LOG.warn("Initial canonical block root not found: {}", initialCanonicalBlockRoot);
      return;
    }

    applyToNodes(
        (protoNode, nodeIndex) ->
            updateBestChildAndDescendantOfParent(protoNode, nodeIndex, headSelectionContext));

    // let's peak the best descendant of the initial canonical block root
    ProtoNode node =
        initialCanonicalProtoNode
            .get()
            .getBestDescendantIndex()
            .map(this::getNodeByIndex)
            .orElse(initialCanonicalProtoNode.get());

    // add a single weight to from the best descendant up to the root
    node.adjustWeight(1);
    while (node.getParentIndex().isPresent()) {
      final ProtoNode parent = getNodeByIndex(node.getParentIndex().get());
      parent.adjustWeight(1);
      node = parent;
    }

    applyToNodes(
        (protoNode, nodeIndex) ->
            updateBestChildAndDescendantOfParent(protoNode, nodeIndex, headSelectionContext));
  }

  /**
   * Follows the best-descendant links to find the best-block (i.e. head-block), including any
   * optimistic nodes which have not yet been fully validated.
   *
   * @param currentEpoch the current epoch according to the Store current time
   * @param justifiedCheckpoint the justified checkpoint
   * @param finalizedCheckpoint the finalized checkpoint
   * @return the best node according to fork choice
   */
  public ProtoNode findOptimisticHead(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final HeadSelectionContext headSelectionContext) {
    return findHead(currentEpoch, justifiedCheckpoint, finalizedCheckpoint, headSelectionContext)
        .orElseThrow(fatalException("Finalized block was found to be invalid."));
  }

  public Optional<ProtoNode> findOptimisticallySyncedMergeTransitionBlock(
      final ForkChoiceNode head) {
    final Optional<ProtoNode> maybeStartingNode = getNode(head);
    if (maybeStartingNode.isEmpty()) {
      return Optional.empty();
    }
    ProtoNode currentNode = maybeStartingNode.get();
    if (currentNode.getExecutionBlockHash().isZero()) {
      // Transition not yet reached so no transition block
      return Optional.empty();
    }
    while (containsNode(currentNode.getForkChoiceNode())) {
      if (currentNode.getParentIndex().isEmpty() || currentNode.isFullyValidated()) {
        // Stop searching when we reach fully validated nodes or a node we don't have the parent for
        return Optional.empty();
      }
      final ProtoNode parentNode = getNodeByIndex(currentNode.getParentIndex().get());
      if (parentNode.getExecutionBlockHash().isZero()) {
        return Optional.of(currentNode);
      }
      currentNode = parentNode;
    }
    return Optional.empty();
  }

  private Supplier<FatalServiceFailureException> fatalException(final String message) {
    return () -> new FatalServiceFailureException("fork choice", message);
  }

  private Optional<ProtoNode> findHead(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final HeadSelectionContext headSelectionContext) {
    if (!this.currentEpoch.equals(currentEpoch)
        || !this.justifiedCheckpoint.equals(justifiedCheckpoint)
        || !this.finalizedCheckpoint.equals(finalizedCheckpoint)) {
      this.currentEpoch = currentEpoch;
      this.justifiedCheckpoint = justifiedCheckpoint;
      this.finalizedCheckpoint = finalizedCheckpoint;
      // Justified or finalized epoch changed so we have to re-evaluate all best descendants.
      applyToNodes(
          (node, nodeIndex) ->
              updateBestChildAndDescendantOfParent(node, nodeIndex, headSelectionContext));
    }
    int justifiedIndex =
        indices
            .get(ForkChoiceNode.createBase(justifiedCheckpoint.getRoot()))
            .orElseThrow(
                fatalException(
                    "Invalid or unknown justified root: " + justifiedCheckpoint.getRoot()));

    ProtoNode justifiedNode = getNodeByIndex(justifiedIndex);

    if (justifiedNode.isInvalid()) {
      return Optional.empty();
    }

    int bestDescendantIndex = justifiedNode.getBestDescendantIndex().orElse(justifiedIndex);
    ProtoNode bestNode = getNodeByIndex(bestDescendantIndex);

    // Normally the best descendant index would point straight to chain head, but onBlock /
    // onExecutionPayload only update the immediate parent, not all the ancestors. When
    // applyScoreChanges runs it propagates the change back up and everything works, but we run
    // findHead to determine if the new block should become the best head so need to follow down
    // the chain.
    //
    // After each descent step, ask the per-slot fork-choice model to redirect to the
    // model-preferred sibling (e.g. GLOAS BASE.bestChild flipping EMPTY→FULL after
    // onExecutionPayload). This mirrors the spec's get_head semantics, which picks the preferred
    // child at every level rather than just at the leaf.
    bestNode = headSelectionContext.resolveBestDescendant(bestNode, this);
    while (bestNode.getBestDescendantIndex().isPresent() && !bestNode.isInvalid()) {
      bestDescendantIndex = bestNode.getBestDescendantIndex().get();
      bestNode =
          headSelectionContext.resolveBestDescendant(getNodeByIndex(bestDescendantIndex), this);
    }

    // Walk backwards to find the last valid node in the chain
    while (bestNode.isInvalid()) {
      final Optional<Integer> maybeParentIndex = bestNode.getParentIndex();
      if (maybeParentIndex.isEmpty()) {
        // No node on this chain with sufficient validity.
        return Optional.empty();
      }
      final int parentIndex = maybeParentIndex.get();
      bestNode = getNodeByIndex(parentIndex);
    }

    // Perform a sanity check that the node is indeed valid to be the head. The justified block is
    // always accepted as the fallback head, in any of its payload-status variants.
    if (!nodeIsViableForHead(bestNode)
        && !bestNode.getBlockRoot().equals(justifiedNode.getBlockRoot())) {
      throw new IllegalStateException(
          "ProtoArray: Best node " + bestNode.toLogString() + " is not viable for head");
    }
    return Optional.of(bestNode);
  }

  public void markNodeValid(final ForkChoiceNode nodeIdentity) {
    final Optional<ProtoNode> maybeNode = getNode(nodeIdentity);
    if (maybeNode.isEmpty()) {
      // Most likely just pruned prior to the validation result being received.
      LOG.debug("Couldn't mark node {} valid because it was unknown", nodeIdentity);
      return;
    }
    final ProtoNode node = maybeNode.get();
    node.setValidationStatus(VALID);

    Optional<Integer> parentIndex = node.getParentIndex();
    while (parentIndex.isPresent()) {
      final ProtoNode parentNode = getNodeByIndex(parentIndex.get());
      if (parentNode.isFullyValidated()) {
        break;
      }
      parentNode.setValidationStatus(VALID);
      parentIndex = parentNode.getParentIndex();
    }
  }

  /**
   * Marks blockRoot node and all found ancestors up to a node containing block with
   * `latestValidHash` (exclusive) execution block as INVALID. If node with `latestValidHash` is
   * found it's marked as VALID along with its ancestors
   *
   * @param nodeIdentity Transition node
   * @param latestValidHash Latest valid hash of execution block
   */
  public void markNodeInvalid(
      final ForkChoiceNode nodeIdentity,
      final Optional<Bytes32> latestValidHash,
      final HeadSelectionContext headSelectionContext) {
    markNodeInvalid(nodeIdentity, latestValidHash, true, headSelectionContext);
  }

  /**
   * Tries to find an INVALID chain starting from `blockRoot` up to a node containing block with
   * `latestValidHash` (exclusive), if such chain segment is found it's marked as INVALID, while
   * node containing `latestValidHash` and its ancestors are marked as VALID. If such chain segment
   * is not found, no changes are applied.
   *
   * @param nodeIdentity Parent of the node with INVALID execution block
   * @param latestValidHash Latest valid hash of execution block
   */
  public void markParentChainInvalid(
      final ForkChoiceNode nodeIdentity,
      final Optional<Bytes32> latestValidHash,
      final HeadSelectionContext headSelectionContext) {
    markNodeInvalid(nodeIdentity, latestValidHash, false, headSelectionContext);
  }

  private void markNodeInvalid(
      final ForkChoiceNode nodeIdentity,
      final Optional<Bytes32> latestValidHash,
      final boolean verifiedInvalidTransition,
      final HeadSelectionContext headSelectionContext) {
    if (!verifiedInvalidTransition && latestValidHash.isEmpty()) {
      // Couldn't find invalid chain segment with lack of data
      return;
    }
    final Optional<Integer> maybeIndex = indices.get(nodeIdentity);
    if (maybeIndex.isEmpty()) {
      LOG.debug("Couldn't update status for node {} because it was unknown", nodeIdentity);
      return;
    }
    final int index;
    final ProtoNode node;
    if (latestValidHash.isPresent()) {
      final Optional<Integer> maybeFirstInvalidNodeIndex =
          findFirstInvalidNodeIndex(maybeIndex.get(), latestValidHash.get());
      if (!verifiedInvalidTransition) {
        if (nodeHasExecutionHash(maybeIndex.get(), latestValidHash.get())) {
          // Nothing to do: head blockRoot contains payload with latestValidHash
          return;
        }
        if (maybeFirstInvalidNodeIndex.isEmpty()) {
          // Nothing to do: latestValidHash was not found, no proof of invalid transition
          return;
        }
      }

      index =
          maybeFirstInvalidNodeIndex.orElseGet(
              () -> findExecutionPayloadOwnerIndex(maybeIndex.get()));
      node = getNodeByIndex(index);
    } else {
      index = findExecutionPayloadOwnerIndex(maybeIndex.get());
      node = getNodeByIndex(index);
    }

    node.setValidationStatus(INVALID);
    removeNode(node.getForkChoiceNode());
    markDescendantsAsInvalid(index);
    // Applying zero deltas causes the newly marked INVALID nodes to have their weight set to 0
    applyDeltas(
        new LongArrayList(Collections.nCopies(getTotalTrackedNodeCount(), 0L)),
        headSelectionContext);
  }

  /**
   * Resolves the node that owns the execution payload of the node at {@code nodeIndex}: the topmost
   * ancestor carrying the same execution block hash.
   *
   * <p>Pre-Gloas every block owns its own payload, so this is the node itself. In Gloas, BASE and
   * EMPTY nodes own no payload and inherit the execution block hash of the nearest FULL ancestor.
   * An INVALID verdict that cannot be resolved through a latest valid hash refers to that inherited
   * payload, so it must be applied to the FULL ancestor (or the protoarray root) and everything
   * built on it, rather than to the variant node that happened to be sent to the execution client.
   */
  private int findExecutionPayloadOwnerIndex(final int nodeIndex) {
    int ownerIndex = nodeIndex;
    ProtoNode owner = getNodeByIndex(ownerIndex);
    final Bytes32 executionBlockHash = owner.getExecutionBlockHash();
    if (executionBlockHash.isZero()) {
      return nodeIndex;
    }
    while (owner.getParentIndex().isPresent()) {
      final int parentIndex = owner.getParentIndex().get();
      final ProtoNode parent = getNodeByIndex(parentIndex);
      if (!parent.getExecutionBlockHash().equals(executionBlockHash)) {
        break;
      }
      ownerIndex = parentIndex;
      owner = parent;
    }
    return ownerIndex;
  }

  private boolean nodeHasExecutionHash(final int nodeIndex, final Bytes32 executionHash) {
    return getNodeByIndex(nodeIndex).getExecutionBlockHash().equals(executionHash);
  }

  private Optional<Integer> findFirstInvalidNodeIndex(
      final int invalidNodeIndex, final Bytes32 latestValidHash) {
    int firstInvalidNodeIndex = invalidNodeIndex;
    Optional<Integer> parentIndex = Optional.of(invalidNodeIndex);
    while (parentIndex.isPresent()) {
      final ProtoNode parentNode = getNodeByIndex(parentIndex.get());
      if (parentNode.getExecutionBlockHash().equals(latestValidHash)) {
        return Optional.of(firstInvalidNodeIndex);
      }
      firstInvalidNodeIndex = parentIndex.get();
      parentIndex = parentNode.getParentIndex();
    }
    // Couldn't find the last valid hash - so can't take advantage of it.
    // Alert this user as it may indicate that invalid payloads have been finalized
    // (or the EL client is malfunctioning somehow).
    statusLog.unknownLatestValidHash(latestValidHash);
    return Optional.empty();
  }

  private void markDescendantsAsInvalid(final int index) {
    final IntSet invalidParents = new IntOpenHashSet();
    invalidParents.add(index);
    // Need to mark all nodes extending from this one as invalid
    // Descendant nodes must be later in the array so can start from next index
    for (int i = index + 1; i < nodes.size(); i++) {
      final ProtoNode possibleDescendant = getNodeByIndex(i);
      if (possibleDescendant.getParentIndex().isEmpty()) {
        continue;
      }
      if (invalidParents.contains((int) possibleDescendant.getParentIndex().get())) {
        possibleDescendant.setValidationStatus(INVALID);
        removeNode(possibleDescendant.getForkChoiceNode());
        invalidParents.add(i);
      }
    }
  }

  /**
   * Iterate backwards through the array, touching all nodes and their parents and potentially the
   * bestChildIndex of each parent.
   *
   * <p>NOTE: this function should only throw exceptions when validating the parameters. Once we
   * start updating the protoarray we should not throw exceptions because we are currently not able
   * to rollback the changes. See {@link ForkChoiceStrategy#applyPendingVotes}.
   *
   * <p>The structure of the `nodes` array ensures that the child of each node is always touched
   * before its parent.
   *
   * <p>For each node, the following is done:
   *
   * <ul>
   *   <li>Update the node's weight with the corresponding delta.
   *   <li>Back-propagate each node's delta to its parents delta.
   *   <li>Compare the current node with the parents best child, updating it if the current node
   *       should become the best child.
   *   <li>If required, update the parents best descendant with the current node or its best
   *       descendant.
   * </ul>
   */
  public void applyScoreChanges(
      final LongList deltas,
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final HeadSelectionContext headSelectionContext) {
    checkArgument(
        deltas.size() == getTotalTrackedNodeCount(),
        "ProtoArray: Invalid delta length expected %s but got %s",
        getTotalTrackedNodeCount(),
        deltas.size());

    this.currentEpoch = currentEpoch;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;

    applyDeltas(deltas, headSelectionContext);
  }

  public int getTotalTrackedNodeCount() {
    return nodes.size();
  }

  /**
   * Update the tree with new finalization information. The tree is only actually pruned if both of
   * the two following criteria are met:
   *
   * <ul>
   *   <li>The supplied finalized epoch and root are different to the current values.
   *   <li>The number of nodes in `this` is at least `this.pruneThreshold`.
   * </ul>
   */
  public void maybePrune(final ForkChoiceNode finalizedNode) {
    int finalizedIndex =
        indices
            .get(finalizedNode)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "ProtoArray: Finalized node is unknown " + finalizedNode));

    if (finalizedIndex < pruneThreshold) {
      // Pruning at small numbers incurs more cost than benefit.
      return;
    }

    // Remove the `indices` key/values for all the to-be-deleted nodes.
    for (int nodeIndex = 0; nodeIndex < finalizedIndex; nodeIndex++) {
      indices.remove(getNodeByIndex(nodeIndex).getForkChoiceNode());
    }

    // Drop all the nodes prior to finalization.
    nodes.subList(0, finalizedIndex).clear();

    indices.offsetIndices(finalizedIndex);

    // Iterate through all the existing nodes and adjust their indices to match the
    // new layout of nodes.
    for (ProtoNode node : nodes) {
      node.getParentIndex()
          .ifPresent(
              parentIndex -> {
                // If node.parentIndex is less than finalizedIndex, set is to None.
                if (parentIndex < finalizedIndex) {
                  node.setParentIndex(Optional.empty());
                } else {
                  node.setParentIndex(Optional.of(parentIndex - finalizedIndex));
                }
              });

      node.getBestChildIndex()
          .ifPresent(
              bestChildIndex -> {
                int newBestChildIndex = bestChildIndex - finalizedIndex;
                checkState(
                    newBestChildIndex >= 0, "ProtoArray: New best child index is less than 0");
                node.setBestChildIndex(Optional.of(newBestChildIndex));
              });

      node.getBestDescendantIndex()
          .ifPresent(
              bestDescendantIndex -> {
                int newBestDescendantIndex = bestDescendantIndex - finalizedIndex;
                checkState(
                    newBestDescendantIndex >= 0,
                    "ProtoArray: New best descendant index is less than 0");
                node.setBestDescendantIndex(Optional.of(newBestDescendantIndex));
              });
    }
  }

  /**
   * Observe the parent at `parentIndex` with respect to the child at `childIndex` and potentially
   * modify the `parent.bestChild` and `parent.bestDescendant` values.
   *
   * <p>## Detail
   *
   * <p>There are four outcomes:
   *
   * <ul>
   *   <li>The child is already the best child but it's now invalid due to a FFG change and should
   *       be removed.
   *   <li>The child is already the best child and the parent is updated with the new best
   *       descendant.
   *   <li>The child is not the best child but becomes the best child.
   *   <li>The child is not the best child and does not become the best child.
   * </ul>
   */
  @SuppressWarnings("StatementWithEmptyBody")
  private void maybeUpdateBestChildAndDescendant(
      final int parentIndex,
      final int childIndex,
      final HeadSelectionContext headSelectionContext) {
    ProtoNode child = getNodeByIndex(childIndex);
    ProtoNode parent = getNodeByIndex(parentIndex);

    boolean childLeadsToViableHead = nodeLeadsToViableHead(child);

    parent
        .getBestChildIndex()
        .ifPresentOrElse(
            bestChildIndex -> {
              if (bestChildIndex.equals(childIndex) && !childLeadsToViableHead) {
                // If the child is already the best-child of the parent but it's not viable for
                // the head, remove it.
                changeToNone(parent);
              } else if (bestChildIndex.equals(childIndex)) {
                // If the child is the best-child already, set it again to ensure that the
                // best-descendant of the parent is updated.
                changeToChild(parent, childIndex);
              } else {
                ProtoNode bestChild = getNodeByIndex(bestChildIndex);

                boolean bestChildLeadsToViableHead = nodeLeadsToViableHead(bestChild);

                if (childLeadsToViableHead && !bestChildLeadsToViableHead) {
                  // The child leads to a viable head, but the current best-child doesn't.
                  changeToChild(parent, childIndex);
                } else if (!childLeadsToViableHead && bestChildLeadsToViableHead) {
                  // The best child leads to a viable head, but the child doesn't.
                  // No change.
                } else {
                  final int childComparison =
                      headSelectionContext.compareViableChildren(child, bestChild, parent, this);
                  if (childComparison > 0) {
                    changeToChild(parent, childIndex);
                  } else {
                    // No change.
                  }
                }
              }
            },
            () -> {
              if (childLeadsToViableHead) {
                // There is no current best-child and the child is viable.
                changeToChild(parent, childIndex);
              } else {
                // There is no current best-child but the child is not not viable.
                // No change.
              }
            });
  }

  /** Helper for maybeUpdateBestChildAndDescendant */
  private void changeToChild(final ProtoNode parent, final int childIndex) {
    ProtoNode child = getNodeByIndex(childIndex);
    parent.setBestChildIndex(Optional.of(childIndex));
    parent.setBestDescendantIndex(Optional.of(child.getBestDescendantIndex().orElse(childIndex)));
  }

  /** Helper for maybeUpdateBestChildAndDescendant */
  private void changeToNone(final ProtoNode parent) {
    parent.setBestChildIndex(Optional.empty());
    parent.setBestDescendantIndex(Optional.empty());
  }

  /**
   * Indicates if the node itself is viable for the head, or if it's best descendant is viable for
   * the head.
   */
  private boolean nodeLeadsToViableHead(final ProtoNode node) {
    if (nodeIsViableForHead(node)) {
      return true;
    }

    return node.getBestDescendantIndex()
        .map(this::getNodeByIndex)
        .map(this::nodeIsViableForHead)
        .orElse(false);
  }

  /**
   * This is the equivalent to the <a
   * href="https://github.com/ethereum/consensus-specs/blob/v0.10.0/specs/phase0/fork-choice.md#filter_block_tree">filter_block_tree</a>
   * function in the eth2 spec:
   *
   * <p>Any node that has a different finalized or justified epoch should not be viable for the
   * head.
   */
  public boolean nodeIsViableForHead(final ProtoNode node) {
    if (node.isInvalid()) {
      return false;
    }

    // The voting source should be either at the same height as the store's justified checkpoint or
    // not more than two epochs ago
    if (!isVotingSourceWithinAcceptableRange(
        node.getJustifiedCheckpoint().getEpoch(), justifiedCheckpoint.getEpoch())) {
      return false;
    }

    return node.getFinalizedCheckpoint().getEpoch().equals(initialEpoch)
        || isFinalizedRootOrDescendant(node);
  }

  private boolean isFinalizedRootOrDescendant(final ProtoNode node) {
    final UInt64 finalizedEpoch = finalizedCheckpoint.getEpoch();
    final Bytes32 finalizedRoot = finalizedCheckpoint.getRoot();

    final Checkpoint nodeFinalizedCheckpoint = node.getFinalizedCheckpoint();
    if (nodeFinalizedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeFinalizedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final Checkpoint nodeJustifiedCheckpoint = node.getJustifiedCheckpoint();
    if (nodeJustifiedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeJustifiedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final Checkpoint nodeUnrealizedFinalizedCheckpoint = node.getUnrealizedFinalizedCheckpoint();
    if (nodeUnrealizedFinalizedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeUnrealizedFinalizedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final Checkpoint nodeUnrealizedJustifiedCheckpoint = node.getUnrealizedJustifiedCheckpoint();
    if (nodeUnrealizedJustifiedCheckpoint.getEpoch().equals(finalizedEpoch)
        && nodeUnrealizedJustifiedCheckpoint.getRoot().equals(finalizedRoot)) {
      return true;
    }

    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedCheckpoint.getEpoch());
    return hasAncestorAtSlot(node, finalizedSlot, finalizedRoot);
  }

  /**
   * This is similar to the <a
   * href="https://github.com/ethereum/consensus-specs/blob/master/specs/phase0/fork-choice.md#get_ancestor">get_ancestor</a>
   * function in the eth2 spec.
   *
   * <p>The difference is that this is checking if the ancestor at slot is the required one.
   */
  private boolean hasAncestorAtSlot(
      final ProtoNode start, final UInt64 finalizedSlot, final Bytes32 requiredRoot) {
    ProtoNode node = start;
    while (node != null && node.getBlockSlot().isGreaterThan(finalizedSlot)) {
      node = node.getParentIndex().map(this::getNodeByIndex).orElse(null);
    }
    return node != null && requiredRoot.equals(node.getBlockRoot());
  }

  private boolean isVotingSourceWithinAcceptableRange(
      final UInt64 nodeJustifiedEpoch, final UInt64 currentJustifiedEpoch) {
    return currentJustifiedEpoch.equals(initialEpoch)
        || nodeJustifiedEpoch.equals(currentJustifiedEpoch)
        || nodeJustifiedEpoch.plus(2).isGreaterThanOrEqualTo(currentEpoch);
  }

  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  /**
   * Removes a node from the lookup map. The actual node is not removed from the protoarray to avoid
   * recalculating indices. As a result, looking up the node by identity will not find it but it may
   * still be "found" when iterating through all nodes or following links to parent or ancestor
   * nodes.
   */
  public void removeNode(final ForkChoiceNode nodeIdentity) {
    indices.remove(nodeIdentity);
  }

  public void pullUpCheckpoints(final ForkChoiceNode nodeIdentity) {
    getNode(nodeIdentity).ifPresent(ProtoNode::pullUpCheckpoints);
  }

  private void applyDeltas(final LongList deltas, final HeadSelectionContext headSelectionContext) {
    applyToNodes((node, nodeIndex) -> applyDelta(deltas, node, nodeIndex));
    applyToNodes(
        (node, nodeIndex) ->
            updateBestChildAndDescendantOfParent(node, nodeIndex, headSelectionContext));
  }

  private void updateBestChildAndDescendantOfParent(
      final ProtoNode node, final int nodeIndex, final HeadSelectionContext headSelectionContext) {
    node.getParentIndex()
        .ifPresent(
            parentIndex ->
                maybeUpdateBestChildAndDescendant(parentIndex, nodeIndex, headSelectionContext));
  }

  private void applyDelta(final LongList deltas, final ProtoNode node, final int nodeIndex) {
    // If the node is invalid, remove any existing weight.
    long nodeDelta = node.isInvalid() ? -node.getWeight().longValue() : deltas.getLong(nodeIndex);
    node.adjustWeight(nodeDelta);

    if (node.getParentIndex().isPresent()) {
      int parentIndex = node.getParentIndex().get();
      deltas.set(parentIndex, deltas.getLong(parentIndex) + nodeDelta);
    }
  }

  private void applyToNodes(final NodeVisitor action) {
    for (int nodeIndex = getTotalTrackedNodeCount() - 1; nodeIndex >= 0; nodeIndex--) {
      final ProtoNode node = getNodeByIndex(nodeIndex);

      // No point processing the genesis block.
      if (node.getBlockRoot().equals(Bytes32.ZERO)) {
        continue;
      }
      action.onNode(node, nodeIndex);
    }
  }

  Object2IntMap<ForkChoiceNode> getNodeIndices() {
    return indices.getNodeIndices();
  }

  ProtoNode getNodeByIndex(final int index) {
    return checkNotNull(nodes.get(index), "Missing node %s", index);
  }

  private interface NodeVisitor {
    void onNode(ProtoNode node, int nodeIndex);
  }
}

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

import static java.lang.Math.addExact;
import static java.lang.Math.subtractExact;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;

class ProtoArrayScoreCalculator {

  /**
   * Returns one delta per protoarray index.
   *
   * <p>Each delta captures the net score change for that node identity after applying vote moves,
   * balance changes, and proposer-boost updates between the old and new forkchoice views.
   *
   * <p>The supplied {@code getIndexByNode} function must resolve any node identity referenced by
   * the current votes or proposer-boost nodes to a valid protoarray index.
   */
  static LongList computeDeltas(
      final VoteUpdater store,
      final int protoArraySize,
      final Function<ForkChoiceNode, Optional<Integer>> getIndexByNode,
      final Optional<ForkChoiceNode> previousProposerBoostNode,
      final Optional<ForkChoiceNode> newProposerBoostNode,
      final List<UInt64> oldBalances,
      final List<UInt64> newBalances,
      final UInt64 previousBoostAmount,
      final UInt64 newBoostAmount,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final ForkChoiceModel forkChoiceModel) {
    final LongList deltas = new LongArrayList(Collections.nCopies(protoArraySize, 0L));

    UInt64.rangeClosed(UInt64.ZERO, store.getHighestVotedValidatorIndex())
        .forEach(
            validatorIndex ->
                computeDelta(
                    store,
                    oldBalances,
                    newBalances,
                    deltas,
                    validatorIndex,
                    protoArray,
                    blockNodeIndex,
                    forkChoiceModel,
                    getIndexByNode));

    previousProposerBoostNode.ifPresent(
        node -> subtractBalance(getIndexByNode, deltas, node, previousBoostAmount));
    newProposerBoostNode.ifPresent(
        node -> addBalance(getIndexByNode, deltas, node, newBoostAmount));
    return deltas;
  }

  private static void computeDelta(
      final VoteUpdater store,
      final List<UInt64> oldBalances,
      final List<UInt64> newBalances,
      final LongList deltas,
      final UInt64 validatorIndex,
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final ForkChoiceModel forkChoiceModel,
      final Function<ForkChoiceNode, Optional<Integer>> getIndexByNode) {
    final VoteTracker vote = store.getVote(validatorIndex);

    // There is no need to create a score change if the validator has never voted
    // or both their votes are for the zero hash (alias to the genesis block).
    if (vote.getCurrentRoot().equals(Bytes32.ZERO) && vote.getNextRoot().equals(Bytes32.ZERO)) {
      return;
    }
    // If vote is already count as equivocated, we don't need to do anything more.
    if (vote.isCurrentEquivocating()) {
      return;
    }

    final int validatorIndexInt = validatorIndex.intValue();
    // If the validator was not included in the oldBalances (i.e. it did not exist yet)
    // then say its balance was zero.
    final UInt64 oldBalance =
        validatorIndexInt < oldBalances.size() ? oldBalances.get(validatorIndexInt) : UInt64.ZERO;
    // If the validator vote is not known in the newBalances, then use a balance of zero.
    // It is possible that there is a vote for an unknown validator if we change our
    // justified state to a new state with a higher epoch that is on a different fork
    // because that may have on-boarded fewer validators than the prior fork.
    final UInt64 newBalance =
        validatorIndexInt < newBalances.size() ? newBalances.get(validatorIndexInt) : UInt64.ZERO;
    final UInt64 effectiveNewBalance = vote.isNextEquivocating() ? UInt64.ZERO : newBalance;

    final boolean isVoteRootUnchanged = vote.getCurrentRoot().equals(vote.getNextRoot());
    final boolean isVoteTargetUnchanged =
        isVoteRootUnchanged
            && vote.getCurrentSlot().equals(vote.getNextSlot())
            && vote.isCurrentFullPayloadHint() == vote.isNextFullPayloadHint();
    if (isVoteTargetUnchanged && oldBalance.equals(effectiveNewBalance)) {
      return;
    }

    final Optional<ForkChoiceNode> currentNode =
        forkChoiceModel.getSupportedNode(
            vote.getCurrentRoot(),
            vote.getCurrentSlot(),
            vote.isCurrentFullPayloadHint(),
            protoArray,
            blockNodeIndex);
    final Optional<ForkChoiceNode> nextNode =
        forkChoiceModel.getSupportedNode(
            vote.getNextRoot(),
            vote.getNextSlot(),
            vote.isNextFullPayloadHint(),
            protoArray,
            blockNodeIndex);

    // A vote update matters if the validator moved roots, resolved to a different node variant,
    // or their effective balance changed.
    if (!isVoteRootUnchanged
        || !currentNode.equals(nextNode)
        || !oldBalance.equals(effectiveNewBalance)) {
      if (vote.isNextEquivocating()) {
        subtractBalance(getIndexByNode, deltas, currentNode, oldBalance);
      } else {
        subtractBalance(getIndexByNode, deltas, currentNode, oldBalance);
        addBalance(getIndexByNode, deltas, nextNode, effectiveNewBalance);
      }
      final VoteTracker newVote =
          new VoteTracker(
              vote.getNextRoot(),
              vote.getNextRoot(),
              false,
              vote.isNextEquivocating(),
              vote.getNextSlot(),
              vote.isNextFullPayloadHint(),
              vote.getNextSlot(),
              vote.isNextFullPayloadHint());
      store.putVote(validatorIndex, newVote);
    }
  }

  private static void subtractBalance(
      final Function<ForkChoiceNode, Optional<Integer>> getIndexByNode,
      final LongList deltas,
      final Optional<ForkChoiceNode> node,
      final UInt64 delta) {
    node.ifPresent(nodeIdentity -> subtractBalance(getIndexByNode, deltas, nodeIdentity, delta));
  }

  private static void subtractBalance(
      final Function<ForkChoiceNode, Optional<Integer>> getIndexByNode,
      final LongList deltas,
      final ForkChoiceNode node,
      final UInt64 delta) {
    if (delta.isZero()) {
      return;
    }
    getIndexByNode
        .apply(node)
        .ifPresent(
            index ->
                deltas.set(
                    index.intValue(),
                    subtractExact(deltas.getLong(index.intValue()), delta.longValue())));
  }

  private static void addBalance(
      final Function<ForkChoiceNode, Optional<Integer>> getIndexByNode,
      final LongList deltas,
      final Optional<ForkChoiceNode> node,
      final UInt64 delta) {
    node.ifPresent(nodeIdentity -> addBalance(getIndexByNode, deltas, nodeIdentity, delta));
  }

  private static void addBalance(
      final Function<ForkChoiceNode, Optional<Integer>> getIndexByNode,
      final LongList deltas,
      final ForkChoiceNode node,
      final UInt64 delta) {
    if (delta.isZero()) {
      return;
    }
    getIndexByNode
        .apply(node)
        .ifPresent(
            index ->
                deltas.set(
                    index.intValue(),
                    addExact(deltas.getLong(index.intValue()), delta.longValue())));
  }
}

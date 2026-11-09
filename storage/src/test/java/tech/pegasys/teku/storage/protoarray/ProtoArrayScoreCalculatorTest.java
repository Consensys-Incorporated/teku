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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayScoreCalculator.computeDeltas;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.getHash;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ProtoArrayScoreCalculatorTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalPhase0();
  private static final Checkpoint GENESIS_CHECKPOINT = new Checkpoint(ZERO, Bytes32.ZERO);
  private static final BlockCheckpoints GENESIS_BLOCK_CHECKPOINTS =
      new BlockCheckpoints(
          GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

  private final Object2IntMap<Bytes32> indices = new Object2IntOpenHashMap<>();
  private List<UInt64> oldBalances = new ArrayList<>();
  private List<UInt64> newBalances = new ArrayList<>();
  private Optional<Bytes32> oldProposerBoostRoot = Optional.empty();
  private Optional<Bytes32> newProposerBoostRoot = Optional.empty();
  private UInt64 oldProposerBoostAmount = ZERO;
  private UInt64 newProposerBoostAmount = ZERO;
  private final VoteUpdater store = createStoreToManipulateVotes();
  private final ForkChoiceModelGloas gloasModel =
      new ForkChoiceModelGloas(
          SpecConfigGloas.required(
              TestSpecFactory.createMinimalGloas().forMilestone(SpecMilestone.GLOAS).getConfig()));

  private Optional<Integer> getIndex(final Bytes32 root) {
    final int index = indices.getOrDefault(root, -1);
    return index >= 0 ? Optional.of(index) : Optional.empty();
  }

  private LongList computeDeltas(
      final VoteUpdater store,
      final int protoArraySize,
      final Function<Bytes32, Optional<Integer>> getBaseNodeIndexByRoot,
      final List<UInt64> oldBalances,
      final List<UInt64> newBalances,
      final Optional<Bytes32> previousProposerBoostRoot,
      final Optional<Bytes32> newProposerBoostRoot,
      final UInt64 previousBoostAmount,
      final UInt64 newBoostAmount) {
    final BlockNodeVariantsIndex blockNodeIndex = new BlockNodeVariantsIndex();
    indices.forEach(
        (root, __) -> blockNodeIndex.putBaseNode(root, ZERO, ForkChoiceNode.createBase(root)));
    return ProtoArrayScoreCalculator.computeDeltas(
        store,
        protoArraySize,
        node -> getBaseNodeIndexByRoot.apply(node.blockRoot()),
        previousProposerBoostRoot.map(ForkChoiceNode::createBase),
        newProposerBoostRoot.map(ForkChoiceNode::createBase),
        oldBalances,
        newBalances,
        previousBoostAmount,
        newBoostAmount,
        createProtoArray(),
        blockNodeIndex,
        ForkChoiceModelPhase0.INSTANCE);
  }

  private LongList computeDeltas(
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
    return ProtoArrayScoreCalculator.computeDeltas(
        store,
        protoArraySize,
        getIndexByNode,
        previousProposerBoostNode,
        newProposerBoostNode,
        oldBalances,
        newBalances,
        previousBoostAmount,
        newBoostAmount,
        protoArray,
        blockNodeIndex,
        forkChoiceModel);
  }

  @Test
  void computeDeltas_zeroHash() {
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      store.getVote(UInt64.valueOf(i));
      oldBalances.add(ZERO);
      newBalances.add(ZERO);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    // Deltas should all be zero
    assertThat(deltas).containsOnly(0L);
  }

  @Test
  void computeDeltas_allVotedTheSame() {
    final UInt64 balance = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), getHash(0));
      store.putVote(UInt64.valueOf(i), newVote);
      oldBalances.add(balance);
      newBalances.add(balance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a delta
        assertThat(delta).isEqualTo(balance.longValue() * Integer.toUnsignedLong(validatorCount));
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_differentVotes() {
    final UInt64 balance = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), getHash(i));
      store.putVote(UInt64.valueOf(i), newVote);
      oldBalances.add(balance);
      newBalances.add(balance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    // Each root should have the same delta
    assertThat(deltas).containsOnly(balance.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_movingVotes() {
    final UInt64 balance = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      store.getVote(UInt64.valueOf(i));
      store.putVote(UInt64.valueOf(i), new VoteTracker(getHash(0), getHash(1)));
      oldBalances.add(balance);
      newBalances.add(balance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);

    assertThat(deltas).hasSize(validatorCount);
    long totalDelta = balance.longValue() * Integer.toUnsignedLong(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a negative delta
        assertThat(delta).isEqualTo(-totalDelta);
      } else if (i == 1) {
        // First root should have positive delta
        assertThat(delta).isEqualTo(totalDelta);
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_moveOutOfTree() {
    final UInt64 balance = UInt64.valueOf(42);

    // There is only one block.
    indices.put(getHash(1), 0);

    // There are two validators.
    oldBalances = Collections.nCopies(2, balance);
    newBalances = Collections.nCopies(2, balance);

    // One validator moves their vote from the block to the zero hash.
    store.getVote(UInt64.valueOf(0));
    store.putVote(UInt64.valueOf(0), new VoteTracker(getHash(1), Bytes32.ZERO));

    // One validator moves their vote from the block to something outside the tree.
    store.getVote(UInt64.valueOf(1));
    store.putVote(UInt64.valueOf(1), new VoteTracker(getHash(1), getHash(1337)));

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(1);

    // The block should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-balance.longValue() * 2);

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_changingBalances() {

    final UInt64 oldBalance = UInt64.valueOf(42);
    final UInt64 newBalance = oldBalance.times(2);

    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      store.getVote(UInt64.valueOf(i));
      store.putVote(UInt64.valueOf(i), new VoteTracker(getHash(0), getHash(1)));
      oldBalances.add(oldBalance);
      newBalances.add(newBalance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a negative delta
        assertThat(delta).isEqualTo(-oldBalance.longValue() * validatorCount);
      } else if (i == 1) {
        // First root should have positive delta
        assertThat(delta).isEqualTo(newBalance.longValue() * validatorCount);
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_validatorAppears() {
    final UInt64 balance = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There is only one validator in the old balances.
    oldBalances.add(balance);

    // There are two validators in the new balances.
    newBalances.addAll(List.of(balance, balance));

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      store.getVote(UInt64.valueOf(i));
      store.putVote(UInt64.valueOf(i), new VoteTracker(getHash(1), getHash(2)));
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should have only lost one balance
    assertThat(deltas.get(0)).isEqualTo(-balance.longValue());

    // Block 2 should have gained two balances
    assertThat(deltas.get(1)).isEqualTo(2 * balance.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_validatorDisappears() {
    final UInt64 balance = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There are two validator in the old balances.
    oldBalances.addAll(List.of(balance, balance));

    // There is only one validator in the new balances.
    newBalances.add(balance);

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      store.getVote(UInt64.valueOf(i));
      store.putVote(UInt64.valueOf(i), new VoteTracker(getHash(1), getHash(2)));
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-balance.longValue() * 2);

    // Block 2 should have only gained one balance
    assertThat(deltas.get(1)).isEqualTo(balance.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_addingProposerBoost() {
    oldProposerBoostAmount = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // Proposer boost is added to block 2 with a different value
    newProposerBoostRoot = Optional.of(getHash(2));
    newProposerBoostAmount = UInt64.valueOf(23);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should be unchanged
    assertThat(deltas.get(0)).isZero();

    // Block 2 should have new boost amount added
    assertThat(deltas.get(1)).isEqualTo(newProposerBoostAmount.longValue());
  }

  @Test
  void computeDeltas_removingProposerBoost() {
    oldProposerBoostAmount = UInt64.valueOf(42);
    oldProposerBoostRoot = Optional.of(getHash(2));

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // Proposer boost is removed from block 2 with a different value
    newProposerBoostRoot = Optional.empty();
    newProposerBoostAmount = UInt64.valueOf(23);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should be unchanged
    assertThat(deltas.get(0)).isZero();

    // Block 2 should have old boost amount removed
    assertThat(deltas.get(1)).isEqualTo(-oldProposerBoostAmount.longValue());
  }

  @Test
  void computeDeltas_changingProposerBoost() {
    oldProposerBoostAmount = UInt64.valueOf(42);
    oldProposerBoostRoot = Optional.of(getHash(1));

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // Proposer boost is removed from block 2 with a different value
    newProposerBoostRoot = Optional.of(getHash(2));
    newProposerBoostAmount = UInt64.valueOf(23);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should have old boost amount removed
    assertThat(deltas.get(0)).isEqualTo(-oldProposerBoostAmount.longValue());

    // Block 2 should have new boost amount added
    assertThat(deltas.get(1)).isEqualTo(newProposerBoostAmount.longValue());
  }

  @Test
  void computeDeltas_validatorEquivocates() {
    final UInt64 balance = UInt64.valueOf(42);

    // There is one block.
    indices.put(getHash(1), 0);

    // There are two validator in the old balances.
    oldBalances.addAll(List.of(balance, balance));

    // Same two in new balances.
    newBalances.addAll(List.of(balance, balance));

    // Both validators votes for block.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), getHash(1));
      store.putVote(UInt64.valueOf(i), newVote);
    }

    // Validator #0 is marked as equivocated
    VoteTracker vote = store.getVote(ZERO);
    store.putVote(ZERO, new VoteTracker(vote.getNextRoot(), vote.getNextRoot(), true, true));

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(1);

    // Block should have only one counted vote
    assertThat(deltas.get(0)).isEqualTo(balance.longValue());

    // Votes should be updated
    VoteTracker vote1 = store.getVote(UInt64.ONE);
    assertThat(vote1.getCurrentRoot()).isEqualTo(vote1.getNextRoot());
    // Equivocating validator should be marked
    VoteTracker vote0 = store.getVote(ZERO);
    assertThat(vote0.isCurrentEquivocating()).isTrue();
  }

  @Test
  void computeDeltas_payloadPresentVoteDoesNotFallbackToPendingWhenFullNodeIsMissing() {
    final UInt64 balance = UInt64.valueOf(42);
    final Bytes32 root = getHash(1);
    final ProtoArray protoArray = createProtoArray();
    final BlockNodeVariantsIndex blockNodeIndex =
        createBlockNodeVariantsIndex(protoArray, root, true, false, UInt64.ONE);
    oldBalances = List.of(balance);
    newBalances = List.of(balance);

    store.putVote(
        ZERO,
        new VoteTracker(Bytes32.ZERO, root, false, false, UInt64.valueOf(2), true, ZERO, false));

    final List<Long> deltas =
        computeDeltas(
            store,
            protoArray.getTotalTrackedNodeCount(),
            protoArray::getNodeIndex,
            Optional.empty(),
            Optional.empty(),
            oldBalances,
            newBalances,
            oldProposerBoostAmount,
            newProposerBoostAmount,
            protoArray,
            blockNodeIndex,
            gloasModel);

    assertThat(deltas).containsExactly(0L, 0L);
  }

  @Test
  void computeDeltas_payloadAbsentVoteAtBlockSlotTargetsPendingNode() {
    final UInt64 balance = UInt64.valueOf(42);
    final Bytes32 root = getHash(1);
    final ProtoArray protoArray = createProtoArray();
    final BlockNodeVariantsIndex blockNodeIndex =
        createBlockNodeVariantsIndex(protoArray, root, true, false, UInt64.ONE);
    oldBalances = List.of(balance);
    newBalances = List.of(balance);

    store.putVote(
        ZERO, new VoteTracker(Bytes32.ZERO, root, false, false, UInt64.ONE, false, ZERO, false));

    final List<Long> deltas =
        computeDeltas(
            store,
            protoArray.getTotalTrackedNodeCount(),
            protoArray::getNodeIndex,
            Optional.empty(),
            Optional.empty(),
            oldBalances,
            newBalances,
            oldProposerBoostAmount,
            newProposerBoostAmount,
            protoArray,
            blockNodeIndex,
            gloasModel);

    assertThat(deltas).containsExactly(balance.longValue(), 0L);
  }

  @Test
  void computeDeltas_payloadPresentVoteTargetsFullWhenFullNodeExists() {
    final UInt64 balance = UInt64.valueOf(42);
    final Bytes32 root = getHash(1);
    final ProtoArray protoArray = createProtoArray();
    final BlockNodeVariantsIndex blockNodeIndex =
        createBlockNodeVariantsIndex(protoArray, root, true, true, UInt64.ONE);
    oldBalances = List.of(balance);
    newBalances = List.of(balance);

    store.putVote(
        ZERO,
        new VoteTracker(Bytes32.ZERO, root, false, false, UInt64.valueOf(2), true, ZERO, false));

    final List<Long> deltas =
        computeDeltas(
            store,
            protoArray.getTotalTrackedNodeCount(),
            protoArray::getNodeIndex,
            Optional.empty(),
            Optional.empty(),
            oldBalances,
            newBalances,
            oldProposerBoostAmount,
            newProposerBoostAmount,
            protoArray,
            blockNodeIndex,
            gloasModel);

    assertThat(deltas).containsExactly(0L, 0L, balance.longValue());
  }

  @Test
  void computeDeltas_validatorEquivocatesChain() {
    final UInt64 balance = UInt64.valueOf(42);

    // There are 3 blocks
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);
    indices.put(getHash(3), 2);

    // There are two validator in the old balances.
    oldBalances.addAll(List.of(balance, balance));

    // Same two in new balances.
    newBalances.addAll(List.of(balance, balance));

    // Both validators moves vote to the last block.
    for (int i = 0; i < 2; i++) {
      store.getVote(UInt64.valueOf(i));
      store.putVote(UInt64.valueOf(i), new VoteTracker(getHash(2), getHash(3)));
    }

    // Validator #0 is set to be marked as equivocated
    VoteTracker vote = store.getVote(ZERO);
    store.putVote(ZERO, vote.createNextEquivocating());

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(3);

    assertThat(deltas.get(0)).isEqualTo(0);
    // 2nd block should lose both votes
    assertThat(deltas.get(1)).isEqualTo(-balance.longValue() * 2);
    // Only non-equivocated should be added to 3rd
    assertThat(deltas.get(2)).isEqualTo(balance.longValue());

    // Verify that equivocation affects deltas only once
    List<Long> deltas2 =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas2).hasSize(3);

    // No subsequent penalty for equivocation 2nd run
    for (int i = 0; i < 3; i++) {
      assertThat(deltas2.get(i)).isEqualTo(0);
    }
  }

  @Test
  void computeDeltas_newlyEquivocatingVoteOnSameRootRemovesBalance() {
    final UInt64 balance = UInt64.valueOf(42);
    final Bytes32 root = getHash(1);

    indices.put(root, 0);
    oldBalances = List.of(balance);
    newBalances = List.of(balance);

    store.putVote(ZERO, new VoteTracker(root, root, true, false));

    final List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);

    assertThat(deltas).containsExactly(-balance.longValue());
    assertThat(store.getVote(ZERO).isCurrentEquivocating()).isTrue();
  }

  @Test
  void computeDeltas_unchangedVoteTargetAndBalanceDoesNotResolveVoteNodes() {
    final UInt64 balance = UInt64.valueOf(42);
    final Bytes32 root = getHash(1);
    final UInt64 voteSlot = UInt64.valueOf(12);
    final ForkChoiceModel forkChoiceModel = mock(ForkChoiceModel.class);

    oldBalances = List.of(balance);
    newBalances = List.of(balance);
    store.putVote(ZERO, new VoteTracker(root, root, false, false, voteSlot, true, voteSlot, true));

    final LongList deltas =
        ProtoArrayScoreCalculator.computeDeltas(
            store,
            1,
            node -> {
              throw new AssertionError("Unchanged votes should not require node index resolution");
            },
            Optional.empty(),
            Optional.empty(),
            oldBalances,
            newBalances,
            oldProposerBoostAmount,
            newProposerBoostAmount,
            createProtoArray(),
            new BlockNodeVariantsIndex(),
            forkChoiceModel);

    assertThat(deltas.size()).isEqualTo(1);
    assertThat(deltas.getLong(0)).isZero();
    verifyNoInteractions(forkChoiceModel);
  }

  @Test
  void computeDeltas_sameRootAndBalanceButDifferentSlotResolvesVoteNodes() {
    final UInt64 balance = UInt64.valueOf(42);
    final Bytes32 root = getHash(1);
    final UInt64 currentSlot = UInt64.valueOf(12);
    final UInt64 nextSlot = UInt64.valueOf(13);
    final ForkChoiceNode node = ForkChoiceNode.createBase(root);
    final ProtoArray protoArray = createProtoArray();
    final BlockNodeVariantsIndex blockNodeIndex = new BlockNodeVariantsIndex();
    final ForkChoiceModel forkChoiceModel = mock(ForkChoiceModel.class);

    oldBalances = List.of(balance);
    newBalances = List.of(balance);
    store.putVote(
        ZERO, new VoteTracker(root, root, false, false, nextSlot, false, currentSlot, false));

    when(forkChoiceModel.getSupportedNode(root, currentSlot, false, protoArray, blockNodeIndex))
        .thenReturn(Optional.of(node));
    when(forkChoiceModel.getSupportedNode(root, nextSlot, false, protoArray, blockNodeIndex))
        .thenReturn(Optional.of(node));

    final LongList deltas =
        ProtoArrayScoreCalculator.computeDeltas(
            store,
            1,
            resolvedNode -> {
              throw new AssertionError("Unchanged resolved nodes should not update deltas");
            },
            Optional.empty(),
            Optional.empty(),
            oldBalances,
            newBalances,
            oldProposerBoostAmount,
            newProposerBoostAmount,
            protoArray,
            blockNodeIndex,
            forkChoiceModel);

    assertThat(deltas.size()).isEqualTo(1);
    assertThat(deltas.getLong(0)).isZero();
    verify(forkChoiceModel, times(1))
        .getSupportedNode(root, currentSlot, false, protoArray, blockNodeIndex);
    verify(forkChoiceModel, times(1))
        .getSupportedNode(root, nextSlot, false, protoArray, blockNodeIndex);
  }

  private ProtoArray createProtoArray() {
    return ProtoArray.builder()
        .spec(SPEC)
        .currentEpoch(ZERO)
        .justifiedCheckpoint(GENESIS_CHECKPOINT)
        .finalizedCheckpoint(GENESIS_CHECKPOINT)
        .build();
  }

  private BlockNodeVariantsIndex createBlockNodeVariantsIndex(
      final ProtoArray protoArray,
      final Bytes32 blockRoot,
      final boolean includeEmptyNode,
      final boolean includeFullNode,
      final UInt64 blockSlot) {
    final BlockNodeVariantsIndex blockNodeIndex = new BlockNodeVariantsIndex();
    final ForkChoiceNode baseNode = ForkChoiceNode.createBase(blockRoot);
    protoArray.addNode(
        baseNode,
        blockSlot,
        Bytes32.ZERO,
        Optional.empty(),
        Bytes32.ZERO,
        GENESIS_BLOCK_CHECKPOINTS,
        ZERO,
        Bytes32.ZERO,
        false);
    blockNodeIndex.putBaseNode(blockRoot, blockSlot, baseNode);

    if (includeEmptyNode) {
      final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);
      protoArray.addNode(
          emptyNode,
          blockSlot,
          Bytes32.ZERO,
          Optional.of(baseNode),
          Bytes32.ZERO,
          GENESIS_BLOCK_CHECKPOINTS,
          ZERO,
          Bytes32.ZERO,
          false);
      blockNodeIndex.attachEmptyNode(blockRoot, emptyNode);
    }

    if (includeFullNode) {
      final ForkChoiceNode fullNode = ForkChoiceNode.createFull(blockRoot);
      protoArray.addNode(
          fullNode,
          blockSlot,
          Bytes32.ZERO,
          Optional.of(baseNode),
          Bytes32.ZERO,
          GENESIS_BLOCK_CHECKPOINTS,
          ZERO,
          getHash(999),
          false);
      blockNodeIndex.attachFullNode(blockRoot, fullNode);
    }

    return blockNodeIndex;
  }

  private void votesShouldBeUpdated(final VoteUpdater store) {
    UInt64.rangeClosed(ZERO, store.getHighestVotedValidatorIndex())
        .forEach(
            i -> {
              VoteTracker vote = store.getVote(i);
              assertThat(vote.getCurrentRoot()).isEqualTo(vote.getNextRoot());
            });
  }
}

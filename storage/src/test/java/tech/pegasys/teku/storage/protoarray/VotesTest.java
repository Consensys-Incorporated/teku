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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.createProtoArrayForkChoiceStrategy;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.getHash;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class VotesTest {

  private final Spec spec = TestSpecFactory.createDefault();

  private final TestStoreImpl store = createStoreToManipulateVotes();
  private final ForkChoiceStrategy forkChoice =
      createProtoArrayForkChoiceStrategy(spec, getHash(0), UInt64.valueOf(8), ONE, ONE);
  private List<UInt64> balances = new ArrayList<>(List.of(unsigned(1), unsigned(1)));

  @Test
  void votesTest() {

    // Ensure that the head starts at the finalized block.
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(0));

    // Add a block with a hash of 2.
    //
    // ------- epoch 1 begins ------
    // 8         0 <- justified and finalized
    //          /
    // 9       2
    processBlock(UInt64.valueOf(9), getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is 2
    //
    // 8            0
    //             /
    // 9   head-> 2
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(2));

    // Add a block with a hash of 1 that comes off the genesis block (this is a fork compared
    // to the previous block).
    //
    // 8         0
    //          / \
    // 9       2   1
    processBlock(UInt64.valueOf(9), getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is still 2
    //
    // 8           0
    //            / \
    // 9  head-> 2   1
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(2));

    // Add a vote to block 1
    //
    // 8          0
    //           / \
    // 9        2   1 <- +vote
    forkChoice.processAttestation(store, unsigned(0), getHash(1), unsigned(2));

    // Ensure that the head is now 1, because 1 has a vote.
    //
    // 8         0
    //          / \
    // 9       2   1 <- head
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(1));

    // Add a vote to block 2
    //
    // 8            0
    //             / \
    // 9   +vote-> 2   1
    forkChoice.processAttestation(store, unsigned(1), getHash(2), unsigned(2));

    // Ensure that the head is 2 since 1 and 2 both have a vote
    //
    // 8           0
    //            / \
    // 9   head-> 2   1
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(2));

    // Add block 3.
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3
    processBlock(UInt64.valueOf(10), getHash(3), getHash(1), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is still 2
    //
    // 8           0
    //            / \
    // 9  head-> 2   1
    //               |
    // 10            3
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(2));

    // Move validator #0 vote from 1 to 3
    //
    // 8         0
    //          / \
    // 9       2   1 <- -vote
    //             |
    // 10          3 <- +vote
    forkChoice.processAttestation(store, unsigned(0), getHash(3), unsigned(3));

    // Ensure that the head is still 2
    //
    // 8           0
    //            / \
    // 9  head-> 2   1
    //               |
    // 10            3
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(2));

    // Move validator #1 vote from 2 to 1 (this is an equivocation, but fork choice doesn't
    // care)
    //
    // 8            0
    //             / \
    // 9  -vote-> 2   1 <- +vote
    //                |
    // 10             3
    forkChoice.processAttestation(store, unsigned(1), getHash(1), unsigned(3));

    // Ensure that the head is now 3
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3 <- head
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(3));

    // Add block 4.
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3
    //             |
    // ------- epoch 2 begins ------
    //             |
    // 16          4
    processBlock(UInt64.valueOf(16), getHash(4), getHash(3), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is now 4
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3
    //             |
    // ------- epoch 2 begins ------
    //             |
    // 16          4   <- head
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(4));

    // Add block 5, which has a justified epoch of 2.
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3
    //             |
    // ------- epoch 2 begins ------
    //             |
    // 16          4
    //            /
    // 17        5 <- justified epoch = 2
    processBlock(
        UInt64.valueOf(17), getHash(5), getHash(4), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Ensure that 5 is filtered out and the head stays at 4.
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3
    //             |
    // ------- epoch 2 begins ------
    //             |
    // 16          4 <- head
    //            /
    // 17        5
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(4));

    // Add block 6, which has a justified epoch of 0.
    //
    // 8         0
    //          / \
    // 9       2   1
    //             |
    // 10          3
    //             |
    // ------- epoch 2 begins ------
    //             |
    // 16          4
    //            / \
    // 17        5   6 <- justified epoch = 0
    processBlock(
        UInt64.valueOf(17), getHash(6), getHash(4), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Move both votes to 5.
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17  +2 vote-> 5   6
    forkChoice.processAttestation(store, unsigned(0), getHash(5), unsigned(4));
    forkChoice.processAttestation(store, unsigned(1), getHash(5), unsigned(4));

    // Add blocks 7, 8 and 9. Adding these blocks helps test the `best_descendant`
    // functionality.
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              /
    // 20          9
    processBlock(
        UInt64.valueOf(18), getHash(7), getHash(5), Bytes32.ZERO, unsigned(2), unsigned(2));
    processBlock(
        UInt64.valueOf(19), getHash(8), getHash(7), Bytes32.ZERO, unsigned(2), unsigned(2));
    processBlock(
        UInt64.valueOf(20), getHash(9), getHash(8), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Ensure that 6 is the head, even though 5 has all the votes. This is testing to ensure
    // that 5 is filtered out due to a differing justified epoch.
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6 <- head
    //               |
    // 18            7
    //               |
    // 19            8
    //              /
    // 20          9
    assertThat(
            applyPendingVotes(
                new Checkpoint(ONE, getHash(0)), new Checkpoint(ONE, getHash(0)), balances))
        .isEqualTo(getHash(6));

    // Change fork-choice justified epoch to 1, and the start block to 5 and ensure that 9 is
    // the head.
    //
    // << Change justified epoch to 1 >>
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              /
    // 20  head -> 9
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Move both votes to block 9
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              /
    // 20          9 <- +2 votes
    forkChoice.processAttestation(store, unsigned(0), getHash(9), unsigned(5));
    forkChoice.processAttestation(store, unsigned(1), getHash(9), unsigned(5));

    // Add block 10
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20          9   10
    processBlock(
        UInt64.valueOf(20), getHash(10), getHash(8), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Double-check the head is still 9
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Introduce 2 more validators into the system
    balances.addAll(List.of(unsigned(1), unsigned(1)));

    // Have the two new validators vote for 10
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20          9   10 <- +2 votes
    forkChoice.processAttestation(store, unsigned(2), getHash(10), unsigned(5));
    forkChoice.processAttestation(store, unsigned(3), getHash(10), unsigned(5));

    // Check the head is now 10. (due to lexicographical ordering
    // (when blocks have the same amount of votes))
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- epoch 2 begins ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20          9   10 <- head
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(10));

    // Set the balances of the last two validators to zero
    balances = new ArrayList<>(List.of(unsigned(1), unsigned(1), unsigned(0), unsigned(0)));

    // Check the head is 9 again.
    //
    //               .
    //               .
    //               .
    //               |
    // 19            8
    //              / \
    // 20  head->  9   10
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Set the balances of the last two validators back to 1
    balances = new ArrayList<>(List.of(ONE, ONE, ONE, ONE));

    // Check the head is 10.
    //
    //         .
    //         .
    //         .
    //         |
    // 19      8
    //        / \
    // 20    9   10 <- head
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(10));

    // Equivocate one of the votes on 10
    VoteTracker voteFor3 = store.getVote(unsigned(3));
    store.putVote(unsigned(3), voteFor3.createNextEquivocating());

    // Ensure the head is now 9 again (2 votes > 1)
    //
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20 head->   9   10
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Add 1 validator
    balances = new ArrayList<>(List.of(ONE, ONE, ONE, ONE, ONE));
    // Add 1 vote to 10 (total: 2 good, 1 equivocated)
    forkChoice.processAttestation(store, unsigned(4), getHash(10), unsigned(5));

    // Ensure the head is 10 back
    //
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20          9   10 <- head
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(10));

    // Remove the last two validators
    balances = new ArrayList<>(List.of(ONE, ONE));

    // Check the head is 9 again.
    //
    //  (prior blocks omitted for brevity)
    //               .
    //               .
    //               .
    //               |
    // 19            8
    //              / \
    // 20  head->  9   10
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Ensure that pruning below the prune threshold does not prune.
    forkChoice.setPruneThreshold(Integer.MAX_VALUE);
    forkChoice.applyUpdate(
        emptyList(), emptyMap(), emptySet(), emptyMap(), new Checkpoint(ONE, getHash(5)));
    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(11);

    // Run find-head, ensure the no-op prune didn't change the head.
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Ensure that pruning above the prune threshold does prune.
    //
    //
    // 8             0
    //              / \
    // 9           2   1
    //                 |
    // 10              3
    //                 |
    //     ------- pruned here ------
    //                 |
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20          9   10
    forkChoice.setPruneThreshold(1);
    forkChoice.applyUpdate(
        emptyList(), emptyMap(), emptySet(), emptyMap(), new Checkpoint(ONE, getHash(4)));
    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(7);

    // Run find-head, ensure the prune didn't change the head.
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(9));

    // Add block 11
    // 16              4
    //                / \
    // 17            5   6
    //               |
    // 18            7
    //               |
    // 19            8
    //              / \
    // 20          9   10
    //             |
    // 21         11
    processBlock(
        UInt64.valueOf(21), getHash(11), getHash(9), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Ensure the head is now 11
    // 16             4
    //               / \
    // 17           5   6
    //              |
    // 18           7
    //              |
    // 19           8
    //             / \
    // 20         9   10
    //            |
    // 21 head->  11
    assertThat(applyPendingVotes(checkpoint(2, 4), checkpoint(2, 5), balances))
        .isEqualTo(getHash(11));
  }

  private void processBlock(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final UInt64 justifiedEpoch,
      final UInt64 finalizedEpoch) {
    forkChoice.processBlock(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        new BlockCheckpoints(
            new Checkpoint(justifiedEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedEpoch, Bytes32.ZERO),
            new Checkpoint(justifiedEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedEpoch, Bytes32.ZERO)),
        Optional.empty(),
        Optional.empty());
  }

  private UInt64 unsigned(final int i) {
    return UInt64.valueOf(i);
  }

  private Checkpoint checkpoint(final long epoch, final int root) {
    return new Checkpoint(UInt64.valueOf(epoch), getHash(root));
  }

  private Bytes32 applyPendingVotes(
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedStateEffectiveBalances) {
    return forkChoice
        .applyPendingVotes(
            store,
            Optional.empty(),
            UInt64.valueOf(1000),
            UInt64.valueOf(1000),
            finalizedCheckpoint,
            justifiedCheckpoint,
            justifiedStateEffectiveBalances,
            ZERO)
        .node()
        .blockRoot();
  }
}

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
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class FFGUpdatesTest {
  private final Spec spec = TestSpecFactory.createDefault();

  private final TestStoreImpl store = createStoreToManipulateVotes();
  private final ForkChoiceStrategy forkChoice =
      createProtoArrayForkChoiceStrategy(spec, getHash(0), ZERO, ONE, ONE);
  private final List<UInt64> balances = new ArrayList<>(List.of(unsigned(1), unsigned(1)));

  @Test
  void case1() {

    // Ensure that the head starts at the finalized block.
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 0), balances))
        .isEqualTo(getHash(0));

    // Build the following tree
    //
    //            0 <- just: 0, fin: 0
    //            |
    //            1 <- just: 0, fin: 0
    //            |
    //            2 <- just: 1, fin: 0
    //            |
    //            3 <- just: 2, fin: 1
    processBlock(ONE, getHash(1), getHash(0), Bytes32.ZERO, unsigned(0), unsigned(0));
    processBlock(unsigned(2), getHash(2), getHash(1), Bytes32.ZERO, unsigned(1), unsigned(0));
    processBlock(unsigned(3), getHash(3), getHash(2), Bytes32.ZERO, unsigned(2), unsigned(1));

    // Ensure that with justified epoch 0 we find 3
    //
    //            0 <- start
    //            |
    //            1
    //            |
    //            2
    //            |
    //            3 <- head
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 0), balances))
        .isEqualTo(getHash(3));

    // Ensure that with justified epoch 1 we find 2
    //
    //            0
    //            |
    //            1
    //            |
    //            2 <- start
    //            |
    //            3 <- head
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(1, 2), balances))
        .isEqualTo(getHash(2));

    // Ensure that with justified epoch 2 we find 3
    //
    //            0
    //            |
    //            1
    //            |
    //            2
    //            |
    //            3 <- start + head
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(2, 3), balances))
        .isEqualTo(getHash(3));
  }

  @Test
  void case2() {

    // Ensure that the head starts at the finalized block.
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 0), balances))
        .isEqualTo(getHash(0));

    // Build the following tree.
    //
    //                       0
    //                      / \
    //  just: 0, fin: 0 -> 1   2 <- just: 0, fin: 0
    //                     |   |
    //  just: 1, fin: 0 -> 3   4 <- just: 0, fin: 0
    //                     |   |
    //  just: 1, fin: 0 -> 5   6 <- just: 0, fin: 0
    //                     |   |
    //  just: 1, fin: 0 -> 7   8 <- just: 1, fin: 0
    //                     |   |
    //  just: 2, fin: 0 -> 9  10 <- just: 2, fin: 0

    //  Left branch
    processBlock(unsigned(1), getHash(1), getHash(0), Bytes32.ZERO, unsigned(0), unsigned(0));
    processBlock(unsigned(2), getHash(3), getHash(1), Bytes32.ZERO, unsigned(1), unsigned(0));
    processBlock(unsigned(3), getHash(5), getHash(3), Bytes32.ZERO, unsigned(1), unsigned(0));
    processBlock(unsigned(4), getHash(7), getHash(5), Bytes32.ZERO, unsigned(1), unsigned(0));
    processBlock(unsigned(4), getHash(9), getHash(7), Bytes32.ZERO, unsigned(2), unsigned(0));

    //  Right branch
    processBlock(unsigned(1), getHash(2), getHash(0), Bytes32.ZERO, unsigned(0), unsigned(0));
    processBlock(unsigned(2), getHash(4), getHash(2), Bytes32.ZERO, unsigned(0), unsigned(0));
    processBlock(unsigned(3), getHash(6), getHash(4), Bytes32.ZERO, unsigned(0), unsigned(0));
    processBlock(unsigned(4), getHash(8), getHash(6), Bytes32.ZERO, unsigned(1), unsigned(0));
    processBlock(unsigned(4), getHash(10), getHash(8), Bytes32.ZERO, unsigned(2), unsigned(0));

    // Ensure that if we start at 0 we find 10 (just: 0, fin: 0).
    //
    //           0  <-- start
    //          / \
    //         1   2
    //         |   |
    //         3   4
    //         |   |
    //         5   6
    //         |   |
    //         7   8
    //         |   |
    //         9  10 <-- head
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 0), balances))
        .isEqualTo(getHash(10));

    // Same as above, but with justified epoch 2.
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(2, 0), balances))
        .isEqualTo(getHash(10));

    // Same as above, but with justified epoch 3 (should invalidate all nodes so return justified).
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(3, 0), balances))
        .isEqualTo(getHash(0));

    // Add a vote to 1.
    //
    //                 0
    //                / \
    //    +1 vote -> 1   2
    //               |   |
    //               3   4
    //               |   |
    //               5   6
    //               |   |
    //               7   8
    //               |   |
    //               9  10
    forkChoice.processAttestation(store, unsigned(0), getHash(1), unsigned(0));

    // Ensure that if we start at 0 we find 9 (just: 0, fin: 0).
    //
    //           0  <-- start
    //          / \
    //         1   2
    //         |   |
    //         3   4
    //         |   |
    //         5   6
    //         |   |
    //         7   8
    //         |   |
    // head -> 9  10
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 0), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 2.
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(2, 0), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 3 (should invalidate all and return justified).
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(3, 0), balances))
        .isEqualTo(getHash(0));

    // Add a vote to 2.
    //
    //                 0
    //                / \
    //               1   2 <- +1 vote
    //               |   |
    //               3   4
    //               |   |
    //               5   6
    //               |   |
    //               7   8
    //               |   |
    //               9  10
    forkChoice.processAttestation(store, unsigned(1), getHash(2), unsigned(0));

    // Ensure that if we start at 0 we find 10 (just: 0, fin: 0).
    //
    //           0  <-- start
    //          / \
    //         1   2
    //         |   |
    //         3   4
    //         |   |
    //         5   6
    //         |   |
    //         7   8
    //         |   |
    //         9  10 <-- head
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 0), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 2.
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(2, 0), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 3 (should invalidate all and return justified).
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(3, 0), balances))
        .isEqualTo(getHash(0));

    // Ensure that if we start at 1 we find 9 (just: 0, fin: 0).
    //
    //            0
    //           / \
    //  start-> 1   2
    //          |   |
    //          3   4
    //          |   |
    //          5   6
    //          |   |
    //          7   8
    //          |   |
    //  head -> 9  10
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 1), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 2.
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(2, 1), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 3 (should invalidate all so return justified).
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(3, 1), balances))
        .isEqualTo(getHash(1));

    // Ensure that if we start at 2 we find 10 (just: 0, fin: 0).
    //
    //            0
    //           / \
    //          1   2 <- start
    //          |   |
    //          3   4
    //          |   |
    //          5   6
    //          |   |
    //          7   8
    //          |   |
    //          9  10 <- head
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(0, 2), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 2.
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(2, 2), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 3 (should invalidate all so return justified).
    assertThat(applyPendingVotes(checkpoint(0, 0), checkpoint(3, 2), balances))
        .isEqualTo(getHash(2));
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

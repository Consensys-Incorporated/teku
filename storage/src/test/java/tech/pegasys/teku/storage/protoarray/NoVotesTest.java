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
import java.util.Collections;
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

public class NoVotesTest {
  private final Spec spec = TestSpecFactory.createDefault();

  private final TestStoreImpl store = createStoreToManipulateVotes();

  private final ForkChoiceStrategy forkChoice =
      createProtoArrayForkChoiceStrategy(spec, getHash(0), UInt64.valueOf(8), ONE, ONE);

  private final List<UInt64> balances = new ArrayList<>(Collections.nCopies(16, ZERO));

  @Test
  void noVotesTest() {

    // Check that the head is the finalized block.
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 0), balances))
        .isEqualTo(getHash(0));

    // Add block 2
    //
    //         0
    //        /
    //        2
    processBlock(UInt64.valueOf(9), getHash(2), getHash(0), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure the head is 2
    //
    //         0
    //        /
    //        2 <- head
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 0), balances))
        .isEqualTo(getHash(2));

    // Add block 1
    //
    //         0
    //        / \
    //        2  1
    processBlock(UInt64.valueOf(9), getHash(0), getHash(1), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure the head is still 2
    //
    //         0
    //        / \
    // head-> 2  1
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 0), balances))
        .isEqualTo(getHash(2));

    // Add block 3
    //
    //         0
    //        / \
    //        2  1
    //           |
    //           3
    processBlock(
        UInt64.valueOf(10), getHash(3), getHash(1), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure 2 is still the head
    //
    //          0
    //         / \
    // head-> 2  1
    //           |
    //           3
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 0), balances))
        .isEqualTo(getHash(2));

    // Add block 4
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    //        4  3
    processBlock(
        UInt64.valueOf(10), getHash(4), getHash(2), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure the head is 4.
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    // head-> 4  3
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 0), balances))
        .isEqualTo(getHash(4));

    // Add block 5 with a justified epoch of 2
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    //        4  3
    //        |
    //        5 <- justified epoch = 2
    processBlock(
        UInt64.valueOf(11), getHash(5), getHash(4), Bytes32.ZERO, unsigned(2), unsigned(1));

    // Ensure there is no error when starting from a block that has the wrong justified epoch.
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5 <- starting from 5 with justified epoch 0 should return justified.
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(1, 5), balances))
        .isEqualTo(getHash(5));

    // Set the justified epoch to 2 and the start block to 5 and ensure 5 is the head.
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5 <- head
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(2, 5), balances))
        .isEqualTo(getHash(5));

    // Add block 6
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5
    //     |
    //     6
    processBlock(
        UInt64.valueOf(12), getHash(6), getHash(5), Bytes32.ZERO, unsigned(2), unsigned(1));

    // Ensure 6 is the head
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5
    //     |
    //     6 <- head
    assertThat(applyPendingVotes(checkpoint(1, 0), checkpoint(2, 5), balances))
        .isEqualTo(getHash(6));
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

  private Bytes32 applyPendingVotes(
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedStateEffectiveBalances) {
    final UInt64 currentSlot = spec.getCurrentSlot(store);
    return forkChoice
        .applyPendingVotes(
            store,
            Optional.empty(),
            currentSlot,
            currentSlot,
            finalizedCheckpoint,
            justifiedCheckpoint,
            justifiedStateEffectiveBalances,
            ZERO)
        .node()
        .blockRoot();
  }

  private UInt64 unsigned(final int i) {
    return UInt64.valueOf(i);
  }

  private Checkpoint checkpoint(final long epoch, final int root) {
    return new Checkpoint(UInt64.valueOf(epoch), getHash(root));
  }
}

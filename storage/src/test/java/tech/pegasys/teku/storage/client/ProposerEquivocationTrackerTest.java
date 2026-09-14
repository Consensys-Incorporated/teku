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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;

class ProposerEquivocationTrackerTest {

  private static final UInt64 SLOT = UInt64.valueOf(10);
  private static final UInt64 PROPOSER_INDEX = UInt64.valueOf(3);

  private final Map<Bytes32, BlockTimeliness> blockTimeliness = new HashMap<>();
  private final ProposerEquivocationTracker tracker =
      new ProposerEquivocationTracker(
          16, 4, root -> Optional.ofNullable(blockTimeliness.get(root)));

  @Test
  void reportsEquivocationOnlyForDifferentRootsFromTheSameProposerAndSlot() {
    final SignedBeaconBlock first = block(SLOT, PROPOSER_INDEX, root(1));
    final SignedBeaconBlock second = block(SLOT, PROPOSER_INDEX, root(2));

    tracker.onBlockValidated(first);
    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, first.getRoot())).isFalse();

    tracker.onBlockValidated(block(SLOT, PROPOSER_INDEX.plus(1), root(3)));
    tracker.onBlockValidated(block(SLOT.plus(1), PROPOSER_INDEX, root(4)));
    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, first.getRoot())).isFalse();

    tracker.onBlockValidated(second);
    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, first.getRoot())).isTrue();
    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, second.getRoot())).isTrue();
  }

  @Test
  void tracksPtcTimelinessSeparatelyFromGenericEquivocation() {
    final SignedBeaconBlock first = block(SLOT, PROPOSER_INDEX, root(1));
    final SignedBeaconBlock lateConflict = block(SLOT, PROPOSER_INDEX, root(2));
    blockTimeliness.put(first.getRoot(), new BlockTimeliness(true, true));
    blockTimeliness.put(lateConflict.getRoot(), new BlockTimeliness(false, false));

    tracker.onBlockValidated(first);
    tracker.onBlockValidated(lateConflict);

    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, first.getRoot())).isTrue();
    assertThat(tracker.isPtcTimelyProposerEquivocation(SLOT, PROPOSER_INDEX, first.getRoot()))
        .isFalse();
    assertThat(
            tracker.isPtcTimelyProposerEquivocation(SLOT, PROPOSER_INDEX, lateConflict.getRoot()))
        .isTrue();
  }

  @Test
  void retainsOnlyRecentSlots() {
    final SignedBeaconBlock oldBlock = block(SLOT, PROPOSER_INDEX, root(1));
    tracker.onBlockValidated(oldBlock);
    tracker.onBlockValidated(block(SLOT.plus(5), PROPOSER_INDEX, root(2)));

    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, root(3))).isFalse();
  }

  @Test
  void remainsCorrectWhenMoreThanTwoEquivocatingRootsAreValidated() {
    final SignedBeaconBlock first = block(SLOT, PROPOSER_INDEX, root(1));
    tracker.onBlockValidated(first);
    tracker.onBlockValidated(block(SLOT, PROPOSER_INDEX, root(2)));
    tracker.onBlockValidated(block(SLOT, PROPOSER_INDEX, root(3)));

    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, first.getRoot())).isTrue();
    assertThat(tracker.isProposerEquivocation(SLOT, PROPOSER_INDEX, root(3))).isTrue();
  }

  private SignedBeaconBlock block(
      final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    when(block.getSlot()).thenReturn(slot);
    when(block.getProposerIndex()).thenReturn(proposerIndex);
    when(block.getRoot()).thenReturn(blockRoot);
    return block;
  }

  private Bytes32 root(final int value) {
    return Bytes32.fromHexStringLenient("0x" + Integer.toHexString(value));
  }
}

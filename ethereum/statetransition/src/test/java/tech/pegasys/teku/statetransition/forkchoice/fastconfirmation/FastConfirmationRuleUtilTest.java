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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class FastConfirmationRuleUtilTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ReadOnlyStore store = mock(ReadOnlyStore.class);

  @Test
  void shouldUpdateSlotHeadsOnlyForOrdinarySlot() {
    final Bytes32 previousHead = Bytes32.random();
    final Bytes32 currentHead = Bytes32.random();
    final Bytes32 newHead = Bytes32.random();
    final Checkpoint previousObserved = checkpoint(1);
    final Checkpoint currentObserved = checkpoint(2);
    final Checkpoint previousGreatestUnrealized = checkpoint(3);
    final Checkpoint greatestUnrealized = checkpoint(4);
    final FastConfirmationStore fcrStore =
        fastConfirmationStore(
            previousObserved,
            currentObserved,
            previousGreatestUnrealized,
            previousHead,
            currentHead);

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            fcrStore, newHead, greatestUnrealized, false, false);

    assertThat(updatedStore.previousSlotHead()).isEqualTo(currentHead);
    assertThat(updatedStore.currentSlotHead()).isEqualTo(newHead);
    assertThat(updatedStore.previousEpochObservedJustifiedCheckpoint()).isEqualTo(previousObserved);
    assertThat(updatedStore.currentEpochObservedJustifiedCheckpoint()).isEqualTo(currentObserved);
    assertThat(updatedStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(previousGreatestUnrealized);
  }

  @Test
  void shouldCaptureGreatestUnrealizedJustifiedCheckpointOnLastSlotOfEpoch() {
    final Checkpoint previousGreatestUnrealized = checkpoint(3);
    final Checkpoint greatestUnrealized = checkpoint(4);
    final FastConfirmationStore fcrStore =
        fastConfirmationStore(
            checkpoint(1),
            checkpoint(2),
            previousGreatestUnrealized,
            Bytes32.random(),
            Bytes32.random());

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            fcrStore, Bytes32.random(), greatestUnrealized, false, true);

    assertThat(updatedStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(greatestUnrealized);
  }

  @Test
  void shouldRotateObservedJustifiedCheckpointsOnStartSlotOfEpoch() {
    final Checkpoint previousObserved = checkpoint(1);
    final Checkpoint currentObserved = checkpoint(2);
    final Checkpoint previousGreatestUnrealized = checkpoint(3);
    final FastConfirmationStore fcrStore =
        fastConfirmationStore(
            previousObserved,
            currentObserved,
            previousGreatestUnrealized,
            Bytes32.random(),
            Bytes32.random());

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            fcrStore, Bytes32.random(), checkpoint(4), true, false);

    assertThat(updatedStore.previousEpochObservedJustifiedCheckpoint()).isEqualTo(currentObserved);
    assertThat(updatedStore.currentEpochObservedJustifiedCheckpoint())
        .isEqualTo(previousGreatestUnrealized);
  }

  @Test
  void shouldDetectEpochStartSlots() {
    final UInt64 epochStart = spec.computeStartSlotAtEpoch(UInt64.ONE);

    assertThat(FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, epochStart)).isTrue();
    assertThat(FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, epochStart.plus(1))).isFalse();
  }

  @Test
  void shouldDetectWhenBlockShouldBeFinalized() {
    // Minimal preset: SLOTS_PER_EPOCH == 8. A block at an epoch start is its own epoch's
    // checkpoint (epoch 1), which can be finalized at the earliest at the start of epoch 3.
    assertThat(
            FastConfirmationRuleUtil.blockShouldBeFinalized(
                spec, UInt64.valueOf(8), UInt64.valueOf(23)))
        .isFalse();
    assertThat(
            FastConfirmationRuleUtil.blockShouldBeFinalized(
                spec, UInt64.valueOf(8), UInt64.valueOf(24)))
        .isTrue();
    // Any later block in the epoch is only covered by the next checkpoint (epoch 2), so finality is
    // not expected before the start of epoch 4.
    assertThat(
            FastConfirmationRuleUtil.blockShouldBeFinalized(
                spec, UInt64.valueOf(9), UInt64.valueOf(31)))
        .isFalse();
    assertThat(
            FastConfirmationRuleUtil.blockShouldBeFinalized(
                spec, UInt64.valueOf(15), UInt64.valueOf(32)))
        .isTrue();
  }

  @Test
  void shouldComputeSlotsSinceEpochStart() {
    // Minimal preset: SLOTS_PER_EPOCH == 8
    assertThat(FastConfirmationRuleUtil.computeSlotsSinceEpochStart(spec, UInt64.valueOf(8)))
        .isEqualTo(UInt64.ZERO);
    assertThat(FastConfirmationRuleUtil.computeSlotsSinceEpochStart(spec, UInt64.valueOf(10)))
        .isEqualTo(UInt64.valueOf(2));
    assertThat(FastConfirmationRuleUtil.computeSlotsSinceEpochStart(spec, UInt64.valueOf(7)))
        .isEqualTo(UInt64.valueOf(7));
  }

  @Test
  void shouldDetectWhenFullValidatorSetIsCovered() {
    // Slots 0..7 span the whole of epoch 0
    assertThat(FastConfirmationRuleUtil.isFullValidatorSetCovered(spec, UInt64.ZERO, uint(7)))
        .isTrue();
    // Slots 0..6 do not cover any full epoch
    assertThat(FastConfirmationRuleUtil.isFullValidatorSetCovered(spec, UInt64.ZERO, uint(6)))
        .isFalse();
    // Slots 1..8 span the boundary but cover no full epoch
    assertThat(FastConfirmationRuleUtil.isFullValidatorSetCovered(spec, UInt64.ONE, uint(8)))
        .isFalse();
    // Slots 1..15 span the whole of epoch 1
    assertThat(FastConfirmationRuleUtil.isFullValidatorSetCovered(spec, UInt64.ONE, uint(15)))
        .isTrue();
  }

  @Test
  void shouldAdjustCommitteeWeightEstimateToEnsureSafety() {
    // ceil(1000 / 1000) == 1 -> 1 * (1000 + 5)
    assertThat(FastConfirmationRuleUtil.adjustCommitteeWeightEstimateToEnsureSafety(uint(1000)))
        .isEqualTo(UInt64.valueOf(1005));
    // ceil(1001 / 1000) == 2 -> 2 * (1000 + 5)
    assertThat(FastConfirmationRuleUtil.adjustCommitteeWeightEstimateToEnsureSafety(uint(1001)))
        .isEqualTo(UInt64.valueOf(2010));
    assertThat(FastConfirmationRuleUtil.adjustCommitteeWeightEstimateToEnsureSafety(UInt64.ZERO))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldReturnZeroCommitteeWeightWhenStartAfterEnd() {
    assertThat(
            FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
                spec, uint(8000), uint(5), uint(4)))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldReturnTotalActiveBalanceWhenFullEpochCovered() {
    final UInt64 totalActiveBalance = uint(8000);
    assertThat(
            FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
                spec, totalActiveBalance, UInt64.ZERO, uint(7)))
        .isEqualTo(totalActiveBalance);
  }

  @Test
  void shouldEstimateCommitteeWeightWithinSingleEpoch() {
    // committeeWeight = 8000 / 8 = 1000; slots 2..4 -> 3 committees -> 3000
    assertThat(
            FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
                spec, uint(8000), uint(2), uint(4)))
        .isEqualTo(UInt64.valueOf(3000));
  }

  @Test
  void shouldEstimateCommitteeWeightAcrossEpochBoundaryWithoutFullEpoch() {
    // total = 8000, committeeWeight = 1000; range slots 6..9 (epoch 0: 6,7; epoch 1: 8,9).
    // numSlotsInEndEpoch = 2, remainingSlotsInEndEpoch = 6, numSlotsInStartEpoch = 2.
    // startEpochWeight = 2000, endEpochWeight = 2000, proRated = (2000 / 8) * 6 = 1500.
    // sum = 3500 -> adjust: ceil(3500 / 1000) = 4 -> 4 * 1005 = 4020.
    assertThat(
            FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
                spec, uint(8000), uint(6), uint(9)))
        .isEqualTo(UInt64.valueOf(4020));
  }

  @Test
  void shouldEstimateCommitteeWeightAcrossEpochBoundaryWithNearlyFullStartEpoch() {
    // total = 8000, committeeWeight = 1000; range slots 1..9 (epoch 0: 1..7; epoch 1: 8,9).
    // numSlotsInEndEpoch = 2, remainingSlotsInEndEpoch = 6, numSlotsInStartEpoch = 7.
    // startEpochWeight = 7000, endEpochWeight = 2000, proRated = (7000 / 8) * 6 = 5250.
    // sum = 7250 -> adjust: ceil(7250 / 1000) = 8 -> 8 * 1005 = 8040.
    assertThat(
            FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
                spec, uint(8000), uint(1), uint(9)))
        .isEqualTo(UInt64.valueOf(8040));
  }

  @Test
  void shouldRaiseGreatestUnrealizedJustifiedCheckpointAboveTheJustifiedFloor() {
    final ReadOnlyForkChoiceStrategy forkChoice = mock(ReadOnlyForkChoiceStrategy.class);
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
    when(store.getJustifiedCheckpoint()).thenReturn(checkpoint(0));
    final Checkpoint greatest = checkpoint(5);
    // Materialize the per-block mocks before stubbing getBlockData to avoid nested stubbing.
    final ProtoNodeData lower = blockDataWithUnrealizedJustified(checkpoint(3));
    final ProtoNodeData highest = blockDataWithUnrealizedJustified(greatest);
    final ProtoNodeData middle = blockDataWithUnrealizedJustified(checkpoint(4));
    when(forkChoice.getBlockData()).thenReturn(List.of(lower, highest, middle));

    assertThat(FastConfirmationRuleUtil.getGreatestUnrealizedJustifiedCheckpoint(store))
        .isEqualTo(greatest);
  }

  @Test
  void shouldReturnJustifiedCheckpointWhenNoBlockHasHigherUnrealizedJustification() {
    final ReadOnlyForkChoiceStrategy forkChoice = mock(ReadOnlyForkChoiceStrategy.class);
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
    final Checkpoint justifiedCheckpoint = checkpoint(9);
    when(store.getJustifiedCheckpoint()).thenReturn(justifiedCheckpoint);
    // Blocks report a lower-epoch (zero) unrealized justified checkpoint, so the floor wins.
    final ProtoNodeData block =
        blockDataWithUnrealizedJustified(new Checkpoint(UInt64.ZERO, Bytes32.ZERO));
    when(forkChoice.getBlockData()).thenReturn(List.of(block));

    assertThat(FastConfirmationRuleUtil.getGreatestUnrealizedJustifiedCheckpoint(store))
        .isEqualTo(justifiedCheckpoint);
  }

  private ProtoNodeData blockDataWithUnrealizedJustified(final Checkpoint unrealizedJustified) {
    final ProtoNodeData data = mock(ProtoNodeData.class);
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    when(data.getCheckpoints())
        .thenReturn(new BlockCheckpoints(zero, zero, unrealizedJustified, zero));
    return data;
  }

  private FastConfirmationStore fastConfirmationStore(
      final Checkpoint previousObserved,
      final Checkpoint currentObserved,
      final Checkpoint previousGreatestUnrealized,
      final Bytes32 previousHead,
      final Bytes32 currentHead) {
    return new FastConfirmationStore(
        store,
        Bytes32.random(),
        previousObserved,
        currentObserved,
        previousGreatestUnrealized,
        previousHead,
        currentHead);
  }

  private Checkpoint checkpoint(final int epoch) {
    return new Checkpoint(UInt64.valueOf(epoch), Bytes32.random());
  }

  private UInt64 uint(final long value) {
    return UInt64.valueOf(value);
  }
}

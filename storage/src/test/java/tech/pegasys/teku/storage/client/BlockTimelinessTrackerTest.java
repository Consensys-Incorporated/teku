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

import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockTimelinessTrackerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
  private final UInt64 slot = UInt64.ONE;

  private SignedBlockAndState signedBlockAndState;
  private UInt64 genesisTimeMillis;
  private BlockTimelinessTracker tracker;

  @BeforeEach
  void setup() {
    signedBlockAndState = dataStructureUtil.randomSignedBlockAndState(slot);
    genesisTimeMillis = signedBlockAndState.getState().getGenesisTime().times(1000);
    tracker = new BlockTimelinessTracker(spec, () -> genesisTimeMillis, new HashMap<>());
  }

  @Test
  void shouldReportTimelinessIfSet() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isFalse();
  }

  @Test
  void shouldRefreshUnconfirmedObservationWithLaterArrival() {
    // A block observation isn't confirmed until the block is actually, successfully imported, so
    // a later observation (e.g. from a later import attempt) refreshes an earlier one rather than
    // being stuck with it - this stops a premature or ultimately unsuccessful first arrival from
    // permanently pinning an incorrect timeliness value.
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 3000));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isFalse());
  }

  @Test
  void shouldKeepConfirmedObservationEvenAfterLaterArrival() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));
    tracker.confirmBlockTimeliness(signedBlockAndState.getBlock(), computeTime(slot, 500));

    // Once confirmed (i.e. the block was successfully imported), the recording is final and a
    // later, unrelated arrival observation must not be able to change it.
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 3000));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void confirmBlockTimelinessShouldPromoteExistingUnconfirmedObservation() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));

    tracker.confirmBlockTimeliness(signedBlockAndState.getBlock(), computeTime(slot, 3000));

    // The unconfirmed (timely) observation is promoted as-is; the fallback time passed to
    // confirmBlockTimeliness is ignored since an observation was already present.
    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void confirmBlockTimelinessShouldComputeFreshWhenNoPriorObservationExists() {
    // e.g. a block imported directly via RPC with no prior gossip arrival ever recorded.
    tracker.confirmBlockTimeliness(signedBlockAndState.getBlock(), computeTime(slot, 500));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void invalidateUnconfirmedTimelinessShouldDiscardUnconfirmedObservation() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));

    tracker.invalidateUnconfirmedTimeliness(signedBlockAndState.getRoot());

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot())).isEmpty();
  }

  @Test
  void invalidateUnconfirmedTimelinessShouldNotDiscardConfirmedObservation() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));
    tracker.confirmBlockTimeliness(signedBlockAndState.getBlock(), computeTime(slot, 500));

    tracker.invalidateUnconfirmedTimeliness(signedBlockAndState.getRoot());

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void invalidateThenRetryShouldAllowFreshTimelinessToBeRecorded() {
    // Simulates a block gossiped prematurely (e.g. just before its slot, within clock disparity
    // tolerance), deferred, and then later retried once its slot has genuinely started.
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 2100));
    tracker.invalidateUnconfirmedTimeliness(signedBlockAndState.getRoot());

    // The retried attempt records a fresh, timely observation instead of being stuck with the
    // stale, late-looking premature one.
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));
    tracker.confirmBlockTimeliness(signedBlockAndState.getBlock(), computeTime(slot, 500));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void setBlockTimelinessIfAbsentShouldNotOverwriteExistingConfirmedObservation() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 500));
    tracker.confirmBlockTimeliness(signedBlockAndState.getBlock(), computeTime(slot, 500));

    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 3000));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isTrue());
  }

  @Test
  void shouldReportLateBlock() {
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, 2100));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isFalse());
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isTrue();
  }

  @Test
  void shouldReportLateBlockAtTimelinessLimit() {
    final int timelinessLimit = spec.atSlot(slot).getForkChoiceUtil().getAttestationDueMillis();

    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computeTime(slot, timelinessLimit));

    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot()))
        .isPresent()
        .hasValueSatisfying(timeliness -> assertThat(timeliness.isTimelyAttestation()).isFalse());
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isTrue();
  }

  @Test
  void shouldReturnEmptyForUnknownBlock() {
    assertThat(tracker.getBlockTimeliness(signedBlockAndState.getRoot())).isEmpty();
    assertThat(tracker.isBlockLate(signedBlockAndState.getRoot())).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(SpecMilestone.class)
  void shouldSkipArrivalTimelinessOnlyForFulu(final SpecMilestone milestone) {
    final Spec testSpec = TestSpecFactory.createMinimal(milestone);
    final DataStructureUtil util = new DataStructureUtil(testSpec);
    final SignedBlockAndState block = util.randomSignedBlockAndState(slot);
    final UInt64 milestoneGenesisMillis = block.getState().getGenesisTime().times(1000);
    final BlockTimelinessTracker testTracker =
        new BlockTimelinessTracker(testSpec, () -> milestoneGenesisMillis, new HashMap<>());

    testTracker.setBlockTimelinessFromArrivalTime(
        block.getBlock(), computeTime(milestoneGenesisMillis, testSpec, slot, 100));

    if (milestone == SpecMilestone.FULU) {
      assertThat(testTracker.getBlockTimeliness(block.getRoot())).isEmpty();
    } else {
      assertThat(testTracker.getBlockTimeliness(block.getRoot())).isPresent();
    }
  }

  @Test
  void fuluShouldRecordTimelinessAfterDataAvailabilityWhenTimely() {
    final Spec fuluSpec = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil fuluUtil = new DataStructureUtil(fuluSpec);
    final SignedBlockAndState fuluBlock = fuluUtil.randomSignedBlockAndState(slot);
    final UInt64 fuluGenesisMillis = fuluBlock.getState().getGenesisTime().times(1000);
    final BlockTimelinessTracker fuluTracker =
        new BlockTimelinessTracker(fuluSpec, () -> fuluGenesisMillis, new HashMap<>());

    fuluTracker.setBlockTimelinessAfterDataAvailability(
        fuluBlock.getBlock(), computeTime(fuluGenesisMillis, fuluSpec, slot, 100));

    assertThat(fuluTracker.getBlockTimeliness(fuluBlock.getRoot()))
        .isPresent()
        .hasValueSatisfying(t -> assertThat(t.isTimelyAttestation()).isTrue());
    assertThat(fuluTracker.isBlockLate(fuluBlock.getRoot())).isFalse();
  }

  @Test
  void fuluShouldRecordLateTimelinessAfterDataAvailabilityWhenLate() {
    final Spec fuluSpec = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil fuluUtil = new DataStructureUtil(fuluSpec);
    final SignedBlockAndState fuluBlock = fuluUtil.randomSignedBlockAndState(slot);
    final UInt64 fuluGenesisMillis = fuluBlock.getState().getGenesisTime().times(1000);
    final BlockTimelinessTracker fuluTracker =
        new BlockTimelinessTracker(fuluSpec, () -> fuluGenesisMillis, new HashMap<>());

    final int attestationDueMillis =
        fuluSpec.atSlot(slot).getForkChoiceUtil().getAttestationDueMillis();
    fuluTracker.setBlockTimelinessAfterDataAvailability(
        fuluBlock.getBlock(), computeTime(fuluGenesisMillis, fuluSpec, slot, attestationDueMillis));

    assertThat(fuluTracker.getBlockTimeliness(fuluBlock.getRoot()))
        .isPresent()
        .hasValueSatisfying(t -> assertThat(t.isTimelyAttestation()).isFalse());
    assertThat(fuluTracker.isBlockLate(fuluBlock.getRoot())).isTrue();
  }

  private UInt64 computeTime(final UInt64 slot, final long timeIntoSlot) {
    return genesisTimeMillis.plus(slot.times(millisPerSlot)).plus(timeIntoSlot);
  }

  private UInt64 computeTime(
      final UInt64 genesisMillis,
      final Spec targetSpec,
      final UInt64 targetSlot,
      final long timeIntoSlot) {
    return genesisMillis
        .plus(targetSlot.times(targetSpec.getGenesisSpecConfig().getSlotDurationMillis()))
        .plus(timeIntoSlot);
  }
}

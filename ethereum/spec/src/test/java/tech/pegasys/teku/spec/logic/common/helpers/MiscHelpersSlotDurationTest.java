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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

/**
 * EIP-8198: slot durations change at the Heze fork. Minimal preset: 8 slots per epoch and 6000 ms
 * slots, with Heze at epoch 2 (slot 16, 96 s after genesis) switching to 5000 ms slots.
 */
class MiscHelpersSlotDurationTest {

  private static final UInt64 GENESIS_MILLIS = UInt64.valueOf(1_000_000_000L);
  private static final UInt64 GENESIS_SECONDS = UInt64.valueOf(1_000_000L);

  private final Spec spec = createSpec(UInt64.valueOf(2), 5000);
  private final MiscHelpers miscHelpers = spec.getGenesisSpec().miscHelpers();

  @Test
  void getSlotDurationMillis_shouldChangeAtHezeForkEpoch() {
    assertThat(miscHelpers.getSlotDurationMillis(UInt64.ZERO)).isEqualTo(6000);
    assertThat(miscHelpers.getSlotDurationMillis(UInt64.ONE)).isEqualTo(6000);
    assertThat(miscHelpers.getSlotDurationMillis(UInt64.valueOf(2))).isEqualTo(5000);
    assertThat(miscHelpers.getSlotDurationMillis(UInt64.valueOf(100))).isEqualTo(5000);

    assertThat(spec.getSlotDurationMillis(UInt64.valueOf(15))).isEqualTo(6000);
    assertThat(spec.getSlotDurationMillis(UInt64.valueOf(16))).isEqualTo(5000);
  }

  @Test
  void computeTimeMillisAtSlot_shouldUseEachSlotsDuration() {
    assertThat(miscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, UInt64.valueOf(15)))
        .isEqualTo(GENESIS_MILLIS.plus(90_000));
    assertThat(miscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, UInt64.valueOf(16)))
        .isEqualTo(GENESIS_MILLIS.plus(96_000));
    assertThat(miscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, UInt64.valueOf(17)))
        .isEqualTo(GENESIS_MILLIS.plus(101_000));
    assertThat(miscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, UInt64.valueOf(26)))
        .isEqualTo(GENESIS_MILLIS.plus(146_000));
  }

  @Test
  void computeSlotAtTimeMillis_shouldUseEachSlotsDuration() {
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, GENESIS_MILLIS.plus(95_999)))
        .isEqualTo(UInt64.valueOf(15));
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, GENESIS_MILLIS.plus(96_000)))
        .isEqualTo(UInt64.valueOf(16));
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, GENESIS_MILLIS.plus(100_999)))
        .isEqualTo(UInt64.valueOf(16));
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, GENESIS_MILLIS.plus(101_000)))
        .isEqualTo(UInt64.valueOf(17));
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, GENESIS_MILLIS.minus(1)))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void timeAndSlotConversions_shouldRoundTripAcrossTheFork() {
    for (int i = 1; i < 64; i++) {
      final UInt64 slot = UInt64.valueOf(i);
      final UInt64 slotStartMillis = miscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, slot);
      assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, slotStartMillis))
          .isEqualTo(slot);
      assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_MILLIS, slotStartMillis.minus(1)))
          .isEqualTo(slot.minus(1));

      final UInt64 slotStartSeconds = miscHelpers.computeTimeAtSlot(GENESIS_SECONDS, slot);
      assertThat(slotStartSeconds.times(1000))
          .isEqualTo(miscHelpers.computeTimeMillisAtSlot(GENESIS_SECONDS.times(1000), slot));
      assertThat(miscHelpers.computeSlotAtTime(GENESIS_SECONDS, slotStartSeconds)).isEqualTo(slot);
      assertThat(miscHelpers.computeSlotAtTime(GENESIS_SECONDS, slotStartSeconds.minus(1)))
          .isEqualTo(slot.minus(1));
    }
  }

  @Test
  void allSpecVersions_shouldAgreeOnTheClock() {
    final MiscHelpers hezeMiscHelpers = spec.forMilestone(SpecMilestone.HEZE).miscHelpers();
    final MiscHelpers gloasMiscHelpers = spec.forMilestone(SpecMilestone.GLOAS).miscHelpers();
    for (int i = 0; i < 64; i++) {
      final UInt64 slot = UInt64.valueOf(i);
      assertThat(gloasMiscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, slot))
          .isEqualTo(hezeMiscHelpers.computeTimeMillisAtSlot(GENESIS_MILLIS, slot));
    }
  }

  @Test
  void spec_shouldSwitchMilestoneAtTheForkTime() {
    assertThat(
            spec.getForkSchedule()
                .getSpecMilestoneAtTime(GENESIS_SECONDS, GENESIS_SECONDS.plus(95)))
        .isEqualTo(SpecMilestone.GLOAS);
    assertThat(
            spec.getForkSchedule()
                .getSpecMilestoneAtTime(GENESIS_SECONDS, GENESIS_SECONDS.plus(96)))
        .isEqualTo(SpecMilestone.HEZE);

    assertThat(spec.getCurrentSlot(GENESIS_SECONDS.plus(100), GENESIS_SECONDS))
        .isEqualTo(UInt64.valueOf(16));
    assertThat(spec.getCurrentSlot(GENESIS_SECONDS.plus(101), GENESIS_SECONDS))
        .isEqualTo(UInt64.valueOf(17));
  }

  @Test
  void slotComponentDeadlines_shouldUseTheHezeSlotDurationFromTheFork() {
    // ATTESTATION_DUE_BPS_GLOAS = 2500
    assertThat(spec.getAttestationDueMillis(UInt64.valueOf(15))).isEqualTo(1500);
    assertThat(spec.getAttestationDueMillis(UInt64.valueOf(16))).isEqualTo(1250);
    final int inclusionListDueBps =
        spec.forMilestone(SpecMilestone.HEZE)
            .getConfig()
            .toVersionHeze()
            .orElseThrow()
            .getInclusionListDueBps();
    assertThat(spec.getInclusionListDueMillis(UInt64.valueOf(16)))
        .contains(inclusionListDueBps * 5000 / 10_000);
  }

  @Test
  void hezeAtGenesis_shouldUseTheHezeSlotDurationThroughout() {
    final MiscHelpers helpers = createSpec(UInt64.ZERO, 5000).getGenesisSpec().miscHelpers();
    assertThat(helpers.getSlotDurationMillis(UInt64.ZERO)).isEqualTo(5000);
    assertThat(helpers.computeTimeMillisAtSlot(GENESIS_MILLIS, UInt64.valueOf(3)))
        .isEqualTo(GENESIS_MILLIS.plus(15_000));
    assertThat(helpers.computeSlotAtTimeMillis(GENESIS_MILLIS, GENESIS_MILLIS.plus(14_999)))
        .isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void unchangedSlotDuration_shouldBeLinearFromGenesis() {
    final MiscHelpers helpers = createSpec(UInt64.valueOf(2), 6000).getGenesisSpec().miscHelpers();
    for (int i = 0; i < 64; i++) {
      final UInt64 slot = UInt64.valueOf(i);
      assertThat(helpers.computeTimeMillisAtSlot(GENESIS_MILLIS, slot))
          .isEqualTo(GENESIS_MILLIS.plus(slot.times(6000)));
      assertThat(helpers.computeTimeAtSlot(GENESIS_SECONDS, slot))
          .isEqualTo(GENESIS_SECONDS.plus(slot.times(6)));
    }
    assertThat(helpers.getSlotDurationMillis(UInt64.valueOf(2))).isEqualTo(6000);
  }

  private static Spec createSpec(final UInt64 hezeForkEpoch, final int slotDurationMsHeze) {
    return TestSpecFactory.createMinimalHeze(
        builder -> builder.hezeForkEpoch(hezeForkEpoch).slotDurationMsHeze(slotDurationMsHeze));
  }
}

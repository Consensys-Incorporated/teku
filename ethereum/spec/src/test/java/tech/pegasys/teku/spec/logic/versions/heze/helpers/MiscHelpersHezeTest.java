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

package tech.pegasys.teku.spec.logic.versions.heze.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

/**
 * EIP-8198: the data column sidecar retention window is MIN_BLOB_DATA_RETENTION_MS of wall-clock
 * time. Minimal preset: 8 slots per epoch, 6000 ms slots and a 4096 epoch (196,608,000 ms) window.
 */
class MiscHelpersHezeTest {

  @Test
  void retentionStartEpoch_shouldMatchTheEpochWindowWhenTheSlotDurationIsUnchanged() {
    final MiscHelpersHeze miscHelpers = createMiscHelpers(6000);

    assertThat(miscHelpers.computeDataColumnSidecarRetentionStartEpoch(UInt64.valueOf(6000)))
        .isEqualTo(UInt64.valueOf(6000 - 4096));
    assertThat(miscHelpers.computeDataColumnSidecarRetentionStartEpoch(UInt64.valueOf(4096)))
        .isEqualTo(UInt64.ZERO);
    assertThat(miscHelpers.computeDataColumnSidecarRetentionStartEpoch(UInt64.valueOf(100)))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void retentionStartEpoch_shouldKeepTheWallClockWindowAfterTheSlotDurationChange() {
    final MiscHelpersHeze miscHelpers = createMiscHelpers(5000);

    // epoch 6000 starts at 48,000 + (48,000 - 8) * 5,000 = 240,008,000 ms; the window starts at
    // 43,400,000 ms, which is slot 8 + 43,352,000 / 5,000 = 8678, in epoch 1084
    assertThat(miscHelpers.computeDataColumnSidecarRetentionStartEpoch(UInt64.valueOf(6000)))
        .isEqualTo(UInt64.valueOf(1084));
    assertThat(miscHelpers.computeDataColumnSidecarRetentionStartEpoch(UInt64.valueOf(100)))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void dataColumnSidecarAvailability_shouldUseTheRetentionWindow() {
    final MiscHelpersHeze miscHelpers = createMiscHelpers(5000);

    assertThat(
            miscHelpers.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
                UInt64.valueOf(6000), UInt64.valueOf(1084)))
        .isTrue();
    assertThat(
            miscHelpers.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
                UInt64.valueOf(6000), UInt64.valueOf(1083)))
        .isFalse();
  }

  private static MiscHelpersHeze createMiscHelpers(final int slotDurationMsHeze) {
    return TestSpecFactory.createMinimalHeze(
            builder -> builder.hezeForkEpoch(UInt64.ONE).slotDurationMsHeze(slotDurationMsHeze))
        .forMilestone(SpecMilestone.HEZE)
        .miscHelpers()
        .toVersionHeze()
        .orElseThrow();
  }
}

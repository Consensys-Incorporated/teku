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

package tech.pegasys.teku.spec.logic.versions.deneb.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;

public class AttestationUtilDenebTest {

  private static final SpecVersion SPEC_VERSION =
      TestSpecFactory.createMinimalDeneb().forMilestone(SpecMilestone.DENEB);

  private static final int SECONDS_PER_SLOT = SPEC_VERSION.getConfig().getSecondsPerSlot();

  private final AttestationUtilDeneb attestationUtilDeneb =
      (AttestationUtilDeneb) SPEC_VERSION.getAttestationUtil();

  @ParameterizedTest
  @MethodSource("provideAttestationSlotIsAfterCurrentTimeArguments")
  public void testAttestationSlotIsAfterCurrentTime(
      final int attestationSlot, final UInt64 currentTimeMillis, final boolean expectedResult) {
    // set genesisTime as 0 for simplification
    final UInt64 genesisTimeSeconds = UInt64.ZERO;
    final boolean actualResult =
        attestationUtilDeneb.isAttestationSlotAfterCurrentTime(
            UInt64.valueOf(attestationSlot), genesisTimeSeconds, currentTimeMillis);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @MethodSource("provideAttestationSlotInCurrentOrPreviousEpochArguments")
  public void testAttestationSlotInCurrentOrPreviousEpoch(
      final int attestationSlot, final UInt64 currentTimeSeconds, final boolean expectedResult) {
    final UInt64 currentTimeMillis = secondsToMillis(currentTimeSeconds);
    // set genesisTime as 0 for simplification
    final UInt64 genesisTimeSeconds = UInt64.ZERO;
    final boolean actualResult =
        attestationUtilDeneb.isAttestationSlotInCurrentOrPreviousEpoch(
            UInt64.valueOf(attestationSlot), genesisTimeSeconds, currentTimeMillis);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  private static Stream<Arguments> provideAttestationSlotIsAfterCurrentTimeArguments() {
    return Stream.of(
        Arguments.of(3, getTimeForSlotInMillis(2), true),
        Arguments.of(1, getTimeForSlotInMillis(2), false),
        // testing MAXIMUM_GOSSIP_CLOCK_DISPARITY)
        Arguments.of(2, getTimeForSlotInMillis(2).minus(500), false),
        Arguments.of(2, getTimeForSlotInMillis(2).minus(501), true));
  }

  // minimal is 6 seconds per slot, 8 slots per epoch, MAXIMUM_GOSSIP_CLOCK_DISPARITY is 500 ms
  private static Stream<Arguments> provideAttestationSlotInCurrentOrPreviousEpochArguments() {
    return Stream.of(
        // lower_bound pass
        Arguments.of(8, getTimeForSlot(17), true),
        Arguments.of(15, getTimeForSlot(17), true),
        Arguments.of(16, getTimeForSlot(17), true),
        // lower_bound fail
        Arguments.of(7, getTimeForSlot(17), false),
        Arguments.of(6, getTimeForSlot(17), false),
        // upper_bound pass
        Arguments.of(23, getTimeForSlot(17), true),
        Arguments.of(22, getTimeForSlot(17), true),
        // upper_bound fail
        Arguments.of(24, getTimeForSlot(17), false),
        Arguments.of(25, getTimeForSlot(17), false),
        // edge case
        Arguments.of(0, getTimeForSlot(0), true));
  }

  private static UInt64 getTimeForSlot(final int slot) {
    return UInt64.valueOf(slot).times(SECONDS_PER_SLOT);
  }

  private static UInt64 getTimeForSlotInMillis(final int slot) {
    return secondsToMillis(getTimeForSlot(slot));
  }
}

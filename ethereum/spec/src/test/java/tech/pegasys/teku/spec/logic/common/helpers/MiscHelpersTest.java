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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import it.unimi.dsi.fastutil.ints.IntList;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MiscHelpersTest {
  private static final SpecConfig MINIMAL_SPEC_CONFIG =
      TestSpecFactory.createMinimalPhase0().getGenesisSpecConfig();
  private static final int SECONDS_PER_SLOT = MINIMAL_SPEC_CONFIG.getSecondsPerSlot();
  private static final int MAXIMUM_GOSSIP_CLOCK_DISPARITY =
      MINIMAL_SPEC_CONFIG.getMaximumGossipClockDisparity();
  private static final UInt64 GENESIS_TIME = UInt64.valueOf("1591924193");
  private static final UInt64 GENESIS_TIME_MILLIS = GENESIS_TIME.times(1000L);

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SpecConfig specConfig = spec.getGenesisSpecConfig();
  private final MiscHelpers miscHelpers = new MiscHelpers(specConfig);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot50Time =
      GENESIS_TIME.plus(spec.getGenesisSpecConfig().getSecondsPerSlot() * 50L);
  private final UInt64 slot50TimeMillis = secondsToMillis(slot50Time);

  @Test
  void computeShuffledIndex_boundaryTest() {
    assertThatThrownBy(() -> miscHelpers.computeShuffledIndex(2, 1, Bytes32.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void computeShuffledIndex_samples() {
    final SpecConfig specConfig = mock(SpecConfig.class);
    final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

    when(specConfig.getShuffleRoundCount()).thenReturn(90);
    assertThat(miscHelpers.computeShuffledIndex(320, 2048, Bytes32.ZERO)).isEqualTo(0);
    assertThat(miscHelpers.computeShuffledIndex(1291, 2048, Bytes32.ZERO)).isEqualTo(1);
    assertThat(miscHelpers.computeShuffledIndex(933, 2048, Bytes32.ZERO)).isEqualTo(2047);
  }

  @Test
  void testListShuffleAndShuffledIndexCompatibility() {
    final SpecConfig specConfig = mock(SpecConfig.class);
    final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

    when(specConfig.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int indexCount = 3333;
    int[] indices = IntStream.range(0, indexCount).toArray();

    miscHelpers.shuffleList(indices, seed);
    assertThat(indices)
        .isEqualTo(
            IntStream.range(0, indexCount)
                .map(i -> miscHelpers.computeShuffledIndex(i, indices.length, seed))
                .toArray());
  }

  @Test
  void shuffleList_compareListAndArrayVersions() {
    final SpecConfig specConfig = mock(SpecConfig.class);
    final MiscHelpers miscHelpers = new MiscHelpers(specConfig);

    when(specConfig.getShuffleRoundCount()).thenReturn(10);
    Bytes32 seed = Bytes32.ZERO;
    int indexCount = 3333;

    int[] indices = IntStream.range(0, indexCount).toArray();
    miscHelpers.shuffleList(indices, seed);

    IntList indexList = IntList.of(IntStream.range(0, indexCount).toArray());
    final List<Integer> result = miscHelpers.shuffleList(indexList, seed);

    assertThat(result)
        .containsExactlyElementsOf(Arrays.stream(indices).boxed().collect(Collectors.toList()));
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  void isSlotAtNthEpochBoundary_withSkippedBlock(final int n) {
    final int nthStartSlot = miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_oneEpochAndSlotSkipped(final int n) {
    final int nthStartSlot = miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + specConfig.getSlotsPerEpoch() + 1);
    final UInt64 block2Slot = block1Slot.plus(1);

    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_nearlyNEpochsSkipped(final int n) {
    final int startSlotAt2N =
        miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(n * 2L)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(startSlotAt2N - 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    final UInt64 block3Slot = block2Slot.plus(1);

    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isTrue();
    assertThat(miscHelpers.isSlotAtNthEpochBoundary(block3Slot, block2Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_allSlotsFilled(final int n) {
    final UInt64 epochs = UInt64.valueOf(n * 3L);
    final UInt64 slots = epochs.times(specConfig.getSlotsPerEpoch());

    for (int i = 1; i <= slots.intValue(); i++) {
      final boolean expected = i % (n * specConfig.getSlotsPerEpoch()) == 0 && i != 0;

      final UInt64 blockSlot = UInt64.valueOf(i);
      assertThat(miscHelpers.isSlotAtNthEpochBoundary(blockSlot, blockSlot.minus(1), n))
          .describedAs("Block at %d should %sbe at epoch boundary", i, expected ? "" : "not ")
          .isEqualTo(expected);
    }
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_zero() {
    assertThatThrownBy(() -> miscHelpers.isSlotAtNthEpochBoundary(UInt64.ONE, UInt64.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_negative() {
    assertThatThrownBy(() -> miscHelpers.isSlotAtNthEpochBoundary(UInt64.ONE, UInt64.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @ParameterizedTest
  @MethodSource("provideSubnetsForNodeIds")
  public void testDiscoveryNodeBasedSubnetIds(
      final UInt256 nodeId, final UInt64 epoch, final List<UInt64> subnetIds) {
    final List<UInt64> nodeSubnetIds = miscHelpers.computeSubscribedSubnets(nodeId, epoch);
    assertThat(nodeSubnetIds).hasSize(subnetIds.size());
    assertThat(nodeSubnetIds).isEqualTo(subnetIds);
  }

  @ParameterizedTest
  @MethodSource("provideNodeIdsAndSlots")
  public void unsubscriptionEpochMustMatchSubnetsCalculationResultChange(
      final UInt256 nodeId, final UInt64 slotAtEpoch) {
    for (int epoch = 0; epoch < 1000; epoch++) {
      final List<UInt64> currentSubnets =
          miscHelpers.computeSubscribedSubnets(nodeId, UInt64.valueOf(epoch));
      final List<UInt64> nextSubnets =
          miscHelpers.computeSubscribedSubnets(nodeId, UInt64.valueOf(epoch + 1));
      final UInt64 currentSlot =
          miscHelpers.computeStartSlotAtEpoch(UInt64.valueOf(epoch)).plus(slotAtEpoch);
      final UInt64 unsubscriptionSlot =
          miscHelpers.calculateNodeSubnetUnsubscriptionSlot(nodeId, currentSlot);
      final UInt64 unsubscriptionEpoch = miscHelpers.computeEpochAtSlot(unsubscriptionSlot);
      if (!currentSubnets.equals(nextSubnets)) {
        assertThat(unsubscriptionEpoch).isEqualTo(UInt64.valueOf(epoch + 1));
      } else {
        assertThat(unsubscriptionEpoch.isGreaterThan(epoch)).isTrue();
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getComputesSlotAtTimeArguments")
  public void computesSlotAtTime(final long currentTime, final UInt64 expectedSlot) {
    final UInt64 actualSlot =
        miscHelpers.computeSlotAtTime(UInt64.ZERO, UInt64.valueOf(currentTime));
    assertThat(actualSlot).isEqualTo(expectedSlot);
  }

  @ParameterizedTest
  @MethodSource("getComputesTimeAtSlotArguments")
  public void computesTimeAtSlot(final UInt64 slot, final long expectedTime) {
    final UInt64 actualTime = miscHelpers.computeTimeAtSlot(UInt64.ZERO, slot);
    assertThat(actualTime).isEqualTo(UInt64.valueOf(expectedTime));
  }

  @ParameterizedTest
  @MethodSource("getCommitteeComputationArguments")
  public void committeeComputationShouldNotOverflow(
      final int activeValidatorsCount, final int committeeIndex) {
    final IntList indices = IntList.of(IntStream.range(0, activeValidatorsCount).toArray());
    Assertions.assertDoesNotThrow(
        () -> {
          miscHelpers.computeCommittee(
              dataStructureUtil.randomBeaconState(),
              indices,
              dataStructureUtil.randomBytes32(),
              committeeIndex,
              2048);
        });
  }

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void computeForkVersion_seesAllForks(final SpecMilestone milestone) {
    // This test would fail if computeForkVersion is not seeing a spec milestone
    final Spec spec = TestSpecFactory.create(milestone, Eth2Network.MINIMAL);
    final MiscHelpers miscHelpers = spec.atSlot(UInt64.ZERO).miscHelpers();
    assertThat(spec.getForkSchedule().getFork(milestone).getCurrentVersion())
        .isEqualTo(miscHelpers.computeForkVersion(UInt64.ZERO));
  }

  @Test
  public void isFormerDepositMechanismDisabled_returnsFalseForAllForksPriorToElectra() {
    SpecMilestone.getAllPriorMilestones(SpecMilestone.ELECTRA)
        .forEach(
            milestone -> {
              final Spec spec = TestSpecFactory.create(milestone, Eth2Network.MINIMAL);
              final MiscHelpers miscHelpers = spec.atSlot(UInt64.ZERO).miscHelpers();
              assertThat(
                      miscHelpers.isFormerDepositMechanismDisabled(
                          dataStructureUtil.randomBeaconState()))
                  .isFalse();
            });
  }

  @Test
  public void getSlotStartTime_shouldGetCorrectTimePastGenesis() {
    assertThat(miscHelpers.computeTimeAtSlot(GENESIS_TIME, UInt64.valueOf(50L)))
        .isEqualTo(slot50Time);
  }

  @Test
  public void getSlotStartTime_shouldGetGenesisTimeForBlockZero() {
    assertThat(miscHelpers.computeTimeAtSlot(GENESIS_TIME, UInt64.ZERO)).isEqualTo(GENESIS_TIME);
  }

  @Test
  public void getSlotStartTimeMillis_shouldGetGenesisTimeForBlockZeroMillis() {
    assertThat(miscHelpers.computeTimeMillisAtSlot(GENESIS_TIME_MILLIS, UInt64.ZERO))
        .isEqualTo(GENESIS_TIME_MILLIS);
  }

  @Test
  public void getSlotStartTimeMillis_shouldGetCorrectTimePastGenesisMillis() {
    assertThat(miscHelpers.computeTimeMillisAtSlot(GENESIS_TIME_MILLIS, UInt64.valueOf(50L)))
        .isEqualTo(slot50TimeMillis);
  }

  @ParameterizedTest
  @MethodSource("provideCurrentTimeWithinInclusiveSlotRangeArguments")
  void isCurrentTimeWithinInclusiveSlotRange_shouldApplyClockDisparityToBothBoundaries(
      final UInt64 currentTimeMillis, final boolean expectedResult) {
    // set genesisTime as 0 for simplification
    final UInt64 genesisTimeSeconds = UInt64.ZERO;
    final UInt64 startSlot = UInt64.valueOf(8);
    final UInt64 inclusiveEndSlotOffset = UInt64.valueOf(7);

    assertThat(
            miscHelpers.isCurrentTimeWithinInclusiveSlotRange(
                startSlot, inclusiveEndSlotOffset, genesisTimeSeconds, currentTimeMillis))
        .isEqualTo(expectedResult);
  }

  @Test
  public void getCurrentSlot_shouldGetZeroAtGenesis() {
    assertThat(miscHelpers.computeSlotAtTime(GENESIS_TIME, GENESIS_TIME)).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {
    assertThat(miscHelpers.computeSlotAtTime(GENESIS_TIME, slot50Time))
        .isEqualTo(UInt64.valueOf(50L));
  }

  @Test
  public void getCurrentSlot_shouldGetZeroPriorToGenesis() {
    assertThat(miscHelpers.computeSlotAtTime(GENESIS_TIME, GENESIS_TIME.minus(1)))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlotForMillis_shouldGetZeroAtGenesisMillis() {
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_TIME_MILLIS, GENESIS_TIME_MILLIS))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  public void getCurrentSlotForMillis_shouldGetNonZeroPastGenesisMillis() {
    assertThat(miscHelpers.computeSlotAtTimeMillis(GENESIS_TIME_MILLIS, slot50TimeMillis))
        .isEqualTo(UInt64.valueOf(50L));
  }

  @Test
  public void getCurrentSlotForMillis_shouldGetZeroPriorToGenesisMillis() {
    assertThat(
            miscHelpers.computeSlotAtTimeMillis(
                GENESIS_TIME_MILLIS.minus(1000), GENESIS_TIME_MILLIS))
        .isEqualTo(UInt64.ZERO);
  }

  public static Stream<Arguments> getComputesSlotAtTimeArguments() {
    // 6 seconds per slot
    return Stream.of(
        Arguments.of(6 * 10, UInt64.valueOf(10)),
        Arguments.of(6 * 4, UInt64.valueOf(4)),
        Arguments.of(0, UInt64.ZERO),
        Arguments.of(60253, UInt64.valueOf(10042)));
  }

  public static Stream<Arguments> getComputesTimeAtSlotArguments() {
    // 6 seconds per slot
    return Stream.of(
        Arguments.of(UInt64.valueOf(10), 6 * 10),
        Arguments.of(UInt64.valueOf(4), 6 * 4),
        Arguments.of(UInt64.ZERO, 0),
        Arguments.of(UInt64.valueOf(10042), 60252));
  }

  private static Stream<Arguments> provideCurrentTimeWithinInclusiveSlotRangeArguments() {
    return Stream.of(
        // start of [8, 15], with MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance
        Arguments.of(getTimeForSlotInMillis(8).minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY + 1), false),
        Arguments.of(getTimeForSlotInMillis(8).minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY), true),
        Arguments.of(getTimeForSlotInMillis(12), true),
        // end of [8, 15] is start of slot 16, with MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance
        Arguments.of(getTimeForSlotInMillis(16).plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY), true),
        Arguments.of(getTimeForSlotInMillis(16).plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY + 1), false));
  }

  private static UInt64 getTimeForSlotInMillis(final int slot) {
    return secondsToMillis(UInt64.valueOf(slot).times(SECONDS_PER_SLOT));
  }

  public static Stream<Arguments> getNValues() {
    return Stream.of(
        Arguments.of(1), Arguments.of(2), Arguments.of(3), Arguments.of(4), Arguments.of(5));
  }

  public static Stream<Arguments> provideSubnetsForNodeIds() {
    return Stream.of(
        Arguments.of(
            UInt256.valueOf(434726285098L),
            UInt64.valueOf(6717051035888874875L),
            List.of(UInt64.valueOf(28), UInt64.valueOf(29))),
        Arguments.of(
            UInt256.valueOf(288055627580L),
            UInt64.valueOf("13392352527348795112"),
            List.of(UInt64.valueOf(8), UInt64.valueOf(9))),
        Arguments.of(
            UInt256.valueOf(
                new BigInteger(
                    "57467522110468688239177851250859789869070302005900722885377252304169193209346")),
            UInt64.valueOf(6226203858325459337L),
            List.of(UInt64.valueOf(44), UInt64.valueOf(45))));
  }

  public static Stream<Arguments> provideNodeIdsAndSlots() {
    return Stream.of(
        Arguments.of(UInt256.valueOf(434726285098L), UInt64.valueOf(0)),
        Arguments.of(UInt256.valueOf(288055627580L), UInt64.valueOf(5)),
        Arguments.of(
            UInt256.valueOf(
                new BigInteger(
                    "57467522110468688239177851250859789869070302005900722885377252304169193209346")),
            UInt64.valueOf(7)));
  }

  public static Stream<Arguments> getCommitteeComputationArguments() {
    return Stream.of(Arguments.of(2_100_000, 1024), Arguments.of(1_049_088, 2047));
  }
}

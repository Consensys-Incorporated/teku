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

package tech.pegasys.teku.spec.logic.common.util;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.BLSTestUtil.NegatedKeyPairs;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SyncCommitteeUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SpecConfigAltair config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
  private final BeaconStateSchemaAltair stateSchema =
      BeaconStateSchemaAltair.required(spec.getGenesisSchemaDefinitions().getBeaconStateSchema());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SszList<Validator> validators =
      dataStructureUtil.randomSszList(
          dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
          config.getSyncCommitteeSize(),
          dataStructureUtil::randomValidator);
  private final List<SszPublicKey> validatorPublicKeys =
      validators.stream().map(Validator::getPubkeyBytes).map(SszPublicKey::new).collect(toList());
  private final int subcommitteeSize = config.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;

  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(UInt64.ZERO);

  private static final NegatedKeyPairs KEYS = BLSTestUtil.negatedKeyPairs(1);

  private final UInt64 contributionSlot = UInt64.valueOf(9);
  private final Bytes32 contributionBlockRoot = dataStructureUtil.randomBytes32();

  @Test
  void createSyncAggregate_shouldCreateEmptyAggregateWhenNoContributions() {
    final SyncAggregate syncAggregate = syncCommitteeUtil.createSyncAggregate(List.of());

    assertThat(syncAggregate).isEqualTo(dataStructureUtil.emptySyncAggregate());
  }

  @Test
  void createSyncAggregate_shouldCreateEmptyAggregateWhenSignatureIsInfinity() {
    final List<SyncCommitteeContribution> contributions =
        List.of(createContribution(0, 1, KEYS.p()), createContribution(1, 2, KEYS.minusP()));

    assertThat(syncCommitteeUtil.createSyncAggregate(contributions))
        .isEqualTo(dataStructureUtil.emptySyncAggregate());
  }

  @Test
  void createSyncAggregate_shouldAggregateWhenSignatureIsNotInfinity() {
    final List<SyncCommitteeContribution> contributions =
        List.of(
            createContribution(0, 1, KEYS.p()),
            createContribution(1, 2, KEYS.minusP()),
            createContribution(2, 3, KEYS.q()));

    final SyncAggregate syncAggregate = syncCommitteeUtil.createSyncAggregate(contributions);

    assertThat(syncAggregate.getSyncCommitteeBits().getAllSetBits().toIntArray())
        .containsExactly(1, subcommitteeSize + 2, 2 * subcommitteeSize + 3);
    // P + (-P) + Q == Q
    assertThat(syncAggregate.getSyncCommitteeSignature().getSignature()).isEqualTo(sign(KEYS.q()));
  }

  private SyncCommitteeContribution createContribution(
      final int subcommitteeIndex, final int participationBit, final BLSKeyPair keyPair) {
    return syncCommitteeUtil.createSyncCommitteeContribution(
        contributionSlot,
        contributionBlockRoot,
        UInt64.valueOf(subcommitteeIndex),
        IntSet.of(participationBit),
        sign(keyPair));
  }

  private BLSSignature sign(final BLSKeyPair keyPair) {
    // signatures are never verified; any message shared by all participants will do
    return BLS.sign(keyPair.getSecretKey(), contributionBlockRoot);
  }

  @Test
  void getSyncSubCommittees_shouldCalculateSubcommitteeAssignments() {
    final BeaconState state = createStateWithCurrentSyncCommittee(validatorPublicKeys);

    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.getCurrentEpoch(state));

    // We explicitly created the committee to have every validator once in order so all validators
    // should be present with a single subcommittee assignment
    IntStream.range(0, validators.size())
        .forEach(
            index -> {
              final UInt64 validatorIndex = UInt64.valueOf(index);
              assertThat(syncSubcommittees).containsKey(validatorIndex);
              final SyncSubcommitteeAssignments assignments = syncSubcommittees.get(validatorIndex);
              final int expectedSubcommittee = index / subcommitteeSize;
              assertThat(assignments.getAssignedSubcommittees())
                  .describedAs("validator %d assigned subcommittees", index)
                  .containsExactly(expectedSubcommittee);
              assertThat(assignments.getParticipationBitIndices(expectedSubcommittee))
                  .containsExactly(index - (expectedSubcommittee * subcommitteeSize));
            });
  }

  @Test
  void getSyncSubCommittees_shouldSupportValidatorsInMultipleSubCommittees() {
    final List<SszPublicKey> singleSubcommittee = validatorPublicKeys.subList(0, subcommitteeSize);
    final List<SszPublicKey> syncCommittee = new ArrayList<>();
    for (int i = 0; i < SYNC_COMMITTEE_SUBNET_COUNT; i++) {
      syncCommittee.addAll(singleSubcommittee);
    }
    final List<UInt64> expectedValidatorIndices =
        IntStream.range(0, subcommitteeSize).mapToObj(UInt64::valueOf).collect(toList());

    final BeaconState state = createStateWithCurrentSyncCommittee(syncCommittee);
    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.getCurrentEpoch(state));

    assertThat(syncSubcommittees).containsOnlyKeys(expectedValidatorIndices);
    expectedValidatorIndices.forEach(
        index -> {
          final SyncSubcommitteeAssignments assignments = syncSubcommittees.get(index);
          assertThat(assignments.getAssignedSubcommittees())
              .containsExactlyInAnyOrderElementsOf(
                  IntStream.range(0, SYNC_COMMITTEE_SUBNET_COUNT).boxed().collect(toList()));
          IntStream.range(0, SYNC_COMMITTEE_SUBNET_COUNT)
              .forEach(
                  subcommitteeIndex ->
                      assertThat(assignments.getParticipationBitIndices(subcommitteeIndex))
                          .containsExactly(index.intValue()));
        });
  }

  @Test
  void getSyncSubCommittees_shouldSupportValidatorsInTheSameSubCommitteeMultipleTimes() {
    final List<SszPublicKey> syncCommittee = new ArrayList<>(validatorPublicKeys);
    final SszPublicKey validator0 = syncCommittee.get(0);
    syncCommittee.set(2, validator0);
    syncCommittee.set(3, validator0);

    final BeaconState state = createStateWithCurrentSyncCommittee(syncCommittee);
    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.getCurrentEpoch(state));

    final SyncSubcommitteeAssignments assignments = syncSubcommittees.get(UInt64.ZERO);
    assertThat(assignments.getAssignedSubcommittees()).containsExactly(0);
    assertThat(assignments.getParticipationBitIndices(0)).containsExactlyInAnyOrder(0, 2, 3);
  }

  @Test
  void getCommitteeIndices() {
    final BeaconState state = createStateWithCurrentSyncCommittee(validatorPublicKeys);
    final IntSet indices =
        syncCommitteeUtil.getCommitteeIndices(
            state, spec.computeEpochAtSlot(state.getSlot()), UInt64.valueOf(12));
    assertThat(indices).containsExactly(12);
  }

  @Test
  void getMinSlotForSyncCommitteeAssignments_shouldReturnZeroInFirstTwoSyncCommitteePeriods() {
    assertThat(syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(UInt64.ZERO))
        .isEqualTo(UInt64.ZERO);
    final UInt64 epochsPerPeriod = UInt64.valueOf(config.getEpochsPerSyncCommitteePeriod());
    assertThat(syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epochsPerPeriod))
        .isEqualTo(UInt64.ZERO);
    assertThat(
            syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(
                epochsPerPeriod.times(2).minus(1)))
        .isEqualTo(UInt64.ZERO);

    // Then has to kick over to the next sync committee period.
    assertThat(syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epochsPerPeriod.times(2)))
        .isEqualTo(epochsPerPeriod);
  }

  @Test
  void getMinEpochForSyncCommitteeAssignments_shouldReturnFirstEpochOfPreviousPeriod() {
    assertMinEpochForSyncCommitteePeriod(3);
    assertMinEpochForSyncCommitteePeriod(5);
  }

  @Test
  void getMinEpochForSyncCommitteeAssignments_shouldNotAllowEpochToBeLessThanForkEpoch() {
    final UInt64 altairForkEpoch = UInt64.ONE;
    final UInt64 altairForkSlot = spec.computeStartSlotAtEpoch(altairForkEpoch);
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(altairForkEpoch);
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(altairForkSlot);
    assertThat(syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(altairForkEpoch))
        .isEqualTo(altairForkEpoch);
    assertThat(syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(UInt64.valueOf(2)))
        .isEqualTo(altairForkEpoch);
  }

  private void assertMinEpochForSyncCommitteePeriod(final int period) {
    final UInt64 epochsPerPeriod = UInt64.valueOf(config.getEpochsPerSyncCommitteePeriod());
    UInt64.range(epochsPerPeriod.times(period), epochsPerPeriod.times(period + 1))
        .forEach(
            epoch ->
                assertThat(syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(epoch))
                    .isEqualTo(epochsPerPeriod.times(period - 1)));
  }

  @Test
  void shouldComputeFirstEpochOfCurrentSyncCommitteePeriod() {
    final UInt64 period1Start = UInt64.ZERO;
    final UInt64 period2Start = period1Start.plus(config.getEpochsPerSyncCommitteePeriod());

    assertThat(syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(period1Start))
        .isEqualTo(period1Start);
    assertThat(
            syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(period1Start.plus(1)))
        .isEqualTo(period1Start);
    assertThat(
            syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(period1Start.plus(7)))
        .isEqualTo(period1Start);

    assertThat(syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(period2Start))
        .isEqualTo(period2Start);
    assertThat(
            syncCommitteeUtil.computeFirstEpochOfCurrentSyncCommitteePeriod(period2Start.plus(1)))
        .isEqualTo(period2Start);
  }

  @Test
  void shouldComputeLastEpochOfCurrentSyncCommitteePeriod() {
    final UInt64 period1Start = UInt64.ZERO;
    final UInt64 period1End = period1Start.plus(config.getEpochsPerSyncCommitteePeriod() - 1);
    final UInt64 period2Start = period1Start.plus(config.getEpochsPerSyncCommitteePeriod());
    final UInt64 period2End = period1End.plus(config.getEpochsPerSyncCommitteePeriod());

    assertThat(syncCommitteeUtil.computeLastEpochOfCurrentSyncCommitteePeriod(period1Start))
        .isEqualTo(period1End);
    assertThat(syncCommitteeUtil.computeLastEpochOfCurrentSyncCommitteePeriod(period1Start.plus(1)))
        .isEqualTo(period1End);
    assertThat(syncCommitteeUtil.computeLastEpochOfCurrentSyncCommitteePeriod(period1Start.plus(7)))
        .isEqualTo(period1End);

    assertThat(syncCommitteeUtil.computeLastEpochOfCurrentSyncCommitteePeriod(period2Start))
        .isEqualTo(period2End);
    assertThat(syncCommitteeUtil.computeLastEpochOfCurrentSyncCommitteePeriod(period2Start.plus(1)))
        .isEqualTo(period2End);
  }

  @Test
  void shouldComputeFirstEpochOfNextSyncCommitteePeriod() {
    final int epochsPerPeriod = config.getEpochsPerSyncCommitteePeriod();
    final UInt64 period1Start = UInt64.ZERO;
    final UInt64 period2Start = period1Start.plus(epochsPerPeriod);
    final UInt64 period3Start = period2Start.plus(epochsPerPeriod);

    assertThat(syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period1Start))
        .isEqualTo(period2Start);
    assertThat(syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period1Start.plus(1)))
        .isEqualTo(period2Start);
    assertThat(syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period1Start.plus(7)))
        .isEqualTo(period2Start);

    assertThat(syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period2Start))
        .isEqualTo(period3Start);
    assertThat(syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(period2Start.plus(1)))
        .isEqualTo(period3Start);
  }

  @Test
  void shouldComputeLastEpochOfNextSyncCommitteePeriod() {
    final int epochsPerPeriod = config.getEpochsPerSyncCommitteePeriod();
    final UInt64 period1Start = UInt64.ZERO;
    final UInt64 period1End = UInt64.ZERO.plus(epochsPerPeriod - 1);
    final UInt64 period2Start = period1Start.plus(epochsPerPeriod);
    final UInt64 period2End = period1End.plus(epochsPerPeriod);
    final UInt64 period3End = period2End.plus(epochsPerPeriod);

    assertThat(syncCommitteeUtil.computeLastEpochOfNextSyncCommitteePeriod(period1Start))
        .isEqualTo(period2End);
    assertThat(syncCommitteeUtil.computeLastEpochOfNextSyncCommitteePeriod(period1Start.plus(1)))
        .isEqualTo(period2End);
    assertThat(syncCommitteeUtil.computeLastEpochOfNextSyncCommitteePeriod(period1Start.plus(7)))
        .isEqualTo(period2End);

    assertThat(syncCommitteeUtil.computeLastEpochOfNextSyncCommitteePeriod(period2Start))
        .isEqualTo(period3End);
    assertThat(syncCommitteeUtil.computeLastEpochOfNextSyncCommitteePeriod(period2Start.plus(1)))
        .isEqualTo(period3End);
  }

  @Test
  void isStateUsableForCommitteeCalculationAtEpoch_shouldBeFalseWhenStateIsNotFromAltairFork() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    assertThat(
            syncCommitteeUtil.isStateUsableForCommitteeCalculationAtEpoch(
                dataStructureUtil.randomBeaconState(UInt64.ZERO), UInt64.ZERO))
        .isFalse();
  }

  @Test
  void isStateUsableForCommitteeCalculationAtEpoch_shouldBeFalseWhenStateFromNextPeriod() {
    final int epochsPerPeriod = config.getEpochsPerSyncCommitteePeriod();
    final UInt64 period1Start = UInt64.ZERO;
    final UInt64 period2Start = spec.computeStartSlotAtEpoch(period1Start.plus(epochsPerPeriod));

    final BeaconStateAltair state =
        dataStructureUtil.stateBuilderAltair().slot(period2Start).build();
    assertThat(syncCommitteeUtil.isStateUsableForCommitteeCalculationAtEpoch(state, UInt64.ZERO))
        .isFalse();
  }

  @Test
  void isStateUsableForCommitteeCalculationAtEpoch_shouldBeFalseWhenStateFromTwoPeriodsAgo() {
    final int epochsPerPeriod = config.getEpochsPerSyncCommitteePeriod();

    final BeaconStateAltair state =
        dataStructureUtil.stateBuilderAltair().slot(UInt64.ZERO).build();
    assertThat(
            syncCommitteeUtil.isStateUsableForCommitteeCalculationAtEpoch(
                state, UInt64.valueOf(epochsPerPeriod).times(2)))
        .isFalse();
  }

  @Test
  void isStateUsableForCommitteeCalculationAtEpoch_shouldBeTrueWhenStateFromRequestedSyncPeriod() {
    final BeaconStateAltair state =
        dataStructureUtil.stateBuilderAltair().slot(UInt64.ZERO).build();
    assertThat(syncCommitteeUtil.isStateUsableForCommitteeCalculationAtEpoch(state, UInt64.ONE))
        .isTrue();
  }

  @Test
  void isStateUsableForCommitteeCalculationAtEpoch_shouldBeTrueWhenStateFromPreviousSyncPeriod() {
    final int epochsPerPeriod = config.getEpochsPerSyncCommitteePeriod();

    final BeaconStateAltair state =
        dataStructureUtil.stateBuilderAltair().slot(UInt64.ZERO).build();
    assertThat(
            syncCommitteeUtil.isStateUsableForCommitteeCalculationAtEpoch(
                state, UInt64.valueOf(epochsPerPeriod).times(1)))
        .isTrue();
  }

  private BeaconState createStateWithCurrentSyncCommittee(
      final List<SszPublicKey> committeePublicKeys) {
    return dataStructureUtil
        .stateBuilderAltair()
        .validators(validators)
        .currentSyncCommittee(
            stateSchema
                .getCurrentSyncCommitteeSchema()
                .create(committeePublicKeys, new SszPublicKey(BLSPublicKey.empty())))
        .build();
  }
}

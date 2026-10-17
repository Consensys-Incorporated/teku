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

package tech.pegasys.teku.statetransition.attestation.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_HEAD_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_SOURCE_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_TARGET_WEIGHT;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_HEAD_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_TARGET_FLAG_INDEX;
import static tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.NOOP;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.statetransition.attestation.PooledAttestation;
import tech.pegasys.teku.statetransition.attestation.PooledAttestationWithData;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;

/**
 * NOTE: Here we assume the reward calculation is correct since we test it as part of the reference
 * test execution via {@link
 * tech.pegasys.teku.reference.common.operations.OperationsTestExecutor#verifyRewardBasedAttestationSorter}
 */
public class RewardBasedAttestationSorterTest {
  private static final int VALIDATOR_COUNT = 5;
  private static final UInt64 STATE_SLOT = UInt64.valueOf(100);
  private static final UInt64 CURRENT_EPOCH = UInt64.valueOf(3);
  private static final UInt64 PREVIOUS_EPOCH = CURRENT_EPOCH.minus(1);
  private static final UInt64 BASE_REWARD_VALUE = UInt64.valueOf(1000);

  private static final SszListSchema<SszByte, ?> PARTICIPATION_LIST_SCHEMA =
      SszListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, VALIDATOR_COUNT);

  final Spec spec = mock(Spec.class);
  final SpecVersion specVersion = mock(SpecVersion.class);
  final BeaconStateAltair state = mock(BeaconStateAltair.class);
  final BeaconStateAccessorsAltair beaconStateAccessors = mock(BeaconStateAccessorsAltair.class);

  RewardBasedAttestationSorter sorter;

  @BeforeEach
  void setUp() {
    final Spec actualSpec = TestSpecFactory.createMinimalAltair();
    when(specVersion.miscHelpers()).thenReturn(actualSpec.getGenesisSpec().miscHelpers());
    when(specVersion.beaconStateAccessors()).thenReturn(beaconStateAccessors);
    when(specVersion.getMilestone()).thenReturn(SpecMilestone.ALTAIR);

    when(state.toVersionAltair()).thenReturn(Optional.of(state));
    when(spec.atSlot(any())).thenReturn(specVersion);
    when(spec.getCurrentEpoch(state)).thenReturn(CURRENT_EPOCH);

    when(state.getSlot()).thenReturn(STATE_SLOT);

    // Default empty participation
    final List<SszByte> initialParticipationBytes =
        IntStream.range(0, VALIDATOR_COUNT).mapToObj(i -> SszByte.of((byte) 0)).toList();
    final SszList<SszByte> initialParticipationSszList =
        PARTICIPATION_LIST_SCHEMA.createFromElements(initialParticipationBytes);

    when(state.getCurrentEpochParticipation()).thenReturn(initialParticipationSszList);
    when(state.getPreviousEpochParticipation()).thenReturn(initialParticipationSszList);

    // Default base reward for any validator
    when(beaconStateAccessors.getCurrentEpoch(state)).thenReturn(CURRENT_EPOCH);
    when(beaconStateAccessors.getPreviousEpoch(state)).thenReturn(PREVIOUS_EPOCH);
    when(beaconStateAccessors.getBaseReward(eq(state), anyInt(), any()))
        .thenReturn(BASE_REWARD_VALUE);

    sorter = RewardBasedAttestationSorter.create(spec, state);
  }

  @Test
  void create_shouldReturnNOOP() {
    when(specVersion.getMilestone()).thenReturn(SpecMilestone.PHASE0);

    assertThat(RewardBasedAttestationSorter.create(spec, state)).isSameAs(NOOP);
  }

  @Test
  void noop_shouldJustApplyLimit() {
    final List<PooledAttestationWithData> attestationList =
        List.of(
            mock(PooledAttestationWithData.class),
            mock(PooledAttestationWithData.class),
            mock(PooledAttestationWithData.class));

    assertThat(
            NOOP.sort(attestationList, 2).stream()
                .map(PooledAttestationWithRewardInfo::getAttestation))
        .containsExactly(attestationList.getFirst(), attestationList.get(1));
  }

  @Test
  void sort_shouldSkipSortingWhenNotNeeded() {
    // att1: validator 0, timely source (reward: 1000 * 14 = 14000)
    final PooledAttestationWithData att1 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(1), CURRENT_EPOCH, List.of(0), List.of(TIMELY_SOURCE_FLAG_INDEX));
    // att2: validator 1, timely target (reward: 1000 * 26 = 26000)
    final PooledAttestationWithData att2 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(2), CURRENT_EPOCH, List.of(1), List.of(TIMELY_TARGET_FLAG_INDEX));

    final List<PooledAttestationWithData> attestations = List.of(att1, att2);
    final List<RewardBasedAttestationSorter.PooledAttestationWithRewardInfo> sortedAttestations =
        sorter.sort(attestations, 2);

    assertThat(sortedAttestations).hasSize(2);
    assertThat(sortedAttestations.get(0).getAttestation()).isEqualTo(att1);
    assertThat(sortedAttestations.get(1).getAttestation()).isEqualTo(att2);
  }

  @Test
  void sort_shouldReturnEmptyList_whenInputIsEmpty() {
    final List<PooledAttestationWithData> attestations = List.of();
    final List<PooledAttestationWithRewardInfo> sortedAttestations = sorter.sort(attestations, 10);
    assertThat(sortedAttestations).isEmpty();
  }

  @Test
  void sort_shouldApplyLimit() {
    final PooledAttestationWithData att1 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(1), CURRENT_EPOCH, List.of(0), List.of(TIMELY_SOURCE_FLAG_INDEX));
    final PooledAttestationWithData att2 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(2), CURRENT_EPOCH, List.of(1), List.of(TIMELY_TARGET_FLAG_INDEX));

    final List<PooledAttestationWithData> attestations = List.of(att1, att2);
    final List<RewardBasedAttestationSorter.PooledAttestationWithRewardInfo> sortedAttestations =
        sorter.sort(attestations, 1);

    assertThat(sortedAttestations).hasSize(1);
  }

  @Test
  void sort_shouldReturnAllAttestationsIfLimitIsHighEnough_sortedByReward() {
    // att1: validator 0, timely source (reward: 1000 * 14 = 14000)
    final PooledAttestationWithData att1 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(1), CURRENT_EPOCH, List.of(0), List.of(TIMELY_SOURCE_FLAG_INDEX));
    // att2: validator 1, timely target (reward: 1000 * 26 = 26000)
    final PooledAttestationWithData att2 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(2), CURRENT_EPOCH, List.of(1), List.of(TIMELY_TARGET_FLAG_INDEX));
    // att3: validator 2, timely target and source (reward: 1000 * (14+26) = 40000)
    final PooledAttestationWithData att3 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(1),
            CURRENT_EPOCH,
            List.of(2),
            List.of(TIMELY_SOURCE_FLAG_INDEX, TIMELY_TARGET_FLAG_INDEX));

    final List<PooledAttestationWithData> attestations = List.of(att1, att2, att3);
    final List<RewardBasedAttestationSorter.PooledAttestationWithRewardInfo> sortedAttestations =
        sorter.sort(attestations, 2);

    assertThat(sortedAttestations).hasSize(2);
    // Expect att3 (higher reward) to be first
    assertThat(sortedAttestations.get(0).getAttestation()).isEqualTo(att3);
    assertThat(sortedAttestations.get(1).getAttestation()).isEqualTo(att2);
    assertThat(sortedAttestations.get(0).getRewardNumerator())
        .isEqualTo(BASE_REWARD_VALUE.times(TIMELY_TARGET_WEIGHT.plus(TIMELY_SOURCE_WEIGHT)));
    assertThat(sortedAttestations.get(1).getRewardNumerator())
        .isEqualTo(BASE_REWARD_VALUE.times(TIMELY_TARGET_WEIGHT));
  }

  @Test
  void sort_shouldRecalculateRewardsAndReorderWhenParticipationChanges_currentEpoch() {
    // Setup initial participation: validator 0 and 1 have no flags set.
    // (This is default from setUp)

    // Att1 (Val 0): Timely Source + Target. Initial Reward: 1000*(14+26) = 40000
    // This will be chosen first. It sets TS and TT flags for Val 0.
    final PooledAttestationWithData att1 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(1),
            CURRENT_EPOCH,
            List.of(0),
            List.of(TIMELY_SOURCE_FLAG_INDEX, TIMELY_TARGET_FLAG_INDEX));

    // Att2 (Val 0): Timely Source.
    // Initial Reward (if val 0 has no flags): 1000*14 = 14000
    // After Att1, val 0 has TS flag, so reward for TS becomes 0. Reward for Att2 -> 0.
    final PooledAttestationWithData att2 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(2), CURRENT_EPOCH, List.of(0), List.of(TIMELY_SOURCE_FLAG_INDEX));

    // Att3 (Val 1): Timely Target. Initial Reward: 1000*26 = 26000
    // Unaffected by Att1's participation changes for Val 0.
    final PooledAttestationWithData att3 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(3), CURRENT_EPOCH, List.of(1), List.of(TIMELY_TARGET_FLAG_INDEX));

    // Att4 (Val 2, Previous Epoch): Timely Head. Reward: 1000*14 = 14000. Should not be recomputed.
    // TH is unrealistic here, but we want to test the case where a previous epoch attestation
    final PooledAttestationWithData att4 =
        mockPooledAttestationWithData(
            STATE_SLOT.minus(35), // Slot in previous epoch
            PREVIOUS_EPOCH,
            List.of(2),
            List.of(TIMELY_HEAD_FLAG_INDEX));

    // Att5 (Val 3): no flags, will be valued 0 and left at the end at first sorting iteration.
    final PooledAttestationWithData att5 =
        mockPooledAttestationWithData(STATE_SLOT.minus(1), CURRENT_EPOCH, List.of(3), List.of());

    final List<PooledAttestationWithData> attestations = List.of(att1, att2, att3, att4, att5);

    /* Initial rewards computed:
    Att1: 40000 (Val 0: TS+TT)
    Att2: 14000 (Val 0: TS)
    Att3: 26000 (Val 1: TT)
    Att4: 14000 (Val 2: TH, Previous Epoch)
    Att5: 0 (Val 3: --, Previous Epoch)
    Initial Queue Order (highest reward first): Att1, Att3, Att2 (or Att4), Att4 (or Att2), Att5
    */

    final List<RewardBasedAttestationSorter.PooledAttestationWithRewardInfo> sortedAttestations =
        sorter.sort(attestations, 4);

    /*
     Step 1: Att1 (40000) is chosen.
     Validator 0 participation updated with TS_FLAG | TT_FLAG.
     Att2 and Att3 (current epoch) are re-evaluated. Att4 (previous epoch) is not.
      - Re-evaluating Att2 (Val 0, wants TS): Val 0 now has TS_FLAG. Reward contribution for TS is 0.
      - Re-evaluating Att3 (Val 1, wants TT): Unchanged as it's for Val 1. Reward = 26000.
     New Queue Order (Att1 already picked): Att3 (26000), Att4 (14000), Att2 (0)

     Step 2: Att3 (26000) is chosen.
     Validator 1 participation updated with TT_FLAG.
     Att2 (current epoch) is re-evaluated. Att4 (previous epoch) is not.
      - Re-evaluating Att2 (Val 0, wants TS): Still 0 reward.
     New Queue Order (Att1, Att3 picked): Att4 (14000), Att2 (0)

     Step 3: Att4 (14000) is chosen.
     Validator 2 (previous epoch) participation updated.
     Att2 (current epoch) is NOT re-evaluated because Att4 is the previous epoch.
     New Queue Order (Att1, Att3, Att4 picked): Att2 (0)

     Step 4: Att2 (0) is chosen.
    */

    // Expected final order: Att1, Att3, Att4, Att2
    assertThat(sortedAttestations).hasSize(4);
    assertThat(
            sortedAttestations.stream()
                .map(RewardBasedAttestationSorter.PooledAttestationWithRewardInfo::getAttestation))
        .containsExactly(att1, att3, att4, att2);

    // Verify rewards in the final list (reflects reward at time of selection)
    assertThat(sortedAttestations.get(0).getRewardNumerator())
        .isEqualTo(
            BASE_REWARD_VALUE.times(TIMELY_SOURCE_WEIGHT.plus(TIMELY_TARGET_WEIGHT))); // Att1
    assertThat(sortedAttestations.get(1).getRewardNumerator())
        .isEqualTo(BASE_REWARD_VALUE.times(TIMELY_TARGET_WEIGHT)); // Att3
    assertThat(sortedAttestations.get(2).getRewardNumerator())
        .isEqualTo(BASE_REWARD_VALUE.times(TIMELY_HEAD_WEIGHT)); // Att4
    assertThat(sortedAttestations.get(3).getRewardNumerator())
        .isEqualTo(UInt64.ZERO); // Att2 (after re-evaluation)

    // Verify base rewards were fetched at the target epoch (cached, so once per validator)
    verify(beaconStateAccessors, times(1)).getBaseReward(state, 0, CURRENT_EPOCH); // For Att1, Att2
    verify(beaconStateAccessors, times(1)).getBaseReward(state, 1, CURRENT_EPOCH); // For Att3
    verify(beaconStateAccessors, times(1)).getBaseReward(state, 2, PREVIOUS_EPOCH); // For Att4
  }

  private PooledAttestationWithData mockPooledAttestationWithData(
      final UInt64 attestationSlot,
      final UInt64 targetEpoch,
      final List<Integer> validatorIndicesInts,
      final List<Integer> participationFlagIndices) {

    final PooledAttestationWithData pooledAttestationWithData =
        mock(PooledAttestationWithData.class);
    final AttestationData attestationData = mock(AttestationData.class);
    final PooledAttestation corePooledAttestation = mock(PooledAttestation.class);

    final List<UInt64> validatorIndices =
        validatorIndicesInts.stream().map(UInt64::valueOf).toList();

    when(pooledAttestationWithData.data()).thenReturn(attestationData);
    when(pooledAttestationWithData.pooledAttestation()).thenReturn(corePooledAttestation);
    when(corePooledAttestation.validatorIndices()).thenReturn(Optional.of(validatorIndices));

    final Checkpoint target = mock(Checkpoint.class);
    when(attestationData.getTarget()).thenReturn(target);
    when(target.getEpoch()).thenReturn(targetEpoch);
    when(attestationData.getSlot()).thenReturn(attestationSlot);

    final UInt64 slotDifference = STATE_SLOT.minusMinZero(attestationSlot);
    when(beaconStateAccessors.getAttestationParticipationFlagIndices(
            state, attestationData, slotDifference))
        .thenReturn(participationFlagIndices);

    return pooledAttestationWithData;
  }
}

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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.Committee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateAccessorsTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final BeaconStateAccessors beaconStateAccessors = genesisSpec.beaconStateAccessors();
  private final SpecConfig specConfig = spec.atSlot(UInt64.ZERO).getConfig();

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState = createBeaconState().updated(state -> state.setSlot(GENESIS_SLOT));
    assertEquals(GENESIS_EPOCH, beaconStateAccessors.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState =
        createBeaconState()
            .updated(state -> state.setSlot(GENESIS_SLOT.plus(specConfig.getSlotsPerEpoch())));
    assertEquals(GENESIS_EPOCH, beaconStateAccessors.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState =
        createBeaconState()
            .updated(state -> state.setSlot(GENESIS_SLOT.plus(2L * specConfig.getSlotsPerEpoch())));
    assertEquals(GENESIS_EPOCH.increment(), beaconStateAccessors.getPreviousEpoch(beaconState));
  }

  public static Stream<Arguments> validateStateForCommitteeQueryParams() {
    return Stream.of(
        Arguments.of(8, 15, false),
        Arguments.of(21, 15, false),
        Arguments.of(2, 15, false),
        Arguments.of(7, 16, true));
  }

  @ParameterizedTest
  @MethodSource("validateStateForCommitteeQueryParams")
  void validateStateForCommitteeQuery(
      final long stateSlot, final long querySlotLong, final boolean shouldThrow) {
    final UInt64 slot = UInt64.valueOf(stateSlot);
    final UInt64 querySlot = UInt64.valueOf(querySlotLong);
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);
    if (shouldThrow) {
      assertThatThrownBy(
              () -> beaconStateAccessors.validateStateForCommitteeQuery(state, querySlot))
          .isInstanceOf(StateTooOldException.class);
    } else {
      assertDoesNotThrow(
          () -> beaconStateAccessors.validateStateForCommitteeQuery(state, querySlot));
    }
  }

  @Test
  void getTotalBalanceAddsAndReturnsEffectiveTotalBalancesCorrectly() {
    // Data Setup
    BeaconState state = createBeaconState();
    Committee committee = new Committee(UInt64.ONE, IntList.of(0, 1, 2));

    // Calculate Expected Results
    UInt64 expectedBalance = UInt64.ZERO;
    for (UInt64 balance : state.getBalances().asListUnboxed()) {
      if (balance.isLessThan(specConfig.getMaxEffectiveBalance())) {
        expectedBalance = expectedBalance.plus(balance);
      } else {
        expectedBalance = expectedBalance.plus(specConfig.getMaxEffectiveBalance());
      }
    }

    UInt64 totalBalance = beaconStateAccessors.getTotalBalance(state, committee.getCommittee());
    assertEquals(expectedBalance, totalBalance);
  }

  @Test
  public void getBeaconCommittee_stateIsTooOld() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = spec.computeStartSlotAtEpoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = spec.computeStartSlotAtEpoch(epoch.plus(2));
    assertThatThrownBy(() -> beaconStateAccessors.getBeaconCommittee(state, outOfRangeSlot, ONE))
        .isInstanceOf(StateTooOldException.class)
        .hasMessageContaining(
            "Committee information must be derived from a state no older than the previous epoch");
  }

  @Test
  public void getBeaconCommittee_stateFromEpochThatIsTooOld() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = spec.computeStartSlotAtEpoch(epoch.plus(ONE)).minus(ONE);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = spec.computeStartSlotAtEpoch(epoch.plus(2));
    assertThatThrownBy(() -> beaconStateAccessors.getBeaconCommittee(state, outOfRangeSlot, ONE))
        .isInstanceOf(StateTooOldException.class)
        .hasMessageContaining(
            "Committee information must be derived from a state no older than the previous epoch");
  }

  @Test
  public void getBeaconCommittee_stateIsJustNewEnough() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = spec.computeStartSlotAtEpoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = spec.computeStartSlotAtEpoch(epoch.plus(2));
    final UInt64 inRangeSlot = outOfRangeSlot.minus(ONE);
    assertDoesNotThrow(() -> beaconStateAccessors.getBeaconCommittee(state, inRangeSlot, ONE));
  }

  @Test
  public void getBeaconCommittee_stateIsNewerThanSlot() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = spec.computeStartSlotAtEpoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 oldSlot = epochSlot.minus(ONE);
    assertDoesNotThrow(() -> beaconStateAccessors.getBeaconCommittee(state, oldSlot, ONE));
  }

  @Test
  public void getBeaconCommitteesSize_stateIsTooOld() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = spec.computeStartSlotAtEpoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = spec.computeStartSlotAtEpoch(epoch.plus(2));
    assertThatThrownBy(() -> beaconStateAccessors.getBeaconCommitteesSize(state, outOfRangeSlot))
        .isInstanceOf(StateTooOldException.class)
        .hasMessageContaining(
            "Committee information must be derived from a state no older than the previous epoch");
  }

  @Test
  public void getBeaconCommitteesSize_hasTheSizeOfTheCommitteeCount() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());

    final Int2IntMap committeesSize =
        beaconStateAccessors.getBeaconCommitteesSize(state, state.getSlot());

    final UInt64 committeeCount = beaconStateAccessors.getCommitteeCountPerSlot(state, epoch);

    assertThat(committeesSize).hasSize(committeeCount.intValue());
  }

  @Test
  void calculateCommitteeFraction_full() {
    final BeaconState state = dataStructureUtil.randomBeaconState(1024);
    final UInt64 totalActiveBalance =
        spec.atSlot(state.getSlot()).beaconStateAccessors().getTotalActiveBalance(state);
    final UInt64 totalActiveBalancePerSlot =
        totalActiveBalance.dividedBy(spec.getGenesisSpec().getSlotsPerEpoch());
    final UInt64 fraction = beaconStateAccessors.calculateCommitteeFraction(state, 100);
    // at its simplest, if we've divided by slots in the function, this should be
    // totalActiveBalance/slots (because fraction is 100%)
    assertThat(fraction).isEqualTo(totalActiveBalancePerSlot);
  }

  @Test
  void calculateCommitteeFraction_minimal() {
    final BeaconState state = dataStructureUtil.randomBeaconState(1024);
    final UInt64 totalActiveBalance =
        spec.atSlot(state.getSlot()).beaconStateAccessors().getTotalActiveBalance(state);
    final UInt64 totalActiveBalancePerSlot =
        totalActiveBalance.dividedBy(spec.getGenesisSpec().getSlotsPerEpoch());
    final UInt64 fraction = beaconStateAccessors.calculateCommitteeFraction(state, 1);
    // should be 1% of balance per slot...
    assertThat(fraction).isEqualTo(totalActiveBalancePerSlot.dividedBy(100));
  }

  @Test
  void calculateCommitteeFraction_shouldExcludeSlashedValidators() {
    final BeaconState state = stateWithOneSlashedOfFourValidators();
    final UInt64 unslashedBalancePerSlot =
        UInt64.THIRTY_TWO_ETH.times(3).dividedBy(specConfig.getSlotsPerEpoch());

    assertThat(beaconStateAccessors.getTotalActiveUnslashedBalance(state))
        .isEqualTo(UInt64.THIRTY_TWO_ETH.times(3));
    assertThat(beaconStateAccessors.calculateCommitteeFraction(state, 100))
        .isEqualTo(unslashedBalancePerSlot);
  }

  @Test
  void getProposerBoostAmount_shouldExcludeSlashedValidators() {
    final BeaconState state = stateWithOneSlashedOfFourValidators();
    final UInt64 unslashedBalancePerSlot =
        UInt64.THIRTY_TWO_ETH.times(3).dividedBy(specConfig.getSlotsPerEpoch());

    assertThat(beaconStateAccessors.getProposerBoostAmount(state))
        .isEqualTo(
            unslashedBalancePerSlot.times(specConfig.getProposerScoreBoost()).dividedBy(100));
  }

  @Test
  void getTotalActiveUnslashedBalance_shouldExcludeValidatorSlashedAfterCacheWasPopulated() {
    final BeaconState state =
        new BeaconStateTestBuilder(dataStructureUtil)
            .activeValidator(UInt64.THIRTY_TWO_ETH)
            .activeValidator(UInt64.THIRTY_TWO_ETH)
            .build();
    assertThat(beaconStateAccessors.getTotalActiveUnslashedBalance(state))
        .isEqualTo(UInt64.THIRTY_TWO_ETH.times(2));

    final BeaconStateMutators beaconStateMutators = genesisSpec.beaconStateMutators();
    final BeaconState slashedState =
        state.updated(
            mutableState ->
                beaconStateMutators.slashValidator(
                    mutableState,
                    1,
                    beaconStateMutators.createValidatorExitContextSupplier(mutableState)));

    assertThat(beaconStateAccessors.getTotalActiveUnslashedBalance(slashedState))
        .isEqualTo(UInt64.THIRTY_TWO_ETH);
    assertThat(beaconStateAccessors.getTotalActiveUnslashedBalance(state))
        .isEqualTo(UInt64.THIRTY_TWO_ETH.times(2));
  }

  private BeaconState stateWithOneSlashedOfFourValidators() {
    return new BeaconStateTestBuilder(dataStructureUtil)
        .activeValidator(UInt64.THIRTY_TWO_ETH)
        .activeValidator(UInt64.THIRTY_TWO_ETH)
        .activeValidator(UInt64.THIRTY_TWO_ETH)
        .validator(
            dataStructureUtil
                .validatorBuilder()
                .slashed(true)
                .effectiveBalance(UInt64.THIRTY_TWO_ETH)
                .activationEpoch(UInt64.ZERO)
                .exitEpoch(SpecConfig.FAR_FUTURE_EPOCH)
                .build())
        .build();
  }

  private BeaconState createBeaconState() {
    return new BeaconStateTestBuilder(dataStructureUtil)
        .forkVersion(specConfig.getGenesisForkVersion())
        .validator(dataStructureUtil.randomValidator())
        .validator(dataStructureUtil.randomValidator())
        .validator(dataStructureUtil.randomValidator())
        .build();
  }
}

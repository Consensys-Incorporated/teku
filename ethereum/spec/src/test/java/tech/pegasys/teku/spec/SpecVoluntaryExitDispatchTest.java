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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Operation validation must be dispatched on the fork of the state being validated against, not on
 * an epoch supplied by the message. A message-supplied epoch is attacker controlled and has no
 * lower bound, so dispatching on it lets a caller select an arbitrarily old milestone's validation
 * rules and signature domain - diverging from what block processing will later apply.
 */
class SpecVoluntaryExitDispatchTest {

  // Capella at epoch 1, Deneb at 2, Electra at 3, Fulu far in the future. A state in Electra
  // therefore has a genuinely different milestone from the one epoch 0 resolves to (Bellatrix).
  private final Spec spec =
      TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
          UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(1000));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldApplyElectraExitRulesToExitNamingAPastEpoch() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(70));
    assertThat(spec.atSlot(slot).getMilestone()).isEqualTo(SpecMilestone.ELECTRA);
    assertThat(spec.atEpoch(UInt64.ZERO).getMilestone()).isEqualTo(SpecMilestone.BELLATRIX);

    final Validator activeValidator =
        dataStructureUtil
            .validatorBuilder()
            .activationEligibilityEpoch(UInt64.ZERO)
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(FAR_FUTURE_EPOCH)
            .withdrawableEpoch(FAR_FUTURE_EPOCH)
            .slashed(false)
            .build();

    final BeaconState state =
        dataStructureUtil
            .stateBuilderElectra(1, 0)
            .slot(slot)
            .validators(activeValidator)
            .pendingPartialWithdrawals(
                List.of(dataStructureUtil.randomPendingPartialWithdrawal(0L)))
            .build();

    final SignedVoluntaryExit exit =
        new SignedVoluntaryExit(
            new VoluntaryExit(UInt64.ZERO, UInt64.ZERO), dataStructureUtil.randomSignature());

    // Dispatching on the message's epoch would select the Phase 0 validator, which knows nothing
    // about EIP-7251 pending withdrawals and would wrongly report this exit as valid.
    assertThat(spec.validateVoluntaryExit(state, exit))
        .contains(ExitInvalidReason.pendingWithdrawalsInQueue());
    assertThat(spec.validateVoluntaryExitForGossip(state, exit))
        .contains(ExitInvalidReason.pendingWithdrawalsInQueue());
  }

  @Test
  void shouldSkipStateRelativeEpochAndExitStatusChecksForGossip() {
    final UInt64 currentEpoch = UInt64.valueOf(70);
    final UInt64 slot = spec.computeStartSlotAtEpoch(currentEpoch);
    final Validator activeValidator =
        dataStructureUtil
            .validatorBuilder()
            .activationEligibilityEpoch(UInt64.ZERO)
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(currentEpoch.plus(1))
            .withdrawableEpoch(FAR_FUTURE_EPOCH)
            .slashed(false)
            .build();
    final BeaconState state =
        dataStructureUtil.stateBuilderElectra(1, 0).slot(slot).validators(activeValidator).build();
    final SignedVoluntaryExit exit =
        new SignedVoluntaryExit(
            new VoluntaryExit(currentEpoch.plus(2), UInt64.ZERO),
            dataStructureUtil.randomSignature());

    assertThat(spec.validateVoluntaryExit(state, exit)).contains(ExitInvalidReason.exitInitiated());
    assertThat(spec.validateVoluntaryExitForGossip(state, exit)).isEmpty();
  }
}

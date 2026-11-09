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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(
    milestone = {
      SpecMilestone.PHASE0,
      SpecMilestone.ALTAIR,
      SpecMilestone.BELLATRIX,
      SpecMilestone.CAPELLA
    })
class EpochProcessorTest {
  /**
   * Updating the effective balance to the same value is wasteful and results in new validator
   * objects being created for every validator, every epoch even though they usually are unchanged.
   */
  @TestTemplate
  void shouldNotUpdateEffectiveBalanceWhenAlreadyAtMaxValue(final SpecContext ctx) {
    final DataStructureUtil dataStructureUtil = ctx.getDataStructureUtil();
    final Spec spec = ctx.getSpec();
    final EpochProcessor processor = spec.atSlot(UInt64.ZERO).getEpochProcessor();
    final UInt64 maxEffectiveBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();
    final Validator validator =
        spy(dataStructureUtil.randomValidator().withEffectiveBalance(maxEffectiveBalance));
    when(validator.getEffectiveBalance()).thenReturn(maxEffectiveBalance);

    final BeaconState state =
        dataStructureUtil
            .stateBuilder(spec.getGenesisSpec().getMilestone(), 1, 0)
            .balances(maxEffectiveBalance.times(2))
            .validators(validator)
            .build();
    final ValidatorStatuses statuses =
        spec.atSlot(state.getSlot()).getValidatorStatusFactory().createValidatorStatuses(state);
    state.updated(
        mutableState ->
            processor.processEffectiveBalanceUpdates(mutableState, statuses.getStatuses()));
    verify(validator, never()).withEffectiveBalance(any());
  }
}

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

package tech.pegasys.teku.spec.logic.versions.capella.withdrawals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers.ExpectedWithdrawals;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WithdrawalsHelpersCapellaTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void getsExpectedWithdrawals() {
    final UInt64 minActivationBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();
    final long excessBalance = 1024000L;
    final BeaconState preState =
        new BeaconStateTestBuilder(dataStructureUtil)
            .activeEth1Validator(minActivationBalance.plus(excessBalance))
            .build();
    final ExpectedWithdrawals expectedWithdrawals =
        spec.atSlot(preState.getSlot())
            .getWithdrawalsHelpers()
            .orElseThrow()
            .getExpectedWithdrawals(preState);

    assertThat(expectedWithdrawals.withdrawals().getFirst().getAmount())
        .isEqualTo(UInt64.valueOf(1024000));
    assertThat(expectedWithdrawals.processedPartialWithdrawalsCount()).isEqualTo(0);
  }
}

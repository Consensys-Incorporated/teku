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

package tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EpochProcessorGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigGloas configGloas = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas =
      BeaconStateAccessorsGloas.required(spec.getGenesisSpec().beaconStateAccessors());
  private final EpochProcessorGloas epochProcessorGloas =
      (EpochProcessorGloas) spec.getGenesisSpec().getEpochProcessor();

  /**
   * EIP-8061: deposits are admitted using the capped activation churn limit, not the uncapped exit
   * churn. Once active balance is high enough that activation/16 exceeds the configured cap, the
   * deposit budget must clamp to the cap while {@code getExitChurnLimit} continues to grow.
   */
  @Test
  public void getPendingDepositsChurnLimit_shouldEqualActivationChurnLimitClampedAtCap() {
    // 65 validators × 32 ETH → activation/16 = 130 ETH which exceeds the 128 ETH cap, so the
    // deposit churn budget should equal the cap. The exit churn at the same active balance is
    // uncapped (130 ETH) — the two diverge here.
    final MutableBeaconStateElectra state = activeStateWithValidators(65).createWritableCopy();

    final UInt64 depositChurn = epochProcessorGloas.getPendingDepositsChurnLimit(state);

    assertThat(depositChurn).isEqualTo(configGloas.getMaxPerEpochActivationChurnLimitGloas());
    assertThat(depositChurn).isEqualTo(beaconStateAccessorsGloas.getActivationChurnLimit(state));
    assertThat(depositChurn).isLessThan(beaconStateAccessorsGloas.getExitChurnLimit(state));
  }

  @Test
  public void getPendingDepositsChurnLimit_shouldFloorAtMinPerEpochChurnLimitForLowActiveBalance() {
    // 8 validators × 32 ETH = 256 ETH. 256/16 = 16 ETH which is below the
    // MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA floor, so the deposit budget must equal the floor.
    final MutableBeaconStateElectra state = activeStateWithValidators(8).createWritableCopy();

    assertThat(epochProcessorGloas.getPendingDepositsChurnLimit(state))
        .isEqualTo(configGloas.getMinPerEpochChurnLimitElectra());
  }

  private BeaconStateElectra activeStateWithValidators(final int validatorCount) {
    final BeaconStateTestBuilder builder = new BeaconStateTestBuilder(dataStructureUtil).slot(0);
    IntStream.range(0, validatorCount)
        .forEach(__ -> builder.activeValidator(UInt64.THIRTY_TWO_ETH));
    return BeaconStateElectra.required(builder.build());
  }
}

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

package tech.pegasys.teku.spec.logic.versions.phase0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateAccessorsPhase0Test {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SpecConfig config = spec.getGenesisSpecConfig();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateAccessors stateAccessors = spec.getGenesisSpec().beaconStateAccessors();

  @Test
  public void getPreviousEpochAttestationCapacity_intermediateValue() {
    final int maxAttestations = config.getMaxAttestations() * config.getSlotsPerEpoch();
    final int existingAttCount = maxAttestations / 2;
    final BeaconState state = dataStructureUtil.stateBuilderPhase0(5, existingAttCount).build();

    assertThat(stateAccessors.getPreviousEpochAttestationCapacity(state))
        .isEqualTo(maxAttestations - existingAttCount);
  }

  @Test
  public void getPreviousEpochAttestationCapacity_zero() {
    final int maxAttestations = config.getMaxAttestations() * config.getSlotsPerEpoch();
    final BeaconState state = dataStructureUtil.stateBuilderPhase0(5, maxAttestations).build();

    assertThat(stateAccessors.getPreviousEpochAttestationCapacity(state)).isEqualTo(0);
  }

  @Test
  public void getPreviousEpochAttestationCapacity_max() {
    final int maxAttestations = config.getMaxAttestations() * config.getSlotsPerEpoch();
    final BeaconState state = dataStructureUtil.stateBuilderPhase0(5, 0).build();

    assertThat(stateAccessors.getPreviousEpochAttestationCapacity(state))
        .isEqualTo(maxAttestations);
  }
}

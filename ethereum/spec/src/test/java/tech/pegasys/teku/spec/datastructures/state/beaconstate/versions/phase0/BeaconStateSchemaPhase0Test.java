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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchemaTest;

public class BeaconStateSchemaPhase0Test
    extends AbstractBeaconStateSchemaTest<BeaconStatePhase0, MutableBeaconStatePhase0> {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalPhase0();
  }

  @Override
  protected BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> getSchema(
      final SpecConfig specConfig) {
    return BeaconStateSchemaPhase0.create(specConfig);
  }

  @Override
  protected BeaconStatePhase0 randomState() {
    return dataStructureUtil.stateBuilderPhase0().build();
  }

  @Test
  public void changeSpecConfigTest_checkPhase0Fields() {
    final Spec standardSpec = TestSpecFactory.createMinimalPhase0();
    final SpecConfig modifiedConfig =
        SpecConfigLoader.loadConfig("minimal", b -> b.maxAttestations(123)).specConfig();

    BeaconStatePhase0 s1 = getSchema(modifiedConfig).createEmpty();
    BeaconStatePhase0 s2 = getSchema(standardSpec.getGenesisSpecConfig()).createEmpty();

    assertThat(s1.getPreviousEpochAttestations().getSchema())
        .isNotEqualTo(s2.getPreviousEpochAttestations().getSchema());
    assertThat(s1.getCurrentEpochAttestations().getSchema())
        .isNotEqualTo(s2.getCurrentEpochAttestations().getSchema());
  }
}

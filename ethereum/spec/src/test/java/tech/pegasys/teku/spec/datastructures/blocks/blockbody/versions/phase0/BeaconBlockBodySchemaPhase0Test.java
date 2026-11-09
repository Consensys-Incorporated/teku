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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;

public class BeaconBlockBodySchemaPhase0Test {

  @Test
  public void create_minimal() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final SpecConfig specConfig = spec.getGenesisSpecConfig();
    final BeaconBlockBodySchemaPhase0 specA =
        BeaconBlockBodySchemaPhase0.create(
            specConfig,
            "BeaconBlockBodyPhase0",
            spec.getGenesisSchemaDefinitions().getSchemaRegistry());
    final BeaconBlockBodySchemaPhase0 specB =
        BeaconBlockBodySchemaPhase0.create(
            specConfig,
            "BeaconBlockBodyPhase0",
            spec.getGenesisSchemaDefinitions().getSchemaRegistry());

    assertThat(specA).isEqualTo(specB);
  }

  @Test
  public void create_mainnet() {
    final Spec spec = TestSpecFactory.createMainnetPhase0();
    final SpecConfig specConfig = spec.getGenesisSpecConfig();
    final BeaconBlockBodySchemaPhase0 specA =
        BeaconBlockBodySchemaPhase0.create(
            specConfig,
            "BeaconBlockBodyPhase0",
            spec.getGenesisSchemaDefinitions().getSchemaRegistry());
    final BeaconBlockBodySchemaPhase0 specB =
        BeaconBlockBodySchemaPhase0.create(
            specConfig,
            "BeaconBlockBodyPhase0",
            spec.getGenesisSchemaDefinitions().getSchemaRegistry());

    assertThat(specA).isEqualTo(specB);
  }
}

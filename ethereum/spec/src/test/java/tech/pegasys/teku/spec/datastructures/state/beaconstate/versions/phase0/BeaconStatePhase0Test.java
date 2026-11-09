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

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateTest;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStatePhase0Test
    extends AbstractBeaconStateTest<BeaconStatePhase0, MutableBeaconStatePhase0> {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalPhase0();
  }

  @Override
  protected BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> getSchema(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return BeaconStateSchemaPhase0.create(specConfig);
  }

  @Override
  protected BeaconStatePhase0 randomState() {
    return dataStructureUtil.stateBuilderPhase0().build();
  }
}

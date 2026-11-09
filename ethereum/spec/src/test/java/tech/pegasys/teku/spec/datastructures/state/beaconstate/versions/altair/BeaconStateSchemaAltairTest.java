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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchemaTest;

public class BeaconStateSchemaAltairTest
    extends AbstractBeaconStateSchemaTest<BeaconStateAltair, MutableBeaconStateAltair> {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalAltair();
  }

  @Test
  void shouldRoundTripViaJson() throws JsonProcessingException {
    final BeaconStateAltair state = randomState();
    final BeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> schema =
        getSchema(dataStructureUtil.getSpec().getGenesisSpecConfig());
    final DeserializableTypeDefinition<BeaconStateAltair> typeDefinition =
        schema.getJsonTypeDefinition();
    final String json = JsonUtil.serialize(state, typeDefinition);
    final BeaconState result = JsonUtil.parse(json, typeDefinition);
    assertThat(result).isEqualTo(state);
  }

  @Override
  protected BeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> getSchema(
      final SpecConfig specConfig) {
    return BeaconStateSchemaAltair.create(specConfig);
  }

  @Override
  protected BeaconStateAltair randomState() {
    return dataStructureUtil.stateBuilderAltair().build();
  }

  @Test
  public void changeSpecConfigTest_checkAltairFields() {
    final Spec standardSpec = TestSpecFactory.createMinimalAltair();
    final SpecConfig modifiedConstants =
        SpecConfigLoader.loadConfig("minimal", b -> b.validatorRegistryLimit(123L)).specConfig();

    BeaconStateAltair s1 = getSchema(modifiedConstants).createEmpty();
    BeaconStateAltair s2 = getSchema(standardSpec.getGenesisSpecConfig()).createEmpty();

    assertThat(s1.getPreviousEpochParticipation().getSchema())
        .isNotEqualTo(s2.getPreviousEpochParticipation().getSchema());
    assertThat(s1.getCurrentEpochParticipation().getSchema())
        .isNotEqualTo(s2.getCurrentEpochParticipation().getSchema());
  }
}

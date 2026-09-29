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

package tech.pegasys.teku.spec.datastructures.lightclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientOptimisticUpdateSchemaTest {
  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void shouldRoundTripViaSsz() {
    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ZERO);
    final LightClientOptimisticUpdate result =
        optimisticUpdateSchema().sszDeserialize(update.sszSerialize());

    assertThat(result).isEqualTo(update);
  }

  private LightClientOptimisticUpdateSchema optimisticUpdateSchema() {
    return SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
        .getLightClientOptimisticUpdateSchema();
  }
}

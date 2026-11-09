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
public class LightClientHeaderSchemaTest {

  private static final int CAPELLA_EXECUTION_BRANCH_LENGTH =
      4; // floorLog2(EXECUTION_PAYLOAD_GINDEX)
  private static final int GLOAS_EXECUTION_BRANCH_LENGTH =
      11; // floorLog2(EXECUTION_BLOCK_HASH_GINDEX_GLOAS)

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void toVersion_shouldBePresentOnlyForMatchingFork() {
    final LightClientHeader header = dataStructureUtil.randomLightClientHeader(UInt64.ZERO);
    final SpecMilestone milestone = milestone();

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      assertThat(header.toVersionGloas()).isPresent();
      assertThat(header.toVersionCapella()).isEmpty();
      assertThat(header.toVersionAltair()).isEmpty();
    } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      assertThat(header.toVersionCapella()).isPresent();
      assertThat(header.toVersionAltair()).isEmpty();
      assertThat(header.toVersionGloas()).isEmpty();
    } else {
      assertThat(header.toVersionAltair()).isPresent();
      assertThat(header.toVersionCapella()).isEmpty();
      assertThat(header.toVersionGloas()).isEmpty();
    }
  }

  @TestTemplate
  public void executionBranch_shouldHaveSpecLength() {
    final LightClientHeaderSchema<?> schema = headerSchema();
    final SpecMilestone milestone = milestone();

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      assertThat(schema.toVersionGloasRequired().getExecutionBranchSchema().getLength())
          .isEqualTo(GLOAS_EXECUTION_BRANCH_LENGTH);
    } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      assertThat(schema.toVersionCapellaRequired().getExecutionBranchSchema().getLength())
          .isEqualTo(CAPELLA_EXECUTION_BRANCH_LENGTH);
    }
  }

  @TestTemplate
  public void shouldRoundTripViaSsz() {
    final LightClientHeader header = dataStructureUtil.randomLightClientHeader(UInt64.ZERO);
    final LightClientHeaderSchema<?> schema = headerSchema();

    final LightClientHeader result = schema.sszDeserialize(header.sszSerialize());

    assertThat(result).isEqualTo(header);
  }

  private SpecMilestone milestone() {
    return spec.getGenesisSpec().getMilestone();
  }

  private LightClientHeaderSchema<?> headerSchema() {
    return SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
        .getLightClientHeaderSchema();
  }
}

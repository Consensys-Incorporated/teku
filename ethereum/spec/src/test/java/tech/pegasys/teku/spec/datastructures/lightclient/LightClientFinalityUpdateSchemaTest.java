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
public class LightClientFinalityUpdateSchemaTest {

  private static final int FINALITY_BRANCH_LENGTH = 6; // floorLog2(FINALIZED_ROOT_GINDEX)
  private static final int ELECTRA_FINALITY_BRANCH_LENGTH =
      7; // floorLog2(FINALIZED_ROOT_GINDEX_ELECTRA)
  private static final int GLOAS_FINALITY_BRANCH_LENGTH =
      9; // floorLog2(FINALIZED_ROOT_GINDEX_GLOAS)

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void finalityBranch_shouldHaveSpecLength() {
    final int expectedLength;
    if (milestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      expectedLength = GLOAS_FINALITY_BRANCH_LENGTH;
    } else if (milestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      expectedLength = ELECTRA_FINALITY_BRANCH_LENGTH;
    } else {
      expectedLength = FINALITY_BRANCH_LENGTH;
    }

    assertThat(finalityUpdateSchema().getFinalizedBranchSchema().getLength())
        .isEqualTo(expectedLength);
  }

  @TestTemplate
  public void shouldRoundTripViaSsz() {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(UInt64.ZERO);

    final LightClientFinalityUpdate result =
        finalityUpdateSchema().sszDeserialize(update.sszSerialize());

    assertThat(result).isEqualTo(update);
  }

  private SpecMilestone milestone() {
    return spec.getGenesisSpec().getMilestone();
  }

  private LightClientFinalityUpdateSchema finalityUpdateSchema() {
    return SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
        .getLightClientFinalityUpdateSchema();
  }
}

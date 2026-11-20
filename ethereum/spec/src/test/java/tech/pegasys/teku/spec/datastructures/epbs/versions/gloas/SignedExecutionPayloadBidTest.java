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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.GLOAS, SpecMilestone.PQ})
class SignedExecutionPayloadBidTest {
  private DataStructureUtil dataStructureUtil;
  private SignedExecutionPayloadBidSchema schema;
  private SpecMilestone milestone;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    schema =
        SchemaDefinitionsGloas.required(specContext.getSpec().getGenesisSchemaDefinitions())
            .getSignedExecutionPayloadBidSchema();
    milestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  void shouldCreateSelfBuiltBidWithTheEmptySignatureOfItsKind() {
    final SignedExecutionPayloadBid bid =
        schema.createSelfBuilt(dataStructureUtil.randomExecutionPayloadBid());

    assertThat(bid.isSelfBuilt()).isTrue();
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.PQ)) {
      assertThat(bid.isXmssSigned()).isTrue();
      assertThat(bid.getXmssSignature().isZero()).isTrue();
    } else {
      assertThat(bid.getSignature()).isEqualTo(BLSSignature.infinity());
    }
  }

  @TestTemplate
  void shouldNotTreatSignedBidAsSelfBuilt() {
    final SignedExecutionPayloadBid bid = dataStructureUtil.randomSignedExecutionPayloadBid();

    assertThat(bid.isSelfBuilt()).isFalse();
  }
}

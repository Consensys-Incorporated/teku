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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
class SignedBeaconBlockTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void shouldRoundTripViaSsz() {
    final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final Bytes ssz = block.sszSerialize();
    final SignedBeaconBlock result =
        schemaDefinitions.getSignedBeaconBlockSchema().sszDeserialize(ssz);
    assertThat(result).isEqualTo(block);
  }

  @TestTemplate
  void shouldBlindAndUnblind() {
    final SignedBeaconBlock original = dataStructureUtil.randomSignedBeaconBlock(10);

    final SignedBeaconBlock blinded = original.blind(spec.getGenesisSchemaDefinitions());
    assertThat(blinded.hashTreeRoot()).isEqualTo(original.hashTreeRoot());

    if (!blinded.getMessage().getBody().isBlinded()) {
      // Didn't blind the block so we must have a spec prior to bellatrix or post-gloas that
      // doesn't have payloads
      final SpecMilestone milestone = spec.getGenesisSpec().getMilestone();
      assertThat(milestone.isLessThan(BELLATRIX) || milestone.isGreaterThanOrEqualTo(GLOAS))
          .isTrue();
    } else {
      // Check the blinded block actually matches the schema by serializing it
      assertThatNoException().isThrownBy(blinded::sszSerialize);
      assertThatNoException()
          .isThrownBy(
              () ->
                  JsonUtil.serialize(
                      blinded,
                      spec.getGenesisSchemaDefinitions()
                          .getSignedBlindedBeaconBlockSchema()
                          .getJsonTypeDefinition()));

      // Otherwise, we should be able to unblind it again
      final SignedBeaconBlock unblinded =
          blinded.unblind(
              spec.getGenesisSchemaDefinitions(),
              original.getMessage().getBody().getOptionalExecutionPayload().orElseThrow());
      assertThat(unblinded.hashTreeRoot()).isEqualTo(original.hashTreeRoot());
      assertThat(unblinded.sszSerialize()).isEqualTo(original.sszSerialize());
      assertThatNoException()
          .isThrownBy(
              () ->
                  JsonUtil.serialize(
                      unblinded,
                      spec.getGenesisSchemaDefinitions()
                          .getSignedBeaconBlockSchema()
                          .getJsonTypeDefinition()));
    }
  }
}

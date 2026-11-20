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
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.GLOAS, SpecMilestone.EIP8483, SpecMilestone.PQ})
class SignedExecutionPayloadEnvelopeTest {
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private SchemaDefinitionsGloas schemaDefinitions;
  private SpecMilestone milestone;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaDefinitions = SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());
    milestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  void shouldBlindAndUnblind() {
    final SignedExecutionPayloadEnvelope originalSignedExecutionPayloadEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(10);

    final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope =
        originalSignedExecutionPayloadEnvelope.blind(spec);
    assertThat(signedBlindedExecutionPayloadEnvelope.hashTreeRoot())
        .isEqualTo(originalSignedExecutionPayloadEnvelope.hashTreeRoot());
    assertThat(signedBlindedExecutionPayloadEnvelope.getMessage().getPayloadHeader().hashTreeRoot())
        .isEqualTo(originalSignedExecutionPayloadEnvelope.getMessage().getPayload().hashTreeRoot());
    // From PQ on, the envelope keeps its leanXMSS signature when blinded
    final boolean xmssSigned = milestone.isGreaterThanOrEqualTo(SpecMilestone.PQ);
    assertThat(originalSignedExecutionPayloadEnvelope.isXmssSigned()).isEqualTo(xmssSigned);
    assertThat(signedBlindedExecutionPayloadEnvelope.isXmssSigned()).isEqualTo(xmssSigned);
    if (xmssSigned) {
      assertThat(signedBlindedExecutionPayloadEnvelope.getXmssSignature())
          .isEqualTo(originalSignedExecutionPayloadEnvelope.getXmssSignature());
    } else {
      assertThat(signedBlindedExecutionPayloadEnvelope.getSignature())
          .isEqualTo(originalSignedExecutionPayloadEnvelope.getSignature());
    }
    assertThat(
            spec.deserializeSignedBlindedExecutionPayloadEnvelope(
                signedBlindedExecutionPayloadEnvelope.sszSerialize()))
        .isEqualTo(signedBlindedExecutionPayloadEnvelope);
    assertThatNoException().isThrownBy(signedBlindedExecutionPayloadEnvelope::sszSerialize);
    assertThatNoException()
        .isThrownBy(
            () ->
                JsonUtil.serialize(
                    signedBlindedExecutionPayloadEnvelope,
                    schemaDefinitions
                        .getSignedBlindedExecutionPayloadEnvelopeSchema()
                        .getJsonTypeDefinition()));

    final SignedExecutionPayloadEnvelope unblindedSignedExecutionPayloadEnvelope =
        signedBlindedExecutionPayloadEnvelope.unblind(
            schemaDefinitions, originalSignedExecutionPayloadEnvelope.getMessage().getPayload());
    assertThat(unblindedSignedExecutionPayloadEnvelope.hashTreeRoot())
        .isEqualTo(originalSignedExecutionPayloadEnvelope.hashTreeRoot());
    assertThat(unblindedSignedExecutionPayloadEnvelope.sszSerialize())
        .isEqualTo(originalSignedExecutionPayloadEnvelope.sszSerialize());
    assertThatNoException()
        .isThrownBy(
            () ->
                JsonUtil.serialize(
                    unblindedSignedExecutionPayloadEnvelope,
                    schemaDefinitions
                        .getSignedExecutionPayloadEnvelopeSchema()
                        .getJsonTypeDefinition()));
  }
}

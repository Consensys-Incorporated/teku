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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/** From PQ, PTC members sign their payload attestation messages with XMSS. */
class PostPayloadAttestationsPqTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalPq());
    setHandler(new PostPayloadAttestations(validatorDataProvider, spec, schemaDefinitionCache));
  }

  @Test
  void shouldReadPqJsonMessagesWithPqHeader() throws Exception {
    final PayloadAttestationMessagePq message = randomMessage();
    final String json =
        JsonUtil.serialize(
            List.of(message),
            DeserializableTypeDefinition.listOf(message.getSchema().getJsonTypeDefinition()));

    final Object requestBody =
        getRequestBodyFromMetadata(handler, Map.of(HEADER_CONSENSUS_VERSION, "pq"), json);

    assertThat(requestBody).isEqualTo(List.of(message));
  }

  @Test
  void shouldReadPqSszMessagesWithPqHeader() throws Exception {
    final PayloadAttestationMessagePq message = randomMessage();
    final int ptcSize =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.PQ).getConfig())
            .getPayloadTimelinessCommitteeSize();
    final byte[] ssz =
        SszListSchema.create(message.getSchema(), ptcSize)
            .createFromElements(List.of(message))
            .sszSerialize()
            .toArrayUnsafe();

    final Object requestBody =
        handler
            .getMetadata()
            .getRequestBody(
                new ByteArrayInputStream(ssz),
                Map.of("Content-Type", ContentTypes.OCTET_STREAM, HEADER_CONSENSUS_VERSION, "pq"));

    assertThat(requestBody).isEqualTo(List.of(message));
  }

  @Test
  void shouldStillReadGloasMessagesWithoutHeader() throws Exception {
    final PayloadAttestationMessage message = dataStructureUtil.randomPayloadAttestationMessage();
    final String json =
        JsonUtil.serialize(
            List.of(message),
            DeserializableTypeDefinition.listOf(message.getSchema().getJsonTypeDefinition()));

    final Object requestBody = getRequestBodyFromMetadata(handler, Map.of(), json);

    assertThat(requestBody).isEqualTo(List.of(message));
  }

  @Test
  void shouldRejectUnknownHeader() throws Exception {
    final PayloadAttestationMessagePq message = randomMessage();
    final String json =
        JsonUtil.serialize(
            List.of(message),
            DeserializableTypeDefinition.listOf(message.getSchema().getJsonTypeDefinition()));
    assertThatThrownBy(
            () -> getRequestBodyFromMetadata(handler, Map.of(HEADER_CONSENSUS_VERSION, "x"), json))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldSubmitPqMessagesToPqPath() throws Exception {
    final List<PayloadAttestationMessagePq> messages = List.of(randomMessage());
    request.setRequestBody(messages);
    when(validatorDataProvider.submitPqPayloadAttestationMessages(messages))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    verify(validatorDataProvider, never()).submitPayloadAttestationMessages(any());
  }

  private PayloadAttestationMessagePq randomMessage() {
    return SchemaDefinitionsPq.required(spec.getGenesisSchemaDefinitions())
        .getPqAttestationSchemas()
        .getPayloadAttestationMessageSchema()
        .create(
            UInt64.valueOf(3),
            dataStructureUtil.randomPayloadAttestationData(),
            dataStructureUtil.randomXmssSignature().getBytes());
  }
}

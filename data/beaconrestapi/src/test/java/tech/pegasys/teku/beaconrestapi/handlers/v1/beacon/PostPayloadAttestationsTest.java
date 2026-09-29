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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.validator.api.SubmitDataError;

class PostPayloadAttestationsTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new PostPayloadAttestations(validatorDataProvider, spec, schemaDefinitionCache));
  }

  @Test
  void shouldSubmitPayloadAttestations() throws Exception {
    final List<PayloadAttestationMessage> messages =
        List.of(dataStructureUtil.randomPayloadAttestationMessage());
    request.setRequestBody(messages);
    when(validatorDataProvider.submitPayloadAttestationMessages(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReportInvalidPayloadAttestations() throws Exception {
    final List<SubmitDataError> errors = List.of(new SubmitDataError(UInt64.ZERO, "Darn"));
    final ErrorListBadRequest response =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    request.setRequestBody(List.of(dataStructureUtil.randomPayloadAttestationMessage()));
    when(validatorDataProvider.submitPayloadAttestationMessages(any()))
        .thenReturn(SafeFuture.completedFuture(errors));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(response);
  }

  @Test
  void shouldReadJsonRequestBody() throws IOException {
    final PayloadAttestationMessage message = dataStructureUtil.randomPayloadAttestationMessage();
    final String json =
        JsonUtil.serialize(
            List.of(message),
            DeserializableTypeDefinition.listOf(message.getSchema().getJsonTypeDefinition()));
    final Object requestBody =
        getRequestBodyFromMetadata(
            handler,
            Map.of(RestApiConstants.HEADER_CONSENSUS_VERSION, SpecMilestone.GLOAS.name()),
            json);
    assertThat(requestBody).isInstanceOf(List.class);
    assertThat(((List<?>) requestBody).get(0)).isInstanceOf(PayloadAttestationMessage.class);
  }

  @Test
  void shouldReadSszRequestBody() throws Exception {
    final PayloadAttestationMessage message = dataStructureUtil.randomPayloadAttestationMessage();
    final PayloadAttestationMessageSchema schema = message.getSchema();
    final int payloadTimelinessCommitteeSize =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig())
            .getPayloadTimelinessCommitteeSize();
    final byte[] sszBytes =
        SszListSchema.create(schema, payloadTimelinessCommitteeSize)
            .createFromElements(List.of(message))
            .sszSerialize()
            .toArrayUnsafe();

    final Object requestBody =
        handler
            .getMetadata()
            .getRequestBody(
                new ByteArrayInputStream(sszBytes), Optional.of(ContentTypes.OCTET_STREAM));

    assertThat(requestBody).isInstanceOf(List.class);
    assertThat(((List<?>) requestBody).getFirst()).isEqualTo(message);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(UInt64.ZERO, "Darn"), new SubmitDataError(UInt64.ONE, "Oops"));
    final ErrorListBadRequest responseData =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);
    final String data = getResponseStringFromMetadata(handler, SC_BAD_REQUEST, responseData);
    assertThat(data).isNotEmpty();
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }
}

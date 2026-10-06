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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BLOB_DATA_INCLUDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;

public class PostExecutionPayloadEnvelopeTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new PostExecutionPayloadEnvelope(validatorDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnOkIfSuccess() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final PublishSignedExecutionPayloadResult successResult =
        PublishSignedExecutionPayloadResult.success(envelope.getBeaconBlockRoot());

    request.setRequestBody(envelope);
    when(validatorDataProvider.publishSignedExecutionPayload(
            any(SignedExecutionPayloadEnvelope.class), any()))
        .thenReturn(SafeFuture.completedFuture(successResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnAcceptedIfPublishedButRejected() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final PublishSignedExecutionPayloadResult failResult =
        PublishSignedExecutionPayloadResult.notImported(
            envelope.getBeaconBlockRoot(), "Invalid payload");

    request.setRequestBody(envelope);
    when(validatorDataProvider.publishSignedExecutionPayload(
            any(SignedExecutionPayloadEnvelope.class), any()))
        .thenReturn(SafeFuture.completedFuture(failResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_ACCEPTED);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnBadRequestIfRejectedAndNotPublished() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final PublishSignedExecutionPayloadResult failResult =
        PublishSignedExecutionPayloadResult.rejected(envelope.getBeaconBlockRoot(), "oopsy");

    request.setRequestBody(envelope);
    when(validatorDataProvider.publishSignedExecutionPayload(
            any(SignedExecutionPayloadEnvelope.class), any()))
        .thenReturn(SafeFuture.completedFuture(failResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBodyAsJson(handler))
        .isEqualTo("{\"code\":400,\"message\":\"oopsy\"}");
  }

  @Test
  void shouldReturnBadRequestIfInvalidBroadcastValidation() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);

    request.setRequestBody(envelope);
    request.setOptionalQueryParameter("broadcast_validation", "invalid_value");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBodyAsJson(handler))
        .isEqualTo(
            "{\"code\":400,\"message\":\"Invalid value for broadcast_validation: Unknown enum value: invalid_value\"}");
  }

  @Test
  void shouldReturnOkIfSuccessWithContents() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final SignedExecutionPayloadEnvelopeContents contents = contentsFor(envelope);
    final PublishSignedExecutionPayloadResult successResult =
        PublishSignedExecutionPayloadResult.success(envelope.getBeaconBlockRoot());

    request.setRequestBody(contents);
    when(validatorDataProvider.publishSignedExecutionPayload(
            any(SignedExecutionPayloadEnvelopeContents.class), any()))
        .thenReturn(SafeFuture.completedFuture(successResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnAcceptedIfPublishedButRejectedWithContents() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final SignedExecutionPayloadEnvelopeContents contents = contentsFor(envelope);
    final PublishSignedExecutionPayloadResult failResult =
        PublishSignedExecutionPayloadResult.notImported(
            envelope.getBeaconBlockRoot(), "Invalid payload");

    request.setRequestBody(contents);
    when(validatorDataProvider.publishSignedExecutionPayload(
            any(SignedExecutionPayloadEnvelopeContents.class), any()))
        .thenReturn(SafeFuture.completedFuture(failResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_ACCEPTED);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldRejectRequestWhenBlobDataIncludedHeaderMissing() {
    assertThatThrownBy(
            () ->
                getRequestBodyFromMetadata(handler, Map.of(HEADER_CONSENSUS_VERSION, "gloas"), ""))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining(HEADER_BLOB_DATA_INCLUDED);
  }

  private SignedExecutionPayloadEnvelopeContents contentsFor(
      final SignedExecutionPayloadEnvelope envelope) {
    return schemaDefinitionCache
        .getSchemaDefinition(SpecMilestone.GLOAS)
        .toVersionGloas()
        .orElseThrow()
        .getSignedExecutionPayloadEnvelopeContentsSchema()
        .create(envelope, List.of(), List.of());
  }
}

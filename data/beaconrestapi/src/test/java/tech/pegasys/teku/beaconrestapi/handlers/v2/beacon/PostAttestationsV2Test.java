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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.validator.api.SubmitDataError;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class PostAttestationsV2Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;

  @BeforeEach
  void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    setSpec(specContext.getSpec());
    specMilestone = specContext.getSpecMilestone();
    setHandler(new PostAttestationsV2(validatorDataProvider, spec, schemaDefinitionCache));
  }

  @TestTemplate
  void shouldBeAbleToSubmitAttestation() throws Exception {
    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());
    request.setRequestBody(attestations);
    request.setRequestHeader(
        HEADER_CONSENSUS_VERSION, specMilestone.name().toLowerCase(Locale.ROOT));
    when(validatorDataProvider.submitAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @TestTemplate
  void shouldReportInvalidAttestations() throws Exception {
    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(UInt64.ZERO, "Bad Attestation"));
    final ErrorListBadRequest response =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    request.setRequestHeader(
        HEADER_CONSENSUS_VERSION, specMilestone.name().toLowerCase(Locale.ROOT));
    request.setRequestBody(attestations);

    when(validatorDataProvider.submitAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(errors));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(response);
  }

  @TestTemplate
  void shouldReadRequestBody() throws IOException {
    final String data = getExpectedResponseAsJson(specMilestone);
    final Object requestBody =
        getRequestBodyFromMetadata(
            handler, Map.of(HEADER_CONSENSUS_VERSION, specMilestone.name()), data);
    assertThat(requestBody).isInstanceOf(List.class);
    assertThat(((List<?>) requestBody).get(0)).isInstanceOf(Attestation.class);
  }

  @TestTemplate
  void metadata_shouldHandle400() throws Exception {
    final List<SubmitDataError> errors =
        List.of(
            new SubmitDataError(UInt64.ZERO, "Darn"), new SubmitDataError(UInt64.ONE, "Incorrect"));
    final ErrorListBadRequest responseData =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    final JsonNode data =
        JsonTestUtil.parseAsJsonNode(
            getResponseStringFromMetadata(handler, SC_BAD_REQUEST, responseData));
    final JsonNode expected =
        JsonTestUtil.parseAsJsonNode(
            Resources.toString(
                Resources.getResource(PostAttestationsV2Test.class, "errorListBadRequest.json"),
                UTF_8));
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }

  @TestTemplate
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @TestTemplate
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }

  private String getExpectedResponseAsJson(final SpecMilestone specMilestone) throws IOException {
    final String fileName =
        String.format("postAttestationRequestBody%s.json", specMilestone.name());
    return Resources.toString(Resources.getResource(PostAttestationsV2Test.class, fileName), UTF_8);
  }

  @TestTemplate
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @TestTemplate
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}

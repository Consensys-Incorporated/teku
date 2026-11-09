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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

class GetPayloadAttestationDataTest extends AbstractMigratedBeaconHandlerTest {

  private PayloadAttestationData payloadAttestationData;
  private ObjectAndMetaData<PayloadAttestationData> payloadAttestationDataResponse;

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    payloadAttestationData = dataStructureUtil.randomPayloadAttestationData(ONE);
    payloadAttestationDataResponse =
        new ObjectAndMetaData<>(payloadAttestationData, SpecMilestone.GLOAS, false, true, false);
    setHandler(new GetPayloadAttestationData(validatorDataProvider, schemaDefinitionCache));
    request.setQueryParameter(RestApiConstants.SLOT, ONE.toString());
  }

  @Test
  void metadata_shouldUsePayloadAttestationDataRoute() {
    assertThat(handler.getMetadata().getPath())
        .isEqualTo("/eth/v1/validator/payload_attestation_data");
  }

  @Test
  void shouldReturnPayloadAttestationData() throws Exception {
    when(validatorDataProvider.createPayloadAttestationData(ONE))
        .thenReturn(SafeFuture.completedFuture(Optional.of(payloadAttestationData)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(payloadAttestationDataResponse);
  }

  @Test
  void shouldUseMilestoneFromRequestedSlot() throws Exception {
    setSpec(TestSpecFactory.createMinimalWithHezeForkEpoch(ONE));
    setHandler(new GetPayloadAttestationData(validatorDataProvider, schemaDefinitionCache));
    final UInt64 hezeSlot = spec.computeStartSlotAtEpoch(ONE);
    request.setQueryParameter(RestApiConstants.SLOT, hezeSlot.toString());
    final PayloadAttestationData hezePayloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData(hezeSlot);
    when(validatorDataProvider.createPayloadAttestationData(hezeSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(hezePayloadAttestationData)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new ObjectAndMetaData<>(
                hezePayloadAttestationData, SpecMilestone.HEZE, false, true, false));
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION)).isEqualTo("heze");
  }

  @Test
  void shouldReturnNoContentWhenNoBlockSeenAtSlot() throws Exception {
    when(validatorDataProvider.createPayloadAttestationData(ONE))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle204() throws JsonProcessingException {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data =
        getResponseStringFromMetadata(handler, SC_OK, payloadAttestationDataResponse);

    assertThat(data)
        .contains(
            "\"version\":\"gloas\"",
            RestApiConstants.BEACON_BLOCK_ROOT,
            RestApiConstants.SLOT,
            "payload_present",
            "blob_data_available");
  }

  @Test
  void metadata_shouldDocumentMilestoneVersion() throws Exception {
    final String schema =
        JsonUtil.serialize(
            handler.getMetadata().getResponseType(SC_OK, JSON)::serializeOpenApiType);

    assertThat(schema)
        .contains(
            "\"enum\":[\"phase0\",\"altair\",\"bellatrix\",\"capella\",\"deneb\",\"electra\",\"fulu\",\"gloas\",\"heze\"]");
  }
}

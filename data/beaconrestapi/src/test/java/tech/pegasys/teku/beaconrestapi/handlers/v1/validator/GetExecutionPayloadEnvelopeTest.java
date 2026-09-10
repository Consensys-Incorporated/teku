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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BEACON_BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

class GetExecutionPayloadEnvelopeTest extends AbstractMigratedBeaconHandlerTest {

  private ExecutionPayloadEnvelope envelope;
  private ObjectAndMetaData<ExecutionPayloadEnvelope> envelopeResponse;

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    envelope = dataStructureUtil.randomExecutionPayloadEnvelope(ONE);
    envelopeResponse = new ObjectAndMetaData<>(envelope, SpecMilestone.GLOAS, false, true, false);
    setHandler(new GetExecutionPayloadEnvelope(validatorDataProvider, schemaDefinitionCache));
    request.setPathParameter(SLOT, ONE.toString());
    request.setPathParameter(BEACON_BLOCK_ROOT, envelope.getBeaconBlockRoot().toHexString());
  }

  @Test
  void metadata_shouldUseExecutionPayloadEnvelopeRoute() {
    assertThat(handler.getMetadata().getPath())
        .isEqualTo("/eth/v1/validator/execution_payload_envelopes/{slot}/{beacon_block_root}");
  }

  @Test
  void shouldReturnExecutionPayloadEnvelope() throws Exception {
    when(validatorDataProvider.createUnsignedExecutionPayload(ONE, envelope.getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(envelope)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(envelopeResponse);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION)).isEqualTo("gloas");
  }

  @Test
  void shouldReturnNotFoundWhenNoEnvelopeRetrieved() throws Exception {
    when(validatorDataProvider.createUnsignedExecutionPayload(ONE, envelope.getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, envelopeResponse);

    assertThat(data).contains("\"version\":\"gloas\"", "\"data\":");
  }
}

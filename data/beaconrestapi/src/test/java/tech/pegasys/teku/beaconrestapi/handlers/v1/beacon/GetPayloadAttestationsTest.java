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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

class GetPayloadAttestationsTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new GetPayloadAttestations(nodeDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnPayloadAttestationsAndMetaData() throws JsonProcessingException {
    final List<PayloadAttestation> responseData =
        List.of(
            dataStructureUtil.randomPayloadAttestation(),
            dataStructureUtil.randomPayloadAttestation());
    final ObjectAndMetaData<List<PayloadAttestation>> responseWithMetaData =
        new ObjectAndMetaData<>(responseData, SpecMilestone.GLOAS, false, false, false);
    when(nodeDataProvider.getPayloadAttestationsAndMetaData(any()))
        .thenReturn(responseWithMetaData);
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseWithMetaData);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final List<PayloadAttestation> responseData =
        List.of(
            dataStructureUtil.randomPayloadAttestation(),
            dataStructureUtil.randomPayloadAttestation());
    final ObjectAndMetaData<List<PayloadAttestation>> responseWithMetaData =
        new ObjectAndMetaData<>(responseData, SpecMilestone.GLOAS, false, false, false);
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseWithMetaData);
    assertThat(data).isNotEmpty();
  }
}

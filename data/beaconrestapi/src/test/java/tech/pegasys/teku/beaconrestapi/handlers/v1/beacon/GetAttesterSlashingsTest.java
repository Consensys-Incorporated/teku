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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;

class GetAttesterSlashingsTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setHandler(new GetAttesterSlashings(nodeDataProvider, schemaDefinitionCache));
  }

  @Test
  public void shouldReturnAttesterSlashingsInformation() throws JsonProcessingException {
    List<AttesterSlashing> attesterSlashings =
        List.of(
            dataStructureUtil.randomAttesterSlashing(), dataStructureUtil.randomAttesterSlashing());
    when(nodeDataProvider.getAttesterSlashings()).thenReturn(attesterSlashings);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(attesterSlashings);
  }

  @Test
  public void shouldReturnEmptyAttesterSlashingsInformation() throws JsonProcessingException {
    List<AttesterSlashing> attesterSlashings = Collections.emptyList();
    when(nodeDataProvider.getAttesterSlashings()).thenReturn(attesterSlashings);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(attesterSlashings);
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
  void metadata_shouldHandle200() throws Exception {
    List<AttesterSlashing> responseData =
        List.of(
            dataStructureUtil.randomAttesterSlashing(), dataStructureUtil.randomAttesterSlashing());

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final JsonNode responseDataAsJsonNode = JsonTestUtil.parseAsJsonNode(data);
    final String expected =
        Resources.toString(
            Resources.getResource(GetAttesterSlashingsTest.class, "getAttesterSlashings.json"),
            UTF_8);
    final JsonNode expectedAsJsonNode = JsonTestUtil.parseAsJsonNode(expected);
    assertThat(responseDataAsJsonNode).isEqualTo(expectedAsJsonNode);
  }
}

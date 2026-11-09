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

package tech.pegasys.teku.beaconrestapi.handlers.v1.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.SpecConfigData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.spec.SpecFactory;

class GetSpecTest extends AbstractMigratedBeaconHandlerTest {
  private final ConfigProvider configProvider = new ConfigProvider(spec);
  private final SpecConfigData response = new SpecConfigData(configProvider.getSpecConfig());

  @BeforeEach
  void setUp() {
    setHandler(new GetSpec(configProvider));
  }

  @Test
  void shouldGetSuccessfulResponse() throws JsonProcessingException {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(response.getConfigMap());
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
  void shouldGetCorrectMainnetConfig() throws Exception {
    final ConfigProvider configProvider = new ConfigProvider(SpecFactory.create("mainnet"));
    setHandler(new GetSpec(configProvider));
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final String json = request.getResponseBodyAsJson(handler);

    assertThat(json).contains("BLOB_SCHEDULE");
    assertThat(json).isNotEmpty();
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode resultNode = mapper.readTree(json).get("data");
    final JsonNode referenceNode =
        mapper.readTree(
            Resources.toString(
                Resources.getResource(GetSpecTest.class, "mainnetConfig.json"), UTF_8));

    assertThat(resultNode)
        .withFailMessage(
            String.format(
                "Expected: %s\nbut was: %s",
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(referenceNode),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultNode)))
        .isEqualTo(referenceNode);
  }
}

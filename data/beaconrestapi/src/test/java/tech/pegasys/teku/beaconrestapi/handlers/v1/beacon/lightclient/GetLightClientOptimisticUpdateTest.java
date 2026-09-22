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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetLightClientOptimisticUpdateTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalAltair());
    setHandler(new GetLightClientOptimisticUpdate(chainDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnLightClientOptimisticUpdate() throws Exception {
    final LightClientOptimisticUpdate lightClientOptimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE);

    when(chainDataProvider.getLatestLightClientOptimisticUpdate())
        .thenReturn(Optional.of(lightClientOptimisticUpdate));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(withMilestone(lightClientOptimisticUpdate, SpecMilestone.ALTAIR));
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
  }

  @Test
  void shouldReturnNotFoundWhenNoOptimisticUpdateAvailable() throws Exception {
    when(chainDataProvider.getLatestLightClientOptimisticUpdate()).thenReturn(Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle200() throws Exception {
    final LightClientOptimisticUpdate lightClientOptimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE);

    final String data =
        getResponseStringFromMetadata(
            handler, SC_OK, withMilestone(lightClientOptimisticUpdate, SpecMilestone.ALTAIR));
    final JsonNode responseDataAsJsonNode = JsonTestUtil.parseAsJsonNode(data);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetLightClientOptimisticUpdateTest.class, "getLightClientOptimisticUpdate.json"),
            StandardCharsets.UTF_8);
    final JsonNode expectedAsJsonNode = JsonTestUtil.parseAsJsonNode(expected);
    assertThat(responseDataAsJsonNode).isEqualTo(expectedAsJsonNode);
  }

  @ParameterizedTest
  @EnumSource(value = SpecMilestone.class, mode = EnumSource.Mode.EXCLUDE, names = "PHASE0")
  void shouldSerializeForEveryMilestoneWithItsOwnSchema(final SpecMilestone milestone)
      throws Exception {
    setSpec(TestSpecFactory.createMinimal(milestone));
    setHandler(new GetLightClientOptimisticUpdate(chainDataProvider, schemaDefinitionCache));

    final ObjectAndMetaData<LightClientOptimisticUpdate> lightClientOptimisticUpdate =
        withMilestone(dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE), milestone);

    final Map<String, Object> response =
        JsonTestUtil.parse(
            getResponseStringFromMetadata(handler, SC_OK, lightClientOptimisticUpdate));

    assertThat(response.get("version")).isEqualTo(milestone.lowerCaseName());
    assertThat(sszConsensusVersionHeader(lightClientOptimisticUpdate))
        .isEqualTo(milestone.lowerCaseName());

    final Map<String, Object> data = JsonTestUtil.getObject(response, "data");
    assertThat(JsonTestUtil.getObject(data, "attested_header").keySet())
        .containsExactlyInAnyOrderElementsOf(expectedHeaderFields(milestone));
    assertThat(data).containsKeys("sync_aggregate", "signature_slot");
  }

  private static Set<String> expectedHeaderFields(final SpecMilestone milestone) {
    return switch (milestone) {
      case ALTAIR, BELLATRIX -> Set.of("beacon");
      case GLOAS, HEZE -> Set.of("beacon", "execution_block_hash", "execution_branch");
      default -> Set.of("beacon", "execution", "execution_branch");
    };
  }

  @Test
  void metadata_shouldHandleSsz200() throws Exception {
    final LightClientOptimisticUpdate lightClientOptimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE);

    assertThat(
            getResponseSszFromMetadata(
                handler, SC_OK, withMilestone(lightClientOptimisticUpdate, SpecMilestone.ALTAIR)))
        .isEqualTo(lightClientOptimisticUpdate.sszSerialize().toArray());
  }

  private static ObjectAndMetaData<LightClientOptimisticUpdate> withMilestone(
      final LightClientOptimisticUpdate lightClientOptimisticUpdate,
      final SpecMilestone milestone) {
    return new ObjectAndMetaData<>(lightClientOptimisticUpdate, milestone, false, false, false);
  }

  @SuppressWarnings("unchecked")
  private String sszConsensusVersionHeader(
      final ObjectAndMetaData<LightClientOptimisticUpdate> lightClientOptimisticUpdate) {
    final ResponseContentTypeDefinition<ObjectAndMetaData<LightClientOptimisticUpdate>> sszType =
        (ResponseContentTypeDefinition<ObjectAndMetaData<LightClientOptimisticUpdate>>)
            handler.getMetadata().getResponseType(SC_OK, ContentTypes.OCTET_STREAM);
    return sszType.getAdditionalHeaders(lightClientOptimisticUpdate).get(HEADER_CONSENSUS_VERSION);
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
}

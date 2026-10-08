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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetLightClientBootstrapTest extends AbstractMigratedBeaconHandlerTest {

  private final Bytes32 blockRoot =
      Bytes32.fromHexString("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2");

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalAltair());
    setHandler(new GetLightClientBootstrap(chainDataProvider, schemaDefinitionCache));
    request.setPathParameter("block_root", blockRoot.toHexString());
  }

  @Test
  void shouldReturnLightClientBootstrap() throws Exception {
    final ObjectAndMetaData<LightClientBootstrap> lightClientBootstrap =
        withMilestone(
            dataStructureUtil.randomLightClientBoostrap(UInt64.ONE), SpecMilestone.ALTAIR);

    when(chainDataProvider.getLightClientBoostrap(eq(blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(lightClientBootstrap)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(lightClientBootstrap);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
  }

  @Test
  void shouldReturnNotFoundWhenNoBootstrapAvailable() throws Exception {
    when(chainDataProvider.getLightClientBoostrap(eq(blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle200() throws Exception {
    final ObjectAndMetaData<LightClientBootstrap> lightClientBootstrap =
        withMilestone(
            dataStructureUtil.randomLightClientBoostrap(UInt64.ONE), SpecMilestone.ALTAIR);

    final String data = getResponseStringFromMetadata(handler, SC_OK, lightClientBootstrap);
    final JsonNode responseDataAsJsonNode = JsonTestUtil.parseAsJsonNode(data);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetLightClientBootstrapTest.class, "getLightClientBootstrap.json"),
            StandardCharsets.UTF_8);
    final JsonNode expectedAsJsonNode = JsonTestUtil.parseAsJsonNode(expected);
    assertThat(responseDataAsJsonNode).isEqualTo(expectedAsJsonNode);
  }

  @ParameterizedTest
  @EnumSource(value = SpecMilestone.class, mode = EnumSource.Mode.EXCLUDE, names = "PHASE0")
  void shouldSerializeForEveryMilestoneWithItsOwnSchema(final SpecMilestone milestone)
      throws Exception {
    setSpec(TestSpecFactory.createMinimal(milestone));
    setHandler(new GetLightClientBootstrap(chainDataProvider, schemaDefinitionCache));

    final ObjectAndMetaData<LightClientBootstrap> lightClientBootstrap =
        withMilestone(dataStructureUtil.randomLightClientBoostrap(UInt64.ONE), milestone);

    final Map<String, Object> response =
        JsonTestUtil.parse(getResponseStringFromMetadata(handler, SC_OK, lightClientBootstrap));

    assertThat(response.get("version")).isEqualTo(milestone.lowerCaseName());
    assertThat(sszConsensusVersionHeader(lightClientBootstrap))
        .isEqualTo(milestone.lowerCaseName());

    final Map<String, Object> data = JsonTestUtil.getObject(response, "data");
    assertThat(JsonTestUtil.getObject(data, "header").keySet())
        .containsExactlyInAnyOrderElementsOf(expectedHeaderFields(milestone));

    final List<Object> syncCommitteeBranch =
        JsonTestUtil.getList(data, "current_sync_committee_branch");
    assertThat(syncCommitteeBranch).hasSize(expectedSyncCommitteeBranchLength(milestone));
  }

  private static Set<String> expectedHeaderFields(final SpecMilestone milestone) {
    return switch (milestone) {
      case ALTAIR, BELLATRIX -> Set.of("beacon");
      case GLOAS, HEZE -> Set.of("beacon", "execution_block_hash", "execution_branch");
      default -> Set.of("beacon", "execution", "execution_branch");
    };
  }

  private static int expectedSyncCommitteeBranchLength(final SpecMilestone milestone) {
    return switch (milestone) {
      case ELECTRA, FULU -> 6;
      case GLOAS, HEZE -> 11;
      default -> 5;
    };
  }

  @Test
  void metadata_shouldHandleSsz200() throws Exception {
    final LightClientBootstrap lightClientBootstrap =
        dataStructureUtil.randomLightClientBoostrap(UInt64.ONE);

    assertThat(
            getResponseSszFromMetadata(
                handler, SC_OK, withMilestone(lightClientBootstrap, SpecMilestone.ALTAIR)))
        .isEqualTo(lightClientBootstrap.sszSerialize().toArray());
  }

  private static ObjectAndMetaData<LightClientBootstrap> withMilestone(
      final LightClientBootstrap lightClientBootstrap, final SpecMilestone milestone) {
    return new ObjectAndMetaData<>(lightClientBootstrap, milestone, false, false, false);
  }

  @SuppressWarnings("unchecked")
  private String sszConsensusVersionHeader(
      final ObjectAndMetaData<LightClientBootstrap> lightClientBootstrap) {
    final ResponseContentTypeDefinition<ObjectAndMetaData<LightClientBootstrap>> sszType =
        (ResponseContentTypeDefinition<ObjectAndMetaData<LightClientBootstrap>>)
            handler.getMetadata().getResponseType(SC_OK, ContentTypes.OCTET_STREAM);
    return sszType.getAdditionalHeaders(lightClientBootstrap).get(HEADER_CONSENSUS_VERSION);
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
}

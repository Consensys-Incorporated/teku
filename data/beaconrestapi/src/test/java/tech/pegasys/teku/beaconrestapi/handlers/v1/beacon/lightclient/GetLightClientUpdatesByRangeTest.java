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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.metadata.LightClientUpdateWithContext;

public class GetLightClientUpdatesByRangeTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalAltair());
    setHandler(new GetLightClientUpdatesByRange(chainDataProvider, schemaDefinitionCache));
    request.setQueryParameter("start_period", "1");
    request.setQueryParameter("count", "1");
  }

  @Test
  void shouldReturnBestUpdates() throws Exception {
    final List<LightClientUpdateWithContext> updates =
        List.of(dataStructureUtil.randomLightClientUpdateWithContext(UInt64.ONE));
    when(chainDataProvider.getBestLightClientUpdates(UInt64.ONE, 1)).thenReturn(updates);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(updates);
  }

  @Test
  void shouldCapCountAtMaxRequestLightClientUpdates() throws Exception {
    setHandler(new GetLightClientUpdatesByRange(chainDataProvider, schemaDefinitionCache));
    request.setQueryParameter("start_period", "1");
    request.setQueryParameter("count", "1000");
    when(chainDataProvider.getBestLightClientUpdates(UInt64.ONE, MAX_REQUEST_LIGHT_CLIENT_UPDATES))
        .thenReturn(List.of());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(List.of());
  }

  @Test
  void shouldReturnEmptyListWhenGenesisDataIsUnavailableAndRangeIsEmpty() throws Exception {
    initialise(SpecMilestone.ALTAIR);
    setHandler(new GetLightClientUpdatesByRange(chainDataProvider, schemaDefinitionCache));
    request.setQueryParameter("start_period", "1");
    request.setQueryParameter("count", "1");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(List.of());
  }

  @Test
  void metadata_shouldHandleJson200() throws Exception {
    final LightClientUpdate lightClientUpdate =
        dataStructureUtil.randomLightClientUpdate(UInt64.ONE);
    final List<LightClientUpdateWithContext> response =
        List.of(
            new LightClientUpdateWithContext(
                dataStructureUtil.randomBytes4(), SpecMilestone.ALTAIR, lightClientUpdate));

    final String data = getResponseStringFromMetadata(handler, SC_OK, response);
    final JsonNode responseDataAsJsonNode = JsonTestUtil.parseAsJsonNode(data);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetLightClientUpdatesByRangeTest.class, "getLightClientUpdatesByRange.json"),
            StandardCharsets.UTF_8);
    final JsonNode expectedAsJsonNode = JsonTestUtil.parseAsJsonNode(expected);
    assertThat(responseDataAsJsonNode).isEqualTo(expectedAsJsonNode);
  }

  @ParameterizedTest
  @EnumSource(value = SpecMilestone.class, mode = EnumSource.Mode.EXCLUDE, names = "PHASE0")
  void shouldSerializeForEveryMilestoneWithItsOwnSchema(final SpecMilestone milestone)
      throws Exception {
    setSpec(TestSpecFactory.createMinimal(milestone));
    setHandler(new GetLightClientUpdatesByRange(chainDataProvider, schemaDefinitionCache));

    final LightClientUpdateWithContext lightClientUpdate =
        dataStructureUtil.randomLightClientUpdateWithContext(UInt64.ONE);

    final JsonNode response =
        JsonTestUtil.parseAsJsonNode(
            getResponseStringFromMetadata(handler, SC_OK, List.of(lightClientUpdate)));

    assertThat(response).hasSize(1);
    assertThat(response.get(0).get("version").asText()).isEqualTo(milestone.lowerCaseName());

    final JsonNode data = response.get(0).get("data");
    assertThat(ImmutableSet.copyOf(data.get("attested_header").fieldNames()))
        .containsExactlyInAnyOrderElementsOf(expectedHeaderFields(milestone));
    assertThat(ImmutableSet.copyOf(data.get("finalized_header").fieldNames()))
        .containsExactlyInAnyOrderElementsOf(expectedHeaderFields(milestone));

    assertThat(data.get("next_sync_committee_branch"))
        .hasSize(expectedSyncCommitteeBranchLength(milestone));
    assertThat(data.get("finality_branch")).hasSize(expectedFinalityBranchLength(milestone));
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

  private static int expectedFinalityBranchLength(final SpecMilestone milestone) {
    return switch (milestone) {
      case ELECTRA, FULU -> 7;
      case GLOAS, HEZE -> 9;
      default -> 6;
    };
  }

  @Test
  void metadata_shouldHandleSsz200() throws Exception {
    final LightClientUpdateWithContext first =
        dataStructureUtil.randomLightClientUpdateWithContext(UInt64.ONE);
    final LightClientUpdateWithContext second =
        dataStructureUtil.randomLightClientUpdateWithContext(UInt64.ONE);

    final byte[] actual = getResponseSszFromMetadata(handler, SC_OK, List.of(first, second));

    assertThat(Bytes.wrap(actual))
        .isEqualTo(Bytes.wrap(responseChunk(first), responseChunk(second)));
  }

  private static Bytes responseChunk(final LightClientUpdateWithContext updateWithContext) {
    final Bytes payload = updateWithContext.update().sszSerialize();
    return Bytes.wrap(
        Bytes.ofUnsignedLong(Bytes4.SIZE + payload.size(), ByteOrder.LITTLE_ENDIAN),
        updateWithContext.context().getWrappedBytes(),
        payload);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
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

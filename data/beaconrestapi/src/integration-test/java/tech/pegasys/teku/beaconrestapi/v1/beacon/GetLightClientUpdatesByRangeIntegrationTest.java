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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient.GetLightClientUpdatesByRange;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class GetLightClientUpdatesByRangeIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    startRestAPIAtGenesis(specContext.getSpecMilestone());
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @TestTemplate
  void shouldReturnEmptyListWhenNoUpdatesAvailable() throws Exception {
    final Response response = get(UInt64.ZERO, 1);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(JsonTestUtil.parseAsJsonNode(response.body().string())).isEmpty();
  }

  @TestTemplate
  void shouldReturnBestUpdates(final TestSpecInvocationContextProvider.SpecContext specContext)
      throws Exception {
    final LightClientUpdate expected =
        dataStructureUtil.createRandomLightClientUpdateBuilder(UInt64.ONE).build();
    lightClientUpdateStore.addUpdate(
        expected, dataStructureUtil.randomBytes32(), (slot, blockRoot) -> true);

    final Response response = get(UInt64.ZERO, 1);

    assertThat(response.code()).isEqualTo(SC_OK);

    final String body = response.body().string();
    final JsonNode json = JsonTestUtil.parseAsJsonNode(body);
    assertThat(json).hasSize(1);
    assertThat(json.get(0).get("version").asText())
        .isEqualTo(specContext.getSpecMilestone().lowerCaseName());

    final LightClientUpdateSchema schema =
        SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
            .getLightClientUpdateSchema();
    final List<LightClientUpdate> parsed =
        JsonUtil.parse(
            body, DeserializableTypeDefinition.listOf(SharedApiTypes.withDataWrapper(schema)));

    assertThat(parsed).containsExactly(expected);
  }

  @TestTemplate
  void shouldReturnBadRequestWhenParametersMissing() throws IOException {
    final Response response = getResponse(GetLightClientUpdatesByRange.ROUTE);
    assertBadRequest(response);
  }

  private Response get(final UInt64 startPeriod, final int count) throws IOException {
    return getResponse(
        GetLightClientUpdatesByRange.ROUTE + "?start_period=" + startPeriod + "&count=" + count);
  }
}

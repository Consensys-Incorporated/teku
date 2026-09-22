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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient.GetLightClientOptimisticUpdate;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdateSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class GetLightClientOptimisticUpdateIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    startRestAPIAtGenesis(specContext.getSpecMilestone());
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @TestTemplate
  void shouldReturnNotFoundWhenNoOptimisticUpdateAvailable() throws IOException {
    final Response response = getResponse(GetLightClientOptimisticUpdate.ROUTE);
    assertNotFound(response);
  }

  @TestTemplate
  void shouldReturnLatestOptimisticUpdate(
      final TestSpecInvocationContextProvider.SpecContext specContext) throws IOException {
    final LightClientOptimisticUpdate expected =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE);
    lightClientUpdateStore.addOptimisticUpdate(
        expected, dataStructureUtil.randomBytes32(), (slot, blockRoot) -> true);

    final Response response = getResponse(GetLightClientOptimisticUpdate.ROUTE);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specContext.getSpecMilestone().lowerCaseName());

    final LightClientOptimisticUpdateSchema schema =
        SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
            .getLightClientOptimisticUpdateSchema();
    final LightClientOptimisticUpdate parsed =
        JsonUtil.parse(response.body().string(), SharedApiTypes.withDataWrapper(schema));

    assertThat(parsed).isEqualTo(expected);
  }
}

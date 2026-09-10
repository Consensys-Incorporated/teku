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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient.GetLightClientBootstrap;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

@TestSpecContext(milestone = {SpecMilestone.ALTAIR, SpecMilestone.ELECTRA})
public class GetLightClientBootstrapIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  final Bytes32 blockRoot = Bytes32.random();

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    startRestAPIAtGenesis(specContext.getSpecMilestone());
  }

  @TestTemplate
  void shouldReturnResultIfCreatedSuccessfully(
      final TestSpecInvocationContextProvider.SpecContext specContext) throws IOException {
    final BeaconState state =
        safeJoin(dataProvider.getChainDataProvider().getBeaconStateAtHead())
            .orElseThrow()
            .getData();
    final Bytes32 headBlockRoot = BeaconBlockHeader.fromState(state).getRoot();
    final LightClientBootstrap expectedBootstrap =
        safeJoin(dataProvider.getChainDataProvider().getLightClientBoostrap(headBlockRoot))
            .orElseThrow()
            .getData();

    final Response response = get(headBlockRoot);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specContext.getSpecMilestone().lowerCaseName());

    final LightClientBootstrapSchema lightClientBootstrapSchema =
        SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
            .getLightClientBootstrapSchema();
    final LightClientBootstrap parsedBootstrapResponse =
        JsonUtil.parse(
            response.body().string(), SharedApiTypes.withDataWrapper(lightClientBootstrapSchema));

    assertThat(parsedBootstrapResponse).isEqualTo(expectedBootstrap);
  }

  @TestTemplate
  void shouldReturnBadRequestIfInvalidPath() throws IOException {
    final Response response =
        getResponse(GetLightClientBootstrap.ROUTE.replace("{block_root}", "foo"));
    assertBadRequest(response);
  }

  @TestTemplate
  void shouldReturnNotFoundIfNoBlock() throws IOException {
    final Response response = get(blockRoot);
    assertNotFound(response);
  }

  public Response get(final Bytes32 blockRoot) throws IOException {
    return getResponse(
        GetLightClientBootstrap.ROUTE.replace("{block_root}", blockRoot.toHexString()));
  }
}

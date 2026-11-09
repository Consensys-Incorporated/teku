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

package tech.pegasys.teku.beaconrestapi.v2.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetState;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.storage.server.StateStorageMode;

public class GetStateIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  public void shouldGetPhase0StateAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = get("head", ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final ResponseData<? extends BeaconState> stateResponse =
        JsonUtil.parse(
            response.body().string(),
            typeDefinition(
                spec.forMilestone(SpecMilestone.PHASE0)
                    .getSchemaDefinitions()
                    .getBeaconStateSchema()));
    assertThat(stateResponse).isNotNull();
    assertThat(stateResponse.getVersion()).isEqualTo(SpecMilestone.PHASE0);
    assertThat(stateResponse.getData()).isInstanceOf(BeaconStatePhase0.class);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.PHASE0.lowerCaseName());
  }

  @Test
  public void shouldGetAltairStateAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final Response response = get("head", ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final ResponseData<? extends BeaconState> stateResponse =
        JsonUtil.parse(
            response.body().string(),
            typeDefinition(
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBeaconStateSchema()));
    assertThat(stateResponse).isNotNull();
    assertThat(stateResponse.getVersion()).isEqualTo(SpecMilestone.ALTAIR);
    assertThat(stateResponse.getData()).isInstanceOf(BeaconStateAltair.class);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
  }

  @Test
  public void shouldGetAltairStateAsSsz() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
  }

  @Test
  public void shouldGetFinalizedState()
      throws IOException, ExecutionException, InterruptedException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    final BeaconState finalizedState =
        combinedChainDataClient.getBestFinalizedState().get().orElseThrow();

    final Response response = get("finalized", ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
    final ResponseData<? extends BeaconState> stateResponse =
        JsonUtil.parse(
            response.body().string(),
            typeDefinition(
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBeaconStateSchema()));

    assertThat(stateResponse.getData().hashTreeRoot()).isEqualTo(finalizedState.hashTreeRoot());
  }

  @Test
  public void shouldGetFinalizedStateByRootInPruneMode()
      throws IOException, ExecutionException, InterruptedException {
    startRestAPIAtGenesis(StateStorageMode.PRUNE, SpecMilestone.ALTAIR);

    final BeaconState finalizedState =
        combinedChainDataClient.getBestFinalizedState().get().orElseThrow();

    final Response response = get(finalizedState.hashTreeRoot().toHexString(), ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ALTAIR.lowerCaseName());
    final ResponseData<? extends BeaconState> stateResponse =
        JsonUtil.parse(
            response.body().string(),
            typeDefinition(
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBeaconStateSchema()));

    assertThat(stateResponse.getData().hashTreeRoot()).isEqualTo(finalizedState.hashTreeRoot());
  }

  public Response get(final String stateIdIdString, final String contentType) throws IOException {
    return getResponse(GetState.ROUTE.replace("{state_id}", stateIdIdString), contentType);
  }

  public Response get(final String stateIdIdString) throws IOException {
    return getResponse(GetState.ROUTE.replace("{state_id}", stateIdIdString));
  }

  private <T extends SszData> DeserializableTypeDefinition<ResponseData<T>> typeDefinition(
      final SszSchema<T> dataSchema) {
    return DeserializableTypeDefinition.<ResponseData<T>, ResponseData<T>>object()
        .initializer(ResponseData::new)
        .finisher(Function.identity())
        .withField(
            "version",
            DeserializableTypeDefinition.enumOf(SpecMilestone.class),
            ResponseData::getVersion,
            ResponseData::setVersion)
        .withField(
            EXECUTION_OPTIMISTIC,
            CoreTypes.BOOLEAN_TYPE,
            ResponseData::isExecutionOptimistic,
            ResponseData::setExecutionOptimistic)
        .withField(
            "data",
            dataSchema.getJsonTypeDefinition(),
            ResponseData::getData,
            ResponseData::setData)
        .build();
  }

  private static class ResponseData<T> {
    private SpecMilestone version;
    private Boolean executionOptimistic;
    private T data;

    public SpecMilestone getVersion() {
      return version;
    }

    public void setVersion(final SpecMilestone version) {
      this.version = version;
    }

    public T getData() {
      return data;
    }

    public void setData(final T data) {
      this.data = data;
    }

    public Boolean isExecutionOptimistic() {
      return executionOptimistic;
    }

    public void setExecutionOptimistic(final Boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
    }
  }
}

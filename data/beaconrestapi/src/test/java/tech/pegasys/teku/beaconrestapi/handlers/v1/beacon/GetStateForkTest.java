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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateForkTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private StateAndMetaData responseData;

  @BeforeEach
  void setup() throws ExecutionException, InterruptedException {
    initialise(SpecMilestone.PHASE0);
    genesis();
    setHandler(new GetStateFork(chainDataProvider));
    request.setPathParameter("state_id", "head");

    final BeaconState beaconState = recentChainData.getBestState().orElseThrow().get();
    responseData =
        new StateAndMetaData(beaconState, spec.getGenesisSpec().getMilestone(), false, true, true);
  }

  @Test
  public void shouldReturnForkInfo() throws Exception {
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isInstanceOf(StateAndMetaData.class);
    assertThat(request.getResponseBody()).isEqualTo(responseData);
  }

  @Test
  public void shouldReturnNotFound() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", dataStructureUtil.randomBytes32().toHexString())
            .build();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Not found"));
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
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final StateAndMetaData stateAndMetaData =
        new StateAndMetaData(
            dataStructureUtil.randomBeaconState(),
            spec.getGenesisSpec().getMilestone(),
            false,
            true,
            false);
    final String data = getResponseStringFromMetadata(handler, SC_OK, stateAndMetaData);

    assertThat(data)
        .isEqualTo(
            "{\"execution_optimistic\":false,\"finalized\":false,\"data\":{\"previous_version\":\"0x103ac940\",\"current_version\":\"0x6fdfab40\",\"epoch\":\"4658411424342975020\"}}");
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}

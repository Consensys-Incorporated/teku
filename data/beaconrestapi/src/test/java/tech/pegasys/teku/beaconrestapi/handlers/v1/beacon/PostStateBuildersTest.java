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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.BuilderStatus;
import tech.pegasys.teku.api.migrated.StateBuilderData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.ethereum.json.types.beacon.StateBuilderRequestBodyType;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

public class PostStateBuildersTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new PostStateBuilders(chainDataProvider));
  }

  @Test
  void shouldGetBuildersFromStateWithIds() throws Exception {
    final StateBuilderRequestBodyType requestBody = new StateBuilderRequestBodyType(List.of("0"));
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();
    request.setRequestBody(requestBody);
    final ObjectAndMetaData<List<StateBuilderData>> expectedResponse =
        new ObjectAndMetaData<>(getBuildersList(), SpecMilestone.GLOAS, false, true, false);
    when(chainDataProvider.getStateBuilders("head", List.of("0"), List.of()))
        .thenReturn(completedFuture(Optional.of(expectedResponse)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldGetBuildersFromStateWithIdsAndStatuses() throws Exception {
    final StateBuilderRequestBodyType requestBody =
        new StateBuilderRequestBodyType(List.of("0"), List.of(BuilderStatus.ACTIVE));
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();
    request.setRequestBody(requestBody);
    final ObjectAndMetaData<List<StateBuilderData>> expectedResponse =
        new ObjectAndMetaData<>(getBuildersList(), SpecMilestone.GLOAS, false, true, false);
    when(chainDataProvider.getStateBuilders("head", List.of("0"), List.of(BuilderStatus.ACTIVE)))
        .thenReturn(completedFuture(Optional.of(expectedResponse)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldReadStringStatusesFromRequestBody() throws IOException {
    final StateBuilderRequestBodyType requestBody =
        (StateBuilderRequestBodyType)
            getRequestBodyFromMetadata(
                handler, "{\"ids\":[\"0\"],\"statuses\":[\"active\",\"exited\"]}");

    assertThat(requestBody.getIds()).containsExactly("0");
    assertThat(requestBody.getStatuses())
        .containsExactly(BuilderStatus.ACTIVE, BuilderStatus.EXITED);
  }

  @Test
  void shouldFailIfStatusInvalidInRequestBody() {
    assertThatThrownBy(() -> getRequestBodyFromMetadata(handler, "{\"statuses\":[\"invalid\"]}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldGetBuildersFromStateWithEmptyRequestBody() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();
    final ObjectAndMetaData<List<StateBuilderData>> expectedResponse =
        new ObjectAndMetaData<>(getBuildersList(), SpecMilestone.GLOAS, false, true, false);
    when(chainDataProvider.getStateBuilders("head", List.of(), List.of()))
        .thenReturn(completedFuture(Optional.of(expectedResponse)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final StateBuilderData stateBuilderData = getBuildersList().get(0);
    final ObjectAndMetaData<List<StateBuilderData>> responseData =
        new ObjectAndMetaData<>(List.of(stateBuilderData), SpecMilestone.GLOAS, false, true, false);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);

    assertThat(data)
        .contains("\"execution_optimistic\":false")
        .contains("\"finalized\":false")
        .contains("\"index\":\"0\"")
        .contains("\"status\":\"active\"")
        .contains("\"builder\":")
        .contains("\"pubkey\":\"" + stateBuilderData.builder().getPublicKey() + "\"");
  }

  @Test
  void metadata_shouldSelectJsonWhenOctetStreamRequested() {
    assertThat(handler.getMetadata().getContentType(SC_OK, Optional.of(ContentTypes.OCTET_STREAM)))
        .isEqualTo(ContentTypes.JSON);
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
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  private List<StateBuilderData> getBuildersList() {
    final Builder builder = dataStructureUtil.randomBuilder();
    return List.of(new StateBuilderData(UInt64.ZERO, BuilderStatus.ACTIVE, builder));
  }
}

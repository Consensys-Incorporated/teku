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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;

class GetBlsToExecutionChangesTest extends AbstractMigratedBeaconHandlerTest {

  private List<SignedBlsToExecutionChange> responseData;

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalCapella());
    setHandler(new GetBlsToExecutionChanges(nodeDataProvider, schemaDefinitionCache));

    responseData =
        List.of(
            dataStructureUtil.randomSignedBlsToExecutionChange(),
            dataStructureUtil.randomSignedBlsToExecutionChange());
  }

  @Test
  public void successfullyReturnsBlsToExecutionChanges() throws JsonProcessingException {
    when(nodeDataProvider.getBlsToExecutionChanges(Optional.empty())).thenReturn(responseData);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseData);
  }

  @Test
  public void returnsEmptyListWhenPoolIsEmpty() throws JsonProcessingException {
    when(nodeDataProvider.getBlsToExecutionChanges(Optional.empty()))
        .thenReturn(Collections.emptyList());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(Collections.emptyList());
  }

  @Test
  public void returnsEmptyListOfLocalOperationsIfPoolIsEmpty() throws JsonProcessingException {
    when(nodeDataProvider.getBlsToExecutionChanges(Optional.of(true)))
        .thenReturn(Collections.emptyList());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(Collections.emptyList());
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
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetBlsToExecutionChangesTest.class, "getBlsToExecutionChanges.json"),
            UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}

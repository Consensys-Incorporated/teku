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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ROOT_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;

public class AbstractGetSimpleDataFromStateTest extends AbstractMigratedBeaconHandlerTest {
  private static final String ROUTE = "/test/:state_id";
  private static final SerializableTypeDefinition<StateAndMetaData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(StateAndMetaData.class)
          .name("ResponseObject")
          .withField(
              "data", ROOT_TYPE, stateAndMetaData -> stateAndMetaData.getData().hashTreeRoot())
          .build();

  @BeforeEach
  void setUp() {
    setHandler(new TestHandler(chainDataProvider));
  }

  @Test
  void shouldReturnNotFound() throws JsonProcessingException {
    request.setPathParameter("state_id", "head");
    when(chainDataProvider.getBeaconStateAndMetadata(eq("head")))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(404);
    assertThat(request.getResponseBody()).isEqualTo(new HttpErrorResponse(404, "Not found"));
  }

  @Test
  public void shouldThrowBadRequest() {
    when(chainDataProvider.getBeaconStateAndMetadata(eq("invalid")))
        .thenThrow(new BadRequestException("invalid state"));
    request.setPathParameter("state_id", "invalid");

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("invalid state");
  }

  static class TestHandler extends AbstractGetSimpleDataFromState {

    public TestHandler(final ChainDataProvider chainDataProvider) {
      super(
          EndpointMetadata.get(ROUTE)
              .operationId("testFn")
              .summary("test function")
              .description("TEST")
              .pathParam(PARAMETER_STATE_ID)
              .response(SC_OK, "Request successful", RESPONSE_TYPE)
              .withNotFoundResponse()
              .build(),
          chainDataProvider);
    }
  }
}

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
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.api.response.ValidatorStatus.active_exiting;
import static tech.pegasys.teku.api.response.ValidatorStatus.active_ongoing;
import static tech.pegasys.teku.api.response.ValidatorStatus.active_slashed;
import static tech.pegasys.teku.api.response.ValidatorStatus.exited_slashed;
import static tech.pegasys.teku.api.response.ValidatorStatus.exited_unslashed;
import static tech.pegasys.teku.api.response.ValidatorStatus.pending_initialized;
import static tech.pegasys.teku.api.response.ValidatorStatus.pending_queued;
import static tech.pegasys.teku.api.response.ValidatorStatus.withdrawal_done;
import static tech.pegasys.teku.api.response.ValidatorStatus.withdrawal_possible;
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
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.beacon.StatusParameter;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateValidatorsTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.ALTAIR);
    genesis();

    setHandler(new GetStateValidators(chainDataProvider));
  }

  @Test
  public void shouldGetValidatorFromState() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2", "3,4"))
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators("head", List.of("1", "2", "3", "4"), emptySet())
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGetValidatorFromStateWithList() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2"))
            .listQueryParameter(
                "status", List.of("active_ongoing", "active_exiting, withdrawal_done"))
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators(
                "head", List.of("1", "2"), Set.of(active_ongoing, active_exiting, withdrawal_done))
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideStatusParameters")
  public void shouldGetValidatorsByStatusParameter(
      final List<String> statusParameters, final Set<ValidatorStatus> expectedValidatorStatuses)
      throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .listQueryParameter("status", statusParameters)
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators("head", List.of(), expectedValidatorStatuses)
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGetBadRequestForInvalidState() {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "invalid")
            .listQueryParameter("id", List.of("1"))
            .build();

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid state ID: invalid");
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() {
    verifyMetadataEmptyResponse(new GetGenesis(chainDataProvider), SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final StateValidatorData data1 =
        new StateValidatorData(
            UInt64.valueOf(0),
            dataStructureUtil.randomUInt64(),
            active_ongoing,
            dataStructureUtil.randomValidator());
    final StateValidatorData data2 =
        new StateValidatorData(
            UInt64.valueOf(1),
            dataStructureUtil.randomUInt64(),
            active_ongoing,
            dataStructureUtil.randomValidator());
    final List<StateValidatorData> value = List.of(data1, data2);
    final ObjectAndMetaData<List<StateValidatorData>> responseData = withMetaData(value);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetStateValidatorsTest.class, "getStateValidatorsTest.json"),
            UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }

  @Test
  void statusParameterEnumContainsAllValidatorStatuses() {
    assertThat(ValidatorStatus.values())
        .allMatch(
            validatorStatus ->
                Arrays.stream(StatusParameter.values())
                    .anyMatch(
                        statusParameter ->
                            statusParameter.getValue().equals(validatorStatus.name())));
  }

  private static Stream<Arguments> provideStatusParameters() {
    return Stream.of(
        Arguments.of(List.of("active"), Set.of(active_ongoing, active_exiting, active_slashed)),
        Arguments.of(List.of("pending"), Set.of(pending_initialized, pending_queued)),
        Arguments.of(List.of("exited"), Set.of(exited_slashed, exited_unslashed)),
        Arguments.of(List.of("withdrawal"), Set.of(withdrawal_done, withdrawal_possible)));
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

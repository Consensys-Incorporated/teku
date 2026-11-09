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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;

public class GetStateCommitteesTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.ALTAIR);
    genesis();

    setHandler(new GetStateCommittees(chainDataProvider));
  }

  @Test
  public void shouldGetCommitteesFromState() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .optionalQueryParameter("epoch", "0")
            .optionalQueryParameter("index", "0")
            .optionalQueryParameter("slot", "1")
            .build();

    final Optional<ObjectAndMetaData<List<CommitteeAssignment>>> expectedData =
        chainDataProvider
            .getStateCommittees(
                "head",
                Optional.of(UInt64.valueOf(0)),
                Optional.of(UInt64.valueOf(0)),
                Optional.of(UInt64.valueOf(1)))
            .get();
    assertThat(expectedData.orElseThrow().getData()).isNotEmpty();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedData.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"index", "slot", "epoch"})
  public void shouldFailIfEpochInvalid(final String queryParam) {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .optionalQueryParameter(queryParam, "a")
            .build();

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("For input string: \"a\"");
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
    final CommitteeAssignment committeeAssignment =
        new CommitteeAssignment(IntList.of(1, 2), ONE, ONE);
    final ObjectAndMetaData<List<CommitteeAssignment>> responseData =
        withMetaData(List.of(committeeAssignment));

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetStateCommitteesTest.class, "getStateCommittees.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}

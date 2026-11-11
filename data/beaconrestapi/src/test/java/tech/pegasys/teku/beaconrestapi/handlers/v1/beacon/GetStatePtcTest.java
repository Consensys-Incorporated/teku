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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.StatePtcData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

class GetStatePtcTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setUp() {
    setUp(SpecMilestone.GLOAS);
  }

  private void setUp(final SpecMilestone milestone) {
    initialise(milestone);
    genesis();
    setHandler(new GetStatePtc(chainDataProvider));
  }

  @Test
  void shouldReturnPtcForRequestedSlot() throws JsonProcessingException {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .optionalQueryParameter("slot", "1")
            .build();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    final StatePtcData data =
        (StatePtcData) ((ObjectAndMetaData<?>) request.getResponseBody()).getData();
    assertThat(data.slot()).isEqualTo(UInt64.ONE);
    assertThat(data.validators())
        .hasSize(
            spec.getGenesisSpecConfig()
                .toVersionGloas()
                .orElseThrow()
                .getPayloadTimelinessCommitteeSize());
  }

  @Test
  void shouldRejectPreGloasState() throws JsonProcessingException {
    setUp(SpecMilestone.FULU);
    request.setPathParameter("state_id", "head");

    handler.handleRequest(request);

    assertThat(request.getResponseError())
        .hasValueSatisfying(
            error ->
                assertThat(error)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("prior to GLOAS"));
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final ObjectAndMetaData<StatePtcData> responseData =
        new ObjectAndMetaData<>(
            new StatePtcData(
                UInt64.valueOf(5),
                List.of(UInt64.valueOf(9), UInt64.valueOf(2), UInt64.valueOf(9))),
            SpecMilestone.GLOAS,
            false,
            true,
            false);

    assertThat(getResponseStringFromMetadata(handler, SC_OK, responseData))
        .isEqualTo(
            "{\"execution_optimistic\":false,\"finalized\":false,\"data\":{\"slot\":\"5\",\"validators\":[\"9\",\"2\",\"9\"]}}");
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

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }
}

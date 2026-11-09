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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ProposersData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.RegisteredValidatorInfo;

public class GetProposersDataTest extends AbstractMigratedBeaconHandlerTest {

  final Map<UInt64, PreparedProposerInfo> preparedProposers =
      ImmutableMap.<UInt64, PreparedProposerInfo>builder()
          .put(
              UInt64.ZERO,
              new PreparedProposerInfo(UInt64.ONE, dataStructureUtil.randomEth1Address()))
          .build();
  final Map<UInt64, RegisteredValidatorInfo> registeredValidators =
      ImmutableMap.<UInt64, RegisteredValidatorInfo>builder()
          .put(
              UInt64.ONE,
              new RegisteredValidatorInfo(
                  UInt64.ONE, dataStructureUtil.randomSignedValidatorRegistration()))
          .build();
  final ProposersData responseData = new ProposersData(preparedProposers, registeredValidators);

  @BeforeEach
  void setup() {
    setHandler(new GetProposersData(nodeDataProvider));
  }

  @Test
  public void shouldReturnProposersData() throws JsonProcessingException {
    when(nodeDataProvider.getPreparedProposerInfo()).thenReturn(preparedProposers);
    when(nodeDataProvider.getValidatorRegistrationInfo()).thenReturn(registeredValidators);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseData);
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
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetProposersDataTest.class, "getProposersData.json"), UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}

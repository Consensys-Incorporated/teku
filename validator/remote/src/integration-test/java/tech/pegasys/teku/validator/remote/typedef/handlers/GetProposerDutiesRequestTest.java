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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class GetProposerDutiesRequestTest extends AbstractTypeDefRequestTestBase {
  private GetProposerDutiesRequest request;

  @BeforeEach
  public void setupRequest() {
    request = new GetProposerDutiesRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  void canDeserializeResponse() {
    final String mockResponse = readResource("responses/get_proposer_duties_response.json");

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    final Optional<ProposerDuties> response = request.submit(UInt64.ZERO);
    assertThat(response).isPresent();
    assertThat(response.get().getDuties().size()).isEqualTo(1);
    assertThat(response.get().getDuties().getFirst().getValidatorIndex()).isEqualTo(1);
  }

  @TestTemplate
  void handle503() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_SERVICE_UNAVAILABLE));
    assertThatThrownBy(() -> request.submit(UInt64.ZERO))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(UInt64.ZERO))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_BAD_REQUEST)
            .setBody("{\"code\": 400, \"message\": \"Bad Request\"}"));
    assertThatThrownBy(() -> request.submit(UInt64.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

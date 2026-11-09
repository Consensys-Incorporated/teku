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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.ethereum.json.types.beacon.GetGenesisApiData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(network = Eth2Network.MINIMAL)
public class GetGenesisRequestTest extends AbstractTypeDefRequestTestBase {

  private GetGenesisRequest getGenesisRequest;

  @BeforeEach
  public void setupRequest() {
    getGenesisRequest = new GetGenesisRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  void shouldHandleSuccessfulResponse() throws IOException {
    final String mockResponse =
        Resources.toString(
            Resources.getResource(GetGenesisRequestTest.class, "getGenesisRequest_200.json"),
            UTF_8);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));
    final Optional<GetGenesisApiData> response = getGenesisRequest.submit();
    assertThat(response).isPresent();
    assertThat(response.get().getGenesisTime()).isEqualTo(UInt64.valueOf(1590832934));
  }

  @TestTemplate
  void shouldHandleNotFoundResponse() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_NOT_FOUND)
            .setBody("{\"code\": 404, \"message\": \"Not Ready\"}"));
    final Optional<GetGenesisApiData> response = getGenesisRequest.submit();
    assertThat(response).isEmpty();
  }

  @TestTemplate
  void shouldHandleErrorResponse() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> getGenesisRequest.submit())
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

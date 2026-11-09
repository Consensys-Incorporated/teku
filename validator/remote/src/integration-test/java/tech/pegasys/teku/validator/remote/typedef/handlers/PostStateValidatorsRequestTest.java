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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.PostStateValidatorsNotExistingException;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(network = Eth2Network.MINIMAL)
public class PostStateValidatorsRequestTest extends AbstractTypeDefRequestTestBase {
  private PostStateValidatorsRequest request;

  @BeforeEach
  public void setupRequest() {
    request = new PostStateValidatorsRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  void canHandleResponse() {
    final String mockResponse = readResource("responses/state_validators_response.json");

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));
    final Optional<ObjectAndMetaData<List<StateValidatorData>>> response =
        request.submit(List.of("1"));
    assertThat(response).isPresent();
    assertThat(response.get().getData().getFirst().getIndex()).isEqualTo(UInt64.ONE);
  }

  @TestTemplate
  void handle404() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));
    assertThatThrownBy(() -> request.submit(List.of("1")))
        .isInstanceOf(PostStateValidatorsNotExistingException.class);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(List.of("1")))
        .isInstanceOf(PostStateValidatorsNotExistingException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(List.of("1")))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

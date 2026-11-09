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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(network = Eth2Network.MINIMAL)
public class PostVoluntaryExitRequestTest extends AbstractTypeDefRequestTestBase {
  private PostVoluntaryExitRequest request;

  @BeforeEach
  public void setupRequest() {
    request = new PostVoluntaryExitRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  void handle200() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    final SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();

    assertThatNoException().isThrownBy(() -> request.submit(exit));
  }

  @TestTemplate
  void handle400() {
    final SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(exit)).isInstanceOf(BadRequestException.class);
  }

  @TestTemplate
  void handle500() {
    final SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(exit))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

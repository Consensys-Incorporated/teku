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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(milestone = SpecMilestone.PHASE0, network = Eth2Network.MINIMAL)
public class SendValidatorLivenessRequestTest extends AbstractTypeDefRequestTestBase {

  private SendValidatorLivenessRequest request;

  @BeforeEach
  void setupRequest() {
    request = new SendValidatorLivenessRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  public void sendValidatorLiveness_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    request.submit(UInt64.ONE, List.of(UInt64.valueOf(1)));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_VALIDATOR_LIVENESS.getPath(Map.of("epoch", "1")));
  }

  @TestTemplate
  public void sendValidatorLiveness_readsResponse() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody("{\"data\":[{\"index\":\"1\",\"is_live\":false}]}"));
    final Optional<List<ValidatorLivenessAtEpoch>> result =
        request.submit(UInt64.ONE, List.of(UInt64.valueOf(1)));

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_VALIDATOR_LIVENESS.getPath(Map.of("epoch", "1")));

    assertThat(result).isPresent();
    assertThat(result.get()).containsExactly(new ValidatorLivenessAtEpoch(UInt64.ONE, false));
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(UInt64.ONE, List.of(UInt64.valueOf(1))))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

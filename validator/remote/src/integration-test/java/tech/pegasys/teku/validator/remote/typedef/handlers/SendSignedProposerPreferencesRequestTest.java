/*
 * Copyright Consensys Software Inc., 2026
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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
public class SendSignedProposerPreferencesRequestTest extends AbstractTypeDefRequestTestBase {
  private SendSignedProposerPreferencesRequest request;
  private List<SignedProposerPreferences> signedProposerPreferences;

  @BeforeEach
  public void setup() {
    request = new SendSignedProposerPreferencesRequest(mockWebServer.url("/"), okHttpClient);
    signedProposerPreferences = List.of(dataStructureUtil.randomSignedProposerPreferences());
  }

  @TestTemplate
  void handle200() throws InterruptedException, JsonProcessingException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    final List<SubmitDataError> response = request.submit(signedProposerPreferences);
    assertThat(response).isEmpty();
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    final List<SignedProposerPreferences> data =
        JsonUtil.parse(
            recordedRequest.getBody().readUtf8(),
            DeserializableTypeDefinition.listOf(
                signedProposerPreferences.getFirst().getSchema().getJsonTypeDefinition()));
    assertThat(data).isEqualTo(signedProposerPreferences);
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_PROPOSER_PREFERENCES.getPath(emptyMap()));
  }

  @TestTemplate
  void shouldNotMakeRequestWhenEmptyList() {
    final List<SubmitDataError> response = request.submit(List.of());
    assertThat(response).isEmpty();
    assertThat(mockWebServer.getRequestCount()).isZero();
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_BAD_REQUEST)
            .setBody(
                "{\"code\": 400,\"message\": \"z\",\"failures\": [{\"index\": 3,\"message\": \"a\"}]}"));
    final List<SubmitDataError> response = request.submit(signedProposerPreferences);
    assertThat(response).containsExactly(new SubmitDataError(UInt64.valueOf(3), "a"));
  }

  @TestTemplate
  void handle400_notParsableMessage() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(signedProposerPreferences))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(signedProposerPreferences))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

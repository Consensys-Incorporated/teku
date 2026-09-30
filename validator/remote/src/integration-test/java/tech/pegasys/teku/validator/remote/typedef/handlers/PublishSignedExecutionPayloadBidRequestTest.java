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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
public class PublishSignedExecutionPayloadBidRequestTest extends AbstractTypeDefRequestTestBase {
  private PublishSignedExecutionPayloadBidRequest request;
  private SignedExecutionPayloadBid signedExecutionPayloadBid;

  @BeforeEach
  public void setup() {
    request =
        new PublishSignedExecutionPayloadBidRequest(spec, mockWebServer.url("/"), okHttpClient);
    signedExecutionPayloadBid = dataStructureUtil.randomSignedExecutionPayloadBid();
  }

  @TestTemplate
  void handle200() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    request.submit(signedExecutionPayloadBid);

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    final SignedExecutionPayloadBid data =
        JsonUtil.parse(
            recordedRequest.getBody().readUtf8(),
            SchemaDefinitionsGloas.required(schemaDefinitions)
                .getSignedExecutionPayloadBidSchema()
                .getJsonTypeDefinition());
    assertThat(data).isEqualTo(signedExecutionPayloadBid);
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_EXECUTION_PAYLOAD_BID.getPath(emptyMap()));
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.lowerCaseName());
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(signedExecutionPayloadBid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(signedExecutionPayloadBid))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}

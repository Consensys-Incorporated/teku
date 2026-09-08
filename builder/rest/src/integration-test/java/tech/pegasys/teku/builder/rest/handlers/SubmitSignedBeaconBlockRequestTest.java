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

package tech.pegasys.teku.builder.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;

import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.builder.rest.AbstractBuilderRequestTestBase;
import tech.pegasys.teku.builder.rest.BuilderApiMethod;
import tech.pegasys.teku.builder.rest.BuilderClientException;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.networks.Eth2Network;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
class SubmitSignedBeaconBlockRequestTest extends AbstractBuilderRequestTestBase {

  private SubmitSignedBeaconBlockRequest request;
  private SignedBeaconBlock signedBeaconBlock;

  @BeforeEach
  void setupRequest() {
    request = new SubmitSignedBeaconBlockRequest(spec, mockWebServer.url("/"), okHttpClient);
    signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
  }

  @TestTemplate
  void shouldSucceedOn202() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_ACCEPTED));

    assertThatNoException().isThrownBy(() -> request.submit(signedBeaconBlock));
  }

  @TestTemplate
  void shouldPostToCorrectUrl() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_ACCEPTED));

    request.submit(signedBeaconBlock);

    final RecordedRequest recorded = mockWebServer.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(recorded.getRequestUrl().encodedPath())
        .isEqualTo("/" + BuilderApiMethod.SUBMIT_SIGNED_BEACON_BLOCK.getPath(Map.of()));
  }

  @TestTemplate
  void shouldSubmitSszEncodedBody() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_ACCEPTED));

    request.submit(signedBeaconBlock);

    final RecordedRequest recorded = mockWebServer.takeRequest();
    assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/octet-stream");
    final byte[] body = recorded.getBody().readByteArray();
    assertThat(body).isEqualTo(signedBeaconBlock.sszSerialize().toArrayUnsafe());
  }

  @TestTemplate
  void shouldIncludeConsensusVersionHeader() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_ACCEPTED));

    request.submit(signedBeaconBlock);

    final RecordedRequest recorded = mockWebServer.takeRequest();
    final String expectedMilestone =
        spec.atSlot(signedBeaconBlock.getSlot()).getMilestone().lowerCaseName();
    assertThat(recorded.getHeader("Eth-Consensus-Version")).isEqualTo(expectedMilestone);
  }

  @TestTemplate
  void shouldThrowBuilderClientExceptionOn400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> request.submit(signedBeaconBlock))
        .isInstanceOf(BuilderClientException.class)
        .hasMessageContaining("Bad request");
  }

  @TestTemplate
  void shouldThrowBuilderClientExceptionOn500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> request.submit(signedBeaconBlock))
        .isInstanceOf(BuilderClientException.class);
  }
}

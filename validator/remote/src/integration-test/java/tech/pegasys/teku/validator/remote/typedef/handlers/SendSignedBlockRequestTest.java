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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BUILDER_URL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.util.Locale;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(
    milestone = {
      SpecMilestone.BELLATRIX,
      SpecMilestone.CAPELLA,
      SpecMilestone.DENEB,
      SpecMilestone.ELECTRA,
      SpecMilestone.GLOAS
    },
    network = Eth2Network.MINIMAL)
public class SendSignedBlockRequestTest extends AbstractTypeDefRequestTestBase {
  private SignedBlockContainer block;
  private SendSignedBlockRequest request;

  @BeforeEach
  public void setup() {
    request = new SendSignedBlockRequest(spec, mockWebServer.url("/"), okHttpClient, true);
    this.block =
        specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)
            ? dataStructureUtil.randomSignedBlockContents()
            : dataStructureUtil.randomSignedBeaconBlock();
  }

  @TestTemplate
  public void shouldIncludeHeadersCorrectlyInJsonRequest() throws InterruptedException {
    request = new SendSignedBlockRequest(spec, mockWebServer.url("/"), okHttpClient, false);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    final String builderUrl = "https://foobar.com";

    request.submit(block, BroadcastValidationLevel.NOT_REQUIRED, Optional.of(builderUrl));

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_BLOCK_V2.getPath(emptyMap()));
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
    assertThat(recordedRequest.getHeader(HEADER_BUILDER_URL)).isEqualTo(builderUrl);
    assertThat(recordedRequest.getHeader("Content-Type")).contains("json");
  }

  @TestTemplate
  public void shouldIncludeHeadersCorrectlyInSszRequest() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    request.submit(block, BroadcastValidationLevel.NOT_REQUIRED, Optional.empty());

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_BLOCK_V2.getPath(emptyMap()));
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
    assertThat(recordedRequest.getHeader(HEADER_BUILDER_URL)).isNull();
    assertThat(recordedRequest.getHeader("Content-Type")).contains("octet");
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(
            () -> request.submit(block, BroadcastValidationLevel.NOT_REQUIRED, Optional.empty()))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_BAD_REQUEST)
            .setBody("{\"code\": 400,\"message\": \"z\"}"));
    assertThatThrownBy(
            () -> request.submit(block, BroadcastValidationLevel.NOT_REQUIRED, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Invalid params response from Beacon Node API");
  }
}

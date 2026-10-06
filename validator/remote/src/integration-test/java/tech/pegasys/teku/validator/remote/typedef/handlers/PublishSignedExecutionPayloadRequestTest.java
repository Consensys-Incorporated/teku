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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BLOB_DATA_INCLUDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.util.Locale;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(
    milestone = {SpecMilestone.GLOAS},
    network = Eth2Network.MINIMAL)
public class PublishSignedExecutionPayloadRequestTest extends AbstractTypeDefRequestTestBase {
  private SignedExecutionPayloadEnvelope signedExecutionPayload;
  private PublishSignedExecutionPayloadRequest request;

  @BeforeEach
  public void setup() {
    request = new PublishSignedExecutionPayloadRequest(spec, mockWebServer.url("/"), okHttpClient);
    this.signedExecutionPayload = dataStructureUtil.randomSignedExecutionPayloadEnvelope(100L);
  }

  @TestTemplate
  public void shouldIncludeConsensusHeaderAndHandle200Ok() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    final PublishSignedExecutionPayloadResult result =
        request.submit(signedExecutionPayload, Optional.of(BroadcastValidationLevel.GOSSIP));

    assertThat(result)
        .isEqualTo(
            PublishSignedExecutionPayloadResult.success(
                signedExecutionPayload.getBeaconBlockRoot()));

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
    assertThat(recordedRequest.getHeader(HEADER_BLOB_DATA_INCLUDED)).isEqualTo("false");
  }

  @TestTemplate
  public void shouldHandle202AcceptedAsPublishedButNotImported() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_ACCEPTED));

    final PublishSignedExecutionPayloadResult result =
        request.submit(signedExecutionPayload, Optional.of(BroadcastValidationLevel.GOSSIP));

    assertThat(result)
        .isEqualTo(
            PublishSignedExecutionPayloadResult.notImported(
                signedExecutionPayload.getBeaconBlockRoot(), "UNKNOWN"));
  }
}

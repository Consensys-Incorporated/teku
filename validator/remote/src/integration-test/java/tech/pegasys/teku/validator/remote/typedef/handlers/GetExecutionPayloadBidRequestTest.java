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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
class GetExecutionPayloadBidRequestTest extends AbstractTypeDefRequestTestBase {

  private static final UInt64 BUILDER_INDEX = UInt64.valueOf(5);

  private GetExecutionPayloadBidRequest request;
  private ExecutionPayloadBid bid;

  @BeforeEach
  void setupRequest() {
    request = new GetExecutionPayloadBidRequest(mockWebServer.url("/"), okHttpClient, spec);
    bid = dataStructureUtil.randomExecutionPayloadBid();
  }

  @TestTemplate
  public void makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    request.submit(bid.getSlot(), BUILDER_INDEX);

    final RecordedRequest recorded = mockWebServer.takeRequest();

    assertThat(recorded.getMethod()).isEqualTo("GET");
    assertThat(recorded.getRequestUrl().encodedPath())
        .isEqualTo(
            "/"
                + ValidatorApiMethod.GET_EXECUTION_PAYLOAD_BID.getPath(
                    Map.of(
                        "slot", bid.getSlot().toString(),
                        "builder_index", BUILDER_INDEX.toString())));
    assertThat(recorded.getHeader("Accept"))
        .isEqualTo("application/octet-stream;q=0.9, application/json;q=0.4");
  }

  @TestTemplate
  public void whenNotFound_returnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(request.submit(bid.getSlot(), BUILDER_INDEX)).isEmpty();
  }

  @TestTemplate
  public void dataCanBeRead() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(serializeSszObjectToJsonWithDataWrapper(bid)));

    final Optional<ExecutionPayloadBid> result = request.submit(bid.getSlot(), BUILDER_INDEX);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(bid);
  }

  @TestTemplate
  public void sszDataCanBeRead() {
    final Buffer responseBody = new Buffer().write(bid.sszSerialize().toArrayUnsafe());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader("Content-Type", OCTET_STREAM_CONTENT_TYPE)
            .setBody(responseBody));

    final Optional<ExecutionPayloadBid> result = request.submit(bid.getSlot(), BUILDER_INDEX);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(bid);
  }
}

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.AbstractBuilderRequestTestBase;
import tech.pegasys.teku.builder.rest.BuilderClientException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedBuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
class GetExecutionPayloadBidRequestTest extends AbstractBuilderRequestTestBase {

  private GetExecutionPayloadBidRequest request;
  private UInt64 slot;
  private Bytes32 parentHash;
  private Bytes32 parentRoot;
  private BLSPublicKey proposerPubkey;
  private SignedBuilderRequestAuth auth;

  @BeforeEach
  void setupRequest() {
    request = new GetExecutionPayloadBidRequest(spec, mockWebServer.url("/"), okHttpClient);
    slot = dataStructureUtil.randomUInt64();
    parentHash = dataStructureUtil.randomBytes32();
    parentRoot = dataStructureUtil.randomBytes32();
    proposerPubkey = dataStructureUtil.randomPublicKey();
    auth = dataStructureUtil.randomSignedBuilderRequestAuth();
  }

  @TestTemplate
  void shouldReturnExecutionPayloadBidOn200() throws Exception {
    final SignedExecutionPayloadBid expected = dataStructureUtil.randomSignedExecutionPayloadBid();
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final String body =
        JsonUtil.serialize(
            expected, withDataWrapper(schemaDefinitions.getSignedExecutionPayloadBidSchema()));
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(body));

    final Optional<SignedExecutionPayloadBid> result =
        request.submit(slot, parentHash, parentRoot, proposerPubkey, auth);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(expected);

    final RecordedRequest recorded = mockWebServer.takeRequest();
    // verify POST
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(recorded.getBody().size()).isGreaterThan(0);
    // verify path parameters
    final String path = recorded.getRequestUrl().encodedPath();
    assertThat(path).contains(slot.toString());
    assertThat(path).contains(parentHash.toHexString());
    assertThat(path).contains(parentRoot.toHexString());
    assertThat(path).contains(proposerPubkey.toString());
    // verify headers
    final String expectedMilestone = spec.atSlot(slot).getMilestone().lowerCaseName();
    assertThat(recorded.getHeader("Eth-Consensus-Version")).isEqualTo(expectedMilestone);
    assertThat(recorded.getHeader("Date-Milliseconds")).isNotBlank();
    assertThat(recorded.getHeader("X-Timeout-Ms")).isNotBlank();
  }

  @TestTemplate
  void shouldReturnEmptyOn204() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    final Optional<SignedExecutionPayloadBid> result =
        request.submit(slot, parentHash, parentRoot, proposerPubkey, auth);

    assertThat(result).isEmpty();
  }

  @TestTemplate
  void shouldThrowBuilderClientExceptionOn400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> request.submit(slot, parentHash, parentRoot, proposerPubkey, auth))
        .isInstanceOf(BuilderClientException.class)
        .hasMessageContaining("Bad request");
  }

  @TestTemplate
  void shouldThrowBuilderClientExceptionOn401() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_UNAUTHORIZED));

    assertThatThrownBy(() -> request.submit(slot, parentHash, parentRoot, proposerPubkey, auth))
        .isInstanceOf(BuilderClientException.class)
        .hasMessageContaining("Unauthorized");
  }

  @TestTemplate
  void shouldThrowBuilderClientExceptionOn500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> request.submit(slot, parentHash, parentRoot, proposerPubkey, auth))
        .isInstanceOf(BuilderClientException.class);
  }
}

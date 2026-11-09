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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
class CreatePayloadAttestationDataRequestTest extends AbstractTypeDefRequestTestBase {

  private CreatePayloadAttestationDataRequest request;

  @BeforeEach
  void setupRequest() {
    request = new CreatePayloadAttestationDataRequest(mockWebServer.url("/"), okHttpClient, spec);
  }

  @TestTemplate
  public void makesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    request.submit(slot);

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getRequestUrl().encodedPath())
        .isEqualTo("/" + ValidatorApiMethod.GET_PAYLOAD_ATTESTATION_DATA.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getHeader("Accept"))
        .isEqualTo("application/octet-stream;q=0.9, application/json;q=0.4");
  }

  @TestTemplate
  public void whenBadRequest_throwsIllegalArgumentException() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> request.submit(UInt64.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void whenNotFound_returnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(request.submit(UInt64.ONE)).isEmpty();
  }

  @TestTemplate
  public void whenInvalidResponse_throwsError() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_FORBIDDEN));

    assertThatThrownBy(() -> request.submit(UInt64.ONE))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @TestTemplate
  public void dataCanBeRead() throws Exception {
    final PayloadAttestationData expectedPayloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData(UInt64.ONE);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(serializeSpecPayloadAttestationDataResponse(expectedPayloadAttestationData)));

    final Optional<PayloadAttestationData> payloadAttestationData = request.submit(UInt64.ONE);

    assertThat(payloadAttestationData).isPresent();
    assertThat(payloadAttestationData.get()).isEqualTo(expectedPayloadAttestationData);
  }

  @TestTemplate
  public void sszDataCanBeRead() {
    final PayloadAttestationData expectedPayloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData(UInt64.ONE);
    final Buffer responseBody =
        new Buffer().write(expectedPayloadAttestationData.sszSerialize().toArrayUnsafe());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader("Content-Type", OCTET_STREAM_CONTENT_TYPE)
            .setBody(responseBody));

    final Optional<PayloadAttestationData> payloadAttestationData = request.submit(UInt64.ONE);

    assertThat(payloadAttestationData).isPresent();
    assertThat(payloadAttestationData.get()).isEqualTo(expectedPayloadAttestationData);
  }

  private String serializeSpecPayloadAttestationDataResponse(
      final PayloadAttestationData payloadAttestationData) throws Exception {
    return "{\"version\":\"gloas\","
        + serializeSszObjectToJsonWithDataWrapper(payloadAttestationData).substring(1);
  }
}

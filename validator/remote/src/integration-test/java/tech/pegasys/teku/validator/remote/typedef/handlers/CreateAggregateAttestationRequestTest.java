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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;

import java.util.Collections;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(milestone = SpecMilestone.PHASE0, network = Eth2Network.MINIMAL)
public class CreateAggregateAttestationRequestTest extends AbstractTypeDefRequestTestBase {

  private CreateAggregateAttestationRequest request;
  private UInt64 slot;
  private Bytes32 attestationHashTreeRoot;

  @BeforeEach
  public void setup() {
    slot = dataStructureUtil.randomSlot();
    attestationHashTreeRoot = dataStructureUtil.randomBytes32();
    request =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestationHashTreeRoot,
            Optional.empty(),
            false,
            spec);
  }

  @TestTemplate
  public void getAggregateAttestation_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request.submit();
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_AGGREGATE.getPath(Collections.emptyMap()));
    assertThat(request.getRequestUrl().queryParameter(SLOT)).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter(ATTESTATION_DATA_ROOT))
        .isEqualTo(attestationHashTreeRoot.toHexString());
  }

  @TestTemplate
  public void shouldGetAggregateAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final CreateAggregateAttestationRequest.GetAggregateAttestationResponse
        getAggregateAttestationResponse =
            new CreateAggregateAttestationRequest.GetAggregateAttestationResponse(attestation);
    final String mockResponse =
        readResource(
            "responses/create_aggregate_attestation_responses/createAggregateAttestationResponse.json");
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));
    request =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestation.hashTreeRoot(),
            Optional.empty(),
            false,
            spec);
    final Optional<ObjectAndMetaData<Attestation>> maybeAttestation = request.submit();
    assertThat(maybeAttestation).isPresent();
    assertThat(maybeAttestation.get().getData())
        .isEqualTo(getAggregateAttestationResponse.getData());
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit()).isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle404() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));
    assertThat(request.submit()).isEmpty();
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit())
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  @TestTemplate
  void shouldUseV2ApiWhenUseAttestationsV2ApisEnabled() throws InterruptedException {
    final Bytes32 attestationHashTreeRoot = dataStructureUtil.randomBytes32();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestationHashTreeRoot,
            Optional.of(dataStructureUtil.randomUInt64()),
            true,
            spec);
    request.submit();
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_AGGREGATE_V2.getPath(Collections.emptyMap()));
  }

  @TestTemplate
  public void shouldThrowWhenCommitteeIndexIsMissingAndUseAttestationsV2ApisEnabled() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    request =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestation.hashTreeRoot(),
            Optional.empty(),
            true,
            spec);
    assertThatThrownBy(() -> request.submit())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter: committee index");
    assertThat(mockWebServer.getRequestCount()).isZero();
  }
}

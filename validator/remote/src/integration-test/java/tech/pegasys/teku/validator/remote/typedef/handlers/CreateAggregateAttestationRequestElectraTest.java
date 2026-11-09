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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(
    milestone = {ELECTRA},
    network = Eth2Network.MINIMAL)
public class CreateAggregateAttestationRequestElectraTest extends AbstractTypeDefRequestTestBase {

  private CreateAggregateAttestationRequest createAggregateAttestationRequest;
  private final UInt64 slot = UInt64.ONE;

  @TestTemplate
  public void getAggregateAttestation_makesExpectedRequest() throws Exception {
    final UInt64 committeeIndex = dataStructureUtil.randomUInt64();
    final Bytes32 attestationHashTreeRoot = dataStructureUtil.randomBytes32();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    createAggregateAttestationRequest =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestationHashTreeRoot,
            Optional.of(committeeIndex),
            false,
            spec);

    createAggregateAttestationRequest.submit();

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_AGGREGATE_V2.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter(SLOT)).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter(ATTESTATION_DATA_ROOT))
        .isEqualTo(attestationHashTreeRoot.toHexString());
    assertThat(request.getRequestUrl().queryParameter(COMMITTEE_INDEX))
        .isEqualTo(committeeIndex.toString());
  }

  @TestTemplate
  public void shouldGetAggregateAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final CreateAggregateAttestationRequest.GetAggregateAttestationResponseV2
        getAggregateAttestationResponse =
            new CreateAggregateAttestationRequest.GetAggregateAttestationResponseV2(attestation);

    final String mockResponse =
        readResource(
            "responses/create_aggregate_attestation_responses/createAggregateAttestationResponseElectra.json");

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    final UInt64 committeeIndex =
        specMilestone.isGreaterThanOrEqualTo(ELECTRA)
            ? UInt64.valueOf(
                attestation.getCommitteeBitsRequired().streamAllSetBits().findFirst().orElseThrow())
            : attestation.getData().getIndex();

    createAggregateAttestationRequest =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestation.hashTreeRoot(),
            Optional.of(committeeIndex),
            false,
            spec);

    final Optional<ObjectAndMetaData<Attestation>> maybeAttestationAndMetaData =
        createAggregateAttestationRequest.submit();
    assertThat(maybeAttestationAndMetaData).isPresent();
    assertThat(maybeAttestationAndMetaData.get().getData())
        .isEqualTo(getAggregateAttestationResponse.getData());
    assertThat(maybeAttestationAndMetaData.get().getMilestone()).isEqualTo(specMilestone);
  }

  @TestTemplate
  public void shouldThrowWhenCommitteeIndexIsMissing() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    createAggregateAttestationRequest =
        new CreateAggregateAttestationRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            slot,
            attestation.hashTreeRoot(),
            Optional.empty(),
            false,
            spec);
    assertThatThrownBy(() -> createAggregateAttestationRequest.submit())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter: committee index");
    assertThat(mockWebServer.getRequestCount()).isZero();
  }
}

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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Locale;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(
    milestone = {SpecMilestone.CAPELLA, SpecMilestone.ELECTRA},
    network = Eth2Network.MINIMAL)
public class SendAggregatesAndProofsRequestTest extends AbstractTypeDefRequestTestBase {
  private SendAggregateAndProofsRequest request;
  private List<SignedAggregateAndProof> aggregateAndProofs;

  @BeforeEach
  public void setup() {
    this.request =
        new SendAggregateAndProofsRequest(mockWebServer.url("/"), okHttpClient, false, spec);
    this.aggregateAndProofs = List.of(dataStructureUtil.randomSignedAggregateAndProof());
  }

  @TestTemplate
  void handle200() throws InterruptedException, JsonProcessingException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    final List<SubmitDataError> response = request.submit(aggregateAndProofs);
    assertThat(response).isEmpty();
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    final List<SignedAggregateAndProof> data =
        JsonUtil.parse(
            recordedRequest.getBody().readUtf8(),
            DeserializableTypeDefinition.listOf(
                spec.getGenesisSchemaDefinitions()
                    .getSignedAggregateAndProofSchema()
                    .getJsonTypeDefinition()));
    assertThat(data).isEqualTo(aggregateAndProofs);
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      assertThat(recordedRequest.getPath())
          .contains(ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOFS_V2.getPath(emptyMap()));
      assertThat(recordedRequest.getHeader(RestApiConstants.HEADER_CONSENSUS_VERSION))
          .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
      assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
          .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
    } else {
      assertThat(recordedRequest.getPath())
          .contains(ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOFS.getPath(emptyMap()));
    }
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(aggregateAndProofs))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_BAD_REQUEST)
            .setBody(
                "{\"code\": 400,\"message\": \"z\",\"failures\": [{\"index\": 3,\"message\": \"a\"}]}"));
    final List<SubmitDataError> response = request.submit(aggregateAndProofs);
    assertThat(response).containsExactly(new SubmitDataError(UInt64.valueOf(3), "a"));
  }

  @TestTemplate
  void shouldUseV2ApiWhenUseAttestationsV2ApisEnabled() throws InterruptedException {
    this.request =
        new SendAggregateAndProofsRequest(mockWebServer.url("/"), okHttpClient, true, spec);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    final List<SubmitDataError> response = request.submit(aggregateAndProofs);
    assertThat(response).isEmpty();
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOFS_V2.getPath(emptyMap()));
  }
}

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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

/** The PQ calls of a validator client using a remote beacon node, as beacon-APIs#684 has them. */
@TestSpecContext(milestone = SpecMilestone.PQ, network = Eth2Network.MINIMAL)
public class PqValidatorRequestsTest extends AbstractTypeDefRequestTestBase {

  @TestTemplate
  void shouldReadPqAggregateWithProof() throws Exception {
    final PqAttestationSchemas schemas = getSchemas();
    final AttestationPq aggregate = (AttestationPq) dataStructureUtil.randomAttestation();
    final AttestationAndProofPq aggregateAndProof =
        schemas.getAttestationAndProofSchema().create(aggregate, dataStructureUtil.randomBytes(64));
    final String data =
        JsonUtil.serialize(
            aggregateAndProof, schemas.getAttestationAndProofSchema().getJsonTypeDefinition());
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody("{\"version\":\"pq\",\"data\":" + data + "}"));

    final Optional<AttestationAndProofPq> result =
        createAggregateRequest(aggregate.getData().getSlot()).submitPq();

    assertThat(result).contains(aggregateAndProof);
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.GET_AGGREGATE_V2.getPath(emptyMap()))
        .contains("committee_index=2");
  }

  @TestTemplate
  void shouldReturnEmptyPqAggregateWhenNotFound() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThat(createAggregateRequest(UInt64.ONE).submitPq()).isEmpty();
  }

  @TestTemplate
  void shouldSendPqAggregatesToV2WithPqHeader() throws Exception {
    final List<SignedAggregateAndProofPq> aggregates =
        List.of(dataStructureUtil.randomSignedAggregateAndProofPq());
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    final List<SubmitDataError> errors =
        new SendAggregateAndProofsRequest(mockWebServer.url("/"), okHttpClient, true, spec)
            .submitPq(aggregates);

    assertThat(errors).isEmpty();
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOFS_V2.getPath(emptyMap()));
    assertThat(recordedRequest.getHeader(RestApiConstants.HEADER_CONSENSUS_VERSION))
        .isEqualTo("pq");
    assertThat(
            JsonUtil.parse(
                recordedRequest.getBody().readUtf8(),
                DeserializableTypeDefinition.listOf(
                    aggregates.getFirst().getSchema().getJsonTypeDefinition())))
        .isEqualTo(aggregates);
  }

  @TestTemplate
  void shouldSendPqPayloadAttestationMessagesWithPqHeader() throws Exception {
    final List<PayloadAttestationMessagePq> messages =
        List.of(
            getSchemas()
                .getPayloadAttestationMessageSchema()
                .create(
                    UInt64.valueOf(5),
                    dataStructureUtil.randomPayloadAttestationData(),
                    dataStructureUtil.randomXmssSignature().getBytes()));
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    final List<SubmitDataError> errors =
        new SendPayloadAttestationMessagesRequest(spec, mockWebServer.url("/"), okHttpClient)
            .submitPq(messages);

    assertThat(errors).isEmpty();
    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getPath())
        .contains(ValidatorApiMethod.SEND_PAYLOAD_ATTESTATION_MESSAGES.getPath(emptyMap()));
    assertThat(recordedRequest.getHeader(RestApiConstants.HEADER_CONSENSUS_VERSION))
        .isEqualTo("pq");
    assertThat(
            JsonUtil.parse(
                recordedRequest.getBody().readUtf8(),
                DeserializableTypeDefinition.listOf(
                    messages.getFirst().getSchema().getJsonTypeDefinition())))
        .isEqualTo(messages);
  }

  private CreateAggregateAttestationRequest createAggregateRequest(final UInt64 slot) {
    return new CreateAggregateAttestationRequest(
        mockWebServer.url("/"),
        okHttpClient,
        new SchemaDefinitionCache(spec),
        slot,
        dataStructureUtil.randomBytes32(),
        Optional.of(UInt64.valueOf(2)),
        true,
        spec);
  }

  private PqAttestationSchemas getSchemas() {
    return SchemaDefinitionsPq.required(schemaDefinitions).getPqAttestationSchemas();
  }
}

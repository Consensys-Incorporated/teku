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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.validateMetrics;
import static tech.pegasys.teku.validator.client.signer.ExternalSignerTestUtil.verifySignRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.validator.api.signer.SignType;
import tech.pegasys.teku.validator.api.signer.VersionedWrapper;

public class ExternalSignerGloasIntegrationTest extends AbstractExternalSignerIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public Spec getSpec() {
    return TestSpecFactory.createMinimalGloas();
  }

  @Test
  void shouldSignExecutionPayloadBid() throws Exception {
    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid();
    final BLSSignature expectedSignature = dataStructureUtil.randomSignature();
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signExecutionPayloadBid(bid, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignExecutionPayloadBid(bid, forkInfo),
            SignType.EXECUTION_PAYLOAD_BID,
            Map.of("fork_info", forkInfo, "execution_payload_bid", gloas(bid)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
    verifyVersionedPayload("execution_payload_bid", "slot", bid.getSlot().toString());
  }

  @Test
  void shouldSignExecutionPayloadEnvelope() throws Exception {
    final ExecutionPayloadEnvelope envelope = dataStructureUtil.randomExecutionPayloadEnvelope();
    final BLSSignature expectedSignature = dataStructureUtil.randomSignature();
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signExecutionPayloadEnvelope(envelope, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignExecutionPayloadEnvelope(envelope, forkInfo),
            SignType.EXECUTION_PAYLOAD_ENVELOPE,
            Map.of("fork_info", forkInfo, "execution_payload_envelope", gloas(envelope)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
    verifyVersionedPayload(
        "execution_payload_envelope", "builder_index", envelope.getBuilderIndex().toString());
  }

  @Test
  void shouldSignPayloadAttestationData() throws Exception {
    final PayloadAttestationData payloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData();
    final BLSSignature expectedSignature = dataStructureUtil.randomSignature();
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signPayloadAttestationData(payloadAttestationData, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignPayloadAttestationData(
                payloadAttestationData, forkInfo),
            SignType.PAYLOAD_ATTESTATION_MESSAGE,
            Map.of(
                "fork_info",
                forkInfo,
                "payload_attestation_message",
                gloas(payloadAttestationData)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
    verifyVersionedPayload(
        "payload_attestation_message", "slot", payloadAttestationData.getSlot().toString());
  }

  @Test
  void shouldSignProposerPreferences() throws Exception {
    final ProposerPreferences proposerPreferences = dataStructureUtil.randomProposerPreferences();
    final BLSSignature expectedSignature = dataStructureUtil.randomSignature();
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response =
        externalSigner.signProposerPreferences(proposerPreferences, forkInfo).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignProposerPreferences(proposerPreferences, forkInfo),
            SignType.PROPOSER_PREFERENCES,
            Map.of("fork_info", forkInfo, "proposer_preferences", gloas(proposerPreferences)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
    verifyVersionedPayload(
        "proposer_preferences", "proposal_slot", proposerPreferences.getProposalSlot().toString());
  }

  @Test
  void shouldSignBuilderRequestAuth() throws Exception {
    final BuilderRequestAuth builderRequestAuth = dataStructureUtil.randomBuilderRequestAuth();
    final BLSSignature expectedSignature = dataStructureUtil.randomSignature();
    client.when(request()).respond(response().withBody(expectedSignature.toString()));

    final BLSSignature response = externalSigner.signBuilderRequestAuth(builderRequestAuth).join();
    assertThat(response).isEqualTo(expectedSignature);

    final SigningRequestBody signingRequestBody =
        new SigningRequestBody(
            signingRootUtil.signingRootForSignBuilderRequestAuth(builderRequestAuth),
            SignType.BUILDER_REQUEST_AUTH,
            Map.of(SignType.BUILDER_REQUEST_AUTH.getName(), gloas(builderRequestAuth)));
    verifySignRequest(
        client,
        KEYPAIR.getPublicKey().toString(),
        signingRequestBody,
        getSpec().getGenesisSchemaDefinitions());
    validateMetrics(metricsSystem, 1, 0, 0);
    verifyVersionedPayload("builder_request_auth", "slot", builderRequestAuth.getSlot().toString());
  }

  private static <T extends SszData> VersionedWrapper<T> gloas(final T data) {
    return new VersionedWrapper<>(SpecMilestone.GLOAS, data);
  }

  private void verifyVersionedPayload(
      final String key, final String dataField, final String expectedValue) throws Exception {
    final HttpRequest[] recordedRequests = client.retrieveRecordedRequests(request());
    assertThat(recordedRequests).hasSize(1);
    final JsonNode body = OBJECT_MAPPER.readTree(recordedRequests[0].getBodyAsString());
    final JsonNode payload = body.get(key);
    assertThat(payload).isNotNull();
    assertThat(payload.get("version").asText()).isEqualTo("GLOAS");
    assertThat(payload.get("data").isObject()).isTrue();
    assertThat(payload.get("data").get(dataField).asText()).isEqualTo(expectedValue);
  }
}

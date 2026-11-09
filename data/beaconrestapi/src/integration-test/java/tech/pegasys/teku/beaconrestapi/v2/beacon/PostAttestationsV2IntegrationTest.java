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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostAttestationsV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAttestationsV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  protected DataStructureUtil dataStructureUtil;
  protected SpecMilestone specMilestone;

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalPhase0();
    specMilestone = SpecMilestone.PHASE0;
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void shouldPostAttestations_NoErrors() throws Exception {
    final List<Attestation> attestations = getAttestationList(2);

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final Response response = postAttestations(attestations, specMilestone.name());

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @Test
  void shouldPostAttestationsAsSsz_NoErrors() throws Exception {
    final List<Attestation> attestations = getAttestationList(2);

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final Response response = postAttestationsAsSsz(attestations, specMilestone.name());

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @Test
  void shouldPartiallyPostAttestations_ReturnsErrors() throws Exception {
    final SubmitDataError firstSubmitDataError =
        new SubmitDataError(UInt64.ZERO, "Bad attestation");
    final SubmitDataError secondSubmitDataError =
        new SubmitDataError(UInt64.ONE, "Very bad attestation");

    final List<Attestation> attestations = getAttestationList(3);

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(
            SafeFuture.completedFuture(List.of(firstSubmitDataError, secondSubmitDataError)));

    final Response response = postAttestations(attestations, specMilestone.name());

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());

    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("Some items failed to publish, refer to errors for details");
    assertThat(resultAsJsonNode.get("failures").size()).isEqualTo(2);
    assertThat(resultAsJsonNode.get("failures").get(0).get("index").asText())
        .isEqualTo(firstSubmitDataError.index().toString());
    assertThat(resultAsJsonNode.get("failures").get(0).get("message").asText())
        .isEqualTo(firstSubmitDataError.message());
    assertThat(resultAsJsonNode.get("failures").get(1).get("index").asText())
        .isEqualTo(secondSubmitDataError.index().toString());
    assertThat(resultAsJsonNode.get("failures").get(1).get("message").asText())
        .isEqualTo(secondSubmitDataError.message());
  }

  @Test
  void shouldFailWhenMissingConsensusHeader() throws Exception {
    final List<Attestation> attestations = getAttestationList(2);

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final Response response = postAttestations(attestations, null);

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("Missing required header value for (%s)", HEADER_CONSENSUS_VERSION);
  }

  @Test
  void shouldFailWhenBadConsensusHeaderValue() throws Exception {
    final List<Attestation> attestations = getAttestationList(2);

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    final String badConsensusHeaderValue = "NonExistingMileStone";
    final Response response = postAttestations(attestations, badConsensusHeaderValue);

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo(
            String.format(
                "Invalid value for (%s) header: %s",
                HEADER_CONSENSUS_VERSION, badConsensusHeaderValue));
  }

  @Test
  void shouldPostAttestationsIfLowerCaseConsensusHeaderValueIsPassed() throws IOException {
    final List<Attestation> attestations = getAttestationList(2);

    when(validatorApiChannel.sendSignedAttestations(attestations))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final String postData = serializeAttestations(attestations);

    final Response response =
        post(
            PostAttestationsV2.ROUTE,
            postData,
            requestBuilder -> requestBuilder.header("eth-consensus-version", specMilestone.name()));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  protected List<Attestation> getAttestationList(final int listSize) {
    final List<Attestation> attestations = new ArrayList<>(listSize);
    for (int i = 0; i < listSize; i++) {
      attestations.add(dataStructureUtil.randomAttestation());
    }
    return attestations;
  }

  protected String serializeAttestations(final List<Attestation> attestations) throws IOException {
    final SerializableTypeDefinition<List<Attestation>> attestationsListTypeDef =
        SerializableTypeDefinition.listOf(getAttestationSchema().getJsonTypeDefinition());
    return JsonUtil.serialize(attestations, attestationsListTypeDef);
  }

  protected byte[] serializeAttestationsToSsz(final List<Attestation> attestations) {
    return SszListSchema.create(
            getAttestationSchema(),
            (long) specConfig.getMaxValidatorsPerCommittee() * specConfig.getMaxCommitteesPerSlot())
        .createFromElements(attestations)
        .sszSerialize()
        .toArrayUnsafe();
  }

  protected AttestationSchema<Attestation> getAttestationSchema() {
    return spec.getGenesisSchemaDefinitions().getAttestationSchema();
  }

  private Response postAttestations(final List<Attestation> attestations, final String milestone)
      throws IOException {
    if (milestone == null) {
      return post(PostAttestationsV2.ROUTE, serializeAttestations(attestations));
    }
    return post(
        PostAttestationsV2.ROUTE,
        serializeAttestations(attestations),
        Collections.emptyMap(),
        Optional.of(milestone));
  }

  private Response postAttestationsAsSsz(
      final List<Attestation> attestations, final String milestone) throws IOException {
    return postSsz(
        PostAttestationsV2.ROUTE,
        serializeAttestationsToSsz(attestations),
        Collections.emptyMap(),
        Optional.of(milestone));
  }
}

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

package tech.pegasys.teku.beaconrestapi.v2.validator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.validator.PostAggregateAndProofsV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class PostAggregateAndProofsV2IntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;
  private SerializableTypeDefinition<List<SignedAggregateAndProof>> aggregateAndProofsListTypeDef;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
    aggregateAndProofsListTypeDef =
        SerializableTypeDefinition.listOf(
            spec.getGenesisSchemaDefinitions()
                .getSignedAggregateAndProofSchema()
                .getJsonTypeDefinition());
  }

  @TestTemplate
  void shouldPostAggregateAndProofs() throws Exception {
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final List<SignedAggregateAndProof> aggregateAndProofs =
        List.of(
            dataStructureUtil.randomSignedAggregateAndProof(),
            dataStructureUtil.randomSignedAggregateAndProof());

    final Response response =
        post(
            PostAggregateAndProofsV2.ROUTE,
            JsonUtil.serialize(aggregateAndProofs, aggregateAndProofsListTypeDef),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldPostAggregateAndProofsAsSsz() throws Exception {
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final List<SignedAggregateAndProof> aggregateAndProofs =
        List.of(
            dataStructureUtil.randomSignedAggregateAndProof(),
            dataStructureUtil.randomSignedAggregateAndProof());

    final byte[] aggregateAndProofsBytes =
        SszListSchema.create(
                spec.getGenesisSchemaDefinitions().getSignedAggregateAndProofSchema(),
                (long) specConfig.getMaxCommitteesPerSlot()
                    * ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE)
            .createFromElements(aggregateAndProofs)
            .sszSerialize()
            .toArrayUnsafe();

    final Response response =
        postSsz(
            PostAggregateAndProofsV2.ROUTE,
            aggregateAndProofsBytes,
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldReturnBadRequestWhenInvalidAggregateAndProofs() throws Exception {
    final SubmitDataError firstSubmitDataError =
        new SubmitDataError(UInt64.ZERO, "Bad aggregate and proofs");
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of(firstSubmitDataError)));
    final List<SignedAggregateAndProof> aggregateAndProofs =
        List.of(
            dataStructureUtil.randomSignedAggregateAndProof(),
            dataStructureUtil.randomSignedAggregateAndProof());
    final Response response =
        post(
            PostAggregateAndProofsV2.ROUTE,
            JsonUtil.serialize(aggregateAndProofs, aggregateAndProofsListTypeDef),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText()).isEqualTo(PARTIAL_PUBLISH_FAILURE_MESSAGE);
  }

  @TestTemplate
  void shouldFailWhenMissingConsensusHeader() throws Exception {
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final List<SignedAggregateAndProof> aggregateAndProofs =
        List.of(
            dataStructureUtil.randomSignedAggregateAndProof(),
            dataStructureUtil.randomSignedAggregateAndProof());

    final Response response =
        post(
            PostAggregateAndProofsV2.ROUTE,
            JsonUtil.serialize(aggregateAndProofs, aggregateAndProofsListTypeDef));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo(
            String.format("Missing required header value for (%s)", HEADER_CONSENSUS_VERSION));
  }

  @TestTemplate
  void shouldFailWhenBadConsensusHeaderValue() throws Exception {
    when(validatorApiChannel.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));

    final List<SignedAggregateAndProof> aggregateAndProofs =
        List.of(
            dataStructureUtil.randomSignedAggregateAndProof(),
            dataStructureUtil.randomSignedAggregateAndProof());
    final String badConsensusHeaderValue = "NonExistingMileStone";
    final Response response =
        post(
            PostAggregateAndProofsV2.ROUTE,
            JsonUtil.serialize(aggregateAndProofs, aggregateAndProofsListTypeDef),
            Collections.emptyMap(),
            Optional.of(badConsensusHeaderValue));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo(
            String.format(
                "Invalid value for (%s) header: %s",
                HEADER_CONSENSUS_VERSION, badConsensusHeaderValue));
  }
}

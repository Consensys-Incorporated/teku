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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostProposerPreferences;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostProposerPreferencesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {
  private SignedProposerPreferencesSchema signedProposerPreferencesSchema;
  private DeserializableTypeDefinition<List<SignedProposerPreferences>> jsonListTypeDefinition;
  private int maxItems;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    dataStructureUtil = new DataStructureUtil(spec);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());
    signedProposerPreferencesSchema = schemaDefinitions.getSignedProposerPreferencesSchema();
    jsonListTypeDefinition =
        DeserializableTypeDefinition.listOf(
            signedProposerPreferencesSchema.getJsonTypeDefinition());
    final SpecConfigGloas specConfig =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
    maxItems = (specConfig.getMinSeedLookahead() + 1) * specConfig.getSlotsPerEpoch();
  }

  @Test
  void shouldReturnBadRequestForEmptyBody() throws IOException {
    try (Response response = post(PostProposerPreferences.ROUTE, "")) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verifyNoInteractions(validatorApiChannel);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "{", "[{}]"})
  void shouldReturnBadRequestForMalformedJson(final String body) throws IOException {
    try (Response response = post(PostProposerPreferences.ROUTE, body)) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  void shouldSubmitProposerPreferencesAsJson() throws IOException {
    final List<SignedProposerPreferences> preferences =
        List.of(dataStructureUtil.randomSignedProposerPreferences());
    when(validatorApiChannel.sendSignedProposerPreferences(preferences))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    try (Response response =
        post(
            PostProposerPreferences.ROUTE,
            JsonUtil.serialize(preferences, jsonListTypeDefinition),
            requestBuilder ->
                requestBuilder.header(
                    HEADER_CONSENSUS_VERSION,
                    SpecMilestone.GLOAS.name().toLowerCase(Locale.ROOT)))) {

      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.body().string()).isEmpty();
    }
  }

  @Test
  void shouldSubmitProposerPreferencesAsSsz() throws IOException {
    final List<SignedProposerPreferences> preferences =
        List.of(dataStructureUtil.randomSignedProposerPreferences());
    when(validatorApiChannel.sendSignedProposerPreferences(preferences))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final byte[] sszBytes =
        SszListSchema.create(signedProposerPreferencesSchema, maxItems)
            .createFromElements(preferences)
            .sszSerialize()
            .toArrayUnsafe();

    try (Response response =
        postSsz(
            PostProposerPreferences.ROUTE,
            sszBytes,
            Optional.of(SpecMilestone.GLOAS.name().toLowerCase(Locale.ROOT)))) {

      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.body().string()).isEmpty();
    }
  }

  @Test
  void shouldReturnIndexedRejectionDescription() throws Exception {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final String rejectionDescription = "Invalid proposer preferences signature";
    when(validatorApiChannel.sendSignedProposerPreferences(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(new SubmitDataError(UInt64.ZERO, rejectionDescription))));

    final JsonNode body;
    try (Response response =
        post(
            PostProposerPreferences.ROUTE,
            JsonUtil.serialize(List.of(preferences), jsonListTypeDefinition),
            requestBuilder ->
                requestBuilder.header(
                    HEADER_CONSENSUS_VERSION,
                    SpecMilestone.GLOAS.name().toLowerCase(Locale.ROOT)))) {

      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
      body = OBJECT_MAPPER.readTree(response.body().string());
    }

    assertThat(body.get("message").asText()).isEqualTo(PARTIAL_PUBLISH_FAILURE_MESSAGE);
    assertThat(body.get("failures").get(0).get("index").asText()).isEqualTo("0");
    assertThat(body.get("failures").get(0).get("message").asText()).isEqualTo(rejectionDescription);
  }
}

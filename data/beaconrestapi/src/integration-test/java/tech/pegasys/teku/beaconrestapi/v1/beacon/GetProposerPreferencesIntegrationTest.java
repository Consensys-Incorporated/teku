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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetProposerPreferences;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetProposerPreferencesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {
  private SignedProposerPreferencesSchema signedProposerPreferencesSchema;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    dataStructureUtil = new DataStructureUtil(spec);
    signedProposerPreferencesSchema =
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
            .getSignedProposerPreferencesSchema();
  }

  @Test
  void shouldReturnFilteredProposerPreferencesAsJson() throws IOException {
    final UInt64 slot = UInt64.valueOf(5);
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    final List<SignedProposerPreferences> preferences =
        List.of(dataStructureUtil.randomSignedProposerPreferences());
    when(proposerPreferencesManager.getSignedProposerPreferences(
            Optional.of(slot), Optional.of(dependentRoot)))
        .thenReturn(preferences);

    try (Response response =
        getResponse(
            GetProposerPreferences.ROUTE,
            Map.of("slot", slot.toString(), "dependent_root", dependentRoot.toHexString()))) {
      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.header(HEADER_CONSENSUS_VERSION))
          .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
      final DeserializableTypeDefinition<List<SignedProposerPreferences>> responseType =
          SharedApiTypes.withDataWrapper(
              "proposerPreferences",
              listOf(signedProposerPreferencesSchema.getJsonTypeDefinition()));
      assertThat(JsonUtil.parse(response.body().string(), responseType))
          .containsExactlyElementsOf(preferences);
    }
  }

  @Test
  void shouldReturnProposerPreferencesAsSsz() throws IOException {
    final List<SignedProposerPreferences> preferences =
        List.of(
            dataStructureUtil.randomSignedProposerPreferences(),
            dataStructureUtil.randomSignedProposerPreferences());
    when(proposerPreferencesManager.getSignedProposerPreferences(
            Optional.empty(), Optional.empty()))
        .thenReturn(preferences);

    try (Response response = getResponse(GetProposerPreferences.ROUTE, OCTET_STREAM)) {
      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.header(HEADER_CONSENSUS_VERSION))
          .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
      assertThat(
              SszProgressiveListSchema.create(signedProposerPreferencesSchema)
                  .sszDeserialize(Bytes.of(response.body().bytes()))
                  .asList())
          .containsExactlyElementsOf(preferences);
    }
  }

  @Test
  void shouldReturnEmptyListWhenNoProposerPreferencesKnown() throws IOException {
    when(proposerPreferencesManager.getSignedProposerPreferences(any(), any()))
        .thenReturn(List.of());

    try (Response response = getResponse(GetProposerPreferences.ROUTE)) {
      assertThat(response.code()).isEqualTo(SC_OK);
      assertThat(response.body().string()).isEqualTo("{\"version\":\"gloas\",\"data\":[]}");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"slot", "dependent_root"})
  void shouldReturnBadRequestForInvalidQueryParameter(final String parameter) throws IOException {
    try (Response response =
        getResponse(GetProposerPreferences.ROUTE, Map.of(parameter, "invalid"))) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verify(proposerPreferencesManager, never()).getSignedProposerPreferences(any(), any());
  }
}

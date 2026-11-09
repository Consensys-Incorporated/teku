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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

class GetProposerPreferencesTest extends AbstractMigratedBeaconHandlerTest {
  private static final Spec SPEC_WITH_LATER_GLOAS_FORK =
      TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE);
  private static final UInt64 GLOAS_FORK_SLOT =
      SPEC_WITH_LATER_GLOAS_FORK.computeStartSlotAtEpoch(UInt64.ONE);

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new GetProposerPreferences(nodeDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnProposerPreferencesAndMetaData() throws JsonProcessingException {
    final ObjectAndMetaData<List<SignedProposerPreferences>> responseWithMetaData =
        createResponse();
    when(nodeDataProvider.getProposerPreferencesAndMetaData(Optional.empty(), Optional.empty()))
        .thenReturn(responseWithMetaData);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseWithMetaData);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
  }

  @Test
  void shouldPassSlotAndDependentRootFilters() throws JsonProcessingException {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    final ObjectAndMetaData<List<SignedProposerPreferences>> responseWithMetaData =
        createResponse();
    request.setOptionalQueryParameter("slot", slot.toString());
    request.setOptionalQueryParameter("dependent_root", dependentRoot.toHexString());
    when(nodeDataProvider.getProposerPreferencesAndMetaData(
            Optional.of(slot), Optional.of(dependentRoot)))
        .thenReturn(responseWithMetaData);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseWithMetaData);
  }

  @Test
  void shouldReturnBadRequestForSlotPriorToGloasWithoutQueryingProvider() {
    final GetProposerPreferences handlerWithLaterGloasFork = createHandlerWithLaterGloasFork();
    request.setOptionalQueryParameter("slot", GLOAS_FORK_SLOT.minus(1).toString());

    assertThatThrownBy(() -> handlerWithLaterGloasFork.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Cannot request proposer preferences for slots prior to gloas fork.");
    verifyNoInteractions(nodeDataProvider);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION)).isNull();
  }

  @Test
  void shouldReturnProposerPreferencesForGloasForkSlot() throws JsonProcessingException {
    final GetProposerPreferences handlerWithLaterGloasFork = createHandlerWithLaterGloasFork();
    final UInt64 slot = GLOAS_FORK_SLOT;
    final ObjectAndMetaData<List<SignedProposerPreferences>> responseWithMetaData =
        createResponse();
    request.setOptionalQueryParameter("slot", slot.toString());
    when(nodeDataProvider.getProposerPreferencesAndMetaData(Optional.of(slot), Optional.empty()))
        .thenReturn(responseWithMetaData);

    handlerWithLaterGloasFork.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseWithMetaData);
  }

  @Test
  void shouldRejectInvalidDependentRoot() {
    request.setOptionalQueryParameter("dependent_root", "not-a-root");

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(nodeDataProvider);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final ObjectAndMetaData<List<SignedProposerPreferences>> responseWithMetaData =
        createResponse();

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseWithMetaData);

    assertThat(data).startsWith("{\"version\":\"gloas\",\"data\":[{\"message\":");
  }

  @Test
  void metadata_shouldHandle200Ssz() throws JsonProcessingException {
    final ObjectAndMetaData<List<SignedProposerPreferences>> responseWithMetaData =
        createResponse();

    final byte[] data = getResponseSszFromMetadata(handler, SC_OK, responseWithMetaData);

    final SszProgressiveListSchema<SignedProposerPreferences> listSchema =
        SszProgressiveListSchema.create(
            SchemaDefinitionsGloas.required(
                    schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
                .getSignedProposerPreferencesSchema());
    assertThat(listSchema.sszDeserialize(Bytes.of(data)).asList())
        .containsExactlyElementsOf(responseWithMetaData.getData());
  }

  private GetProposerPreferences createHandlerWithLaterGloasFork() {
    return new GetProposerPreferences(
        nodeDataProvider, new SchemaDefinitionCache(SPEC_WITH_LATER_GLOAS_FORK));
  }

  private ObjectAndMetaData<List<SignedProposerPreferences>> createResponse() {
    final List<SignedProposerPreferences> responseData =
        List.of(
            dataStructureUtil.randomSignedProposerPreferences(),
            dataStructureUtil.randomSignedProposerPreferences());
    return new ObjectAndMetaData<>(responseData, SpecMilestone.GLOAS, false, false, false);
  }
}

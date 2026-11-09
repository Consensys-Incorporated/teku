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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.validator.api.SubmitDataError;

class PostProposerPreferencesTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new PostProposerPreferences(validatorDataProvider, spec, schemaDefinitionCache));
  }

  @Test
  void shouldSubmitProposerPreferences() throws Exception {
    final List<SignedProposerPreferences> preferences =
        List.of(dataStructureUtil.randomSignedProposerPreferences());
    request.setRequestBody(preferences);
    when(validatorDataProvider.submitProposerPreferences(preferences))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnIndexedValidationErrors() throws Exception {
    final List<SignedProposerPreferences> preferences =
        List.of(dataStructureUtil.randomSignedProposerPreferences());
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(UInt64.ZERO, "Invalid proposer preferences signature"));
    final ErrorListBadRequest expectedResponse =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);
    request.setRequestBody(preferences);
    when(validatorDataProvider.submitProposerPreferences(preferences))
        .thenReturn(SafeFuture.completedFuture(errors));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldReadJsonRequestBody() throws IOException {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final String json =
        JsonUtil.serialize(
            List.of(preferences),
            DeserializableTypeDefinition.listOf(preferences.getSchema().getJsonTypeDefinition()));

    final Object requestBody =
        getRequestBodyFromMetadata(
            handler, Map.of(HEADER_CONSENSUS_VERSION, SpecMilestone.GLOAS.name()), json);

    assertThat(requestBody).isInstanceOf(List.class);
    assertThat(((List<?>) requestBody).getFirst()).isInstanceOf(SignedProposerPreferences.class);
  }

  @Test
  void shouldRejectJsonRequestBodyExceedingMaximumItems() throws Exception {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final SpecConfigGloas specConfig =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
    final int maxItems = (specConfig.getMinSeedLookahead() + 1) * specConfig.getSlotsPerEpoch();
    final String json =
        JsonUtil.serialize(
            Collections.nCopies(maxItems + 1, preferences),
            DeserializableTypeDefinition.listOf(preferences.getSchema().getJsonTypeDefinition()));

    request.setRequestBody(
        getRequestBodyFromMetadata(
            handler, Map.of(HEADER_CONSENSUS_VERSION, SpecMilestone.GLOAS.name()), json));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new HttpErrorResponse(
                SC_BAD_REQUEST,
                "A maximum of "
                    + maxItems
                    + " SignedProposerPreferences objects can be submitted to the node at one time"));
    verifyNoInteractions(validatorDataProvider);
  }

  @Test
  void shouldReadSszRequestBody() throws Exception {
    final List<SignedProposerPreferences> preferences =
        List.of(
            dataStructureUtil.randomSignedProposerPreferences(),
            dataStructureUtil.randomSignedProposerPreferences());
    final SpecConfigGloas specConfig =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
    final int maxItems = (specConfig.getMinSeedLookahead() + 1) * specConfig.getSlotsPerEpoch();
    final SignedProposerPreferencesSchema schema = preferences.getFirst().getSchema();
    final byte[] sszBytes =
        SszListSchema.create(schema, maxItems)
            .createFromElements(preferences)
            .sszSerialize()
            .toArrayUnsafe();

    final Object requestBody =
        handler
            .getMetadata()
            .getRequestBody(
                new ByteArrayInputStream(sszBytes), Optional.of(ContentTypes.OCTET_STREAM));

    assertThat(requestBody).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    final List<SignedProposerPreferences> parsedPreferences =
        (List<SignedProposerPreferences>) requestBody;
    assertThat(parsedPreferences).containsExactlyElementsOf(preferences);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(UInt64.ZERO, "Invalid proposer preferences signature"));
    final ErrorListBadRequest responseData =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    assertThat(getResponseStringFromMetadata(handler, SC_BAD_REQUEST, responseData)).isNotEmpty();
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle415() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }
}

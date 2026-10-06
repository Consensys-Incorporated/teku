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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesEntry;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.SubmitDataError;

class PostBuilderPreferencesTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setHandler(new PostBuilderPreferences(validatorDataProvider));
  }

  @Test
  void shouldSubmitBuilderPreferences() throws Exception {
    final SszList<BuilderPreferencesEntry> entries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(
            List.of(dataStructureUtil.randomBuilderPreferencesEntry()));
    request.setRequestBody(entries);
    when(validatorDataProvider.submitBuilderPreferences(entries))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnIndexedValidationErrors() throws Exception {
    final SszList<BuilderPreferencesEntry> entries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(
            List.of(dataStructureUtil.randomBuilderPreferencesEntry()));
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(UInt64.ZERO, "Invalid builder preferences entry"));
    final ErrorListBadRequest expectedResponse =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);
    request.setRequestBody(entries);
    when(validatorDataProvider.submitBuilderPreferences(entries))
        .thenReturn(SafeFuture.completedFuture(errors));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldReadJsonRequestBody() throws IOException {
    final SszList<BuilderPreferencesEntry> entries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(
            List.of(dataStructureUtil.randomBuilderPreferencesEntry()));
    final String json =
        JsonUtil.serialize(
            entries, ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.getJsonTypeDefinition());

    final Object requestBody = getRequestBodyFromMetadata(handler, Map.of(), json);

    assertThat(requestBody).isEqualTo(entries);
  }

  @Test
  void shouldReadSszRequestBody() throws Exception {
    final SszList<BuilderPreferencesEntry> entries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(
            List.of(
                dataStructureUtil.randomBuilderPreferencesEntry(),
                dataStructureUtil.randomBuilderPreferencesEntry()));
    final byte[] sszBytes = entries.sszSerialize().toArrayUnsafe();

    final Object requestBody =
        handler
            .getMetadata()
            .getRequestBody(
                new ByteArrayInputStream(sszBytes), Optional.of(ContentTypes.OCTET_STREAM));

    assertThat(requestBody).isEqualTo(entries);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(UInt64.ZERO, "Invalid builder preferences entry"));
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

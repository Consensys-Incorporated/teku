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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult.success;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;

public class DeleteKeysTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());
  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private Path tempDir;
  private DeleteKeys handler;

  private final DeleteKeysRequest requestData = new DeleteKeysRequest();
  private final RestApiRequest request = mock(RestApiRequest.class);

  @BeforeEach
  void setup() throws IOException {
    tempDir = Files.createTempDirectory("teku_unit_test");
    assertTrue(tempDir.toFile().mkdirs() || tempDir.toFile().isDirectory());
    tempDir.toFile().deleteOnExit();
    handler = new DeleteKeys(keyManager, tempDir);
  }

  @Test
  void shouldCallKeyManagerWithListOfKeys() throws JsonProcessingException {
    final List<BLSPublicKey> keys =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    requestData.setPublicKeys(keys);
    when(keyManager.deleteValidators(eq(keys), any()))
        .thenReturn(new DeleteKeysResponse(List.of(success(), success()), ""));
    when(request.getRequestBody()).thenReturn(requestData);

    handler.handleRequest(request);
    verify(keyManager).deleteValidators(eq(keys), any());
    verify(request, never()).respondError(anyInt(), any());
    verify(request, times(1)).respondOk(any(DeleteKeysResponse.class));
  }

  @Test
  void shouldReturnSuccessIfNoKeysArePassed() throws JsonProcessingException {
    when(request.getRequestBody()).thenReturn(requestData);
    when(keyManager.deleteValidators(any(), any()))
        .thenReturn(new DeleteKeysResponse(List.of(), ""));

    handler.handleRequest(request);
    verify(keyManager).deleteValidators(eq(Collections.emptyList()), any());
    verify(request, never()).respondError(anyInt(), any());
    verify(request, times(1)).respondOk(any(DeleteKeysResponse.class));
  }

  @Test
  void shouldReturnBadRequest() throws JsonProcessingException {
    when(request.getRequestBody()).thenThrow(new MissingRequestBodyException());

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(MissingRequestBodyException.class);
    verify(keyManager, never()).deleteValidators(any(), any());
    verify(request, never()).respondOk(any());
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final List<DeleteKeyResult> deleteKeyResults =
        List.of(DeleteKeyResult.success(), DeleteKeyResult.success());
    final String slashingData = "{slashingData}";
    final String responseData =
        getResponseStringFromMetadata(
            handler, SC_OK, new DeleteKeysResponse(deleteKeyResults, slashingData));
    assertThat(responseData)
        .isEqualTo(
            "{\"data\":[{\"status\":\"deleted\"},{\"status\":\"deleted\"}],\"slashing_protection\":\"{slashingData}\"}");
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle401() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_UNAUTHORIZED);
  }

  @Test
  void metadata_shouldHandle403() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_FORBIDDEN);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }
}

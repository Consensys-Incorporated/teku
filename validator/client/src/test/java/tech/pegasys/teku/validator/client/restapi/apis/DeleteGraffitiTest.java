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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.GraffitiManagementException;
import tech.pegasys.teku.validator.api.GraffitiManager;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.Validator;

class DeleteGraffitiTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();

  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private final GraffitiManager graffitiManager = mock(GraffitiManager.class);
  private final DeleteGraffiti handler = new DeleteGraffiti(keyManager, graffitiManager);
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .metadata(handler.getMetadata())
          .pathParameter("pubkey", publicKey.toHexString())
          .build();

  @Test
  void shouldSuccessfullyDeleteGraffiti() throws JsonProcessingException {
    final Validator validator = new Validator(publicKey, NO_OP_SIGNER, Optional::empty);
    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.of(validator));

    handler.handleRequest(request);

    verify(graffitiManager).deleteGraffiti(eq(publicKey));
    assertThat(request.getResponseCode()).isEqualTo(SC_NO_CONTENT);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnErrorWhenIssueDeletingGraffiti()
      throws IOException, GraffitiManagementException {
    final GraffitiManagementException exception =
        new GraffitiManagementException("Unable to delete graffiti for validator " + publicKey);
    final Validator validator = new Validator(publicKey, NO_OP_SIGNER, Optional::empty);
    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.of(validator));
    doThrow(exception).when(graffitiManager).deleteGraffiti(any());

    handler.handleRequest(request);

    verify(graffitiManager).deleteGraffiti(eq(publicKey));
    assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_INTERNAL_SERVER_ERROR, exception.getMessage()));
  }

  @Test
  void shouldRespondNotFoundWhenNoValidator() throws JsonProcessingException {
    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.empty());

    handler.handleRequest(request);

    verifyNoInteractions(graffitiManager);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
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

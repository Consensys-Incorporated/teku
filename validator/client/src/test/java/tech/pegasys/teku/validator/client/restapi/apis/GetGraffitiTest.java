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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.GraffitiManagementException;
import tech.pegasys.teku.validator.api.UpdatableGraffitiProvider;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.Validator;

class GetGraffitiTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final String stringGraffiti = "Test graffiti";
  private final Bytes32 bytesGraffiti = Bytes32Parser.toBytes32(stringGraffiti);
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();

  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private final GetGraffiti handler = new GetGraffiti(keyManager);
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .metadata(handler.getMetadata())
          .pathParameter("pubkey", publicKey.toHexString())
          .build();

  @Test
  void shouldGetGraffiti() throws JsonProcessingException {
    checkGraffiti(Optional.of(bytesGraffiti));
  }

  @Test
  void shouldGetEmptyGraffiti() throws JsonProcessingException {
    checkGraffiti(Optional.empty());
  }

  @Test
  void shouldHandleGraffitiManagementException() throws JsonProcessingException {
    final GraffitiManagementException exception =
        new GraffitiManagementException("Unable to retrieve graffiti from storage");
    final UpdatableGraffitiProvider provider = mock(UpdatableGraffitiProvider.class);
    doThrow(exception).when(provider).getUnsafe();
    final Validator validator = new Validator(publicKey, NO_OP_SIGNER, provider);
    when(keyManager.getValidatorByPublicKey(eq(publicKey))).thenReturn(Optional.of(validator));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_INTERNAL_SERVER_ERROR, exception.getMessage()));
    verify(provider).getUnsafe();
  }

  @Test
  void shouldHandleValidatorNotFound() throws IOException {
    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Validator not found"));
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String responseData =
        getResponseStringFromMetadata(handler, SC_OK, Optional.of(bytesGraffiti));
    final String expectedResponse =
        String.format("{\"data\":{\"graffiti\":\"%s\"}}", stringGraffiti);
    assertThat(responseData).isEqualTo(expectedResponse);
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

  private void checkGraffiti(final Optional<Bytes32> graffiti) throws JsonProcessingException {
    final UpdatableGraffitiProvider provider = mock(UpdatableGraffitiProvider.class);
    when(provider.getUnsafe()).thenReturn(graffiti);
    final Validator validator = new Validator(publicKey, NO_OP_SIGNER, provider);
    when(keyManager.getValidatorByPublicKey(eq(publicKey))).thenReturn(Optional.of(validator));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(graffiti);
    verify(provider).getUnsafe();
  }
}

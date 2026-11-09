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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostRemoteKeysRequest;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class PostRemoteKeysTest {
  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private final RestApiRequest request = mock(RestApiRequest.class);
  private final SlashingRiskAction doppelgangerDetectionAction = mock(SlashingRiskAction.class);
  final PostRemoteKeys handler =
      new PostRemoteKeys(keyManager, Optional.empty(), doppelgangerDetectionAction);

  @Test
  void emptyRequest_shouldGiveEmptySuccess() throws JsonProcessingException {
    final PostRemoteKeysRequest body = new PostRemoteKeysRequest();
    when(request.getRequestBody()).thenReturn(body);

    handler.handleRequest(request);
    verify(request).respondOk(List.of());
  }

  @Test
  void validResponse_shouldGiveValidPostKeyResults()
      throws JsonProcessingException, MalformedURLException {

    List<ExternalValidator> externalValidators =
        List.of(
            new ExternalValidator(
                BLSTestUtil.randomKeyPair(1).getPublicKey(),
                Optional.of(URI.create("http://host.com").toURL())),
            new ExternalValidator(BLSTestUtil.randomKeyPair(2).getPublicKey(), Optional.empty()));
    final PostRemoteKeysRequest body = new PostRemoteKeysRequest(externalValidators);
    when(request.getRequestBody()).thenReturn(body);

    List<PostKeyResult> results = List.of(PostKeyResult.success(), PostKeyResult.success());
    when(keyManager.importExternalValidators(
            externalValidators, Optional.empty(), doppelgangerDetectionAction))
        .thenReturn(results);

    handler.handleRequest(request);
    verify(request).respondOk(results);
  }

  @Test
  void duplicate_shouldGiveDuplicateResponse()
      throws JsonProcessingException, MalformedURLException {

    BLSPublicKey publicKey = BLSTestUtil.randomKeyPair(1).getPublicKey();
    URL url = URI.create("http://host.com").toURL();

    List<ExternalValidator> externalValidators =
        List.of(
            new ExternalValidator(publicKey, Optional.of(url)),
            new ExternalValidator(publicKey, Optional.of(url)));
    final PostRemoteKeysRequest body = new PostRemoteKeysRequest(externalValidators);
    when(request.getRequestBody()).thenReturn(body);

    List<PostKeyResult> results = List.of(PostKeyResult.success(), PostKeyResult.duplicate());
    when(keyManager.importExternalValidators(
            externalValidators, Optional.empty(), doppelgangerDetectionAction))
        .thenReturn(results);

    handler.handleRequest(request);
    verify(request).respondOk(results);
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

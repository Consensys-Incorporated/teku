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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.generator.signatures.NoOpRemoteSigner.NO_OP_REMOTE_SIGNER;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;

class GetRemoteKeysTest {
  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private final GetRemoteKeys handler = new GetRemoteKeys(keyManager);
  private final RestApiRequest request = mock(RestApiRequest.class);
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalCapella());

  @Test
  void shouldListValidatorKeys() throws Exception {
    final List<ExternalValidator> remoteValidatorList =
        getValidatorList().stream()
            .map(
                val ->
                    new ExternalValidator(
                        val.getPublicKey(),
                        val.getSigner().getSigningServiceUrl(),
                        val.isReadOnly()))
            .collect(Collectors.toList());
    when(keyManager.getRemoteValidatorKeys()).thenReturn(remoteValidatorList);
    handler.handleRequest(request);

    verify(request).respondOk(remoteValidatorList);
  }

  @Test
  void shouldListEmptyValidatorKeys() throws Exception {
    final List<ExternalValidator> remoteValidatorList = Collections.emptyList();
    when(keyManager.getRemoteValidatorKeys()).thenReturn(remoteValidatorList);
    handler.handleRequest(request);

    verify(request).respondOk(Collections.emptyList());
  }

  private List<Validator> getValidatorList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_REMOTE_SIGNER, Optional::empty, false);
    Validator validator2 =
        new Validator(keyPair2.getPublicKey(), NO_OP_REMOTE_SIGNER, Optional::empty, false);
    return Arrays.asList(validator1, validator2);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final List<ExternalValidator> validators = new ArrayList<>();
    final BLSPublicKey key = dataStructureUtil.randomPublicKey();
    validators.add(new ExternalValidator(key, Optional.empty()));
    final String responseData = getResponseStringFromMetadata(handler, SC_OK, validators);
    assertThat(responseData)
        .isEqualTo("{\"data\":[{\"pubkey\":\"" + key + "\",\"readonly\":false}]}");
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

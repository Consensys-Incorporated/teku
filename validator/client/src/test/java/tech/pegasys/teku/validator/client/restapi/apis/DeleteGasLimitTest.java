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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

public class DeleteGasLimitTest {

  private final ProposerConfigManager proposerConfigManager = mock(ProposerConfigManager.class);
  private final DeleteGasLimit handler = new DeleteGasLimit(Optional.of(proposerConfigManager));
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .metadata(handler.getMetadata())
          .pathParameter("pubkey", publicKey.toBytesCompressed().toHexString())
          .build();

  @Test
  void shouldReturnFailureWhenKeyNotFound() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(any())).thenReturn(false);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldReturnForbiddenWhenDeleteFails() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(any())).thenReturn(true);
    when(proposerConfigManager.getGasLimit(any())).thenReturn(dataStructureUtil.randomUInt64());
    when(proposerConfigManager.deleteGasLimit(any())).thenReturn(false);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_FORBIDDEN);
  }

  @Test
  void shouldReturnSuccessWhenDeleteSucceeds() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(any())).thenReturn(true);
    when(proposerConfigManager.getGasLimit(any())).thenReturn(dataStructureUtil.randomUInt64());
    when(proposerConfigManager.deleteGasLimit(any())).thenReturn(true);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NO_CONTENT);
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
  void metadata_shouldHandle403() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_FORBIDDEN);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }
}

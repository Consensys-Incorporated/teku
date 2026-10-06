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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

public class SetGasLimitTest {

  private final ProposerConfigManager proposerConfigManager = mock(ProposerConfigManager.class);
  private final SetGasLimit handler = new SetGasLimit(Optional.of(proposerConfigManager));

  private final StubRestApiRequest request = new StubRestApiRequest(handler.getMetadata());

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final UInt64 gasLimit = dataStructureUtil.randomUInt64();

  @Test
  void badPubkey_shouldGiveIllegalArgument() {
    request.setPathParameter("pubkey", "pubkey");
    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
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
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle202() {
    verifyMetadataEmptyResponse(handler, SC_ACCEPTED);
  }

  @Test
  void shouldReturnNotFoundWhenValidatorIsNotOwned() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    when(proposerConfigManager.isOwnedValidator(publicKey)).thenReturn(false);
    request.setPathParameter("pubkey", publicKey.toBytesCompressed().toHexString());
    request.setRequestBody(new SetGasLimit.SetGasLimitBody(gasLimit));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldShareContextIfBellatrixNotEnabled() {
    request.setPathParameter("pubkey", dataStructureUtil.randomPublicKey().toString());
    request.setRequestBody(new SetGasLimit.SetGasLimitBody(gasLimit));
    assertThatThrownBy(
            () -> {
              SetGasLimit handler = new SetGasLimit(Optional.empty());
              handler.handleRequest(request);
            })
        .hasMessageContaining(
            "Bellatrix is not currently scheduled on this network, unable to set gas limit");
  }

  @Test
  void metadata_shouldReadRequestBody() throws IOException {
    SetGasLimit.SetGasLimitBody body =
        (SetGasLimit.SetGasLimitBody)
            getRequestBodyFromMetadata(handler, "{\"gas_limit\":\"" + gasLimit + "\"}");
    assertThat(body).isEqualTo(new SetGasLimit.SetGasLimitBody(gasLimit));
  }

  @Test
  void metadata_shouldThrowIllegalArgumentException() {
    assertThatThrownBy(() -> getRequestBodyFromMetadata(handler, "{\"gas_limit\":\"-45000\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldReturnBadRequestWhenGasLimitIsZero() throws JsonProcessingException {
    request.setPathParameter("pubkey", dataStructureUtil.randomPublicKey().toString());
    request.setRequestBody(new SetGasLimit.SetGasLimitBody(UInt64.ZERO));
    handler.handleRequest(request);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new HttpErrorResponse(
                SC_BAD_REQUEST,
                "Gas limit cannot be set to 0. It must match the regex: ^[1-9][0-9]{0,19}$"));
  }
}

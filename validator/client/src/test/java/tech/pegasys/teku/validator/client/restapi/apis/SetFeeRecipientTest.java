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
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

public class SetFeeRecipientTest {
  private final ProposerConfigManager proposerConfigManager = mock(ProposerConfigManager.class);
  private final SetFeeRecipient handler = new SetFeeRecipient(Optional.of(proposerConfigManager));

  private final StubRestApiRequest request = new StubRestApiRequest(handler.getMetadata());

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void badPubkey_shouldGiveIllegalArgument() {
    request.setPathParameter("pubkey", "pubkey");
    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void metadata_shouldHandle202() {
    verifyMetadataEmptyResponse(handler, SC_ACCEPTED);
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
  void shouldReturnNotFoundWhenValidatorIsNotOwned() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    when(proposerConfigManager.isOwnedValidator(publicKey)).thenReturn(false);
    request.setPathParameter("pubkey", publicKey.toBytesCompressed().toHexString());
    request.setRequestBody(
        new SetFeeRecipient.SetFeeRecipientBody(dataStructureUtil.randomEth1Address()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldShareContextIfBellatrixNotEnabled() {
    request.setPathParameter("pubkey", dataStructureUtil.randomPublicKey().toString());
    request.setRequestBody(
        new SetFeeRecipient.SetFeeRecipientBody(dataStructureUtil.randomEth1Address()));
    assertThatThrownBy(
            () -> {
              SetFeeRecipient handler = new SetFeeRecipient(Optional.empty());
              handler.handleRequest(request);
            })
        .hasMessageContaining("Bellatrix is not currently scheduled");
  }

  @Test
  void metadata_shouldReadRequestBody() throws IOException {
    SetFeeRecipient.SetFeeRecipientBody body =
        (SetFeeRecipient.SetFeeRecipientBody)
            getRequestBodyFromMetadata(
                handler, "{\"ethaddress\":\"0xabcf8e0d4e9587369b2301d0790347320302cc09\"}");
    assertThat(body)
        .isEqualTo(
            new SetFeeRecipient.SetFeeRecipientBody(
                Eth1Address.fromHexString("0xabcf8e0d4e9587369b2301d0790347320302cc09")));
  }

  @Test
  void metadata_shoulThrowInvalidArgument() {
    assertThatThrownBy(
            () ->
                getRequestBodyFromMetadata(
                    handler, "{\"ethaddress\":\"0xabcF8e0d4E9587369b2301d0790347320302CC09\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.ProposerConfigManager;

class GetFeeRecipientTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ProposerConfigManager proposerConfigManager = mock(ProposerConfigManager.class);
  private final GetFeeRecipient handler = new GetFeeRecipient(Optional.of(proposerConfigManager));
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .metadata(handler.getMetadata())
          .pathParameter("pubkey", publicKey.toBytesCompressed().toHexString())
          .build();

  @Test
  void shouldEncodePubkey() throws JsonProcessingException {
    final String keyString = JsonUtil.serialize(publicKey, PARAM_PUBKEY_TYPE.getType());
    assertThat(keyString).isEqualTo("\"" + publicKey.toBytesCompressed().toHexString() + "\"");
  }

  @Test
  void shouldDecodePubkey() {
    final BLSPublicKey pubkey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    assertThat(pubkey).isEqualTo(publicKey);
  }

  @Test
  void shouldRespondNotFound() throws JsonProcessingException {
    when(proposerConfigManager.getFeeRecipient(any())).thenReturn(Optional.empty());
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String responseData =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new GetFeeRecipient.GetFeeRecipientResponse(
                dataStructureUtil.randomEth1Address(),
                Optional.of(dataStructureUtil.randomPublicKey())));
    assertThat(responseData)
        .isEqualTo(
            "{\"data\":{\"ethaddress\":\"0x367CbD40AC7318427aAdB97345a91FA2e965DAf3\",\"pubkey\":\"0xb3f3faa8dfa1030714559b95cb0107e53c9ee9c6f2b4b11f29e60417dbc4462052ff2d2dbbe98d808e3093858a3acdcc\"}}");
  }

  @Test
  void metadata_shouldHandle200RequiredFieldsOnly() throws JsonProcessingException {
    final String responseData =
        getResponseStringFromMetadata(
            handler,
            SC_OK,
            new GetFeeRecipient.GetFeeRecipientResponse(dataStructureUtil.randomEth1Address()));
    assertThat(responseData)
        .isEqualTo("{\"data\":{\"ethaddress\":\"0x367CbD40AC7318427aAdB97345a91FA2e965DAf3\"}}");
  }
}

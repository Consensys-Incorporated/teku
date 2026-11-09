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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.ResolvedBuilderConfig;
import tech.pegasys.teku.validator.client.ResolvedBuilderConfig.ResolvedBuilderEntry;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;

public class GetBuilderConfigTest {

  private static final String BUILDER_URL = "https://builder.example.com";

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ProposerConfigManager proposerConfigManager = mock(ProposerConfigManager.class);

  private final GetBuilderConfig handler = new GetBuilderConfig(Optional.of(proposerConfigManager));
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .metadata(handler.getMetadata())
          .pathParameter("pubkey", publicKey.toBytesCompressed().toHexString())
          .build();

  @Test
  void shouldReturnNotFoundWhenValidatorIsNotOwned() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(any())).thenReturn(false);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldRespondOkWithResolvedBuilderConfig()
      throws JsonProcessingException, MalformedURLException {
    final Bytes authData = dataStructureUtil.randomBytes(8);
    final List<BLSPublicKey> builderPubkeys = List.of(dataStructureUtil.randomPublicKey());
    when(proposerConfigManager.isOwnedValidator(any())).thenReturn(true);
    when(proposerConfigManager.resolveBuilderConfig(publicKey))
        .thenReturn(
            new ResolvedBuilderConfig(
                UInt64.ONE,
                UInt64.valueOf(100),
                List.of(
                    new ResolvedBuilderEntry(
                        URI.create(BUILDER_URL).toURL(),
                        authData,
                        builderPubkeys,
                        UInt64.valueOf(3),
                        UInt64.valueOf(2),
                        UInt64.valueOf(110)))));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new BuilderConfig(
                Optional.of(UInt64.ONE),
                Optional.of(UInt64.valueOf(100)),
                Optional.of(
                    List.of(
                        new BuilderEntry(
                            BUILDER_URL,
                            Optional.of(authData),
                            Optional.of(builderPubkeys),
                            Optional.of(UInt64.valueOf(3)),
                            Optional.of(UInt64.valueOf(2)),
                            Optional.of(UInt64.valueOf(110)))))));
  }

  @Test
  void shouldShareContextIfBellatrixNotEnabled() {
    final GetBuilderConfig handler = new GetBuilderConfig(Optional.empty());
    assertThatThrownBy(() -> handler.handleRequest(request))
        .hasMessageContaining(
            "Bellatrix is not currently scheduled on this network, unable to get builder config");
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final BuilderConfig builderConfig =
        new BuilderConfig(
            Optional.of(UInt64.ONE),
            Optional.of(UInt64.valueOf(100)),
            Optional.of(
                List.of(
                    new BuilderEntry(
                        BUILDER_URL,
                        Optional.empty(),
                        Optional.of(List.of()),
                        Optional.of(UInt64.ZERO),
                        Optional.of(UInt64.ONE),
                        Optional.of(UInt64.valueOf(100))))));
    final String responseData = getResponseStringFromMetadata(handler, SC_OK, builderConfig);
    assertThat(responseData)
        .isEqualTo(
            "{\"data\":{\"min_bid\":\"1\",\"builder_boost_factor\":\"100\",\"builders\":[{\"url\":\""
                + BUILDER_URL
                + "\",\"builder_pubkeys\":[],\"max_execution_payment\":\"0\",\"min_bid\":\"1\",\"builder_boost_factor\":\"100\"}]}}");
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
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

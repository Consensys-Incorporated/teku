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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequiredFieldException;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.ProposerConfigManager;
import tech.pegasys.teku.validator.client.SetBuilderConfigException;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;

public class SetBuilderConfigTest {

  private static final String BUILDER_URL = "https://builder.example.com";

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ProposerConfigManager proposerConfigManager = mock(ProposerConfigManager.class);
  private final SetBuilderConfig handler = new SetBuilderConfig(Optional.of(proposerConfigManager));

  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .metadata(handler.getMetadata())
          .pathParameter("pubkey", publicKey.toBytesCompressed().toHexString())
          .build();

  private final BuilderConfig builderConfig =
      new BuilderConfig(
          Optional.of(UInt64.ONE),
          Optional.empty(),
          Optional.of(
              List.of(
                  new BuilderEntry(
                      BUILDER_URL,
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(UInt64.ZERO),
                      Optional.empty(),
                      Optional.empty()))));

  @Test
  void shouldReturnNotFoundWhenValidatorIsNotOwned() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(publicKey)).thenReturn(false);
    request.setRequestBody(builderConfig);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldSetBuilderConfig() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(publicKey)).thenReturn(true);
    request.setRequestBody(builderConfig);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_ACCEPTED);
    verify(proposerConfigManager).setBuilderConfig(publicKey, builderConfig);
  }

  @Test
  void shouldReturnBadRequestWhenBuilderConfigCannotBeSet() throws JsonProcessingException {
    when(proposerConfigManager.isOwnedValidator(publicKey)).thenReturn(true);
    doThrow(new SetBuilderConfigException("Cannot update builder config via api."))
        .when(proposerConfigManager)
        .setBuilderConfig(any(), any());
    request.setRequestBody(builderConfig);

    handler.handleRequest(request);

    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_BAD_REQUEST, "Cannot update builder config via api."));
  }

  @Test
  void shouldShareContextIfBellatrixNotEnabled() {
    final SetBuilderConfig handler = new SetBuilderConfig(Optional.empty());
    request.setRequestBody(builderConfig);
    assertThatThrownBy(() -> handler.handleRequest(request))
        .hasMessageContaining(
            "Bellatrix is not currently scheduled on this network, unable to set builder config");
  }

  @Test
  void metadata_shouldReadRequestBody() throws IOException {
    final Object body =
        getRequestBodyFromMetadata(
            handler,
            "{\"min_bid\":\"1\",\"builders\":[{\"url\":\""
                + BUILDER_URL
                + "\",\"max_execution_payment\":\"0\"}]}");
    assertThat(body).isEqualTo(builderConfig);
  }

  @Test
  void metadata_shouldReadEmptyRequestBody() throws IOException {
    final Object body = getRequestBodyFromMetadata(handler, "{}");
    assertThat(body)
        .isEqualTo(new BuilderConfig(Optional.empty(), Optional.empty(), Optional.empty()));
  }

  @Test
  void metadata_shouldRejectBuilderWithoutUrl() {
    assertThatThrownBy(
            () -> getRequestBodyFromMetadata(handler, "{\"builders\":[{\"min_bid\":\"1\"}]}"))
        .isInstanceOf(MissingRequiredFieldException.class)
        .hasMessage("required fields: (url) were not set");
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
}

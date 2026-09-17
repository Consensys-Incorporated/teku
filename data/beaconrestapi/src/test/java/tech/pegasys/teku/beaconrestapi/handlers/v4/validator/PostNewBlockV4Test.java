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

package tech.pegasys.teku.beaconrestapi.handlers.v4.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BUILDER_URL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.OpenApiTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;

@TestSpecContext(allMilestones = true)
public class PostNewBlockV4Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;
  protected final BLSSignature signature = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    setSpec(specContext.getSpec());
    specMilestone = specContext.getSpecMilestone();
    setHandler(new PostNewBlockV4(validatorDataProvider, schemaDefinitionCache));
    request.setPathParameter(SLOT, "1");
    request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());
    when(validatorDataProvider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(specMilestone);
  }

  @TestTemplate
  void shouldReturnBadRequestForPreGloasFork() throws Exception {
    assumeThat(specMilestone).isLessThan(GLOAS);
    request.setQueryParameter(INCLUDE_PAYLOAD, "false");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
  }

  @TestTemplate
  void shouldHandleWhenBlockContainerAndMetaDataIsProduced() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);
    request.setQueryParameter(INCLUDE_PAYLOAD, "true");
    final String builderUrl = "https://example.com";
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE).toBuilder()
            .payloadIncluded(true)
            .builderUrl(Optional.of(builderUrl))
            .build();
    request.setRequestBody(BuilderConfig.NO_OP);

    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty(), true, BuilderConfig.NO_OP);

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(blockContainerAndMetaData.milestone().lowerCaseName());
    assertThat(request.getResponseHeaders(HEADER_INCLUDE_PAYLOAD)).isEqualTo("true");
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(blockContainerAndMetaData.consensusBlockValue().toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockContainerAndMetaData.executionPayloadValue().toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_BUILDER_URL)).isEqualTo(builderUrl);
  }

  @TestTemplate
  void shouldDeclareIncludePayloadAsRequired() throws Exception {
    // a missing include_payload is rejected by the rest api framework rather than by the handler,
    // so all the handler has to guarantee is that the parameter is declared as required
    final JsonNode metadata =
        JsonTestUtil.parseAsJsonNode(OpenApiTestUtil.serializeEndpointMetadata(handler));
    final JsonNode includePayloadParameter =
        StreamSupport.stream(metadata.get("post").get("parameters").spliterator(), false)
            .filter(parameter -> INCLUDE_PAYLOAD.equals(parameter.get("name").asText()))
            .findFirst()
            .orElseThrow();

    assertThat(includePayloadParameter.get("required").asBoolean()).isTrue();
  }

  @TestTemplate
  void shouldThrowExceptionWhenEmptyBlock() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);
    request.setQueryParameter(INCLUDE_PAYLOAD, "false");
    request.setRequestBody(BuilderConfig.NO_OP);
    doReturn(SafeFuture.completedFuture(Optional.empty()))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty(), false, BuilderConfig.NO_OP);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(request.getResponseBody().toString()).contains("Unable to produce a block");
  }

  @TestTemplate
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @TestTemplate
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @TestTemplate
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @TestTemplate
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}

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

package tech.pegasys.teku.beaconrestapi.v4;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BUILDER_URL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v4.validator.PostNewBlockV4;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.GLOAS, SpecMilestone.HEZE})
public class PostNewBlockV4IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private static final Logger LOG = LogManager.getLogger();

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  void shouldReturnBeaconBlockWhenIncludePayloadIsFalse() throws Exception {
    final String builderUrl = "https://foobar.com";
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE).toBuilder()
            .builderUrl(Optional.of(builderUrl))
            .build();
    final BLSSignature signature =
        blockContainerAndMetaData.blockContainer().getBlock().getBody().getRandaoReveal();

    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(false), eq(Optional.of(BuilderConfig.NO_OP))))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)));

    final Response response = post(signature, false);

    assertResponseWithHeaders(
        response,
        false,
        blockContainerAndMetaData.executionPayloadValue().toDecimalString(),
        blockContainerAndMetaData.consensusBlockValue().toDecimalString(),
        builderUrl);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    final JsonNode expectedAsJsonNode =
        JsonTestUtil.parseAsJsonNode(getExpectedBlockAsJson(specMilestone, false));

    assertThat(resultAsJsonNode).isEqualTo(expectedAsJsonNode);
  }

  @TestTemplate
  void shouldReturnBlockContentsWhenIncludePayloadIsTrue() throws Exception {
    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);
    assertThat(blockContents).isInstanceOf(BlockContentsGloas.class);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(blockContents, ONE).toBuilder()
            .payloadIncluded(true)
            .build();
    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();

    when(validatorApiChannel.createUnsignedBlock(
            eq(UInt64.ONE), eq(signature), any(), eq(true), eq(Optional.of(BuilderConfig.NO_OP))))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)));

    final Response response = post(signature, true);

    assertResponseWithHeaders(
        response,
        true,
        blockContainerAndMetaData.executionPayloadValue().toDecimalString(),
        blockContainerAndMetaData.consensusBlockValue().toDecimalString(),
        null);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    final JsonNode expectedAsJsonNode =
        JsonTestUtil.parseAsJsonNode(getExpectedBlockAsJson(specMilestone, true));

    assertThat(resultAsJsonNode).isEqualTo(expectedAsJsonNode);
  }

  private void assertResponseWithHeaders(
      final Response response,
      final boolean includePayload,
      final String executionPayloadValue,
      final String consensusBlockValue,
      final String builderUrl) {
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(specMilestone.lowerCaseName());
    assertThat(response.header(HEADER_INCLUDE_PAYLOAD)).isEqualTo(Boolean.toString(includePayload));
    assertThat(response.header(HEADER_EXECUTION_PAYLOAD_VALUE)).isEqualTo(executionPayloadValue);
    assertThat(response.header(HEADER_CONSENSUS_BLOCK_VALUE)).isEqualTo(consensusBlockValue);
    assertThat(response.header(HEADER_BUILDER_URL)).isEqualTo(builderUrl);
  }

  private Response post(final BLSSignature signature, final boolean includePayload)
      throws IOException {
    return post(
        PostNewBlockV4.ROUTE.replace("{slot}", "1"),
        JsonUtil.serialize(
            BuilderConfig.NO_OP, ApiSchemas.BUILDER_CONFIG_SCHEMA.getJsonTypeDefinition()),
        Map.of(
            "randao_reveal", signature.toString(),
            "include_payload", Boolean.toString(includePayload)));
  }

  protected String getExpectedBlockAsJson(
      final SpecMilestone specMilestone, final boolean blockContents) throws IOException {
    final String fileName =
        String.format(
            "new%s%s.json", blockContents ? "BlockContents" : "Block", specMilestone.name());

    LOG.info("Read expected json file: {}", fileName);
    return Resources.toString(
        Resources.getResource(PostNewBlockV4IntegrationTest.class, fileName), UTF_8);
  }
}

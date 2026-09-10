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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true)
public class ProduceBlockRequestTest extends AbstractTypeDefRequestTestBase {
  private static final Logger LOG = LogManager.getLogger();
  private ProduceBlockRequest request;
  private Buffer responseBodyBuffer;

  @BeforeEach
  void setupRequest() {
    request =
        new ProduceBlockRequest(
            mockWebServer.url("/"),
            okHttpClient,
            new SchemaDefinitionCache(spec),
            UInt64.ONE,
            false);
    responseBodyBuffer = new Buffer();
  }

  @AfterEach
  void reset() {
    responseBodyBuffer.clear();
    responseBodyBuffer.close();
  }

  @TestTemplate
  public void shouldGetUnblindedBeaconBlockAsJson() throws JsonProcessingException {
    assumeThat(specMilestone.isLessThan(DENEB)).isTrue();
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);

    LOG.debug(
        "Expected block in JSON: {}",
        JsonUtil.serialize(
            beaconBlock, schemaDefinitions.getBeaconBlockSchema().getJsonTypeDefinition()));

    final String mockResponse = readExpectedJsonResource(specMilestone, false, false);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(mockResponse)
            .setHeader(HEADER_EXECUTION_PAYLOAD_BLINDED, "false"));

    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        request.submitV3(signature, Optional.empty(), Optional.empty());

    assertThat(maybeBlockContainerAndMetaData).isPresent();

    final BeaconBlock actualBlock =
        maybeBlockContainerAndMetaData.get().blockContainer().getBlock();
    assertThat(actualBlock).isEqualTo(beaconBlock.getBlock());

    assertThat(maybeBlockContainerAndMetaData.get().consensusBlockValue())
        .isEqualTo(UInt256.valueOf(123000000000L));
    assertThat(maybeBlockContainerAndMetaData.get().executionPayloadValue())
        .isEqualTo(UInt256.valueOf(12345));
  }

  @TestTemplate
  public void shouldGetUnblindedBeaconBlockAsSsz() {
    assumeThat(specMilestone.isLessThan(DENEB)).isTrue();
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);

    responseBodyBuffer.write(
        spec.getGenesisSchemaDefinitions()
            .getBlockContainerSchema()
            .sszSerialize(beaconBlock)
            .toArrayUnsafe());

    final MockResponse mockResponse =
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader("Content-Type", MediaType.OCTET_STREAM)
            .setBody(responseBodyBuffer);

    final UInt256 expectedConsensusBlockValue;
    final UInt256 expectedExecutionPayloadValue;

    if (specMilestone.isGreaterThanOrEqualTo(BELLATRIX)) {
      mockResponse
          .setHeader(HEADER_CONSENSUS_BLOCK_VALUE, "123000000000")
          .setHeader(HEADER_EXECUTION_PAYLOAD_VALUE, "12345");

      expectedConsensusBlockValue = UInt256.valueOf(123000000000L);
      expectedExecutionPayloadValue = UInt256.valueOf(12345);
    } else {
      expectedConsensusBlockValue = UInt256.ZERO;
      expectedExecutionPayloadValue = UInt256.ZERO;
    }

    mockWebServer.enqueue(mockResponse);

    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        request.submitV3(signature, Optional.empty(), Optional.empty());

    assertThat(maybeBlockContainerAndMetaData).isPresent();

    assertThat(maybeBlockContainerAndMetaData.get().blockContainer()).isEqualTo(beaconBlock);

    assertThat(maybeBlockContainerAndMetaData.get().consensusBlockValue())
        .isEqualTo(expectedConsensusBlockValue);
    assertThat(maybeBlockContainerAndMetaData.get().executionPayloadValue())
        .isEqualTo(expectedExecutionPayloadValue);
  }

  @TestTemplate
  public void shouldGetBlindedBeaconBlockAsJson() {
    assumeThat(specMilestone).isBetween(BELLATRIX, FULU);
    final BeaconBlock blindedBeaconBlock = dataStructureUtil.randomBlindedBeaconBlock(ONE);

    final String mockResponse = readExpectedJsonResource(specMilestone, true, false);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(mockResponse)
            .setHeader(HEADER_EXECUTION_PAYLOAD_BLINDED, "true"));

    final BLSSignature signature = blindedBeaconBlock.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        request.submitV3(signature, Optional.empty(), Optional.empty());

    assertThat(maybeBlockContainerAndMetaData.map(BlockContainerAndMetaData::blockContainer))
        .hasValue(blindedBeaconBlock);
  }

  @TestTemplate
  public void shouldGetBlindedBeaconBlockAsSsz() {
    assumeThat(specMilestone).isBetween(BELLATRIX, FULU);
    final BeaconBlock blindedBeaconBlock = dataStructureUtil.randomBlindedBeaconBlock(ONE);

    responseBodyBuffer.write(
        spec.getGenesisSchemaDefinitions()
            .getBlindedBlockContainerSchema()
            .sszSerialize(blindedBeaconBlock)
            .toArrayUnsafe());

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader(HEADER_EXECUTION_PAYLOAD_BLINDED, "true")
            .setHeader("Content-Type", MediaType.OCTET_STREAM)
            .setHeader(HEADER_CONSENSUS_BLOCK_VALUE, "123000000000")
            .setHeader(HEADER_EXECUTION_PAYLOAD_VALUE, "12345")
            .setBody(responseBodyBuffer));

    final BLSSignature signature = blindedBeaconBlock.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        request.submitV3(signature, Optional.empty(), Optional.empty());

    assertThat(maybeBlockContainerAndMetaData).isPresent();

    assertThat(maybeBlockContainerAndMetaData.get().blockContainer()).isEqualTo(blindedBeaconBlock);
  }

  @TestTemplate
  public void shouldGetUnblindedBlockContentsPostDenebAsJson() {
    assumeThat(specMilestone).isBetween(DENEB, FULU);
    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);

    final String mockResponse = readExpectedJsonResource(specMilestone, false, true);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(mockResponse)
            .setHeader(HEADER_EXECUTION_PAYLOAD_BLINDED, "false"));

    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        request.submitV3(signature, Optional.empty(), Optional.empty());

    assertThat(maybeBlockContainerAndMetaData).isPresent();

    assertThat(maybeBlockContainerAndMetaData.get().blockContainer()).isEqualTo(blockContents);
  }

  @TestTemplate
  public void shouldGetUnblindedBlockContentsPostDenebAsSsz() {
    assumeThat(specMilestone).isBetween(DENEB, FULU);
    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);

    responseBodyBuffer.write(
        spec.getGenesisSchemaDefinitions()
            .getBlockContainerSchema()
            .sszSerialize(blockContents)
            .toArray());

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader("Content-Type", MediaType.OCTET_STREAM)
            .setBody(responseBodyBuffer));

    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> maybeBlockContainerAndMetaData =
        request.submitV3(signature, Optional.empty(), Optional.empty());

    assertThat(maybeBlockContainerAndMetaData).isPresent();

    assertThat(maybeBlockContainerAndMetaData.get().blockContainer()).isEqualTo(blockContents);
  }

  @TestTemplate
  public void shouldGetBlockContentsAsSszV4() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);
    assertThat(blockContents).isInstanceOf(BlockContentsGloas.class);

    responseBodyBuffer.write(blockContents.sszSerialize().toArrayUnsafe());

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader("Content-Type", MediaType.OCTET_STREAM)
            .setHeader(HEADER_INCLUDE_PAYLOAD, "true")
            .setHeader(HEADER_CONSENSUS_BLOCK_VALUE, "123000000000")
            .setHeader(HEADER_EXECUTION_PAYLOAD_VALUE, "12345")
            .setBody(responseBodyBuffer));

    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> result =
        request.submitV4(signature, Optional.empty(), true, BuilderConfig.NO_OP, specMilestone);

    assertThat(result).isPresent();
    assertThat(result.get().blockContainer()).isEqualTo(blockContents);
    assertThat(result.get().executionPayloadValue()).isEqualTo(UInt256.valueOf(12345));
    assertThat(result.get().consensusBlockValue()).isEqualTo(UInt256.valueOf(123000000000L));
  }

  @TestTemplate
  public void shouldGetBeaconBlockAsSszV4WhenPayloadNotIncluded() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);

    responseBodyBuffer.write(
        schemaDefinitions.getBeaconBlockSchema().sszSerialize(beaconBlock).toArrayUnsafe());

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setHeader("Content-Type", MediaType.OCTET_STREAM)
            .setHeader(HEADER_INCLUDE_PAYLOAD, "false")
            .setHeader(HEADER_CONSENSUS_BLOCK_VALUE, "123000000000")
            .setHeader(HEADER_EXECUTION_PAYLOAD_VALUE, "12345")
            .setBody(responseBodyBuffer));

    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> result =
        request.submitV4(signature, Optional.empty(), false, BuilderConfig.NO_OP, specMilestone);

    assertThat(result).isPresent();
    assertThat(result.get().blockContainer()).isEqualTo(beaconBlock);
    assertThat(result.get().executionPayloadValue()).isEqualTo(UInt256.valueOf(12345));
    assertThat(result.get().consensusBlockValue()).isEqualTo(UInt256.valueOf(123000000000L));
  }

  @TestTemplate
  public void shouldGetBlockContentsAsJsonV4() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    final SchemaDefinitionsGloas schemaDefinitionsGloas =
        SchemaDefinitionsGloas.required(schemaDefinitions);
    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);
    assertThat(blockContents).isInstanceOf(BlockContentsGloas.class);

    final String dataJson =
        JsonUtil.serialize(
            blockContents,
            schemaDefinitionsGloas
                .getBlockContentsSchema()
                .castTypeToBlockContainer()
                .getJsonTypeDefinition());
    final String mockResponse =
        String.format(
            "{\"version\":\"gloas\",\"execution_payload_included\":true,"
                + "\"execution_payload_value\":\"12345\",\"consensus_block_value\":\"123000000000\","
                + "\"data\":%s}",
            dataJson);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(mockResponse)
            .setHeader(HEADER_INCLUDE_PAYLOAD, "true"));

    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> result =
        request.submitV4(signature, Optional.empty(), true, BuilderConfig.NO_OP, specMilestone);

    assertThat(result).isPresent();
    assertThat(result.get().blockContainer()).isEqualTo(blockContents);
  }

  @TestTemplate
  public void shouldGetBeaconBlockAsJsonV4WhenPayloadNotIncluded() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);

    final String dataJson =
        JsonUtil.serialize(
            beaconBlock, schemaDefinitions.getBeaconBlockSchema().getJsonTypeDefinition());
    final String mockResponse =
        String.format(
            "{\"version\":\"gloas\",\"execution_payload_included\":false,"
                + "\"execution_payload_value\":\"12345\",\"consensus_block_value\":\"123000000000\","
                + "\"data\":%s}",
            dataJson);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(mockResponse)
            .setHeader(HEADER_INCLUDE_PAYLOAD, "false"));

    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();

    final Optional<BlockContainerAndMetaData> result =
        request.submitV4(signature, Optional.empty(), false, BuilderConfig.NO_OP, specMilestone);

    assertThat(result).isPresent();
    assertThat(result.get().blockContainer()).isEqualTo(beaconBlock);
  }

  @TestTemplate
  public void shouldPassUrlParameters() throws InterruptedException {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    RecordedRequest recordedRequest;

    // here we are testing that the request is sent with the correct parameters, so we don't bother
    // creating an actual block. We just return a 404 which will cause the request to throw

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    // no optional parameters
    assertThat(request.submitV3(signature, Optional.empty(), Optional.empty())).isEmpty();

    recordedRequest = mockWebServer.takeRequest();

    // the request should not contain any optional parameters
    assertThat(recordedRequest.getRequestUrl().queryParameterNames())
        .doesNotContain("graffiti", "builder_boost_factor");
    assertThat(recordedRequest.getRequestUrl().queryParameter("randao_reveal"))
        .isEqualTo(signature.toString());

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    // with all parameters
    assertThat(
            request.submitV3(signature, Optional.of(Bytes32.ZERO), Optional.of(UInt64.valueOf(48))))
        .isEmpty();

    recordedRequest = mockWebServer.takeRequest();

    // the request should contain all optional parameters
    assertThat(recordedRequest.getRequestUrl().queryParameter("randao_reveal"))
        .isEqualTo(signature.toString());
    assertThat(recordedRequest.getRequestUrl().queryParameter("graffiti"))
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
    assertThat(recordedRequest.getRequestUrl().queryParameter("builder_boost_factor"))
        .isEqualTo("48");
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(
            () ->
                request.submitV3(
                    BLSSignature.empty(),
                    Optional.of(Bytes32.ZERO),
                    Optional.of(UInt64.valueOf(48))))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  private String readExpectedJsonResource(
      final SpecMilestone specMilestone, final boolean blinded, final boolean blockContents) {
    final String fileName =
        String.format(
            "new%s%s%s.json",
            blinded ? "Blinded" : "",
            blockContents ? "BlockContents" : "Block",
            specMilestone.name());
    LOG.info("Read expected json file: responses/produce_block_responses/{}", fileName);
    return readResource("responses/produce_block_responses/" + fileName);
  }
}

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

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BUILDER_BOOST_FACTOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.GRAFFITI;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INCLUDE_EXECUTION_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT256_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK_V3;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.POST_UNSIGNED_BLOCK_V4;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class ProduceBlockRequest extends AbstractTypeDefRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final UInt64 slot;
  private final boolean preferSszBlockEncoding;
  private final BlockContainerSchema<BlockContainer> blockContainerSchema;
  private final BlockContainerSchema<BlockContainer> blindedBlockContainerSchema;
  private final BlockContainerSchema<BlockContainer> beaconBlockSchemaForV4;
  private final ResponseHandler<ProduceBlockResponse> responseHandler;
  private final ResponseHandler<ProduceBlockResponse> responseHandlerV4;

  private final DeserializableOneOfTypeDefinition<ProduceBlockResponse> produceBlockTypeDefinition;
  private final DeserializableOneOfTypeDefinition<ProduceBlockResponse>
      produceBlockV4TypeDefinition;

  public ProduceBlockRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final SchemaDefinitionCache schemaDefinitionCache,
      final UInt64 slot,
      final boolean preferSszBlockEncoding) {
    super(baseEndpoint, okHttpClient);
    this.slot = slot;
    this.preferSszBlockEncoding = preferSszBlockEncoding;
    this.blockContainerSchema = schemaDefinitionCache.atSlot(slot).getBlockContainerSchema();
    this.blindedBlockContainerSchema =
        schemaDefinitionCache.atSlot(slot).getBlindedBlockContainerSchema();
    this.beaconBlockSchemaForV4 =
        schemaDefinitionCache.atSlot(slot).getBeaconBlockSchema().castTypeToBlockContainer();

    final DeserializableTypeDefinition<ProduceBlockResponse> produceBlockResponseDefinition =
        buildDeserializableTypeDefinition(blockContainerSchema.getJsonTypeDefinition());
    final DeserializableTypeDefinition<ProduceBlockResponse> produceBlindedBlockResponseDefinition =
        buildDeserializableTypeDefinition(blindedBlockContainerSchema.getJsonTypeDefinition());

    this.produceBlockTypeDefinition =
        DeserializableOneOfTypeDefinition.object(ProduceBlockResponse.class)
            .withType(
                x -> true,
                executionPayloadBlindedHeader ->
                    !Boolean.parseBoolean(executionPayloadBlindedHeader),
                produceBlockResponseDefinition)
            .withType(x -> true, Boolean::parseBoolean, produceBlindedBlockResponseDefinition)
            .build();

    this.responseHandler =
        new ResponseHandler<>(produceBlockTypeDefinition)
            .withHandler(SC_OK, this::handleBlockContainerResult);

    final DeserializableTypeDefinition<ProduceBlockResponse>
        produceBlockV4ContentsResponseDefinition =
            buildV4DeserializableTypeDefinition(blockContainerSchema.getJsonTypeDefinition());
    final DeserializableTypeDefinition<ProduceBlockResponse> produceBlockV4BlockResponseDefinition =
        buildV4DeserializableTypeDefinition(beaconBlockSchemaForV4.getJsonTypeDefinition());

    this.produceBlockV4TypeDefinition =
        DeserializableOneOfTypeDefinition.object(ProduceBlockResponse.class)
            .withType(x -> true, Boolean::parseBoolean, produceBlockV4ContentsResponseDefinition)
            .withType(
                x -> true,
                header -> !Boolean.parseBoolean(header),
                produceBlockV4BlockResponseDefinition)
            .build();

    this.responseHandlerV4 =
        new ResponseHandler<>(produceBlockV4TypeDefinition)
            .withHandler(SC_OK, this::handleV4BlockContainerResult);
  }

  public Optional<BlockContainerAndMetaData> submitV3(
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    final Map<String, String> queryParams =
        buildQueryParamsV3(randaoReveal, graffiti, requestedBuilderBoostFactor);
    final Map<String, String> headers = buildAcceptHeaders();
    return get(
            GET_UNSIGNED_BLOCK_V3,
            Map.of("slot", slot.toString()),
            queryParams,
            emptyMap(),
            headers,
            this.responseHandler)
        .map(this::toMetaData);
  }

  public Optional<BlockContainerAndMetaData> submitV4(
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final BuilderConfig builderConfig,
      final SpecMilestone milestone) {
    final Map<String, String> urlParams = Map.of("slot", slot.toString());
    final Map<String, String> queryParams =
        buildQueryParamsV4(randaoReveal, graffiti, includePayload);
    final Map<String, String> headers = buildAcceptHeaders();
    headers.put(HEADER_CONSENSUS_VERSION, milestone.lowerCaseName());
    return postJson(
            POST_UNSIGNED_BLOCK_V4,
            urlParams,
            queryParams,
            headers,
            builderConfig,
            ApiSchemas.BUILDER_CONFIG_SCHEMA.getJsonTypeDefinition(),
            this.responseHandlerV4)
        .map(this::toMetaData);
  }

  private Map<String, String> buildQueryParamsV3(
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    final Map<String, String> queryParams = buildCommonQueryParams(randaoReveal, graffiti);
    requestedBuilderBoostFactor.ifPresent(
        builderBoostFactor -> queryParams.put(BUILDER_BOOST_FACTOR, builderBoostFactor.toString()));
    return queryParams;
  }

  private Map<String, String> buildQueryParamsV4(
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload) {
    final Map<String, String> queryParams = buildCommonQueryParams(randaoReveal, graffiti);
    queryParams.put(INCLUDE_PAYLOAD, Boolean.toString(includePayload));
    return queryParams;
  }

  private Map<String, String> buildCommonQueryParams(
      final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put(RANDAO_REVEAL, randaoReveal.toString());
    graffiti.ifPresent(bytes32 -> queryParams.put(GRAFFITI, bytes32.toHexString()));
    return queryParams;
  }

  private Map<String, String> buildAcceptHeaders() {
    final Map<String, String> headers = new HashMap<>();
    if (preferSszBlockEncoding) {
      // application/octet-stream is preferred, but will accept application/json
      headers.put("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    }
    return headers;
  }

  private BlockContainerAndMetaData toMetaData(final ProduceBlockResponse response) {
    return new BlockContainerAndMetaData(
        response.getData(),
        response.getSpecMilestone(),
        response.executionPayloadValue,
        response.consensusBlockValue);
  }

  private Optional<ProduceBlockResponse> handleBlockContainerResult(
      final Request request, final Response response) {
    // V3: blinded=true → blindedBlockContainerSchema, false → blockContainerSchema
    return parseResponse(
        response,
        HEADER_EXECUTION_PAYLOAD_BLINDED,
        blindedBlockContainerSchema,
        blockContainerSchema,
        produceBlockTypeDefinition);
  }

  private Optional<ProduceBlockResponse> handleV4BlockContainerResult(
      final Request request, final Response response) {
    // V4: included=true → blockContainerSchema (full contents), false → beaconBlockSchemaForV4
    return parseResponse(
        response,
        HEADER_INCLUDE_PAYLOAD,
        blockContainerSchema,
        beaconBlockSchemaForV4,
        produceBlockV4TypeDefinition);
  }

  private Optional<ProduceBlockResponse> parseResponse(
      final Response response,
      final String discriminatorHeader,
      final BlockContainerSchema<BlockContainer> trueSchema,
      final BlockContainerSchema<BlockContainer> falseSchema,
      final DeserializableOneOfTypeDefinition<ProduceBlockResponse> jsonTypeDefinition) {
    try {
      final String responseContentType = response.header("Content-Type");
      if (responseContentType != null
          && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
        final UInt256 executionPayloadValue =
            parseUInt256Header(response, HEADER_EXECUTION_PAYLOAD_VALUE);
        final UInt256 consensusBlockValue =
            parseUInt256Header(response, HEADER_CONSENSUS_BLOCK_VALUE);
        final BlockContainerSchema<BlockContainer> schema =
            Boolean.parseBoolean(response.header(discriminatorHeader)) ? trueSchema : falseSchema;
        return Optional.of(
            new ProduceBlockResponse(
                schema.sszDeserialize(Bytes.of(response.body().bytes())),
                executionPayloadValue,
                consensusBlockValue));
      } else {
        return Optional.of(
            JsonUtil.parseBasedOnHeader(
                response.header(discriminatorHeader),
                response.body().string(),
                jsonTypeDefinition));
      }
    } catch (final IOException ex) {
      LOG.error("Failed to parse response object creating block", ex);
    }
    return Optional.empty();
  }

  static UInt256 parseUInt256Header(final Response response, final String headerName) {
    final String headerValue = response.header(headerName);
    if (headerValue == null) {
      LOG.warn("Header {} not found in response, defaulting value to ZERO", headerName);
      return UInt256.ZERO;
    }
    try {
      return UInt256.valueOf(new BigInteger(headerValue, 10));
    } catch (final IllegalArgumentException e) {
      LOG.warn(
          "Invalid value for header {}: '{}', " + "defaulting to ZERO", headerName, headerValue);
      return UInt256.ZERO;
    }
  }

  private DeserializableTypeDefinition<ProduceBlockResponse> buildDeserializableTypeDefinition(
      final DeserializableTypeDefinition<BlockContainer> jsonTypeDefinition) {
    return DeserializableTypeDefinition.object(ProduceBlockResponse.class)
        .initializer(ProduceBlockResponse::new)
        .withField(
            EXECUTION_PAYLOAD_BLINDED,
            BOOLEAN_TYPE,
            ProduceBlockResponse::getExecutionPayloadBlinded,
            ProduceBlockResponse::setExecutionPayloadBlinded)
        .withField(
            EXECUTION_PAYLOAD_VALUE,
            UINT256_TYPE,
            ProduceBlockResponse::getExecutionPayloadValue,
            ProduceBlockResponse::setExecutionPayloadValue)
        .withField(
            CONSENSUS_BLOCK_VALUE,
            UINT256_TYPE,
            ProduceBlockResponse::getConsensusBlockValue,
            ProduceBlockResponse::setConsensusBlockValue)
        .withField(
            "data",
            jsonTypeDefinition,
            ProduceBlockResponse::getData,
            ProduceBlockResponse::setData)
        .withField(
            "version",
            DeserializableTypeDefinition.enumOf(SpecMilestone.class),
            ProduceBlockResponse::getSpecMilestone,
            ProduceBlockResponse::setSpecMilestone)
        .build();
  }

  private DeserializableTypeDefinition<ProduceBlockResponse> buildV4DeserializableTypeDefinition(
      final DeserializableTypeDefinition<BlockContainer> jsonTypeDefinition) {
    return DeserializableTypeDefinition.object(ProduceBlockResponse.class)
        .initializer(ProduceBlockResponse::new)
        .withField(
            INCLUDE_EXECUTION_PAYLOAD,
            BOOLEAN_TYPE,
            ProduceBlockResponse::getExecutionPayloadIncluded,
            ProduceBlockResponse::setExecutionPayloadIncluded)
        .withField(
            EXECUTION_PAYLOAD_VALUE,
            UINT256_TYPE,
            ProduceBlockResponse::getExecutionPayloadValue,
            ProduceBlockResponse::setExecutionPayloadValue)
        .withField(
            CONSENSUS_BLOCK_VALUE,
            UINT256_TYPE,
            ProduceBlockResponse::getConsensusBlockValue,
            ProduceBlockResponse::setConsensusBlockValue)
        .withField(
            "data",
            jsonTypeDefinition,
            ProduceBlockResponse::getData,
            ProduceBlockResponse::setData)
        .withField(
            "version",
            DeserializableTypeDefinition.enumOf(SpecMilestone.class),
            ProduceBlockResponse::getSpecMilestone,
            ProduceBlockResponse::setSpecMilestone)
        .build();
  }

  static class ProduceBlockResponse {
    private BlockContainer data;
    private Boolean executionPayloadBlinded;
    private Boolean executionPayloadIncluded;
    private UInt256 executionPayloadValue;
    private UInt256 consensusBlockValue;
    private SpecMilestone specMilestone;

    public ProduceBlockResponse() {}

    public ProduceBlockResponse(
        final BlockContainer data,
        final UInt256 executionPayloadValue,
        final UInt256 consensusBlockValue) {
      this.data = data;
      this.executionPayloadValue = executionPayloadValue;
      this.consensusBlockValue = consensusBlockValue;
    }

    public BlockContainer getData() {
      return data;
    }

    public void setData(final BlockContainer data) {
      this.data = data;
    }

    public Boolean getExecutionPayloadBlinded() {
      return executionPayloadBlinded;
    }

    public void setExecutionPayloadBlinded(final Boolean executionPayloadBlinded) {
      this.executionPayloadBlinded = executionPayloadBlinded;
    }

    public Boolean getExecutionPayloadIncluded() {
      return executionPayloadIncluded;
    }

    public void setExecutionPayloadIncluded(final Boolean executionPayloadIncluded) {
      this.executionPayloadIncluded = executionPayloadIncluded;
    }

    public UInt256 getConsensusBlockValue() {
      return consensusBlockValue;
    }

    public void setConsensusBlockValue(final UInt256 consensusBlockValue) {
      this.consensusBlockValue = consensusBlockValue;
    }

    public UInt256 getExecutionPayloadValue() {
      return executionPayloadValue;
    }

    public void setExecutionPayloadValue(final UInt256 executionPayloadValue) {
      this.executionPayloadValue = executionPayloadValue;
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public void setSpecMilestone(final SpecMilestone specMilestone) {
      this.specMilestone = specMilestone;
    }
  }
}

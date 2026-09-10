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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.GRAFFITI_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.INCLUDE_PAYLOAD_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.RANDAO_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SKIP_RANDAO_VERIFICATION_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_BUILDER_URL_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.blockContainerAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BUILDER_URL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INCLUDE_EXECUTION_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_PATH_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT256_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class PostNewBlockV4 extends RestApiEndpoint {
  private static final Logger LOG = LoggerFactory.getLogger(PostNewBlockV4.class);

  public static final String ROUTE = "/eth/v4/validator/blocks/{slot}";

  protected final ValidatorDataProvider validatorDataProvider;

  public PostNewBlockV4(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostNewBlockV4(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  private static EndpointMetadata getEndpointMetaData(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("produceBlockV4")
        .summary("Produce a new block, without signature.")
        .description(
            """
              Requests a beacon node to produce a valid block, which the validator then signs.

              The beacon node always builds a local payload and MAY consider a p2p bid, so a block is returned
              even when no builder bid is available. The validator client supplies a `BuilderConfig` in the
              request body: a list of `BuilderEntry` objects in its `builders` field, one per bid request,
              plus a top-level `min_bid` and `builder_boost_factor` that apply to p2p bids. Each entry
              requests a bid from its `url`, and the entry applies to the bid that request returns; an empty
              `builder_pubkeys` list accepts any builder, and a bid MUST NOT be accepted unless signed by
              one of a non-empty list.

              A builder bid, over the builder API or p2p, is rejected if its total payment falls below the
              `min_bid` that applies to it. Every surviving bid is then boosted by the
              `builder_boost_factor` that applies to it, and the
              highest boosted bid competes with the local build in Gwei. The local build wins a tie.

              The response carries the full block contents only when the beacon node self-built the block and
              `include_payload` is `true`; in every other case, including any builder bid win, it carries
              only the `BeaconBlock`.

              Every block is published via `POST /eth/v2/beacon/blocks`. A self-built block carries
              `BUILDER_INDEX_SELF_BUILD` as its `builder_index`, and the validator client publishes its
              execution payload envelope via `POST /eth/v1/beacon/execution_payload_envelopes`. When a
              builder bid won, the winning builder releases the envelope instead, and the validator client
              echoes `Eth-Builder-Url` if one was returned.

              This endpoint is specific to the post-Gloas forks and is not backwards compatible with previous
              forks.
              """)
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParam(GRAFFITI_PARAMETER)
        .queryParamAllowsEmpty(SKIP_RANDAO_VERIFICATION_PARAMETER)
        .queryParamRequired(INCLUDE_PAYLOAD_PARAMETER)
        .requestBodyType(
            ApiSchemas.BUILDER_CONFIG_SCHEMA.getJsonTypeDefinition(),
            ApiSchemas.BUILDER_CONFIG_SCHEMA::sszDeserialize)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaDefinitionCache),
            blockContainerAndMetaDataSszResponseType(),
            getHeaders())
        .withChainDataResponses()
        .withNotAcceptableResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getPathParameter(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION));
    if (validatorDataProvider.getMilestoneAtSlot(slot).isLessThan(SpecMilestone.GLOAS)) {
      request.respondError(SC_BAD_REQUEST, "produceBlockV4 is only supported from Gloas onwards");
      return;
    }
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);
    final boolean includePayload = request.getQueryParameter(INCLUDE_PAYLOAD_PARAMETER);
    final BuilderConfig builderConfig = request.getRequestBody();

    final long requestTimeMs = System.currentTimeMillis();
    LOG.debug(
        "produceBlockV4 requested: slot={}, include_payload={}, builder_config={}, timestampMs={}",
        slot,
        includePayload,
        builderConfig,
        requestTimeMs);

    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorDataProvider.produceBlock(slot, randao, graffiti, includePayload, builderConfig);

    request.respondAsync(
        result.thenApply(
            maybeBlock ->
                maybeBlock
                    .map(
                        blockContainerAndMetaData -> {
                          final long responseTimeMs = System.currentTimeMillis();
                          LOG.debug(
                              "produceBlockV4 response: slot={}, execution_payload_included={}, consensus_block_value={}, execution_payload_value={}, builder_url={}, timestampMs={}, elapsedMs={}",
                              slot,
                              blockContainerAndMetaData.payloadIncluded(),
                              blockContainerAndMetaData.consensusBlockValue().toDecimalString(),
                              blockContainerAndMetaData.executionPayloadValue().toDecimalString(),
                              blockContainerAndMetaData.builderUrl(),
                              responseTimeMs,
                              responseTimeMs - requestTimeMs);
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              blockContainerAndMetaData.milestone().lowerCaseName());
                          request.header(
                              HEADER_CONSENSUS_BLOCK_VALUE,
                              blockContainerAndMetaData.consensusBlockValue().toDecimalString());
                          request.header(
                              HEADER_EXECUTION_PAYLOAD_VALUE,
                              blockContainerAndMetaData.executionPayloadValue().toDecimalString());
                          request.header(
                              HEADER_INCLUDE_PAYLOAD,
                              Boolean.toString(blockContainerAndMetaData.payloadIncluded()));
                          blockContainerAndMetaData
                              .builderUrl()
                              .ifPresent(
                                  builderUrl -> request.header(HEADER_BUILDER_URL, builderUrl));
                          return AsyncApiResponse.respondOk(blockContainerAndMetaData);
                        })
                    .orElseGet(
                        () ->
                            AsyncApiResponse.respondWithError(
                                SC_INTERNAL_SERVER_ERROR, "Unable to produce a block"))));
  }

  private static SerializableTypeDefinition<BlockContainerAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {

    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>> schemaGetters =
        generateBlockContainerSchemaGetters(schemaDefinitionCache);

    final SerializableTypeDefinition<BlockContainer> blockContainerType =
        getMultipleSchemaDefinitionFromMilestone(schemaDefinitionCache, "Block", schemaGetters);

    return SerializableTypeDefinition.<BlockContainerAndMetaData>object()
        .name("ProduceBlockV4Response")
        .withField("version", MILESTONE_TYPE, BlockContainerAndMetaData::milestone)
        .withField(
            CONSENSUS_BLOCK_VALUE, UINT256_TYPE, BlockContainerAndMetaData::consensusBlockValue)
        .withField(
            EXECUTION_PAYLOAD_VALUE, UINT256_TYPE, BlockContainerAndMetaData::executionPayloadValue)
        .withField(
            INCLUDE_EXECUTION_PAYLOAD, BOOLEAN_TYPE, BlockContainerAndMetaData::payloadIncluded)
        .withField("data", blockContainerType, BlockContainerAndMetaData::blockContainer)
        .build();
  }

  private static List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
      generateBlockContainerSchemaGetters(final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
        schemaGetterList = new ArrayList<>();

    // Self-built local block: beacon block + envelope + blobs + KZG proofs
    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)
                    && blockContainer instanceof BlockContentsGloas,
            SpecMilestone.GLOAS,
            SchemaDefinitions::getBlockContainerSchema));

    // External builder bid: beacon block only (builder holds the payload)
    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)
                    && !(blockContainer instanceof BlockContentsGloas),
            SpecMilestone.GLOAS,
            schemaDefinitions ->
                schemaDefinitions.getBeaconBlockSchema().castTypeToBlockContainer()));
    return schemaGetterList;
  }

  private static List<SerializableTypeDefinition<?>> getHeaders() {
    List<SerializableTypeDefinition<?>> headers = new ArrayList<>();
    headers.add(ETH_CONSENSUS_HEADER_TYPE);
    headers.add(ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE);
    headers.add(ETH_HEADER_BUILDER_URL_TYPE);
    return headers;
  }
}

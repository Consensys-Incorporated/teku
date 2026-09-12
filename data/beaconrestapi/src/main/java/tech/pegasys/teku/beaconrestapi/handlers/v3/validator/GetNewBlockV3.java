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

package tech.pegasys.teku.beaconrestapi.handlers.v3.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BUILDER_BOOST_FACTOR_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.GRAFFITI_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.RANDAO_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SKIP_RANDAO_VERIFICATION_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_BLINDED_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.blockContainerAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
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
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class GetNewBlockV3 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v3/validator/blocks/{slot}";

  protected final ValidatorDataProvider validatorDataProvider;

  public GetNewBlockV3(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetNewBlockV3(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  private static EndpointMetadata getEndpointMetaData(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("produceBlockV3")
        .summary("Produce a new block, without signature.")
        .description(
            """
              Requests a beacon node to produce a valid block, which can then be signed by a validator. The
              returned block may be blinded or unblinded, depending on the current state of the network as
              decided by the execution and beacon nodes.
              The beacon node must return an unblinded block if it obtains the execution payload from its
              paired execution node. It must only return a blinded block if it obtains the execution payload
              header from an MEV relay.
              Metadata in the response indicates the type of block produced, and the supported types of block
              will be added to as forks progress.
              """)
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParam(GRAFFITI_PARAMETER)
        .queryParamAllowsEmpty(SKIP_RANDAO_VERIFICATION_PARAMETER)
        .queryParam(BUILDER_BOOST_FACTOR_PARAMETER)
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
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);
    final Optional<UInt64> requestedBuilderBoostFactor =
        request.getOptionalQueryParameter(BUILDER_BOOST_FACTOR_PARAMETER);
    if (validatorDataProvider
        .getMilestoneAtSlot(slot)
        .isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      request.respondError(
          SC_BAD_REQUEST, "produceBlockV3 is not supported from Gloas onwards, use produceBlockV4");
      return;
    }

    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorDataProvider.produceBlock(slot, randao, graffiti, requestedBuilderBoostFactor);
    request.respondAsync(
        result.thenApply(
            maybeBlock ->
                maybeBlock
                    .map(
                        blockContainerAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              blockContainerAndMetaData.milestone().lowerCaseName());
                          request.header(
                              HEADER_EXECUTION_PAYLOAD_BLINDED,
                              Boolean.toString(
                                  blockContainerAndMetaData
                                      .blockContainer()
                                      .getBlock()
                                      .isBlinded()));
                          request.header(
                              HEADER_EXECUTION_PAYLOAD_VALUE,
                              blockContainerAndMetaData.executionPayloadValue().toDecimalString());
                          request.header(
                              HEADER_CONSENSUS_BLOCK_VALUE,
                              blockContainerAndMetaData.consensusBlockValue().toDecimalString());
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
        .name("ProduceBlockV3Response")
        .withField("version", MILESTONE_TYPE, BlockContainerAndMetaData::milestone)
        .withField(
            EXECUTION_PAYLOAD_BLINDED,
            BOOLEAN_TYPE,
            blockContainerAndMetaData -> blockContainerAndMetaData.blockContainer().isBlinded())
        .withField(
            EXECUTION_PAYLOAD_VALUE, UINT256_TYPE, BlockContainerAndMetaData::executionPayloadValue)
        .withField(
            CONSENSUS_BLOCK_VALUE, UINT256_TYPE, BlockContainerAndMetaData::consensusBlockValue)
        .withField("data", blockContainerType, BlockContainerAndMetaData::blockContainer)
        .build();
  }

  private static List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
      generateBlockContainerSchemaGetters(final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
        schemaGetterList = new ArrayList<>();

    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && !blockContainer.isBlinded(),
            SpecMilestone.PHASE0,
            Optional.of(SpecMilestone.FULU),
            SchemaDefinitions::getBlockContainerSchema));

    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && milestone.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)
                    && blockContainer.isBlinded(),
            SpecMilestone.BELLATRIX,
            Optional.of(SpecMilestone.FULU),
            SchemaDefinitions::getBlindedBlockContainerSchema));
    return schemaGetterList;
  }

  private static List<SerializableTypeDefinition<?>> getHeaders() {
    List<SerializableTypeDefinition<?>> headers = new ArrayList<>();
    headers.add(ETH_CONSENSUS_HEADER_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_BLINDED_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE);
    headers.add(ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE);
    return headers;
  }
}

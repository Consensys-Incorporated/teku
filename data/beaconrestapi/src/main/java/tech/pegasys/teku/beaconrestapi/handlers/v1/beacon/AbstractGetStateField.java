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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.EndpointMetaDataBuilder;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Serves a single collection field of the beacon state, as JSON or SSZ, for states at or after the
 * milestone that introduced the field. Requests for older states fail with 400.
 *
 * <p>Fields are limited to SSZ collections because the response uses the field schema's JSON type
 * for any instance of {@code T}, which only holds when that type does not depend on the concrete
 * class (see {@link #getResponseType}).
 */
public abstract class AbstractGetStateField<T extends SszCollection<?>> extends RestApiEndpoint {

  private final ChainDataProvider chainDataProvider;
  private final SpecMilestone minimumMilestone;
  private final String fieldLabel;
  private final Function<BeaconState, T> fieldExtractor;

  protected AbstractGetStateField(
      final ChainDataProvider chainDataProvider,
      final EndpointMetaDataBuilder endpointMetadata,
      final String responseTypeName,
      final SpecMilestone minimumMilestone,
      final String fieldLabel,
      final SszCollectionSchema<?, ? extends T> fieldSchema,
      final Function<BeaconState, T> fieldExtractor) {
    super(
        endpointMetadata
            .pathParam(PARAMETER_STATE_ID)
            .tags(TAG_BEACON)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(responseTypeName, fieldSchema),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
    this.minimumMilestone = minimumMilestone;
    this.fieldLabel = fieldLabel;
    this.fieldExtractor = fieldExtractor;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<T>>> future =
        chainDataProvider.getStateField(
            request.getPathParameter(PARAMETER_STATE_ID),
            minimumMilestone,
            fieldLabel,
            fieldExtractor);

    request.respondAsync(
        future.thenApply(
            maybeData ->
                maybeData
                    .map(
                        objectAndMetadata -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              objectAndMetadata.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(objectAndMetadata);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  // Collection JSON types only use the collection interface: list and vector types iterate the
  // elements, byte and bit collections write the SSZ bytes. So the schema's JSON type can serialize
  // any instance of T, not just the schema's own concrete collection class.
  @SuppressWarnings("unchecked")
  private static <T extends SszCollection<?>>
      SerializableTypeDefinition<ObjectAndMetaData<T>> getResponseType(
          final String responseTypeName, final SszCollectionSchema<?, ? extends T> fieldSchema) {
    final SerializableTypeDefinition<T> fieldType =
        (SerializableTypeDefinition<T>) fieldSchema.getJsonTypeDefinition();
    return SerializableTypeDefinition.<ObjectAndMetaData<T>>object()
        .name(responseTypeName)
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField("data", fieldType, ObjectAndMetaData::getData)
        .build();
  }
}

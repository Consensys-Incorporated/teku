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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ATTESTATION_DATA_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetAggregateAttestationV2 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v2/validator/aggregate_attestation";
  private final ValidatorDataProvider provider;

  public GetAggregateAttestationV2(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetAggregateAttestationV2(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAggregatedAttestationV2")
            .summary("Get aggregated attestation")
            .description(
                """
                    Aggregates all attestations matching given attestation data root, slot and committee index.
                    A 503 error must be returned if the block identified by the response
                    `beacon_block_root` is optimistic (i.e. the aggregated attestation attests
                    to a block that has not been fully verified by an execution engine).
                    A 404 error must be returned if no attestation is available for the requested
                    `attestation_data_root`.
                    """)
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .queryParamRequired(ATTESTATION_DATA_ROOT_PARAMETER)
            .queryParamRequired(SLOT_PARAMETER)
            .queryParamRequired(COMMITTEE_INDEX_PARAMETER)
            .response(
                HttpStatusCodes.SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                EthereumTypes.sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Bytes32 beaconBlockRoot = request.getQueryParameter(ATTESTATION_DATA_ROOT_PARAMETER);
    final UInt64 slot = request.getQueryParameter(SLOT_PARAMETER);
    final UInt64 committeeIndex = request.getQueryParameter(COMMITTEE_INDEX_PARAMETER);

    final SafeFuture<Optional<ObjectAndMetaData<Attestation>>> future =
        provider.createAggregateAndMetaData(slot, beaconBlockRoot, committeeIndex);

    request.respondAsync(
        future.thenApply(
            maybeAttestation ->
                maybeAttestation
                    .map(
                        attestationAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              attestationAndMetaData.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(attestationAndMetaData);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<Attestation>> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {

    final SerializableOneOfTypeDefinition<Attestation> oneOfTypeDefinition =
        new SerializableOneOfTypeDefinitionBuilder<Attestation>()
            .withType(
                electraAttestationPredicate(),
                BeaconRestApiTypes.electraAttestationTypeDef(schemaDefinitionCache))
            .withType(
                phase0AttestationPredicate(),
                BeaconRestApiTypes.phase0AttestationTypeDef(schemaDefinitionCache))
            .build();

    return SerializableTypeDefinition.<ObjectAndMetaData<Attestation>>object()
        .name("GetAggregatedAttestationResponseV2")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField("data", oneOfTypeDefinition, ObjectAndMetaData::getData)
        .build();
  }

  private static Predicate<Attestation> phase0AttestationPredicate() {
    // Before Electra attestations do not require committee bits
    return attestation -> !attestation.requiresCommitteeBits();
  }

  private static Predicate<Attestation> electraAttestationPredicate() {
    // Only once we are in Electra attestations will have committee bits
    return Attestation::requiresCommitteeBits;
  }
}

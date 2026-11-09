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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ATTESTATION_DATA_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

public class GetAggregateAttestation extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/aggregate_attestation";
  private final ValidatorDataProvider provider;

  private static final ParameterMetadata<UInt64> SLOT_PARAM =
      SLOT_PARAMETER.withDescription(
          "`uint64` Non-finalized slot for which to create the aggregation.");

  public GetAggregateAttestation(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getValidatorDataProvider(), spec);
  }

  public GetAggregateAttestation(final ValidatorDataProvider provider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAggregatedAttestation")
            .summary("Get aggregated attestation")
            .description(
                "Aggregates all attestations matching given attestation data root and slot.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .deprecated(true)
            .queryParamRequired(ATTESTATION_DATA_ROOT_PARAMETER)
            .queryParamRequired(SLOT_PARAM)
            .response(HttpStatusCodes.SC_OK, "Request successful", getResponseType(spec))
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final Bytes32 beaconBlockRoot = request.getQueryParameter(ATTESTATION_DATA_ROOT_PARAMETER);
    final UInt64 slot = request.getQueryParameter(SLOT_PARAM);

    final SafeFuture<Optional<Attestation>> future =
        provider.createAggregate(slot, beaconBlockRoot, Optional.empty());

    request.respondAsync(
        future.thenApply(
            maybeAttestation ->
                maybeAttestation
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<Attestation> getResponseType(final Spec spec) {
    final AttestationSchema<?> dataSchema =
        spec.getGenesisSchemaDefinitions().getAttestationSchema();

    return SerializableTypeDefinition.object(Attestation.class)
        .name("GetAggregatedAttestationResponse")
        .withField(
            "data",
            dataSchema.castTypeToAttestationSchema().getJsonTypeDefinition(),
            Function.identity())
        .build();
  }
}

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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BEACON_BLOCK_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SUBCOMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class GetSyncCommitteeContribution extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/sync_committee_contribution";
  private final ValidatorDataProvider provider;

  public GetSyncCommitteeContribution(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetSyncCommitteeContribution(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("produceSyncCommitteeContribution")
            .summary("Produce a sync committee contribution")
            .description(
                "Returns a `SyncCommitteeContribution` that is the aggregate of `SyncCommitteeMessage` "
                    + "values known to this node matching the specified slot, subcommittee index and beacon block root.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .queryParamRequired(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .queryParamRequired(SUBCOMMITTEE_INDEX_PARAMETER)
            .queryParamRequired(BEACON_BLOCK_ROOT_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.provider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));
    final Bytes32 blockRoot = request.getQueryParameter(BEACON_BLOCK_ROOT_PARAMETER);
    final Integer subcommitteeIndex = request.getQueryParameter(SUBCOMMITTEE_INDEX_PARAMETER);

    if (subcommitteeIndex < 0
        || subcommitteeIndex >= NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT) {
      throw new IllegalArgumentException(
          String.format(
              "Subcommittee index needs to be between 0 and %s, %s is outside of this range.",
              NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT - 1, subcommitteeIndex));
    } else if (provider.isPhase0Slot(slot)) {
      throw new IllegalArgumentException(String.format("Slot %s is not an Altair slot", slot));
    }

    final SafeFuture<Optional<SyncCommitteeContribution>> future =
        provider.createSyncCommitteeContribution(slot, subcommitteeIndex, blockRoot);

    request.respondAsync(
        future.thenApply(
            maybeSyncCommitteeContribution ->
                maybeSyncCommitteeContribution
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<SyncCommitteeContribution> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SyncCommitteeContributionSchema typeDefinition =
        SchemaDefinitionsAltair.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ALTAIR))
            .getSyncCommitteeContributionSchema();

    return SerializableTypeDefinition.object(SyncCommitteeContribution.class)
        .name("GetSyncCommitteeContributionResponse")
        .withField("data", typeDefinition.getJsonTypeDefinition(), Function.identity())
        .build();
  }
}

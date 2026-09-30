/*
 * Copyright Consensys Software Inc., 2025
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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.validator.InclusionListCommitteeDutiesBuilder.INCLUSION_LIST_DUTIES_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetInclusionListCommitteeDuties extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/duties/inclusion_list/{epoch}";

  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  public GetInclusionListCommitteeDuties(final DataProvider provider) {
    this(provider.getSyncDataProvider(), provider.getValidatorDataProvider());
  }

  public GetInclusionListCommitteeDuties(
      final SyncDataProvider syncDataProvider, final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getInclusionListCommitteeDuties")
            .summary("Get inclusion list committee duties")
            .description(
                "Requests the beacon node to provide a set of inclusion list committee duties for a particular epoch.\n\n"
                    + "Duties should only need to be checked once per epoch, "
                    + "however a chain reorganization (of > MIN_SEED_LOOKAHEAD epochs) could occur, "
                    + "resulting in a change of duties. For full safety, you should monitor head_v2 events and confirm the "
                    + "dependent root in this response matches:\n"
                    + "- event.current_epoch_dependent_root when `compute_epoch_at_slot(event.slot) == epoch`\n"
                    + "- event.next_epoch_dependent_root when `compute_epoch_at_slot(event.slot) + 1 == epoch`\n\n"
                    + "The dependent_root value is `get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch - 1) - 1)` "
                    + "or the genesis block root in the case of underflow.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(
                    UINT64_TYPE.withDescription(
                        "An array of the validator indices for which to obtain the duties."),
                    Optional.of(1),
                    Optional.empty()))
            .pathParam(EPOCH_PARAMETER.withDescription("Should only be allowed 1 epoch ahead"))
            .response(SC_OK, "Success response", INCLUSION_LIST_DUTIES_TYPE)
            .withServiceUnavailableResponse()
            .withInternalErrorResponse()
            .build());
    this.syncDataProvider = syncDataProvider;
    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
      return;
    }

    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    final List<UInt64> requestBody = request.getRequestBody();
    final IntList indices = IntArrayList.toList(requestBody.stream().mapToInt(UInt64::intValue));

    SafeFuture<Optional<InclusionListDuties>> future =
        validatorDataProvider.getInclusionListDuties(epoch, indices);

    request.respondAsync(
        future.thenApply(
            maybeInclusionListDuties ->
                maybeInclusionListDuties
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(
                        () ->
                            AsyncApiResponse.respondWithError(
                                SC_SERVICE_UNAVAILABLE, "Service unavailable"))));
  }
}

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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.BODY_INTEGER_LIST;
import static tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties.PTC_DUTIES_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class PostPayloadTimelinessCommitteeDuties extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/duties/ptc/{epoch}";
  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;
  private final Spec spec;

  public PostPayloadTimelinessCommitteeDuties(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getSyncDataProvider(), spec, dataProvider.getValidatorDataProvider());
  }

  PostPayloadTimelinessCommitteeDuties(
      final SyncDataProvider syncDataProvider,
      final Spec spec,
      final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getPtcDuties")
            .summary("Get PTC duties")
            .description(
                "Requests the beacon node to provide a set of Payload Timeliness Committee (PTC) duties, which "
                    + "should be performed by validators, for a particular epoch. Duties should only need to be "
                    + "checked once per epoch, however a chain reorganization (of > MIN_SEED_LOOKAHEAD epochs) could occur, "
                    + "resulting in a change of duties. For full safety, you should monitor head_v2 events and confirm the "
                    + "dependent root in this response matches:\n"
                    + "- event.current_epoch_dependent_root when `compute_epoch_at_slot(event.slot) == epoch`\n"
                    + "- event.next_epoch_dependent_root when `compute_epoch_at_slot(event.slot) + 1 == epoch`\n\n"
                    + "The dependent_root value is `get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch - 1) - 1)` "
                    + "or the genesis block root in the case of underflow.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(BODY_INTEGER_LIST)
            .pathParam(EPOCH_PARAMETER)
            .response(SC_OK, "Success response", PTC_DUTIES_TYPE_DEFINITION)
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .withServiceUnavailableResponse()
            .build());
    this.syncDataProvider = syncDataProvider;
    this.spec = spec;
    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      request.respondError(
          SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing and not serving requests.");
      return;
    }

    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);

    if (spec.atEpoch(epoch).getMilestone().isLessThan(SpecMilestone.GLOAS)) {
      request.respondError(
          SC_BAD_REQUEST, "Cannot request PTC duties for epochs prior to gloas fork.");
      return;
    }

    final List<Integer> requestBody = request.getRequestBody();
    final IntList indices = IntArrayList.toList(requestBody.stream().mapToInt(Integer::intValue));

    final SafeFuture<Optional<PayloadTimelinessCommitteeDuties>> future =
        validatorDataProvider.getPayloadTimelinessCommitteeDuties(epoch, indices);

    request.respondAsync(
        future.thenApply(
            attesterDuties ->
                attesterDuties
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }
}

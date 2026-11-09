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

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetFinalizedCheckpointState extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/checkpoint/finalized_state";
  private final ChainDataProvider chainDataProvider;

  public GetFinalizedCheckpointState(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getChainDataProvider(), spec);
  }

  public GetFinalizedCheckpointState(final ChainDataProvider chainDataProvider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getFinalizedCheckpointState")
            .summary("Get full BeaconState object for finalized checkpoint state")
            .description(
                "Returns full BeaconState object for a finalized checkpoint state from the Weak Subjectivity period.")
            .tags(TAG_EXPERIMENTAL)
            .defaultResponseType(OCTET_STREAM)
            .response(
                SC_OK,
                "Request successful",
                sszResponseType(
                    beaconState ->
                        spec.getForkSchedule()
                            .getSpecMilestoneAtSlot(((BeaconState) beaconState).getSlot())))
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<StateAndMetaData>> future =
        chainDataProvider.getBeaconStateAndMetadata("finalized");

    request.respondAsync(
        future.thenApply(
            maybeStateAndMetaData ->
                maybeStateAndMetaData
                    .map(
                        stateAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              stateAndMetaData.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(stateAndMetaData.getData());
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}

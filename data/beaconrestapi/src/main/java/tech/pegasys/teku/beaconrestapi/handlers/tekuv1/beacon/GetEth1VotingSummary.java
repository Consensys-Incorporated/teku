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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider.VotingPeriodInfo;

public class GetEth1VotingSummary extends RestApiEndpoint {

  public static final String ROUTE = "/teku/v1/beacon/state/{state_id}/eth1voting";

  private static final SerializableTypeDefinition<Pair<Eth1Data, UInt64>> ETH1DATA_WITH_VOTES_TYPE =
      SerializableTypeDefinition.<Pair<Eth1Data, UInt64>>object()
          .name("Eth1DataWithVotes")
          .withField("eth1_data", Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(), Pair::getLeft)
          .withField("votes", CoreTypes.UINT64_TYPE, Pair::getRight)
          .build();

  private static final SerializableTypeDefinition<Eth1VotingSummary> ETH1VOTING_SUMMARY_TYPE =
      SerializableTypeDefinition.<Eth1VotingSummary>object()
          .name("Eth1VotingSummary")
          .withField(
              "state_eth1_data",
              Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(),
              Eth1VotingSummary::getStateEth1Data)
          .withField(
              "eth1_data_votes",
              listOf(ETH1DATA_WITH_VOTES_TYPE),
              Eth1VotingSummary::getEth1DataVotes)
          .withField("votes_required", CoreTypes.UINT64_TYPE, Eth1VotingSummary::getVotesRequired)
          .withField(
              "voting_period_slots", CoreTypes.UINT64_TYPE, Eth1VotingSummary::getVotingPeriodSlots)
          .withField(
              "voting_period_slots_left",
              CoreTypes.UINT64_TYPE,
              Eth1VotingSummary::getVotingPeriodSlotsLeft)
          .build();

  private static final SerializableTypeDefinition<Eth1VotingSummary>
      ETH1VOTING_SUMMARY_RESPONSE_TYPE =
          SerializableTypeDefinition.<Eth1VotingSummary>object()
              .name("GetEth1VotingSummaryResponse")
              .withField("data", ETH1VOTING_SUMMARY_TYPE, Function.identity())
              .build();

  private final ChainDataProvider chainDataProvider;
  private final Eth1DataProvider eth1DataProvider;

  public GetEth1VotingSummary(
      final DataProvider dataProvider, final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getEth1VotingSummary")
            .summary("Get Eth1 voting summary")
            .description(
                "Returns information about the current state of voting for Eth1Data from the specified state.")
            .pathParam(PARAMETER_STATE_ID)
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", ETH1VOTING_SUMMARY_RESPONSE_TYPE)
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = dataProvider.getChainDataProvider();
    this.eth1DataProvider = eth1DataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        chainDataProvider
            .getBeaconStateAndMetadata(request.getPathParameter(PARAMETER_STATE_ID))
            .thenApply(
                maybeStateAndMetadata -> {
                  if (maybeStateAndMetadata.isEmpty()) {
                    return AsyncApiResponse.respondNotFound();
                  } else {
                    List<Pair<Eth1Data, UInt64>> eth1DataVotes =
                        eth1DataProvider.getEth1DataVotes(maybeStateAndMetadata.get());
                    VotingPeriodInfo votingPeriodInfo =
                        eth1DataProvider.getVotingPeriodInfo(maybeStateAndMetadata.get());
                    return AsyncApiResponse.respondOk(
                        new Eth1VotingSummary(
                            maybeStateAndMetadata.get().getData().getEth1Data(),
                            eth1DataVotes,
                            votingPeriodInfo));
                  }
                }));
  }

  static class Eth1VotingSummary {
    private final Eth1Data stateEth1Data;
    private final List<Pair<Eth1Data, UInt64>> eth1DataVotes;
    private final VotingPeriodInfo votingPeriodInfo;

    public Eth1VotingSummary(
        final Eth1Data stateEth1Data,
        final List<Pair<Eth1Data, UInt64>> eth1DataVotes,
        final VotingPeriodInfo votingPeriodInfo) {
      this.stateEth1Data = stateEth1Data;
      this.eth1DataVotes = eth1DataVotes;
      this.votingPeriodInfo = votingPeriodInfo;
    }

    public Eth1Data getStateEth1Data() {
      return stateEth1Data;
    }

    public List<Pair<Eth1Data, UInt64>> getEth1DataVotes() {
      return eth1DataVotes;
    }

    public UInt64 getVotingPeriodSlots() {
      return votingPeriodInfo.getVotingSlots();
    }

    public UInt64 getVotingPeriodSlotsLeft() {
      return votingPeriodInfo.getVotingSlotsLeft();
    }

    public UInt64 getVotesRequired() {
      return votingPeriodInfo.getVotesRequired();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Eth1VotingSummary that = (Eth1VotingSummary) o;
      return Objects.equals(stateEth1Data, that.stateEth1Data)
          && Objects.equals(eth1DataVotes, that.eth1DataVotes)
          && Objects.equals(votingPeriodInfo, that.votingPeriodInfo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(stateEth1Data, eth1DataVotes, votingPeriodInfo);
    }
  }
}

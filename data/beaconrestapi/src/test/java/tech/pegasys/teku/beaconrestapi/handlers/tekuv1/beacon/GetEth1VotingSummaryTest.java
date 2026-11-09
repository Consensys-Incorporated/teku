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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1VotingSummary.Eth1VotingSummary;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.BeaconStateBuilderPhase0;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider.VotingPeriodInfo;

public class GetEth1VotingSummaryTest extends AbstractMigratedBeaconHandlerTest {
  private static final Eth1Data ETH1_DATA =
      new Eth1Data(
          Bytes32.fromHexString("d543a5c171f43007ec7a6871885a3faeb3fd8c4f5a810097508ffd301459aa22"),
          UInt64.valueOf(20),
          Bytes32.fromHexString(
              "0xaf01b1c1315d727d01f5991ae1481614a7f78e2beeefae22f48c76a05f973b0d"));
  private static final VotingPeriodInfo VOTING_PERIOD_INFO =
      new VotingPeriodInfo(UInt64.valueOf(50), UInt64.valueOf(100), UInt64.valueOf(30));

  private final DataProvider dataProvider = mock(DataProvider.class);
  private final Eth1DataProvider eth1DataProvider = mock(Eth1DataProvider.class);
  private Eth1VotingSummary eth1VotingSummary;

  @BeforeEach
  void setUp() {
    when(dataProvider.getChainDataProvider()).thenReturn(chainDataProvider);
    setHandler(new GetEth1VotingSummary(dataProvider, eth1DataProvider));
    request.setPathParameter("state_id", "head");
    BeaconState beaconState =
        BeaconStateBuilderPhase0.create(dataStructureUtil, spec, 16, 16)
            .eth1Data(ETH1_DATA)
            .build();
    when(chainDataProvider.getBeaconStateAndMetadata(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new StateAndMetaData(beaconState, SpecMilestone.PHASE0, false, true, false))));
    when(eth1DataProvider.getVotingPeriodInfo(any())).thenReturn(VOTING_PERIOD_INFO);
    List<Pair<Eth1Data, UInt64>> eth1Votes = new ArrayList<>();
    eth1Votes.add(Pair.of(ETH1_DATA, UInt64.valueOf(20)));
    when(eth1DataProvider.getEth1DataVotes(any())).thenReturn(eth1Votes);
    eth1VotingSummary = new Eth1VotingSummary(ETH1_DATA, eth1Votes, VOTING_PERIOD_INFO);
  }

  @Test
  public void shouldReturnNotFoundIfStateNotFound() throws Exception {
    when(chainDataProvider.getBeaconStateAndMetadata(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Not found"));
  }

  @Test
  public void shouldReturnVotingSummaryIfEverythingIsOk() throws Exception {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(eth1VotingSummary);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, eth1VotingSummary);
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"state_eth1_data\":{\"deposit_root\":\"0xd543a5c171f43007ec7a6871885a3faeb3fd8c4f5a810097508ffd301459aa22\",\"deposit_count\":\"20\",\"block_hash\":\"0xaf01b1c1315d727d01f5991ae1481614a7f78e2beeefae22f48c76a05f973b0d\"},\"eth1_data_votes\":[{\"eth1_data\":{\"deposit_root\":\"0xd543a5c171f43007ec7a6871885a3faeb3fd8c4f5a810097508ffd301459aa22\",\"deposit_count\":\"20\",\"block_hash\":\"0xaf01b1c1315d727d01f5991ae1481614a7f78e2beeefae22f48c76a05f973b0d\"},\"votes\":\"20\"}],\"votes_required\":\"50\",\"voting_period_slots\":\"100\",\"voting_period_slots_left\":\"30\"}}");
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}

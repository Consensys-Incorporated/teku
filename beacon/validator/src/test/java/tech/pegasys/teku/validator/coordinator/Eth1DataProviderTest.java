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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider.VotingPeriodInfo;

public class Eth1DataProviderTest {

  private static final int SLOTS_IN_VOTING_PERIOD_ASSERTION = 32;

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final BeaconState state = mock(BeaconState.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final Eth1DepositStorageChannel eth1DepositStorageChannel =
      mock(Eth1DepositStorageChannel.class);
  private final StateAndMetaData stateAndMetaData =
      new StateAndMetaData(state, SpecMilestone.PHASE0, false, true, false);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private Eth1DataProvider eth1DataProvider;

  @BeforeEach
  public void setup() {
    // Verifying slots in voting period just to make descriptive usage of test cases constants
    long slotsInVotingPeriod =
        spec.getEpochsPerEth1VotingPeriod(ZERO) * spec.getSlotsPerEpoch(ZERO);
    assertThat(slotsInVotingPeriod).isEqualTo(SLOTS_IN_VOTING_PERIOD_ASSERTION);

    final Eth1VotingPeriod eth1VotingPeriod = new Eth1VotingPeriod(spec);
    final Eth1DataCache eth1DataCache = new Eth1DataCache(spec, metricsSystem, eth1VotingPeriod);
    final DepositProvider depositProvider =
        new DepositProvider(
            new StubMetricsSystem(),
            recentChainData,
            eth1DataCache,
            storageUpdateChannel,
            eth1DepositStorageChannel,
            spec);
    eth1DataProvider = new Eth1DataProvider(eth1DataCache, depositProvider);

    // Defaults
    when(state.getEth1DataVotes()).thenReturn(SszListSchema.create(Eth1Data.SSZ_SCHEMA, 0).of());
    when(state.getSlot()).thenReturn(ONE);
  }

  @Test
  public void whenSlotInTheMiddle_VotingInfoIsCorrect() {
    VotingPeriodInfo votingPeriodInfo = eth1DataProvider.getVotingPeriodInfo(stateAndMetaData);
    assertThat(votingPeriodInfo.getVotingSlots()).isEqualTo(UInt64.valueOf(32));
    assertThat(votingPeriodInfo.getVotesRequired()).isEqualTo(UInt64.valueOf(16));
    assertThat(votingPeriodInfo.getVotingSlotsLeft()).isEqualTo(UInt64.valueOf(31));
  }

  @Test
  public void whenSlotAtLowerBound_VotingInfoIsCorrect() {
    when(state.getSlot()).thenReturn(ZERO);
    final VotingPeriodInfo votingPeriodInfo =
        eth1DataProvider.getVotingPeriodInfo(stateAndMetaData);
    assertThat(votingPeriodInfo.getVotingSlots()).isEqualTo(UInt64.valueOf(32));
    assertThat(votingPeriodInfo.getVotesRequired()).isEqualTo(UInt64.valueOf(16));
    assertThat(votingPeriodInfo.getVotingSlotsLeft()).isEqualTo(UInt64.valueOf(32));
  }

  @Test
  public void whenSlotAtUpperBound_VotingInfoIsCorrect() {
    when(state.getSlot()).thenReturn(UInt64.valueOf(31));
    final VotingPeriodInfo votingPeriodInfo =
        eth1DataProvider.getVotingPeriodInfo(stateAndMetaData);
    assertThat(votingPeriodInfo.getVotingSlots()).isEqualTo(UInt64.valueOf(32));
    assertThat(votingPeriodInfo.getVotesRequired()).isEqualTo(UInt64.valueOf(16));
    assertThat(votingPeriodInfo.getVotingSlotsLeft()).isEqualTo(UInt64.valueOf(1));
  }

  @Test
  public void whenNoVotes_eth1VotesBreakdownNotFails() {
    final List<Pair<Eth1Data, UInt64>> votesBreakdown =
        eth1DataProvider.getEth1DataVotes(stateAndMetaData);
    assertThat(votesBreakdown.isEmpty()).isTrue();
  }

  @Test
  public void whenEqualVotes_eth1VotesBreakdownReturnsBoth() {
    final Eth1Data eth1Data1 = createRandomEth1DataWithConstDeposits();
    when(state.getEth1Data()).thenReturn(eth1Data1);
    final Eth1Data eth1Data2 = createRandomEth1DataWithConstDeposits();
    final SszList<Eth1Data> eth1DataSszList = createEth1DataVotes(eth1Data1, eth1Data2);
    when(state.getEth1DataVotes()).thenReturn(eth1DataSszList);

    final List<Pair<Eth1Data, UInt64>> votesBreakdown =
        eth1DataProvider.getEth1DataVotes(stateAndMetaData);
    assertThat(votesBreakdown.size()).isEqualTo(2);
    final Set<UInt64> votes =
        votesBreakdown.stream().map(Pair::getRight).collect(Collectors.toSet());
    assertThat(votes.size()).isEqualTo(1);
    assertThat(votes.contains(ONE)).isTrue();
    final Set<Eth1Data> eth1DataSet =
        votesBreakdown.stream().map(Pair::getLeft).collect(Collectors.toSet());
    assertThat(eth1DataSet).contains(eth1Data1, eth1Data2);
  }

  @Test
  public void whenSeveralVotes_eth1VotesBreakdownSortedDescending() {
    final Eth1Data eth1Data1 = createRandomEth1DataWithConstDeposits();
    when(state.getEth1Data()).thenReturn(eth1Data1);
    final Eth1Data eth1Data2 = createRandomEth1DataWithConstDeposits();
    final Eth1Data eth1Data3 = createRandomEth1DataWithConstDeposits();
    final SszList<Eth1Data> eth1DataSszList =
        createEth1DataVotes(eth1Data1, eth1Data2, eth1Data1, eth1Data3);
    when(state.getEth1DataVotes()).thenReturn(eth1DataSszList);

    final List<Pair<Eth1Data, UInt64>> votesBreakdown =
        eth1DataProvider.getEth1DataVotes(stateAndMetaData);
    assertThat(votesBreakdown.size()).isEqualTo(3);
    assertThat(votesBreakdown.get(0).getRight()).isEqualTo(UInt64.valueOf(2));
    assertThat(votesBreakdown.get(0).getLeft()).isEqualTo(eth1Data1);
    assertThat(votesBreakdown.get(1).getRight()).isEqualTo(ONE);
    assertThat(votesBreakdown.get(2).getRight()).isEqualTo(ONE);
    final Set<Eth1Data> votedOnce = new HashSet<>();
    votedOnce.add(votesBreakdown.get(1).getLeft());
    votedOnce.add(votesBreakdown.get(2).getLeft());
    assertThat(votedOnce).contains(eth1Data2, eth1Data3);
  }

  private SszList<Eth1Data> createEth1DataVotes(final Eth1Data... eth1Data) {
    final List<Eth1Data> eth1DataList = new ArrayList<>(Arrays.asList(eth1Data));
    return spec.atSlot(state.getSlot())
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .getEth1DataVotesSchema()
        .createFromElements(eth1DataList);
  }

  private Eth1Data createRandomEth1DataWithConstDeposits() {
    return new Eth1Data(
        dataStructureUtil.randomBytes32(), UInt64.valueOf(5), dataStructureUtil.randomBytes32());
  }
}

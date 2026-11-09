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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class Eth1DataCacheTest {

  private static final UInt64 CACHE_DURATION = UInt64.valueOf(10_000);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  // Note: The slot and genesis time won't line up with the voting period start and end
  // This is semi-deliberate - if you use the Eth1VotingPeriod instance it all works,
  // if you duplicate the logic to do the math or depend on some property of that math, it will fail
  // It also saves us doing a bunch of math in this test...
  private static final UInt64 VOTING_PERIOD_START = UInt64.valueOf(50_000);
  private static final UInt64 VOTING_PERIOD_END = UInt64.valueOf(55_000);
  private static final UInt64 SLOT = UInt64.valueOf(125);
  private static final UInt64 GENESIS_TIME = UInt64.valueOf(77777);
  public static final UInt64 HEIGHT_1 = UInt64.valueOf(1000);
  public static final UInt64 IN_RANGE_TIMESTAMP_1 = UInt64.valueOf(51_000);
  public static final UInt64 HEIGHT_2 = UInt64.valueOf(1010);
  public static final UInt64 IN_RANGE_TIMESTAMP_2 = UInt64.valueOf(52_000);
  public static final UInt64 HEIGHT_3 = UInt64.valueOf(1020);
  public static final UInt64 IN_RANGE_TIMESTAMP_3 = UInt64.valueOf(53_000);
  private static final int STATE_DEPOSIT_COUNT = 10;

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Eth1Data stateEth1Data = createEth1Data(STATE_DEPOSIT_COUNT);
  private final Eth1VotingPeriod eth1VotingPeriod = mock(Eth1VotingPeriod.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private Eth1DataCache eth1DataCache;

  @BeforeEach
  void setUp() {
    when(eth1VotingPeriod.getCacheDurationInSeconds()).thenReturn(CACHE_DURATION);
    when(eth1VotingPeriod.getSpecRangeLowerBound(SLOT, GENESIS_TIME))
        .thenReturn(VOTING_PERIOD_START);
    when(eth1VotingPeriod.getSpecRangeUpperBound(SLOT, GENESIS_TIME)).thenReturn(VOTING_PERIOD_END);
    eth1DataCache = new Eth1DataCache(spec, metricsSystem, eth1VotingPeriod);
  }

  // Add tests for eth1 block with no votes
  // - Same block as a deposit
  // - Received out of order (always after deposits though)
  // - Multiple in a row
  // - Voting for a block with no deposits

  @Test
  void shouldUseDepositDataFromPreviousBlockWhenNoDepositBlockAddedAsLatestBlock() {
    final Eth1Data eth1Data = createEth1Data(STATE_DEPOSIT_COUNT);
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onEth1Block(HEIGHT_2, emptyBlockHash, IN_RANGE_TIMESTAMP_2);

    final Eth1Data eth1Vote = eth1DataCache.getEth1Vote(createBeaconStateWithVotes());
    assertThat(eth1Vote).isEqualTo(eth1Data.withBlockHash(emptyBlockHash));
  }

  @Test
  void shouldUseDepositFromPreviousBlockWhenNoDepositBlockAddedBetweenDeposits() {
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT + 1);
    final Eth1Data eth1Data2 = createEth1Data(eth1Data1.getDepositCount());
    final Eth1Data emptyBlockData = eth1Data1.withBlockHash(emptyBlockHash);
    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_3, eth1Data2, IN_RANGE_TIMESTAMP_3);
    eth1DataCache.onEth1Block(HEIGHT_2, emptyBlockHash, IN_RANGE_TIMESTAMP_2);

    final Eth1Data eth1Vote = eth1DataCache.getEth1Vote(createBeaconStateWithVotes(emptyBlockData));
    assertThat(eth1Vote).isEqualTo(emptyBlockData);
  }

  @Test
  void shouldUseEmptyMerkleTrieRootForBlocksWithNoEarlierDeposits() {
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    eth1DataCache.onEth1Block(HEIGHT_1, emptyBlockHash, IN_RANGE_TIMESTAMP_1);

    final BeaconState beaconState = createBeaconStateWithVotes();
    when(beaconState.getEth1Data()).thenReturn(new Eth1Data(Bytes32.ZERO, ZERO, Bytes32.ZERO));

    final Eth1Data eth1Vote = eth1DataCache.getEth1Vote(beaconState);
    assertThat(eth1Vote).isEqualTo(new Eth1Data(Eth1Data.EMPTY_DEPOSIT_ROOT, ZERO, emptyBlockHash));
  }

  @Test
  void shouldAcceptEmptyBlocksWhenAllPreviousDepositsHaveBeenPruned() {
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Bytes32 emptyBlockHash = dataStructureUtil.randomBytes32();
    eth1DataCache.onBlockWithDeposit(
        HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1.minus(CACHE_DURATION));

    // New block would normally prune the first one, but doesn't because we preserve one item
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, IN_RANGE_TIMESTAMP_2);
    assertThat(getCacheSize()).isEqualTo(2);

    // Then register an empty block within the voting period but prior to the latest deposit
    eth1DataCache.onEth1Block(HEIGHT_1, emptyBlockHash, IN_RANGE_TIMESTAMP_1);

    // And it should be recorded
    final Eth1Data emptyBlockData = eth1Data1.withBlockHash(emptyBlockHash);
    final BeaconState beaconState = createBeaconStateWithVotes(emptyBlockData);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(emptyBlockData);
  }

  @Test
  void majorityVoteWins() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    // Both Eth1Data timestamp inside the spec range
    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, IN_RANGE_TIMESTAMP_2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void smallestDistanceWinsIfNoMajority() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2);

    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, IN_RANGE_TIMESTAMP_2);

    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void oldVoteDoesNotCount() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, VOTING_PERIOD_START.minus(ONE));

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void tooRecentVoteDoesNotCount() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);

    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, VOTING_PERIOD_END.plus(ONE));

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void voteWithFewerDepositsThanCurrentStateDoesNotCount() {
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT - 1);

    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, IN_RANGE_TIMESTAMP_2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, eth1Data2, eth1Data2);
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data1);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainLive() {
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);
    eth1DataCache.onBlockWithDeposit(HEIGHT_1, eth1Data1, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data2, IN_RANGE_TIMESTAMP_2);

    BeaconState beaconState = createBeaconStateWithVotes();

    // The most recent Eth1Data in getVotesToConsider wins
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(eth1Data2);
  }

  @Test
  void noValidVotesInThisPeriod_eth1ChainNotLive() {
    BeaconState beaconState = createBeaconStateWithVotes(createEth1Data(STATE_DEPOSIT_COUNT));
    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(stateEth1Data);
  }

  @Test
  void shouldReturnStateEth1Data_ifFormerDepositMechanismHasBeenDisabled() {
    final Spec spec = mock(Spec.class);
    eth1DataCache = new Eth1DataCache(spec, new StubMetricsSystem(), eth1VotingPeriod);
    final BeaconState beaconState = createBeaconStateWithVotes(createEth1Data(STATE_DEPOSIT_COUNT));

    when(spec.isFormerDepositMechanismDisabled(beaconState)).thenReturn(true);

    assertThat(eth1DataCache.getEth1Vote(beaconState)).isEqualTo(beaconState.getEth1Data());
  }

  @Test
  void shouldPruneOldBlocksWhenNewerOnesReceived() {
    final UInt64 olderBlockTimestamp = ZERO;
    final UInt64 oldBlockTimestamp = olderBlockTimestamp.plus(ONE);
    final UInt64 newBlockTimestamp = oldBlockTimestamp.plus(CACHE_DURATION).plus(ONE);
    final UInt64 newerBlockTimestamp = newBlockTimestamp.plus(CACHE_DURATION);

    eth1DataCache.onBlockWithDeposit(ONE, createEth1Data(STATE_DEPOSIT_COUNT), olderBlockTimestamp);
    eth1DataCache.onBlockWithDeposit(
        ONE.increment(), createEth1Data(STATE_DEPOSIT_COUNT), oldBlockTimestamp);
    assertThat(getCacheSize()).isEqualTo(2);

    // Push both old blocks out of the cache period
    eth1DataCache.onBlockWithDeposit(
        UInt64.valueOf(10), createEth1Data(STATE_DEPOSIT_COUNT), newBlockTimestamp);
    // But only the oldest block gets pruned because we need at least one event prior to the cache
    // period so empty blocks right at the start of the period can lookup the
    assertThat(getCacheSize()).isEqualTo(2);

    // Third block is close enough to the second that they are both kept.
    // newBlockTimestamp is now exactly at the start of the cache period so we can remove oldBlock
    eth1DataCache.onBlockWithDeposit(
        UInt64.valueOf(20), createEth1Data(STATE_DEPOSIT_COUNT), newerBlockTimestamp);
    assertThat(getCacheSize()).isEqualTo(2);
  }

  @Test
  void shouldUpdateMetrics() {
    final BeaconState beaconState = getStateForMetricsAssertions();

    eth1DataCache.updateMetrics(beaconState);

    assertGaugeValue(Eth1DataCache.VOTES_TOTAL_METRIC_NAME, 10);
    assertGaugeValue(Eth1DataCache.VOTES_MAX_METRIC_NAME, 50);
    assertGaugeValue(Eth1DataCache.VOTES_UNKNOWN_METRIC_NAME, 2);
    assertGaugeValue(Eth1DataCache.VOTES_CURRENT_METRIC_NAME, 3);
    assertGaugeValue(Eth1DataCache.VOTES_BEST_METRIC_NAME, 4);
  }

  @Test
  void shouldNotUpdateMetrics_ifFormerDepositMechanismHasBeenDisabled() {
    final Spec spec = mock(Spec.class);
    eth1DataCache = new Eth1DataCache(spec, new StubMetricsSystem(), eth1VotingPeriod);
    final BeaconState beaconState = getStateForMetricsAssertions();

    when(spec.isFormerDepositMechanismDisabled(beaconState)).thenReturn(true);

    eth1DataCache.updateMetrics(beaconState);

    // all gauge values are 0
    assertGaugeValue(Eth1DataCache.VOTES_TOTAL_METRIC_NAME, 0);
    assertGaugeValue(Eth1DataCache.VOTES_MAX_METRIC_NAME, 0);
    assertGaugeValue(Eth1DataCache.VOTES_UNKNOWN_METRIC_NAME, 0);
    assertGaugeValue(Eth1DataCache.VOTES_CURRENT_METRIC_NAME, 0);
    assertGaugeValue(Eth1DataCache.VOTES_BEST_METRIC_NAME, 0);
  }

  @Test
  void shouldIncludeUnknownBlocksWhenCalculatingVoteBestMetric() {
    // Metric needs to indicate when a block will be voted in which happens even when unknown
    Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    Eth1Data unknownVote = createEth1Data(STATE_DEPOSIT_COUNT);

    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data1, IN_RANGE_TIMESTAMP_2);

    BeaconState beaconState = createBeaconStateWithVotes(eth1Data1, unknownVote, unknownVote);
    when(eth1VotingPeriod.getTotalSlotsInVotingPeriod(beaconState.getSlot())).thenReturn(50L);

    eth1DataCache.updateMetrics(beaconState);
    assertGaugeValue(Eth1DataCache.VOTES_BEST_METRIC_NAME, 2);
  }

  private BeaconState createBeaconStateWithVotes(final Eth1Data... votes) {
    SszList<Eth1Data> eth1DataVotes =
        SszListSchema.create(Eth1Data.SSZ_SCHEMA, votes.length).of(votes);
    final BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getSlot()).thenReturn(SLOT);
    when(beaconState.getGenesisTime()).thenReturn(GENESIS_TIME);
    when(beaconState.getEth1DataVotes()).thenReturn(eth1DataVotes);
    when(beaconState.getEth1Data()).thenReturn(stateEth1Data);
    return beaconState;
  }

  private Eth1Data createEth1Data(final int depositCount) {
    return createEth1Data(UInt64.valueOf(depositCount));
  }

  private Eth1Data createEth1Data(final UInt64 depositCount) {
    return new Eth1Data(
        dataStructureUtil.randomBytes32(), depositCount, dataStructureUtil.randomBytes32());
  }

  private int getCacheSize() {
    return (int)
        metricsSystem
            .getGauge(TekuMetricCategory.BEACON, Eth1DataCache.CACHE_SIZE_METRIC_NAME)
            .getValue();
  }

  private BeaconState getStateForMetricsAssertions() {
    final Eth1Data eth1Data1 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data eth1Data2 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data unknownVote1 = createEth1Data(STATE_DEPOSIT_COUNT);
    final Eth1Data unknownVote2 = createEth1Data(STATE_DEPOSIT_COUNT);

    eth1DataCache.onBlockWithDeposit(HEIGHT_1, stateEth1Data, IN_RANGE_TIMESTAMP_1);
    eth1DataCache.onBlockWithDeposit(HEIGHT_2, eth1Data1, IN_RANGE_TIMESTAMP_2);
    eth1DataCache.onBlockWithDeposit(HEIGHT_3, eth1Data2, IN_RANGE_TIMESTAMP_3);
    // Still unknown because it's out of range
    eth1DataCache.onBlockWithDeposit(UInt64.valueOf(55), unknownVote1, VOTING_PERIOD_END.plus(ONE));

    final BeaconState beaconState =
        createBeaconStateWithVotes(
            eth1Data1,
            eth1Data2,
            eth1Data2,
            eth1Data2,
            eth1Data2,
            unknownVote1,
            unknownVote2,
            stateEth1Data,
            stateEth1Data,
            stateEth1Data);
    when(eth1VotingPeriod.getTotalSlotsInVotingPeriod(beaconState.getSlot())).thenReturn(50L);

    return beaconState;
  }

  private void assertGaugeValue(final String metricName, final int expected) {
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, metricName).getValue())
        .isEqualTo(expected);
  }
}

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

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class Eth1DataCache {
  static final String CACHE_SIZE_METRIC_NAME = "eth1_block_cache_size";
  static final String VOTES_MAX_METRIC_NAME = "eth1_current_period_votes_max";
  static final String VOTES_TOTAL_METRIC_NAME = "eth1_current_period_votes";
  static final String VOTES_UNKNOWN_METRIC_NAME = "eth1_current_period_votes_unknown";
  static final String VOTES_CURRENT_METRIC_NAME = "eth1_current_period_votes_current";
  static final String VOTES_BEST_METRIC_NAME = "eth1_current_period_votes_best";

  private final Spec spec;
  private final Eth1VotingPeriod eth1VotingPeriod;
  private final UInt64 cacheDuration;

  private final NavigableMap<UInt64, Eth1DataAndHeight> eth1ChainCache =
      new ConcurrentSkipListMap<>();
  private final SettableGauge currentPeriodVotesTotal;
  private final SettableGauge currentPeriodVotesUnknown;
  private final SettableGauge currentPeriodVotesCurrent;
  private final SettableGauge currentPeriodVotesBest;
  private final SettableGauge currentPeriodVotesMax;

  public Eth1DataCache(
      final Spec spec, final MetricsSystem metricsSystem, final Eth1VotingPeriod eth1VotingPeriod) {
    this.spec = spec;
    this.eth1VotingPeriod = eth1VotingPeriod;
    cacheDuration = eth1VotingPeriod.getCacheDurationInSeconds();
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.BEACON,
        CACHE_SIZE_METRIC_NAME,
        "Total number of blocks stored in the Eth1 block cache",
        this::size);
    currentPeriodVotesMax =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            VOTES_MAX_METRIC_NAME,
            "Maximum number of votes that can possibly be cast in the current Eth1 voting period");
    currentPeriodVotesTotal =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            VOTES_TOTAL_METRIC_NAME,
            "Total number of votes cast in the current Eth1 voting period");
    currentPeriodVotesUnknown =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            VOTES_UNKNOWN_METRIC_NAME,
            "Number of votes for locally unknown Eth1 blocks in the current Eth1 voting period");
    currentPeriodVotesCurrent =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            VOTES_CURRENT_METRIC_NAME,
            "Number of votes for the current Eth1 data in the current Eth1 voting period");
    currentPeriodVotesBest =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            VOTES_BEST_METRIC_NAME,
            "Number of votes for the leading block in the current Eth1 voting period");
  }

  public void onBlockWithDeposit(
      final UInt64 blockHeight, final Eth1Data eth1Data, final UInt64 blockTimestamp) {
    eth1ChainCache.put(blockTimestamp, new Eth1DataAndHeight(eth1Data, blockHeight));
    prune(blockTimestamp);
  }

  public void onEth1Block(
      final UInt64 blockHeight, final Bytes32 blockHash, final UInt64 blockTimestamp) {
    final Map.Entry<UInt64, Eth1DataAndHeight> previousBlock =
        eth1ChainCache.floorEntry(blockTimestamp);
    final Eth1Data data;
    if (previousBlock == null) {
      data = new Eth1Data(Eth1Data.EMPTY_DEPOSIT_ROOT, UInt64.ZERO, blockHash);
    } else {
      data = previousBlock.getValue().getEth1Data().withBlockHash(blockHash);
    }
    eth1ChainCache.put(blockTimestamp, new Eth1DataAndHeight(data, blockHeight));
    prune(blockTimestamp);
  }

  public Eth1Data getEth1Vote(final BeaconState state) {
    if (spec.isFormerDepositMechanismDisabled(state)) {
      // no need for a real vote when Eth1 polling has been disabled
      return state.getEth1Data();
    }
    final NavigableMap<UInt64, Eth1Data> votesToConsider =
        getVotesToConsider(state.getSlot(), state.getGenesisTime(), state.getEth1Data());
    // Avoid using .values() directly as it has O(n) lookup which gets expensive fast
    final Set<Eth1Data> validBlocks = new HashSet<>(votesToConsider.values());
    final Map<Eth1Data, Eth1Vote> votes = countVotes(state);

    final Eth1Data defaultVote =
        votesToConsider.isEmpty() ? state.getEth1Data() : votesToConsider.lastEntry().getValue();

    final Optional<Eth1Data> vote =
        votes.entrySet().stream()
            .filter(entry -> validBlocks.contains(entry.getKey()))
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey);

    return vote.orElse(defaultVote);
  }

  public Collection<Eth1Data> getAllEth1Blocks() {
    return this.eth1ChainCache.values().stream().map(Eth1DataAndHeight::getEth1Data).toList();
  }

  public void updateMetrics(final BeaconState state) {
    if (spec.isFormerDepositMechanismDisabled(state)) {
      // no need to update metrics when Eth1 polling has been disabled
      return;
    }
    final Eth1Data currentEth1Data = state.getEth1Data();
    // Avoid using .values() directly as it has O(n) lookup which gets expensive fast
    final Set<Eth1Data> knownBlocks =
        new HashSet<>(
            getVotesToConsider(state.getSlot(), state.getGenesisTime(), currentEth1Data).values());
    final Map<Eth1Data, Eth1Vote> votes = countVotes(state);

    currentPeriodVotesMax.set(eth1VotingPeriod.getTotalSlotsInVotingPeriod(state.getSlot()));
    currentPeriodVotesTotal.set(state.getEth1DataVotes().size());
    currentPeriodVotesUnknown.set(
        votes.keySet().stream().filter(votedBlock -> !knownBlocks.contains(votedBlock)).count());
    currentPeriodVotesCurrent.set(
        votes.getOrDefault(currentEth1Data, new Eth1Vote(0)).getVoteCount());

    currentPeriodVotesBest.set(
        votes.values().stream()
            .max(Comparator.naturalOrder())
            .map(Eth1Vote::getVoteCount)
            .orElse(0));
  }

  protected Map<Eth1Data, Eth1Vote> countVotes(final BeaconState state) {
    final Map<Eth1Data, Eth1Vote> votes = new HashMap<>();
    int i = 0;
    for (Eth1Data eth1Data : state.getEth1DataVotes()) {
      final int currentIndex = i;
      votes.computeIfAbsent(eth1Data, key -> new Eth1Vote(currentIndex)).incrementVotes();
      i++;
    }
    return votes;
  }

  protected Optional<Eth1DataAndHeight> getEth1DataAndHeight(final Eth1Data eth1Data) {
    return eth1ChainCache.values().stream()
        .filter(eth1DataAndHeight -> eth1DataAndHeight.getEth1Data().equals(eth1Data))
        .findFirst();
  }

  private NavigableMap<UInt64, Eth1Data> getVotesToConsider(
      final UInt64 slot, final UInt64 genesisTime, final Eth1Data dataFromState) {
    final NavigableMap<UInt64, Eth1Data> unfiltered =
        Maps.transformValues(
            eth1ChainCache.subMap(
                eth1VotingPeriod.getSpecRangeLowerBound(slot, genesisTime),
                true,
                eth1VotingPeriod.getSpecRangeUpperBound(slot, genesisTime),
                true),
            Eth1DataAndHeight::getEth1Data);
    return Maps.filterValues(
        unfiltered,
        eth1Data ->
            eth1Data.getDepositCount().isGreaterThanOrEqualTo(dataFromState.getDepositCount()));
  }

  protected Eth1VotingPeriod getEth1VotingPeriod() {
    return eth1VotingPeriod;
  }

  private void prune(final UInt64 latestBlockTimestamp) {
    if (latestBlockTimestamp.compareTo(cacheDuration) <= 0 || eth1ChainCache.isEmpty()) {
      // Keep everything
      return;
    }
    final UInt64 earliestBlockTimestampToKeep = latestBlockTimestamp.minus(cacheDuration);
    // Make sure we have at least one entry prior to the cache period so that if we get an empty
    // block before any deposit in the cached period, we can look back and get the deposit info
    final UInt64 earliestKeyToKeep = eth1ChainCache.floorKey(earliestBlockTimestampToKeep);
    if (earliestKeyToKeep == null) {
      return;
    }
    eth1ChainCache.headMap(earliestKeyToKeep, false).clear();
  }

  private int size() {
    return eth1ChainCache.size();
  }

  protected static class Eth1DataAndHeight {
    private final Eth1Data eth1Data;
    private final UInt64 blockHeight;

    public Eth1DataAndHeight(final Eth1Data eth1Data, final UInt64 blockHeight) {
      this.eth1Data = eth1Data;
      this.blockHeight = blockHeight;
    }

    public Eth1Data getEth1Data() {
      return eth1Data;
    }

    public UInt64 getBlockHeight() {
      return blockHeight;
    }
  }
}

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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class Eth1DataProvider {
  private static final Comparator<Eth1Vote> REVERSE_VOTE_COMPARATOR =
      Comparator.comparingInt(Eth1Vote::getVoteCount).reversed();
  private final Eth1DataCache eth1DataCache;
  private final DepositProvider depositProvider;

  public Eth1DataProvider(
      final Eth1DataCache eth1DataCache, final DepositProvider depositProvider) {
    this.eth1DataCache = eth1DataCache;
    this.depositProvider = depositProvider;
  }

  public List<DepositWithIndex> getAvailableDeposits() {
    return depositProvider.getAvailableDeposits();
  }

  public Eth1Data getEth1Vote(final StateAndMetaData stateAndMetaData) {
    return eth1DataCache.getEth1Vote(stateAndMetaData.getData());
  }

  public Collection<Eth1Data> getEth1CachedBlocks() {
    return this.eth1DataCache.getAllEth1Blocks();
  }

  public List<Pair<Eth1Data, UInt64>> getEth1DataVotes(final StateAndMetaData stateAndMetaData) {
    final Map<Eth1Data, Eth1Vote> votes = eth1DataCache.countVotes(stateAndMetaData.getData());
    return votes.entrySet().stream()
        .sorted(Entry.comparingByValue(REVERSE_VOTE_COMPARATOR))
        .map(entry -> Pair.of(entry.getKey(), UInt64.valueOf(entry.getValue().getVoteCount())))
        .toList();
  }

  public VotingPeriodInfo getVotingPeriodInfo(final StateAndMetaData stateAndMetaData) {
    final BeaconState beaconState = stateAndMetaData.getData();
    final Eth1VotingPeriod eth1VotingPeriod = eth1DataCache.getEth1VotingPeriod();
    final UInt64 votingSlots =
        UInt64.valueOf(eth1VotingPeriod.getTotalSlotsInVotingPeriod(beaconState.getSlot()));
    final UInt64 startSlot = eth1VotingPeriod.computeVotingPeriodStartSlot(beaconState.getSlot());
    final UInt64 slotsLeft = startSlot.plus(votingSlots).minus(beaconState.getSlot());
    final UInt64 votesRequired = votingSlots.dividedBy(2);
    return new VotingPeriodInfo(votesRequired, votingSlots, slotsLeft);
  }

  public Optional<DepositTreeSnapshot> getFinalizedDepositTreeSnapshot() {
    return depositProvider.getFinalizedDepositTreeSnapshot();
  }

  public static class VotingPeriodInfo {
    private final UInt64 votesRequired;
    private final UInt64 votingSlots;
    private final UInt64 votingSlotsLeft;

    public VotingPeriodInfo(
        final UInt64 votesRequired, final UInt64 votingSlots, final UInt64 votingSlotsLeft) {
      this.votesRequired = votesRequired;
      this.votingSlots = votingSlots;
      this.votingSlotsLeft = votingSlotsLeft;
    }

    public UInt64 getVotesRequired() {
      return votesRequired;
    }

    public UInt64 getVotingSlots() {
      return votingSlots;
    }

    public UInt64 getVotingSlotsLeft() {
      return votingSlotsLeft;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      VotingPeriodInfo that = (VotingPeriodInfo) o;
      return Objects.equals(votesRequired, that.votesRequired)
          && Objects.equals(votingSlots, that.votingSlots)
          && Objects.equals(votingSlotsLeft, that.votingSlotsLeft);
    }

    @Override
    public int hashCode() {
      return Objects.hash(votesRequired, votingSlots, votingSlotsLeft);
    }
  }
}

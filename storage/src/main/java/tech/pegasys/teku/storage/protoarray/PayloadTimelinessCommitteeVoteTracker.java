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

package tech.pegasys.teku.storage.protoarray;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Tracks the per-root PTC vote material consumed by the Gloas fork-choice helpers
 * `payload_timeliness(...)` and `payload_data_availability(...)`.
 *
 * <p>This is a storage-side representation of the state updated by `notify_ptc_messages(...)` and
 * later queried from `should_extend_payload(...)` and `should_build_on_full(...)`:
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-notify_ptc_messages
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-payload_timeliness
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-payload_data_availability
 *
 * <p>Teku stores the information as per-root PTC position maps so fork choice can count positive
 * votes while reference tests can inspect the full true/false/null vote state. Duplicate validators
 * in the PTC must count once for each assigned position.
 */
class PayloadTimelinessCommitteeVoteTracker {

  private record VotesPerPtcPosition(Map<Integer, Boolean> payload, Map<Integer, Boolean> data) {}

  private final Map<Bytes32, VotesPerPtcPosition> votesByRoot = new ConcurrentHashMap<>();

  void recordVote(
      final Bytes32 blockRoot,
      final IntSet ptcPositions,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    votesByRoot.compute(
        blockRoot,
        (__, existingVotes) -> {
          final VotesPerPtcPosition updatedVotes =
              existingVotes != null
                  ? existingVotes
                  : new VotesPerPtcPosition(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
          ptcPositions.forEach(
              (int ptcPosition) -> {
                updatedVotes.payload.put(ptcPosition, payloadPresent);
                updatedVotes.data.put(ptcPosition, blobDataAvailable);
              });
          return updatedVotes;
        });
  }

  int getPayloadPresentVoteCount(final Bytes32 blockRoot) {
    return getPayloadPresentVoteCount(blockRoot, true);
  }

  int getPayloadVoteCount(final Bytes32 blockRoot) {
    final VotesPerPtcPosition votes = votesByRoot.get(blockRoot);
    return votes != null ? votes.payload.size() : 0;
  }

  int getPayloadPresentVoteCount(final Bytes32 blockRoot, final boolean payloadPresent) {
    return countVotes(votesByRoot.get(blockRoot), VotesPerPtcPosition::payload, payloadPresent);
  }

  int getDataAvailableVoteCount(final Bytes32 blockRoot) {
    return getDataAvailableVoteCount(blockRoot, true);
  }

  int getDataAvailableVoteCount(final Bytes32 blockRoot, final boolean dataAvailable) {
    return countVotes(votesByRoot.get(blockRoot), VotesPerPtcPosition::data, dataAvailable);
  }

  Optional<Boolean> getPayloadPresentVote(final Bytes32 blockRoot, final int ptcPosition) {
    final VotesPerPtcPosition votes = votesByRoot.get(blockRoot);
    return votes != null ? Optional.ofNullable(votes.payload.get(ptcPosition)) : Optional.empty();
  }

  Optional<Boolean> getDataAvailableVote(final Bytes32 blockRoot, final int ptcPosition) {
    final VotesPerPtcPosition votes = votesByRoot.get(blockRoot);
    return votes != null ? Optional.ofNullable(votes.data.get(ptcPosition)) : Optional.empty();
  }

  private int countVotes(
      final VotesPerPtcPosition votes,
      final Function<VotesPerPtcPosition, Map<Integer, Boolean>> voteSelector,
      final boolean expectedVote) {
    if (votes == null) {
      return 0;
    }
    return (int)
        voteSelector.apply(votes).values().stream().filter(vote -> vote == expectedVote).count();
  }

  void remove(final Bytes32 blockRoot) {
    votesByRoot.remove(blockRoot);
  }

  void removeIf(final Predicate<Bytes32> shouldRemove) {
    votesByRoot.keySet().removeIf(shouldRemove);
  }
}

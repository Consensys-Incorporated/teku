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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class StubVoteUpdater implements VoteUpdater {

  private final Map<UInt64, VoteTracker> votes = new HashMap<>();
  private UInt64 highestVotedIndex = UInt64.ZERO;

  @Override
  public VoteTracker getVote(final UInt64 validatorIndex) {
    return votes.getOrDefault(validatorIndex, VoteTracker.DEFAULT);
  }

  @Override
  public UInt64 getHighestVotedValidatorIndex() {
    return highestVotedIndex;
  }

  @Override
  public void putVote(final UInt64 validatorIndex, final VoteTracker vote) {
    highestVotedIndex = highestVotedIndex.max(validatorIndex);
    votes.put(validatorIndex, vote);
  }

  @Override
  public SlotAndForkChoiceNode applyForkChoiceScoreChanges(
      final UInt64 currentSlot,
      final UInt64 currentEpoch,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedCheckpointEffectiveBalances,
      final Optional<Bytes32> proposerBoostRoot,
      final UInt64 proposerScoreBoostAmount) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void commit() {
    // Nothing to do.
  }
}

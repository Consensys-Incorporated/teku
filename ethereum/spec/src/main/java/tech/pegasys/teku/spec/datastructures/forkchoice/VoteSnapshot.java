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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class VoteSnapshot {

  private final UInt64 highestVotedValidatorIndex;
  private final VoteTracker[] votes;

  private VoteSnapshot(final UInt64 highestVotedValidatorIndex, final VoteTracker[] votes) {
    this.highestVotedValidatorIndex = highestVotedValidatorIndex;
    this.votes = votes;
  }

  public static VoteSnapshot create(
      final UInt64 highestVotedValidatorIndex, final VoteTracker[] votes) {
    final int size = highestVotedValidatorIndex.intValue() + 1;
    return new VoteSnapshot(highestVotedValidatorIndex, Arrays.copyOf(votes, size));
  }

  public UInt64 getHighestVotedValidatorIndex() {
    return highestVotedValidatorIndex;
  }

  public int size() {
    return votes.length;
  }

  public VoteTracker getVote(final UInt64 validatorIndex) {
    return getVote(validatorIndex.intValue());
  }

  public VoteTracker getVote(final int validatorIndex) {
    checkArgument(validatorIndex >= 0, "Validator index must be non-negative");
    if (validatorIndex >= votes.length) {
      return VoteTracker.DEFAULT;
    }
    final VoteTracker vote = votes[validatorIndex];
    return vote != null ? vote : VoteTracker.DEFAULT;
  }
}

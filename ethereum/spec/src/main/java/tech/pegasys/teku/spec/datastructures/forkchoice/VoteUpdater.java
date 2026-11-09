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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public interface VoteUpdater {

  VoteTracker getVote(final UInt64 validatorIndex);

  UInt64 getHighestVotedValidatorIndex();

  void putVote(UInt64 validatorIndex, VoteTracker vote);

  SlotAndForkChoiceNode applyForkChoiceScoreChanges(
      UInt64 currentSlot,
      UInt64 currentEpoch,
      Checkpoint finalizedCheckpoint,
      Checkpoint justifiedCheckpoint,
      List<UInt64> justifiedCheckpointEffectiveBalances,
      Optional<Bytes32> proposerBoostRoot,
      UInt64 proposerScoreBoostAmount);

  void commit();
}

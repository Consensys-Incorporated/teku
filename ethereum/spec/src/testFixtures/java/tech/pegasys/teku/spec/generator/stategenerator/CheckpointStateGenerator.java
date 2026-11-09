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

package tech.pegasys.teku.spec.generator.stategenerator;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;

public abstract class CheckpointStateGenerator {

  public static CheckpointState generate(
      final Spec spec, final Checkpoint checkpoint, final SignedBlockAndState blockAndState) {
    checkArgument(
        blockAndState.getRoot().equals(checkpoint.getRoot()), "Block must match checkpoint root");

    // Derive checkpoint state
    final BeaconState state = regenerateCheckpointState(spec, checkpoint, blockAndState.getState());
    return CheckpointState.create(spec, checkpoint, blockAndState.getBlock(), state);
  }

  public static BeaconState regenerateCheckpointState(
      final Spec spec, final Checkpoint checkpoint, final BeaconState baseState) {
    if (baseState.getSlot().isGreaterThan(checkpoint.getEpochStartSlot(spec))) {
      throw new InvalidCheckpointException(
          "Checkpoint state must be at or prior to checkpoint slot boundary");
    }
    try {
      if (baseState.getSlot().equals(checkpoint.getEpochStartSlot(spec))) {
        return baseState;
      }

      return spec.processSlots(baseState, checkpoint.getEpochStartSlot(spec));
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      throw new InvalidCheckpointException(e);
    }
  }
}

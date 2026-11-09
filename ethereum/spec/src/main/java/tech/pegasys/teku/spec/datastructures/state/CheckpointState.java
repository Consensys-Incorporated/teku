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

package tech.pegasys.teku.spec.datastructures.state;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class CheckpointState {

  private final Checkpoint checkpoint;
  private final BeaconBlockSummary block;
  private final BeaconState state;

  private CheckpointState(
      final Spec spec,
      final Checkpoint checkpoint,
      final BeaconBlockSummary block,
      final BeaconState state) {
    checkNotNull(checkpoint);
    checkNotNull(block);
    checkNotNull(state);
    checkArgument(checkpoint.getRoot().equals(block.getRoot()), "Block must match checkpoint root");
    checkArgument(
        state.getSlot().equals(checkpoint.getEpochStartSlot(spec)),
        "State must be advanced to checkpoint epoch boundary slot");

    this.checkpoint = checkpoint;
    this.block = block;
    this.state = state;
  }

  public static CheckpointState create(
      final Spec spec,
      final Checkpoint checkpoint,
      final BeaconBlockSummary block,
      final BeaconState state) {
    return new CheckpointState(spec, checkpoint, block, state);
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public UInt64 getEpoch() {
    return getCheckpoint().getEpoch();
  }

  public Bytes32 getRoot() {
    return block.getRoot();
  }

  public UInt64 getBlockSlot() {
    return block.getSlot();
  }

  /**
   * @return The checkpoint state which is advanced to the checkpoint epoch boundary
   */
  public BeaconState getState() {
    return state;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof final CheckpointState that) {
      return Objects.equals(checkpoint, that.checkpoint)
          && Objects.equals(block, that.block)
          && Objects.equals(state, that.state);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkpoint, block, state);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("checkpoint", checkpoint)
        .add("block", block)
        .add("state", state)
        .toString();
  }
}

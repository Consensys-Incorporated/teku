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

package tech.pegasys.teku.storage.store;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class TransactionBlockData extends SignedBlockAndState {
  private final BlockCheckpoints checkpoints;

  public TransactionBlockData(
      final SignedBeaconBlock block, final BeaconState state, final BlockCheckpoints checkpoints) {
    super(block, state);
    this.checkpoints = checkpoints;
  }

  public BlockCheckpoints getCheckpoints() {
    return checkpoints;
  }

  public BlockAndCheckpoints toBlockAndCheckpoints() {
    return new BlockAndCheckpoints(getBlock(), checkpoints);
  }

  public SignedBlockAndState toSignedBlockAndState() {
    return new SignedBlockAndState(getBlock(), getState());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransactionBlockData that = (TransactionBlockData) o;
    return Objects.equals(getBlock(), that.getBlock())
        && Objects.equals(getState(), that.getState())
        && Objects.equals(checkpoints, that.checkpoints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBlock(), getState(), checkpoints);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("block", getBlock())
        .add("state", getState())
        .add("checkpoints", checkpoints)
        .toString();
  }
}

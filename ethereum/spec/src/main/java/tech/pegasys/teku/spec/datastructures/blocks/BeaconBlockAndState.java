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

package tech.pegasys.teku.spec.datastructures.blocks;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/** Helper datastructure that holds an unsigned block with its corresponding state */
public class BeaconBlockAndState extends StateAndBlockSummary {
  private final BeaconBlock block;

  public BeaconBlockAndState(final BeaconBlock block, final BeaconState state) {
    super(block, state);
    this.block = block;
  }

  public BeaconBlock getBlock() {
    return block;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final BeaconBlockAndState that = (BeaconBlockAndState) o;
    return Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), block);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("block", block).add("state", state).toString();
  }
}

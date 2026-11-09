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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/** Holds a block root and the state corresponding to this block's post-state */
public class BlockRootAndState {
  private final Bytes32 blockRoot;
  private final BeaconState state;

  public BlockRootAndState(final Bytes32 blockRoot, final BeaconState state) {
    this.blockRoot = blockRoot;
    this.state = state;
  }

  public BeaconState getState() {
    return state;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public UInt64 getSlot() {
    return state.getSlot();
  }
}

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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Data stored via this class is designed to be created and accessed during the same slot. When
 * generating a new BeaconState by mutating it via {@link
 * tech.pegasys.teku.spec.logic.StateTransition#processSlots} data will be cleared via
 * onSlotProcessed callback.
 *
 * <p>These caches are meant to store data used during the state transition and along block
 * production flow.
 */
public class SlotCaches {
  private volatile UInt64 blockProposerRewards = UInt64.ZERO;
  private volatile UInt256 blockExecutionValue = UInt256.ZERO;
  // the URL of the builder from which the bid was retrieved when proposing the block
  private volatile Optional<String> builderUrl = Optional.empty();

  private static final SlotCaches NO_OP_INSTANCE =
      new SlotCaches() {
        @Override
        public void increaseBlockProposerRewards(final UInt64 delta) {}

        @Override
        public void setBlockExecutionValue(final UInt256 blockExecutionValue) {}

        @Override
        public void setBuilderUrl(final String builderUrl) {}

        @Override
        public SlotCaches copy() {
          return this;
        }
      };

  private SlotCaches(
      final UInt64 blockProposerRewards,
      final UInt256 blockExecutionValue,
      final Optional<String> builderUrl) {
    this.blockProposerRewards = blockProposerRewards;
    this.blockExecutionValue = blockExecutionValue;
    this.builderUrl = builderUrl;
  }

  private SlotCaches() {}

  /** Creates new instance with clean caches */
  public static SlotCaches createNewEmpty() {
    return new SlotCaches();
  }

  /** Returns the instance which doesn't cache anything */
  public static SlotCaches getNoOp() {
    return NO_OP_INSTANCE;
  }

  public UInt64 getBlockProposerRewards() {
    return blockProposerRewards;
  }

  public UInt256 getBlockExecutionValue() {
    return blockExecutionValue;
  }

  public Optional<String> getBuilderUrl() {
    return builderUrl;
  }

  public void increaseBlockProposerRewards(final UInt64 delta) {
    // state transition is single threaded, so no need to do an atomic update
    this.blockProposerRewards = this.blockProposerRewards.plus(delta);
  }

  public void setBlockExecutionValue(final UInt256 blockExecutionValue) {
    this.blockExecutionValue = blockExecutionValue;
  }

  public void setBuilderUrl(final String builderUrl) {
    this.builderUrl = Optional.of(builderUrl);
  }

  public SlotCaches copy() {
    return new SlotCaches(blockProposerRewards, blockExecutionValue, builderUrl);
  }

  // Called at the end of every slot transition (processSlot)
  // Note: this is always called before the block is processed
  public void onSlotProcessed() {
    this.blockProposerRewards = UInt64.ZERO;
    this.blockExecutionValue = UInt256.ZERO;
    this.builderUrl = Optional.empty();
  }

  // Called at the end of every slot transition (processBlock)
  // this can be used to free cached data that are only used during the state transition and can be
  // garbage collected
  public void onBlockProcessed() {
    // add unit tests in StateCachesTest when adding cleanup logic here
  }
}

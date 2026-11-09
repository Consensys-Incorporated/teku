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

package tech.pegasys.teku.spec.datastructures.execution;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class SlotAndExecutionPayloadSummary {
  private final UInt64 slot;
  private final ExecutionPayloadSummary executionPayloadSummary;

  public SlotAndExecutionPayloadSummary(
      final UInt64 slot, final ExecutionPayloadSummary executionPayloadSummary) {
    this.slot = slot;
    this.executionPayloadSummary = executionPayloadSummary;
  }

  public static Optional<SlotAndExecutionPayloadSummary> fromBlock(final SignedBeaconBlock block) {
    return block
        .getMessage()
        .getBody()
        .getOptionalExecutionPayloadSummary()
        .map(payload -> new SlotAndExecutionPayloadSummary(block.getSlot(), payload));
  }

  public UInt64 getSlot() {
    return slot;
  }

  public ExecutionPayloadSummary getExecutionPayloadSummary() {
    return executionPayloadSummary;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SlotAndExecutionPayloadSummary that = (SlotAndExecutionPayloadSummary) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(executionPayloadSummary, that.executionPayloadSummary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, executionPayloadSummary);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("executionPayload", executionPayloadSummary)
        .toString();
  }
}

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
import java.util.Comparator;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlotAndBlockRoot implements Comparable<SlotAndBlockRoot> {
  private final UInt64 slot;
  private final Bytes32 blockRoot;

  public SlotAndBlockRoot(final UInt64 slot, final Bytes32 blockRoot) {
    this.slot = slot;
    this.blockRoot = blockRoot;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public String toLogString() {
    return LogFormatter.formatBlock(slot, blockRoot);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SlotAndBlockRoot that = (SlotAndBlockRoot) o;
    return Objects.equals(slot, that.slot) && Objects.equals(blockRoot, that.blockRoot);
  }

  @Override
  public int compareTo(final @NotNull SlotAndBlockRoot o) {
    return Comparator.comparing(SlotAndBlockRoot::getSlot)
        .thenComparing(SlotAndBlockRoot::getBlockRoot)
        .compare(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, blockRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("blockRoot", blockRoot)
        .toString();
  }
}

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

package tech.pegasys.teku.spec.datastructures.util;

import com.google.common.base.MoreObjects;
import java.util.Comparator;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

/** Key for storing blobs in DB */
public class SlotAndBlockRootAndBlobIndex implements Comparable<SlotAndBlockRootAndBlobIndex> {

  private final UInt64 slot;
  private final Bytes32 blockRoot;
  private final UInt64 blobIndex;

  public SlotAndBlockRootAndBlobIndex(
      final UInt64 slot, final Bytes32 blockRoot, final UInt64 blobIndex) {
    this.slot = slot;
    this.blockRoot = blockRoot;
    this.blobIndex = blobIndex;
  }

  public UInt64 getSlot() {
    return slot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public UInt64 getBlobIndex() {
    return blobIndex;
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return new SlotAndBlockRoot(slot, blockRoot);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SlotAndBlockRootAndBlobIndex that = (SlotAndBlockRootAndBlobIndex) o;
    return Objects.equals(slot, that.slot)
        && Objects.equals(blockRoot, that.blockRoot)
        && Objects.equals(blobIndex, that.blobIndex);
  }

  @Override
  public int compareTo(final @NotNull SlotAndBlockRootAndBlobIndex o) {
    return Comparator.comparing(SlotAndBlockRootAndBlobIndex::getSlot)
        .thenComparing(SlotAndBlockRootAndBlobIndex::getBlockRoot)
        .thenComparing(SlotAndBlockRootAndBlobIndex::getBlobIndex)
        .compare(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, blockRoot, blobIndex);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("blockRoot", blockRoot)
        .add("blobIndex", blobIndex)
        .toString();
  }
}

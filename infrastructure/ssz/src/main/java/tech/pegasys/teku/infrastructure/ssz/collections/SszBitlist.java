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

package tech.pegasys.teku.infrastructure.ssz.collections;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitlistImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;

/** Specialized implementation of {@code SszList<SszBit>} */
public interface SszBitlist extends SszPrimitiveList<Boolean, SszBit>, SszBitSet {

  static SszBitlist nullableOr(
      final @Nullable SszBitlist bitlist1OrNull, final @Nullable SszBitlist bitlist2OrNull) {
    return SszBitlistImpl.nullableOr(bitlist1OrNull, bitlist2OrNull);
  }

  @Override
  default SszMutablePrimitiveList<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  default boolean isWritableSupported() {
    return false;
  }

  @Override
  SszBitlistSchema<? extends SszBitlist> getSchema();

  // Bitlist methods

  BitSet getAsBitSet();

  BitSet getAsBitSet(int start, int end);

  int getLastSetBitIndex();

  /**
   * Performs a logical OR of this bit list with the bit list argument.
   *
   * @throws IllegalArgumentException if {@code other.getSize() > this.getSize()}
   */
  SszBitlist or(SszBitlist other);

  /** Returns individual bit value */
  boolean getBit(int i);

  @Override
  default boolean isSet(final int i) {
    return i < size() && getBit(i);
  }

  /** Returns the number of bits set to {@code true} in this {@code SszBitlist}. */
  int getBitCount();

  /**
   * Returns {@code true} if the specified {@link SszBitlist} has any bits set to true that are also
   * set to true in this {@link SszBitlist}.
   */
  boolean intersects(SszBitlist other);

  /**
   * Returns {@code true} if this {@link SszBitlist} has all bits set to true that are set to true
   * in the {@link SszBitlist} argument.
   */
  boolean isSuperSetOf(SszBitlist other);

  /** Returns indices of all bits set in this {@link SszBitlist} */
  IntList getAllSetBits();

  /** Streams indices of all bits set in this {@link SszBitlist} */
  @Override
  IntStream streamAllSetBits();

  @Override
  default Boolean getElement(final int index) {
    return getBit(index);
  }
}

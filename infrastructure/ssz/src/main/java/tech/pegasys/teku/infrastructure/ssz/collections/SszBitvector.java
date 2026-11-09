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
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;

/** Specialized implementation of {@code SszVector<SszBit>} */
public interface SszBitvector extends SszPrimitiveVector<Boolean, SszBit>, SszBitSet {

  @Override
  default SszMutablePrimitiveVector<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  default boolean isWritableSupported() {
    return false;
  }

  @Override
  SszBitvectorSchema<? extends SszBitvector> getSchema();

  // Bitlist methods

  SszBitvector withBit(int i);

  SszBitvector or(SszBitvector other);

  SszBitvector and(SszBitvector other);

  /** Returns individual bit value */
  boolean getBit(int i);

  /** Returns the number of bits set to {@code true} in this {@code SszBitlist}. */
  int getBitCount();

  BitSet getAsBitSet();

  @Override
  default boolean isSet(final int i) {
    return i < size() && getBit(i);
  }

  /** Returns new vector with bits shifted to the right by {@code n} positions */
  SszBitvector rightShift(int n);

  /** Returns indices of all bits set in this {@link SszBitvector} */
  IntList getAllSetBits();

  /** Returns last bit set index, -1 if no bits are set * */
  int getLastSetBitIndex();

  /** Streams indices of all bits set in this {@link SszBitvector} */
  @Override
  IntStream streamAllSetBits();

  @Override
  default Boolean getElement(final int index) {
    return getBit(index);
  }
}

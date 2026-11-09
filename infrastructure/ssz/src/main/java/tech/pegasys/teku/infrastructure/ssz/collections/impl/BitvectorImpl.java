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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.bitsCeilToBytes;

import com.google.common.base.Objects;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

class BitvectorImpl {

  public static BitvectorImpl fromBytes(final Bytes bytes, final int size) {
    checkArgument(
        bytes.size() == sszSerializationLength(size),
        "Incorrect data size (%s) for Bitvector of size %s",
        bytes.size(),
        size);
    BitSet bitset = new BitSet(size);

    final int bitsInLastByte = size % 8;
    if (bitsInLastByte != 0) {
      if ((bytes.get((size - 1) / 8) >>> bitsInLastByte) != 0) {
        throw new IllegalArgumentException("Invalid padding bits for Bitvector");
      }
    }

    for (int i = size - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        bitset.set(i);
      }
    }

    return new BitvectorImpl(bitset, size);
  }

  public static BitvectorImpl wrapBitSet(final BitSet bitSet, final int size) {
    final int length = bitSet.length();
    checkArgument(length <= size, "BitSet length (%s) exceeds the size (%s)", length, size);
    return new BitvectorImpl(bitSet, size);
  }

  public static int sszSerializationLength(final int size) {
    return bitsCeilToBytes(size);
  }

  private final BitSet data;
  private final int size;

  private BitvectorImpl(final BitSet bitSet, final int size) {
    this.data = bitSet;
    this.size = size;
  }

  public BitvectorImpl(final int size) {
    this.data = new BitSet(size);
    this.size = size;
  }

  public BitvectorImpl(final int size, final Iterable<Integer> indicesToSet) {
    this(size);
    for (int i : indicesToSet) {
      checkElementIndex(i, size);
      data.set(i);
    }
  }

  public BitvectorImpl(final int size, final int... indicesToSet) {
    this(size, Arrays.stream(indicesToSet).boxed().toList());
  }

  public List<Integer> getSetBitIndices() {
    return data.stream().boxed().toList();
  }

  public BitvectorImpl or(final BitvectorImpl other) {
    if (other.getSize() != getSize()) {
      throw new IllegalArgumentException(
          "Argument bitfield size is different: " + other.getSize() + " != " + getSize());
    }
    final BitSet newData = (BitSet) this.data.clone();
    newData.or(other.data);
    return new BitvectorImpl(newData, size);
  }

  public BitvectorImpl and(final BitvectorImpl other) {
    if (other.getSize() != getSize()) {
      throw new IllegalArgumentException(
          "Argument bitfield size is different: " + other.getSize() + " != " + getSize());
    }
    final BitSet newData = (BitSet) this.data.clone();
    newData.and(other.data);
    return new BitvectorImpl(newData, size);
  }

  public BitvectorImpl withBit(final int i) {
    checkElementIndex(i, size);
    BitSet newSet = (BitSet) data.clone();
    newSet.set(i);
    return new BitvectorImpl(newSet, size);
  }

  public int getBitCount() {
    return data.cardinality();
  }

  public boolean getBit(final int i) {
    checkElementIndex(i, size);
    return data.get(i);
  }

  public BitSet getAsBitSet() {
    return (BitSet) data.clone();
  }

  public int getSize() {
    return size;
  }

  public int getLastSetBitIndex() {
    return data.length() - 1;
  }

  public IntStream streamAllSetBits() {
    return data.stream();
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    byte[] array = new byte[sszSerializationLength(size)];
    IntStream.range(0, size).forEach(i -> array[i / 8] |= ((data.get(i) ? 1 : 0) << (i % 8)));
    return Bytes.wrap(array);
  }

  public BitvectorImpl rightShift(final int i) {
    final BitSet newData = new BitSet(size);
    for (int j = 0; j < size - i; j++) {
      if (this.getBit(j)) {
        newData.set(j + i);
      }
    }
    return new BitvectorImpl(newData, size);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof final BitvectorImpl bitvector) {

      return getSize() == bitvector.getSize() && Objects.equal(data, bitvector.data);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(data, getSize());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size; i++) {
      sb.append(getBit(i) ? 1 : 0);
    }
    return sb.toString();
  }
}

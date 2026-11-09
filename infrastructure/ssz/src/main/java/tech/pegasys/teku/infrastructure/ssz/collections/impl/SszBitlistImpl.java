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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszBitlistSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBitlistImpl extends SszListImpl<SszBit> implements SszBitlist {

  public static Bytes sszTruncateLeadingBit(final Bytes bytes, final int length) {
    Bytes bytesWithoutLast = bytes.slice(0, bytes.size() - 1);
    if (length % 8 == 0) {
      return bytesWithoutLast;
    } else {
      int lastByte = 0xFF & bytes.get(bytes.size() - 1);
      int leadingBit = 1 << (length % 8);
      int lastByteWithoutLeadingBit = lastByte ^ leadingBit;
      return Bytes.concatenate(bytesWithoutLast, Bytes.of(lastByteWithoutLeadingBit));
    }
  }

  public static int sszGetLengthAndValidate(final Bytes bytes) {
    int numBytes = bytes.size();
    checkArgument(numBytes > 0, "BitlistImpl must contain at least one byte");
    checkArgument(bytes.get(numBytes - 1) != 0, "BitlistImpl data must contain end marker bit");
    int lastByte = 0xFF & bytes.get(bytes.size() - 1);
    int leadingBitIndex = Integer.bitCount(Integer.highestOneBit(lastByte) - 1);
    return leadingBitIndex + 8 * (numBytes - 1);
  }

  public static SszBitlist nullableOr(
      final @Nullable SszBitlist bitlist1OrNull, final @Nullable SszBitlist bitlist2OrNull) {
    checkArgument(
        bitlist1OrNull != null || bitlist2OrNull != null,
        "At least one argument should be non-null");
    if (bitlist1OrNull == null) {
      return bitlist2OrNull;
    } else if (bitlist2OrNull == null) {
      return bitlist1OrNull;
    } else {
      return bitlist1OrNull.or(bitlist2OrNull);
    }
  }

  public static SszBitlistImpl ofBits(
      final SszBitlistSchema<?> schema, final int size, final int... bits) {
    return new SszBitlistImpl(schema, new BitlistImpl(size, schema.getMaxLength(), bits));
  }

  public static SszBitlistImpl wrapBitSet(
      final SszBitlistSchema<?> schema, final int size, final BitSet bitSet) {
    return new SszBitlistImpl(schema, BitlistImpl.wrapBitSet(size, schema.getMaxLength(), bitSet));
  }

  private final BitlistImpl value;

  public SszBitlistImpl(final SszListSchema<SszBit, ?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
    value = getBitlist(this);
  }

  public SszBitlistImpl(final SszBitlistSchema<?> schema, final BitlistImpl value) {
    super(
        schema,
        () ->
            ((SszBitlistSchemaImpl) schema)
                .createTreeFromBitData(value.getCurrentSize(), value.toByteArray()));
    this.value = value;
  }

  @Override
  public BitSet getAsBitSet() {
    return value.getAsBitSet();
  }

  @Override
  public BitSet getAsBitSet(final int start, final int end) {
    return value.getAsBitSet(start, end);
  }

  @Override
  public int getLastSetBitIndex() {
    return value.getLastSetBitIndex();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszBitlistSchema<SszBitlist> getSchema() {
    return (SszBitlistSchema<SszBitlist>) super.getSchema();
  }

  @Override
  protected IntCache<SszBit> createCache() {
    // BitlistImpl is far more effective cache than caching individual bits
    return IntCache.noop();
  }

  private BitlistImpl toBitlistImpl(final SszBitlist bl) {
    return ((SszBitlistImpl) bl).value;
  }

  @Override
  public SszBitlist or(final SszBitlist other) {
    return new SszBitlistImpl(getSchema(), value.or(toBitlistImpl(other)));
  }

  @Override
  public boolean getBit(final int i) {
    return value.getBit(i);
  }

  @Override
  public int getBitCount() {
    return value.getBitCount();
  }

  @Override
  public boolean intersects(final SszBitlist other) {
    return value.intersects(toBitlistImpl(other));
  }

  @Override
  public boolean isSuperSetOf(final SszBitlist other) {
    return value.isSuperSetOf(toBitlistImpl(other));
  }

  @Override
  public IntList getAllSetBits() {
    return value.getAllSetBits();
  }

  @Override
  public IntStream streamAllSetBits() {
    return value.streamAllSetBits();
  }

  @Override
  protected int sizeImpl() {
    return value.getCurrentSize();
  }

  private static BitlistImpl getBitlist(final SszList<SszBit> bitlistView) {
    return BitlistImpl.fromSszBytes(
        bitlistView.sszSerialize(), bitlistView.getSchema().getMaxLength());
  }

  @Override
  public SszMutablePrimitiveList<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public String toString() {
    return "SszBitlist{size=" + this.size() + ", " + value.toString() + "}";
  }

  public static SszBitlist fromBytes(final SszBitlistSchema<?> schema, final Bytes value) {
    return new SszBitlistImpl(schema, BitlistImpl.fromSszBytes(value, schema.getMaxLength()));
  }
}

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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszCollection;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszProgressiveBitlistImpl extends AbstractSszCollection<SszBit> implements SszBitlist {

  private final BitlistImpl value;

  public SszProgressiveBitlistImpl(
      final SszProgressiveBitlistSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
    this.value = BitlistImpl.fromSszBytes(this.sszSerialize(), Long.MAX_VALUE);
  }

  private SszProgressiveBitlistImpl(
      final SszProgressiveBitlistSchema schema, final BitlistImpl value) {
    super(schema, () -> schema.createTreeFromBitData(value.getCurrentSize(), value.toByteArray()));
    this.value = value;
  }

  public static SszProgressiveBitlistImpl ofBits(
      final SszProgressiveBitlistSchema schema, final int size, final int... bits) {
    return new SszProgressiveBitlistImpl(schema, new BitlistImpl(size, Long.MAX_VALUE, bits));
  }

  public static SszProgressiveBitlistImpl wrapBitSet(
      final SszProgressiveBitlistSchema schema, final int size, final BitSet bitSet) {
    return new SszProgressiveBitlistImpl(
        schema, BitlistImpl.wrapBitSet(size, Long.MAX_VALUE, bitSet));
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszBitlistSchema<SszBitlist> getSchema() {
    return (SszBitlistSchema<SszBitlist>) super.getSchema();
  }

  @Override
  protected int sizeImpl() {
    return value.getCurrentSize();
  }

  @Override
  protected IntCache<SszBit> createCache() {
    return IntCache.noop();
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

  @Override
  public SszBitlist or(final SszBitlist other) {
    return new SszProgressiveBitlistImpl(
        (SszProgressiveBitlistSchema) getSchema(),
        value.or(((SszProgressiveBitlistImpl) other).value));
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
    return value.intersects(((SszProgressiveBitlistImpl) other).value);
  }

  @Override
  public boolean isSuperSetOf(final SszBitlist other) {
    return value.isSuperSetOf(((SszProgressiveBitlistImpl) other).value);
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
  protected void checkIndex(final int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for progressive bitlist with size " + size());
    }
  }

  @Override
  public SszMutablePrimitiveList<Boolean, SszBit> createWritableCopy() {
    return new SszMutableProgressiveBitlistImpl(this);
  }

  @Override
  public boolean isWritableSupported() {
    return true;
  }

  @Override
  public String toString() {
    return "SszProgressiveBitlist{size=" + this.size() + ", " + value.toString() + "}";
  }
}

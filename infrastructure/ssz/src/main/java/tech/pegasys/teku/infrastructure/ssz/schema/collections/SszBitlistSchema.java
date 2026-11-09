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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import java.util.BitSet;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszBitlistSchemaImpl;

public interface SszBitlistSchema<SszBitlistT extends SszBitlist>
    extends SszPrimitiveListSchema<Boolean, SszBit, SszBitlistT> {

  static SszBitlistSchema<SszBitlist> create(final long maxLength) {
    return new SszBitlistSchemaImpl(maxLength);
  }

  default SszBitlistT empty() {
    return ofBits(0);
  }

  SszBitlistT ofBits(int size, int... setBitIndices);

  /**
   * Creates an SszBitlist with the size inferred from the set bit indices. The size is set to one
   * past the highest set bit index, or 0 if no bits are set. Works for both bounded and progressive
   * schemas.
   */
  default SszBitlistT ofSetBits(final int... setBitIndices) {
    final int size =
        setBitIndices.length == 0 ? 0 : IntStream.of(setBitIndices).max().getAsInt() + 1;
    return ofBits(size, setBitIndices);
  }

  /**
   * Creates an SszBitlist by wrapping a given bitSet. This is an optimized constructor that DOES
   * NOT clone the bitSet. It is used in aggregating attestation pool. DO NOT MUTATE after the
   * wrapping!! SszBitlist is supposed to be immutable.
   */
  SszBitlistT wrapBitSet(int size, BitSet bitSet);

  /**
   * Creates an SszBitlist by wrapping a given bitSet with the size inferred from the bitSet's
   * logical length. Works for both bounded and progressive schemas.
   */
  default SszBitlistT wrapBitSet(final BitSet bitSet) {
    return wrapBitSet(bitSet.length(), bitSet);
  }

  /**
   * Creates a SszBitlist from bytes.
   *
   * @param bytes The bytes to create the SszBitlist from
   * @return A new SszBitlist instance
   */
  SszBitlistT fromBytes(Bytes bytes);

  /**
   * Creates a SszBitlist from a hexadecimal string.
   *
   * @param hexString The hexadecimal string to create the SszBitlist from
   * @return A new SszBitlist instance
   */
  default SszBitlistT fromHexString(final String hexString) {
    return fromBytes(Bytes.fromHexString(hexString));
  }
}

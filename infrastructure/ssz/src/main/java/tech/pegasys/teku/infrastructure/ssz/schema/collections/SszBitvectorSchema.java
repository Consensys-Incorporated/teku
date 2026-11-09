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
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszBitvectorSchemaImpl;

public interface SszBitvectorSchema<SszBitvectorT extends SszBitvector>
    extends SszPrimitiveVectorSchema<Boolean, SszBit, SszBitvectorT> {

  static SszBitvectorSchema<SszBitvector> create(final long length) {
    return new SszBitvectorSchemaImpl(length);
  }

  SszBitvectorT ofBits(int... setBitIndices);

  /**
   * Creates an SszBitvector by wrapping a given bitSet. This is an optimized constructor that DOES
   * NOT clone the bitSet. It is used in aggregating attestation pool. DO NOT MUTATE after the
   * wrapping!! SszBitvector is supposed to be immutable.
   */
  SszBitvectorT wrapBitSet(int size, BitSet bitSet);

  default SszBitvectorT fromBytes(final Bytes bitvectorBytes) {
    return sszDeserialize(bitvectorBytes);
  }

  default SszBitvectorT ofBits(final Iterable<Integer> setBitIndices) {
    int[] indicesArray =
        StreamSupport.stream(setBitIndices.spliterator(), false).mapToInt(i -> i).toArray();
    return ofBits(indicesArray);
  }
}

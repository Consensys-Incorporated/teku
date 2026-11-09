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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;

public interface SszByteVector extends SszPrimitiveVector<Byte, SszByte> {

  static SszByteVector fromBytes(final Bytes byteVector) {
    return SszByteVectorSchema.create(byteVector.size()).fromBytes(byteVector);
  }

  static Bytes32 computeHashTreeRoot(final Bytes byteVector) {
    return fromBytes(byteVector).hashTreeRoot();
  }

  default Bytes getBytes() {
    byte[] data = new byte[size()];
    for (int i = 0; i < size(); i++) {
      data[i] = getElement(i);
    }
    return Bytes.wrap(data);
  }

  @Override
  default SszMutablePrimitiveVector<Byte, SszByte> createWritableCopy() {
    throw new UnsupportedOperationException("Not supported here");
  }
}

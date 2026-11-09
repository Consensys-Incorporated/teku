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

import java.util.stream.IntStream;

public interface SszBitSet {

  /** Streams indices of all bits set in this {@link SszBitSet} */
  IntStream streamAllSetBits();

  /**
   * Returns true if the bit at index i is set. Returns false if i is beyond the size of this set.
   *
   * @param i the index to check
   * @return true if the bit is set, false if unset or beyond the length of the set.
   */
  boolean isSet(int i);

  default boolean isSuperSetOf(final SszBitSet other) {
    return other.streamAllSetBits().allMatch(this::isSet);
  }
}

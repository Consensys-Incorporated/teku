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

package tech.pegasys.teku.infrastructure.ssz.schema;

/**
 * How a collection schema packs its elements in supernodes. The elements form groups, and every
 * supernode of a group holds the same number of elements: a bounded list or a vector is a single
 * group, a progressive list has one for each of its levels.
 */
public interface SszSuperNodeLayout {

  /** The group of the element at {@code elementIndex}. */
  int groupOf(int elementIndex);

  /** The index of the first element of a group. */
  int groupStart(int group);

  /**
   * The depth of the supernodes of a group, which hold {@code 2^depth} elements each, or 0 when the
   * elements of the group have nodes of their own.
   */
  int superNodeDepth(int group);

  /** A single group, starting at the first element, of supernodes of the same depth. */
  static SszSuperNodeLayout uniform(final int superNodeDepth) {
    return new SszSuperNodeLayout() {
      @Override
      public int groupOf(final int elementIndex) {
        return 0;
      }

      @Override
      public int groupStart(final int group) {
        return 0;
      }

      @Override
      public int superNodeDepth(final int group) {
        return superNodeDepth;
      }
    };
  }
}

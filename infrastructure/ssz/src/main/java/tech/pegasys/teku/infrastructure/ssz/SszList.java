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

package tech.pegasys.teku.infrastructure.ssz;

import tech.pegasys.teku.infrastructure.ssz.impl.SszElementBytes;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;

/**
 * Immutable SSZ List structure
 *
 * @param <SszElementT> Type of list elements
 */
public interface SszList<SszElementT extends SszData> extends SszCollection<SszElementT> {

  @Override
  SszMutableList<SszElementT> createWritableCopy();

  @Override
  SszListSchema<SszElementT, ?> getSchema();

  /** Returns the number of elements in this list */
  @Override
  int size();

  /**
   * Visits the serialized bytes of the elements {@code fromIndex} to {@code size() - 1} in index
   * order, see {@link #forEachElementSsz(int, int, SszElementVisitor)}.
   *
   * @return {@code true} when every element was visited, {@code false} when the visitor stopped
   */
  default boolean forEachElementSsz(
      final int fromIndex, final SszElementVisitor<SszElementT> visitor) {
    return forEachElementSsz(fromIndex, Integer.MAX_VALUE, visitor);
  }

  /**
   * Visits the serialized bytes of the elements {@code fromIndex} (inclusive) to {@code toIndex}
   * (exclusive, clamped to {@code size()}) in index order, without creating element views. Nothing
   * is visited when {@code toIndex <= fromIndex}. Only lists of fixed-size composite elements are
   * supported.
   *
   * <p>The visitor receives a {@link SszElementBytes} cursor whose fields are read by the handles
   * of the element schema ({@link SszContainerSchema#uint64Field(String)} and siblings). Elements
   * packed in supernodes are read in place from the supernode's own content, so a scan of a packed
   * list allocates nothing per element; other elements are serialized one by one. The cursor is
   * reused from element to element: it is only valid during the call, must not be stored or shared,
   * and is invalidated when the walk ends. It is not a substitute for an immutable view.
   *
   * <p>The walk reads an immutable backing tree, and on a mutable list the size and the set of
   * changed indices are taken before the walk starts. The visitor may therefore set or update the
   * element it is currently visiting; it must not change any other element.
   *
   * @return {@code true} when every element was visited, {@code false} when the visitor stopped
   */
  boolean forEachElementSsz(int fromIndex, int toIndex, SszElementVisitor<SszElementT> visitor);
}

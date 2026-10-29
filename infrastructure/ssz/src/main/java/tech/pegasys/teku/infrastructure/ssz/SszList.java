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
   * order, without creating element views. Only lists of fixed-size composite elements are
   * supported.
   *
   * <p>Elements packed in supernodes are handed out as a range of the supernode's own content, so a
   * scan of a packed list allocates nothing per element; other elements are serialized one by one.
   * The bytes are only valid during the call and must not be modified.
   *
   * <p>The walk reads an immutable backing tree, and on a mutable list the size and the set of
   * changed indices are taken before the walk starts. The visitor may therefore set or update the
   * element it is currently visiting; it must not change any other element.
   *
   * @return {@code true} when every element was visited, {@code false} when the visitor stopped
   */
  boolean forEachElementSsz(int fromIndex, SszElementVisitor visitor);
}

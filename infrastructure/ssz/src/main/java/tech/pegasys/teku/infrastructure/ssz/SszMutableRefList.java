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

import java.util.function.Consumer;

/**
 * Represents a mutable {@link SszList} which is able to return a mutable child 'by reference' Any
 * modifications made to such child are reflected in this list and its backing tree
 *
 * @param <SszElementT> Class of immutable child views
 * @param <SszMutableElementT> Class of the corresponding mutable child views
 */
public interface SszMutableRefList<
        SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends SszMutableRefComposite<SszElementT, SszMutableElementT>, SszMutableList<SszElementT> {

  /**
   * Returns a mutable child at index 'by reference' Any modifications made to such child are
   * reflected in this structure and its backing tree
   *
   * @throws IndexOutOfBoundsException if index >= size()
   */
  @Override
  SszMutableElementT getByRef(int index);

  /**
   * Appends a new empty element to the list and returns its writeable reference for modification
   */
  default SszMutableElementT append() {
    SszElementT newElement = getSchema().getElementSchema().getDefault();
    append(newElement);
    return getByRef(size() - 1);
  }

  /** Just a functional style helper for {@link #append()} */
  default void append(final Consumer<SszMutableElementT> mutator) {
    mutator.accept(append());
  }
}

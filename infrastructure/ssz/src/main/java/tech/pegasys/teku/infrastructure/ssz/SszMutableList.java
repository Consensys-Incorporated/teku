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

/**
 * Mutable SSZ List with immutable elements. This type of list can be modified by setting or
 * appending immutable elements
 *
 * @param <SszElementT> Type of list elements
 */
public interface SszMutableList<SszElementT extends SszData>
    extends SszMutableCollection<SszElementT>, SszList<SszElementT> {

  @Override
  void set(int index, SszElementT value);

  @Override
  SszList<SszElementT> commitChanges();

  /**
   * Appends a new immutable value to the end of the list. Size is incremented
   *
   * @throws IndexOutOfBoundsException if size would exceed maxLength
   */
  default void append(final SszElementT value) {
    set(size(), value);
  }

  default void appendAll(final Iterable<SszElementT> elements) {
    elements.forEach(this::append);
  }
}

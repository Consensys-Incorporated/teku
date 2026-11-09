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
import java.util.function.Function;

/**
 * Represents composite mutable ssz structure which has descendant ssz structures
 *
 * @param <SszChildT> the type of children
 */
public interface SszMutableComposite<SszChildT extends SszData>
    extends SszMutableData, SszComposite<SszChildT> {

  /**
   * Sets the function which should called by the implementation on any changes in this structure or
   * its descendant structures.
   *
   * <p>This is to propagate changes up in the data hierarchy from child mutable structures to
   * parent mutable structures.
   *
   * @param listener listener to be called with this instance as a parameter
   */
  void setInvalidator(Consumer<SszMutableData> listener);

  /**
   * Sets the child at the index.
   *
   * <p>If {@code index == size()} and the structure is extendable (e.g. List) then this is treated
   * as `append()` operation and the size is expanded. In this case `size` should be less than
   * `maxSize`
   *
   * @throws IndexOutOfBoundsException if index > size() or if index == size() but size() == maxSize
   */
  void set(int index, SszChildT value);

  /**
   * Similar to {@link #set(int, SszData)} but using modifier function which may consider old value
   * to calculate new value. The implementation may potentially optimize this case.
   */
  default void update(final int index, final Function<SszChildT, SszChildT> mutator) {
    set(index, mutator.apply(get(index)));
  }

  default void setAll(final Iterable<SszChildT> newChildren) {
    clear();
    int idx = 0;
    for (SszChildT newChild : newChildren) {
      set(idx++, newChild);
    }
  }

  @Override
  SszComposite<SszChildT> commitChanges();
}

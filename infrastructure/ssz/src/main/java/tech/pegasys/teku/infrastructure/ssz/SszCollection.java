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

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;

public interface SszCollection<ElementT extends SszData>
    extends SszComposite<ElementT>, Iterable<ElementT> {

  default boolean isEmpty() {
    return size() == 0;
  }

  default List<ElementT> asList() {
    return new AbstractList<>() {
      private final int cachedSize = SszCollection.this.size();

      @Override
      public ElementT get(int index) {
        return SszCollection.this.get(index);
      }

      @Override
      public int size() {
        return cachedSize;
      }
    };
  }

  @NotNull
  @Override
  default Iterator<ElementT> iterator() {
    return asList().iterator();
  }

  default Stream<ElementT> stream() {
    return asList().stream();
  }

  @Override
  SszCollectionSchema<ElementT, ?> getSchema();
}

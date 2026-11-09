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

import java.util.AbstractList;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;

public interface SszPrimitiveCollection<ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends SszCollection<SszElementT> {

  default ElementT getElement(final int index) {
    return get(index).get();
  }

  @Override
  SszCollectionSchema<SszElementT, ?> getSchema();

  @Override
  SszMutablePrimitiveCollection<ElementT, SszElementT> createWritableCopy();

  default List<ElementT> asListUnboxed() {
    return new AbstractList<>() {
      private final int cachedSize = SszPrimitiveCollection.this.size();

      @Override
      public ElementT get(int index) {
        return SszPrimitiveCollection.this.getElement(index);
      }

      @Override
      public int size() {
        return cachedSize;
      }
    };
  }

  default Stream<ElementT> streamUnboxed() {
    return asListUnboxed().stream();
  }
}

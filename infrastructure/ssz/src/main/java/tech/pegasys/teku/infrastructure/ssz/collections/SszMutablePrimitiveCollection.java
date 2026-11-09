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

import tech.pegasys.teku.infrastructure.ssz.SszMutableComposite;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;

public interface SszMutablePrimitiveCollection<ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends SszPrimitiveCollection<ElementT, SszElementT>, SszMutableComposite<SszElementT> {

  @SuppressWarnings("unchecked")
  default SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  default void setElement(final int index, final ElementT primitiveValue) {
    SszElementT sszData = getPrimitiveElementSchema().boxed(primitiveValue);
    set(index, sszData);
  }

  default void setAllElements(final Iterable<ElementT> newChildren) {
    clear();
    int idx = 0;
    for (ElementT newChild : newChildren) {
      setElement(idx++, newChild);
    }
  }

  @Override
  SszPrimitiveCollection<ElementT, SszElementT> commitChanges();
}

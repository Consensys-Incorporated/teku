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

import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;

public interface SszMutablePrimitiveList<ElementT, SszElementT extends SszPrimitive<ElementT>>
    extends SszMutablePrimitiveCollection<ElementT, SszElementT>,
        SszMutableList<SszElementT>,
        SszPrimitiveList<ElementT, SszElementT> {

  default void appendElement(final ElementT newElement) {
    append(getPrimitiveElementSchema().boxed(newElement));
  }

  default void appendAllElements(final Iterable<? extends ElementT> newElements) {
    newElements.forEach(this::appendElement);
  }

  @Override
  SszPrimitiveList<ElementT, SszElementT> commitChanges();

  @Override
  SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy();
}

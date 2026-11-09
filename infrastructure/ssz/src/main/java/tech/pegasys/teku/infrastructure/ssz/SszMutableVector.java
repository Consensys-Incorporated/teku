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
 * Mutable {@link SszVector} with immutable elements. This type of vector can be modified by setting
 * immutable elements
 *
 * @param <ElementType> Type of elements
 */
public interface SszMutableVector<ElementType extends SszData>
    extends SszMutableCollection<ElementType>, SszVector<ElementType> {

  /**
   * Set all elements to the given value.
   *
   * @param value The value to set
   */
  default void setAll(final ElementType value) {
    setAll(value, 0, size());
  }

  @Override
  SszVector<ElementType> commitChanges();
}

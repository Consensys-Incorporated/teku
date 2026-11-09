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

package tech.pegasys.teku.infrastructure.ssz.schema;

import java.util.Arrays;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszUnion;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszUnionSchemaImpl;

public interface SszUnionSchema<SszUnionT extends SszUnion> extends SszSchema<SszUnionT> {
  int SELECTOR_SIZE_BYTES = 1;
  int NONE_VALUE_SELECTOR = 0;

  static SszUnionSchema<SszUnion> create(final SszSchema<?>... childrenSchemas) {
    return create(Arrays.asList(childrenSchemas));
  }

  static SszUnionSchema<SszUnion> create(final List<SszSchema<?>> childrenSchemas) {
    return SszUnionSchemaImpl.createGenericSchema(childrenSchemas);
  }

  List<SszSchema<?>> getChildrenSchemas();

  /**
   * Returns the child schema for selector.
   *
   * @throws IndexOutOfBoundsException if selector >= getTypesCount
   */
  default SszSchema<?> getChildSchema(final int selector) {
    return getChildrenSchemas().get(selector);
  }

  default int getTypesCount() {
    return getChildrenSchemas().size();
  }

  SszUnionT createFromValue(int selector, SszData value);

  @Override
  default boolean isFixedSize() {
    return false;
  }

  @Override
  default int getSszFixedPartSize() {
    return SELECTOR_SIZE_BYTES;
  }
}

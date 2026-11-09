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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszOptionalSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public interface SszOptionalSchema<
        ElementDataT extends SszData, SszOptionalT extends SszOptional<ElementDataT>>
    extends SszSchema<SszOptionalT> {

  static <ElementDataT extends SszData>
      SszOptionalSchema<ElementDataT, SszOptional<ElementDataT>> create(
          final SszSchema<ElementDataT> childSchema) {
    return SszOptionalSchemaImpl.createGenericSchema(childSchema);
  }

  SszSchema<ElementDataT> getChildSchema();

  SszOptional<ElementDataT> createFromValue(Optional<ElementDataT> value);

  boolean isPresent(TreeNode node);

  TreeNode getValueNode(TreeNode node);

  @Override
  default boolean isFixedSize() {
    return false;
  }

  @Override
  default int getSszFixedPartSize() {
    return 0;
  }
}

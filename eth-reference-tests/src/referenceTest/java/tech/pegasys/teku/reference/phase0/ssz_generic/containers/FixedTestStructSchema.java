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

package tech.pegasys.teku.reference.phase0.ssz_generic.containers;

import static tech.pegasys.teku.reference.phase0.ssz_generic.containers.UInt32PrimitiveSchema.UINT32_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class FixedTestStructSchema
    extends ContainerSchema3<FixedTestStruct, SszByte, SszUInt64, SszUInt32> {

  public FixedTestStructSchema() {
    super(
        FixedTestStruct.class.getSimpleName(),
        NamedSchema.of("A", SszPrimitiveSchemas.UINT8_SCHEMA),
        NamedSchema.of("B", SszPrimitiveSchemas.UINT64_SCHEMA),
        NamedSchema.of("C", UINT32_SCHEMA));
  }

  @Override
  public FixedTestStruct createFromBackingNode(final TreeNode node) {
    return new FixedTestStruct(this, node);
  }
}

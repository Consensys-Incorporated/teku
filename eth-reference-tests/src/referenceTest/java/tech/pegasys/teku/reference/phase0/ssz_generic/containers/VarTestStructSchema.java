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

import static tech.pegasys.teku.reference.phase0.ssz_generic.containers.UInt16PrimitiveSchema.UINT16_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class VarTestStructSchema
    extends ContainerSchema3<VarTestStruct, SszUInt16, SszList<SszUInt16>, SszByte> {

  public VarTestStructSchema() {
    super(
        VarTestStructSchema.class.getSimpleName(),
        NamedSchema.of("A", UINT16_SCHEMA),
        NamedSchema.of("B", SszListSchema.create(UINT16_SCHEMA, 1024)),
        NamedSchema.of("C", SszPrimitiveSchemas.UINT8_SCHEMA));
  }

  @Override
  public VarTestStruct createFromBackingNode(final TreeNode node) {
    return new VarTestStruct(this, node);
  }
}

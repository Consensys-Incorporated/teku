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

package tech.pegasys.teku.infrastructure.ssz.custom;

import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszCustomBytes48Schema extends SszByteVectorSchemaImpl<SszCustomBytes48> {
  public static final SszCustomBytes48Schema INSTANCE = new SszCustomBytes48Schema();

  private SszCustomBytes48Schema() {
    super(SszPrimitiveSchemas.BYTE_SCHEMA, 48);
  }

  @Override
  public SszCustomBytes48 createFromBackingNode(final TreeNode node) {
    return new SszCustomBytes48(node);
  }

  @Override
  public String toString() {
    return "CustomBytes48";
  }
}

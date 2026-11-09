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

package tech.pegasys.teku.spec.datastructures.type;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszKZGCommitmentSchema extends SszByteVectorSchemaImpl<SszKZGCommitment> {
  private static final int KZG_COMMITMENT_SIZE = 48;

  public static final SszKZGCommitmentSchema INSTANCE = new SszKZGCommitmentSchema();

  private SszKZGCommitmentSchema() {
    super(SszPrimitiveSchemas.BYTE_SCHEMA, KZG_COMMITMENT_SIZE);
  }

  @Override
  protected DeserializableTypeDefinition<SszKZGCommitment> createTypeDefinition() {
    return SszPrimitiveTypeDefinitions.sszSerializedType(this, "Bytes48 hexadecimal");
  }

  @Override
  public SszKZGCommitment createFromBackingNode(final TreeNode node) {
    return new SszKZGCommitment(node);
  }
}

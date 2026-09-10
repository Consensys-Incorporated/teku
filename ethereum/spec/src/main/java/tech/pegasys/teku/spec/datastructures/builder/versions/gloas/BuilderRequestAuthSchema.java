/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.builder.versions.gloas;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BuilderRequestAuthSchema
    extends ContainerSchema2<BuilderRequestAuth, SszByteList, SszUInt64> {

  public BuilderRequestAuthSchema(final long maxDataSize) {
    super(
        "BuilderRequestAuth",
        namedSchema("data", SszByteListSchema.create(maxDataSize)),
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public BuilderRequestAuth create(final Bytes data, final UInt64 slot) {
    return new BuilderRequestAuth(this, getDataSchema().fromBytes(data), slot);
  }

  @SuppressWarnings("unchecked")
  public SszByteListSchema<?> getDataSchema() {
    return (SszByteListSchema<?>) getFieldSchema0();
  }

  @Override
  public BuilderRequestAuth createFromBackingNode(final TreeNode node) {
    return new BuilderRequestAuth(this, node);
  }
}

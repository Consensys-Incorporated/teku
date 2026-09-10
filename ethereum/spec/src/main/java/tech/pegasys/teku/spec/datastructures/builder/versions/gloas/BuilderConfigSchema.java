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

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BuilderConfigSchema
    extends ContainerSchema3<BuilderConfig, SszUInt64, SszUInt64, SszList<BuilderEntry>> {

  private static final long MAX_BUILDER_ENTRIES = 64;

  public BuilderConfigSchema(final BuilderEntrySchema builderEntrySchema) {
    super(
        "BuilderConfig",
        namedSchema("min_bid", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("builder_boost_factor", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("builders", SszListSchema.create(builderEntrySchema, MAX_BUILDER_ENTRIES)));
  }

  public BuilderConfig create(
      final UInt64 minBid, final UInt64 builderBoostFactor, final List<BuilderEntry> builders) {
    return new BuilderConfig(this, minBid, builderBoostFactor, builders);
  }

  @Override
  public BuilderConfig createFromBackingNode(final TreeNode node) {
    return new BuilderConfig(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderEntry, ?> getBuildersSchema() {
    return (SszListSchema<BuilderEntry, ?>) getChildSchema(getFieldIndex("builders"));
  }
}

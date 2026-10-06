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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.schemas.ApiSchemas;

public class BuilderConfig
    extends Container3<BuilderConfig, SszUInt64, SszUInt64, SszList<BuilderEntry>> {

  protected BuilderConfig(
      final BuilderConfigSchema schema,
      final UInt64 minBid,
      final UInt64 builderBoostFactor,
      final List<BuilderEntry> builders) {
    super(
        schema,
        SszUInt64.of(minBid),
        SszUInt64.of(builderBoostFactor),
        schema.getBuildersSchema().createFromElements(builders));
  }

  // used primarily for testing (non-biased value comparison between local and builder bids)
  public static final BuilderConfig NO_OP = withBuilderBoostFactor(UInt64.valueOf(100));

  // used primarily for backwards compatibility with milestones prior to Gloas
  public static BuilderConfig withBuilderBoostFactor(final UInt64 builderBoostFactor) {
    return ApiSchemas.BUILDER_CONFIG_SCHEMA.create(UInt64.ZERO, builderBoostFactor, List.of());
  }

  protected BuilderConfig(final BuilderConfigSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public UInt64 getMinBid() {
    return getField0().get();
  }

  public UInt64 getBuilderBoostFactor() {
    return getField1().get();
  }

  public SszList<BuilderEntry> getBuilders() {
    return getField2();
  }

  @Override
  public BuilderConfigSchema getSchema() {
    return (BuilderConfigSchema) super.getSchema();
  }
}

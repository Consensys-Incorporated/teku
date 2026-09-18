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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BuilderPreferencesSchema extends ContainerSchema1<BuilderPreferences, SszUInt64> {

  public BuilderPreferencesSchema() {
    super(
        "BuilderPreferences",
        namedSchema("max_execution_payment", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public BuilderPreferences create(final UInt64 maxExecutionPayment) {
    return new BuilderPreferences(this, maxExecutionPayment);
  }

  @Override
  public BuilderPreferences createFromBackingNode(final TreeNode node) {
    return new BuilderPreferences(this, node);
  }
}

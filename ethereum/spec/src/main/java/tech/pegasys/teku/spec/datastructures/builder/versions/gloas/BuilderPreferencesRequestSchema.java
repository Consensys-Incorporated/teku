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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BuilderPreferencesRequestSchema
    extends ContainerSchema2<
        BuilderPreferencesRequest, BuilderPreferences, SignedBuilderRequestAuth> {

  public BuilderPreferencesRequestSchema(
      final BuilderPreferencesSchema builderPreferencesSchema,
      final SignedBuilderRequestAuthSchema authSchema) {
    super(
        "BuilderPreferencesRequestV1",
        namedSchema("preferences", builderPreferencesSchema),
        namedSchema("auth", authSchema));
  }

  public BuilderPreferencesRequest create(
      final BuilderPreferences preferences, final SignedBuilderRequestAuth auth) {
    return new BuilderPreferencesRequest(this, preferences, auth);
  }

  @Override
  public BuilderPreferencesRequest createFromBackingNode(final TreeNode node) {
    return new BuilderPreferencesRequest(this, node);
  }
}

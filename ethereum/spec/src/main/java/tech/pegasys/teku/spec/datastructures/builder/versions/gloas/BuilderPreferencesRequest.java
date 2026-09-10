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

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BuilderPreferencesRequest
    extends Container2<BuilderPreferencesRequest, BuilderPreferences, SignedBuilderRequestAuth> {

  BuilderPreferencesRequest(
      final BuilderPreferencesRequestSchema schema,
      final BuilderPreferences preferences,
      final SignedBuilderRequestAuth auth) {
    super(schema, preferences, auth);
  }

  BuilderPreferencesRequest(
      final BuilderPreferencesRequestSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public BuilderPreferences getPreferences() {
    return getField0();
  }

  public SignedBuilderRequestAuth getAuth() {
    return getField1();
  }

  @Override
  public BuilderPreferencesRequestSchema getSchema() {
    return (BuilderPreferencesRequestSchema) super.getSchema();
  }
}

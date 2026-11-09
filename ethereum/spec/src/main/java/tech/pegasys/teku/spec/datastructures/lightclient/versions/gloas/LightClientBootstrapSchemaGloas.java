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

package tech.pegasys.teku.spec.datastructures.lightclient.versions.gloas;

import static tech.pegasys.teku.spec.constants.LightClientConstants.CURRENT_SYNC_COMMITTEE_GINDEX_GLOAS;

import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientBootstrapSchemaGloas extends LightClientBootstrapSchema {
  public LightClientBootstrapSchemaGloas(
      final SpecConfigGloas specConfigGloas,
      final SchemaRegistry schemaRegistry,
      final String schemaName) {
    super(specConfigGloas, CURRENT_SYNC_COMMITTEE_GINDEX_GLOAS, schemaRegistry, schemaName);
  }
}

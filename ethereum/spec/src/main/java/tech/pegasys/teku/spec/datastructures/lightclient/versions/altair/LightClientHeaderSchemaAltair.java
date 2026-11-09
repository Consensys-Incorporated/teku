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

package tech.pegasys.teku.spec.datastructures.lightclient.versions.altair;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;

public class LightClientHeaderSchemaAltair
    extends ContainerSchema1<LightClientHeaderAltair, BeaconBlockHeader>
    implements LightClientHeaderSchema<LightClientHeaderAltair> {

  public LightClientHeaderSchemaAltair(final String schemaName) {
    super(schemaName, namedSchema("beacon", BeaconBlockHeader.SSZ_SCHEMA));
  }

  @Override
  public LightClientHeaderAltair create(final BeaconBlockHeader header) {
    return new LightClientHeaderAltair(this, header);
  }

  @Override
  public LightClientHeaderAltair createFromBackingNode(final TreeNode node) {
    return new LightClientHeaderAltair(this, node);
  }

  @Override
  public LightClientHeaderSchemaAltair toVersionAltairRequired() {
    return this;
  }
}

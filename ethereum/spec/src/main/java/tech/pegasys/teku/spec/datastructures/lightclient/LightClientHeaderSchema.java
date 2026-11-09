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

package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.versions.altair.LightClientHeaderSchemaAltair;
import tech.pegasys.teku.spec.datastructures.lightclient.versions.capella.LightClientHeaderSchemaCapella;
import tech.pegasys.teku.spec.datastructures.lightclient.versions.gloas.LightClientHeaderSchemaGloas;

public interface LightClientHeaderSchema<T extends LightClientHeader>
    extends SszContainerSchema<T> {

  T create(BeaconBlockHeader header);

  @Override
  T createFromBackingNode(TreeNode node);

  default LightClientHeaderSchemaAltair toVersionAltairRequired() {
    throw new UnsupportedOperationException("Not an Altair light client header");
  }

  default LightClientHeaderSchemaCapella toVersionCapellaRequired() {
    throw new UnsupportedOperationException("Not a Capella light client header");
  }

  default LightClientHeaderSchemaGloas toVersionGloasRequired() {
    throw new UnsupportedOperationException("Not a Gloas light client header");
  }
}

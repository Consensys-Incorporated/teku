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

package tech.pegasys.teku.spec.datastructures.blobs.versions.fulu;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CELL_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class DataColumnSchemaFulu extends AbstractSszListSchema<Cell, DataColumn>
    implements DataColumnSchema {

  public DataColumnSchemaFulu(final SpecConfigDeneb specConfig, final SchemaRegistry registry) {
    super(registry.get(CELL_SCHEMA), specConfig.getMaxBlobCommitmentsPerBlock());
  }

  @Override
  public DataColumn create(final List<Cell> cells) {
    final TreeNode backingNode = this.createTreeFromElements(cells);
    return createFromBackingNode(backingNode);
  }

  @Override
  public DataColumn createFromBackingNode(final TreeNode node) {
    return new DataColumn(this, node);
  }
}

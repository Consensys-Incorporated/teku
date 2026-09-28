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

package tech.pegasys.teku.spec.datastructures.blobs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CELL_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.schema.AbstractSszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * Gloas {@code DataColumn}: a progressive (EIP-7916) list of {@link Cell}s. Unlike the bounded Fulu
 * {@code List[Cell, MAX_BLOB_COMMITMENTS_PER_BLOCK]}, the progressive list has no fixed maximum
 * capacity and uses a progressive merkle tree for stable generalized indices. Serialization is
 * identical to a regular list; only the merkleization differs.
 */
public class DataColumnSchemaGloas extends AbstractSszProgressiveListSchema<Cell, DataColumn>
    implements DataColumnSchema {

  public DataColumnSchemaGloas(final SpecConfigDeneb specConfig, final SchemaRegistry registry) {
    super(
        registry.get(CELL_SCHEMA),
        SszSchemaHints.none(),
        specConfig.getMaxBlobCommitmentsPerBlock());
  }

  @Override
  public DataColumn create(final List<Cell> columnCells) {
    return createFromElements(columnCells);
  }

  @Override
  public DataColumn createFromBackingNode(final TreeNode node) {
    return new DataColumn(this, node);
  }
}

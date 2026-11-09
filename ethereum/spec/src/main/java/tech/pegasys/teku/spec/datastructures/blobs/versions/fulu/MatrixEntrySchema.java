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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class MatrixEntrySchema
    extends ContainerSchema4<MatrixEntry, Cell, SszKZGProof, SszUInt64, SszUInt64> {

  MatrixEntrySchema(final CellSchema cellSchema) {
    super(
        "MatrixEntry",
        namedSchema("cell", cellSchema),
        namedSchema("kzg_proof", SszKZGProofSchema.INSTANCE),
        namedSchema("column_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("row_index", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public CellSchema getCellSchema() {
    return (CellSchema) getFieldSchema0();
  }

  public MatrixEntry create(
      final Cell cell, final KZGProof kzgProof, final UInt64 columnIndex, final UInt64 rowIndex) {
    return new MatrixEntry(
        this, cell, new SszKZGProof(kzgProof), SszUInt64.of(columnIndex), SszUInt64.of(rowIndex));
  }

  public MatrixEntry create(
      final KZGCell cell, final KZGProof kzgProof, final int columnIndex, final int rowIndex) {
    return new MatrixEntry(
        this,
        getCellSchema().create(cell.bytes()),
        new SszKZGProof(kzgProof),
        SszUInt64.of(UInt64.valueOf(columnIndex)),
        SszUInt64.of(UInt64.valueOf(rowIndex)));
  }

  public static MatrixEntrySchema create(final CellSchema cellSchema) {
    return new MatrixEntrySchema(cellSchema);
  }

  @Override
  public MatrixEntry createFromBackingNode(final TreeNode node) {
    return new MatrixEntry(this, node);
  }
}

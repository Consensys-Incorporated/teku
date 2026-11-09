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

import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class MatrixEntry extends Container4<MatrixEntry, Cell, SszKZGProof, SszUInt64, SszUInt64> {

  MatrixEntry(final MatrixEntrySchema matrixEntrySchema, final TreeNode backingTreeNode) {
    super(matrixEntrySchema, backingTreeNode);
  }

  public MatrixEntry(
      final MatrixEntrySchema schema,
      final Cell cell,
      final KZGProof kzgProof,
      final UInt64 columnIndex,
      final UInt64 rowIndex) {
    super(
        schema, cell, new SszKZGProof(kzgProof), SszUInt64.of(columnIndex), SszUInt64.of(rowIndex));
  }

  public MatrixEntry(
      final MatrixEntrySchema schema,
      final Cell cell,
      final SszKZGProof sszKzgProof,
      final SszUInt64 sszColumnIndex,
      final SszUInt64 sszRowIndex) {
    super(schema, cell, sszKzgProof, sszColumnIndex, sszRowIndex);
  }

  public Cell getCell() {
    return getField0();
  }

  public KZGProof getKzgProof() {
    return getField1().getKZGProof();
  }

  public UInt64 getColumnIndex() {
    return getField2().get();
  }

  public UInt64 getRowIndex() {
    return getField3().get();
  }
}

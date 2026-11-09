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

package tech.pegasys.teku.spec.propertytest.suppliers.blobs.versions.deneb;

import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;

import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;

public class BlobAndCellProofsSupplier extends DataStructureUtilSupplier<BlobAndCellProofs> {
  public BlobAndCellProofsSupplier() {
    super(
        dataStructureUtil ->
            new BlobAndCellProofs(
                dataStructureUtil.randomValidBlob(),
                dataStructureUtil.randomKZGProofs(CELLS_PER_EXT_BLOB)),
        SpecMilestone.FULU);
  }
}

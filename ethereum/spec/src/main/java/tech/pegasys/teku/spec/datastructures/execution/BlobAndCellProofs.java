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

package tech.pegasys.teku.spec.datastructures.execution;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;

import java.util.List;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;

public record BlobAndCellProofs(Blob blob, List<KZGProof> cellProofs) {
  public BlobAndCellProofs(final Blob blob, final List<KZGProof> cellProofs) {
    checkArgument(
        cellProofs.size() == CELLS_PER_EXT_BLOB,
        "Expected %s proofs but got %s",
        CELLS_PER_EXT_BLOB,
        cellProofs.size());
    this.blob = blob;
    this.cellProofs = cellProofs;
  }
}

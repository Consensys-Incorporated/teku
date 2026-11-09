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

package tech.pegasys.teku.spec.util;

import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;

public class KzgUtil {

  public static BlobAndCellProofs computeBlobAndCellProofs(final KZG kzg, final Blob blob) {
    return new BlobAndCellProofs(
        blob,
        kzg.computeCellsAndProofs(blob.getBytes()).stream().map(KZGCellAndProof::proof).toList());
  }
}

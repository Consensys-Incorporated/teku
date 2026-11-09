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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;

public class BlobsBundleDeneb extends BlobsBundle {

  public BlobsBundleDeneb(
      final List<KZGCommitment> commitments, final List<KZGProof> proofs, final List<Blob> blobs) {
    super(commitments, proofs, blobs);
    checkArgument(
        commitments.size() == blobs.size(),
        "Expected %s commitments but got %s",
        blobs.size(),
        commitments.size());
    checkArgument(
        proofs.size() == blobs.size(),
        "Expected %s proofs but got %s",
        blobs.size(),
        proofs.size());
  }
}

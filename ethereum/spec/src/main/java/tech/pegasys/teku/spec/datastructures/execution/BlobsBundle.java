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

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;

public abstract class BlobsBundle {

  private final List<KZGCommitment> commitments;
  private final List<KZGProof> proofs;
  private final List<Blob> blobs;

  public BlobsBundle(
      final List<KZGCommitment> commitments, final List<KZGProof> proofs, final List<Blob> blobs) {
    this.commitments = commitments;
    this.proofs = proofs;
    this.blobs = blobs;
  }

  public List<KZGCommitment> getCommitments() {
    return commitments;
  }

  public List<KZGProof> getProofs() {
    return proofs;
  }

  public List<Blob> getBlobs() {
    return blobs;
  }

  public int getNumberOfBlobs() {
    return blobs.size();
  }

  public String toBriefString() {
    return MoreObjects.toStringHelper(this)
        .add("commitments", commitments.stream().map(KZGCommitment::toAbbreviatedString).toList())
        .add("proofs", proofs.stream().map(KZGProof::toAbbreviatedString).toList())
        .add("blobs", blobs.stream().map(Blob::toBriefString).toList())
        .toString();
  }

  /** It's very big, use carefully */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("commitments", commitments)
        .add("proofs", proofs)
        .add("blobs", blobs)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlobsBundle that = (BlobsBundle) o;
    return Objects.equals(commitments, that.commitments)
        && Objects.equals(proofs, that.proofs)
        && Objects.equals(blobs, that.blobs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commitments, proofs, blobs);
  }
}

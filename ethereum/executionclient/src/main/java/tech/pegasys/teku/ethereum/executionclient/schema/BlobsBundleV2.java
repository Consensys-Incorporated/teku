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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes48Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.versions.fulu.BlobsBundleFulu;

public class BlobsBundleV2 {
  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes48Deserializer.class)
  private final List<Bytes48> commitments;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes48Deserializer.class)
  private final List<Bytes48> proofs;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  private final List<Bytes> blobs;

  @JsonCreator
  public BlobsBundleV2(
      @JsonProperty("commitments") final List<Bytes48> commitments,
      @JsonProperty("proofs") final List<Bytes48> proofs,
      @JsonProperty("blobs") final List<Bytes> blobs) {
    checkNotNull(commitments, "commitments");
    checkNotNull(proofs, "proofs");
    checkNotNull(blobs, "blobs");
    this.commitments = commitments;
    this.proofs = proofs;
    this.blobs = blobs;
  }

  public static BlobsBundleV2 fromInternalBlobsBundle(final BlobsBundle blobsBundle) {
    return new BlobsBundleV2(
        blobsBundle.getCommitments().stream().map(KZGCommitment::getBytesCompressed).toList(),
        blobsBundle.getProofs().stream().map(KZGProof::getBytesCompressed).toList(),
        blobsBundle.getBlobs().stream().map(SszByteVectorImpl::getBytes).toList());
  }

  public BlobsBundle asInternalBlobsBundle(final BlobSchema blobSchema) {
    return new BlobsBundleFulu(
        commitments.stream().map(KZGCommitment::new).toList(),
        proofs.stream().map(KZGProof::new).toList(),
        blobs.stream().map(blobBytes -> new Blob(blobSchema, blobBytes)).toList());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlobsBundleV2 that = (BlobsBundleV2) o;
    return Objects.equals(commitments, that.commitments)
        && Objects.equals(proofs, that.proofs)
        && Objects.equals(blobs, that.blobs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commitments, proofs, blobs);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("commitments", commitments.stream().map(this::bytesToBriefString).toList())
        .add("proofs", proofs.stream().map(this::bytesToBriefString).toList())
        .add("blobs", blobs.stream().map(this::bytesToBriefString).toList())
        .toString();
  }

  private String bytesToBriefString(final Bytes bytes) {
    return bytes.slice(0, 7).toUnprefixedHexString();
  }
}

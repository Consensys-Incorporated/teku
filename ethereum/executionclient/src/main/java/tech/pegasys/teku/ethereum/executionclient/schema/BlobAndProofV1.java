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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes48Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;

public class BlobAndProofV1 {

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  private final Bytes blob;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes48Deserializer.class)
  private final Bytes48 proof;

  public BlobAndProofV1(
      @JsonProperty("blob") final Bytes blob, @JsonProperty("proof") final Bytes48 proof) {
    checkNotNull(blob, "blob");
    checkNotNull(proof, "proof");
    this.proof = proof;
    this.blob = blob;
  }

  public BlobAndProof asInternalBlobAndProof(final BlobSchema blobSchema) {
    return new BlobAndProof(new Blob(blobSchema, blob), new KZGProof(proof));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlobAndProofV1 that = (BlobAndProofV1) o;
    return Objects.equals(blob, that.blob) && Objects.equals(proof, that.proof);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blob, proof);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blob", bytesToBriefString(blob))
        .add("proof", bytesToBriefString(proof))
        .toString();
  }

  private String bytesToBriefString(final Bytes bytes) {
    return bytes.slice(0, 7).toUnprefixedHexString();
  }
}

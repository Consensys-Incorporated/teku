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

package tech.pegasys.teku.kzg;

import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_PROOF;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

public final class KZGProof {
  public static final int SIZE = BYTES_PER_PROOF;
  public static final KZGProof ZERO = fromArray(new byte[SIZE]);

  public static KZGProof fromHexString(final String hexString) {
    return KZGProof.fromBytesCompressed(Bytes48.fromHexString(hexString));
  }

  public static KZGProof fromBytesCompressed(final Bytes48 bytes) throws IllegalArgumentException {
    return new KZGProof(bytes);
  }

  public static KZGProof fromArray(final byte[] bytes) {
    return fromBytesCompressed(Bytes48.wrap(bytes));
  }

  static List<KZGProof> splitBytes(final Bytes bytes) {
    return CKZG4844Utils.chunkBytes(bytes, BYTES_PER_PROOF).stream()
        .map(b -> new KZGProof(Bytes48.wrap(b)))
        .toList();
  }

  private final Bytes48 bytesCompressed;

  public KZGProof(final Bytes48 bytesCompressed) {
    this.bytesCompressed = bytesCompressed;
  }

  public Bytes48 getBytesCompressed() {
    return bytesCompressed;
  }

  public byte[] toArrayUnsafe() {
    return bytesCompressed.toArrayUnsafe();
  }

  public String toAbbreviatedString() {
    return getBytesCompressed().toUnprefixedHexString().substring(0, 7);
  }

  public String toHexString() {
    return getBytesCompressed().toHexString();
  }

  @Override
  public String toString() {
    return getBytesCompressed().toString();
  }

  @Override
  public boolean equals(final Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (obj instanceof final KZGProof other) {
      return Objects.equals(this.getBytesCompressed(), other.getBytesCompressed());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBytesCompressed());
  }
}

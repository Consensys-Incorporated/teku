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

import static com.google.common.base.Preconditions.checkArgument;

import ethereum.ckzg4844.CKZG4844JNI;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;

public final class KZGCommitment {

  public static KZGCommitment fromHexString(final String hexString) {
    return KZGCommitment.fromBytesCompressed(Bytes48.fromHexString(hexString));
  }

  public static KZGCommitment fromSSZBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == CKZG4844JNI.BYTES_PER_COMMITMENT,
        "Expected " + CKZG4844JNI.BYTES_PER_COMMITMENT + " bytes but received %s.",
        bytes.size());
    return SSZ.decode(
        bytes,
        reader ->
            new KZGCommitment(
                Bytes48.wrap(reader.readFixedBytes(CKZG4844JNI.BYTES_PER_COMMITMENT))));
  }

  public static KZGCommitment fromBytesCompressed(final Bytes48 bytes) {
    return new KZGCommitment(bytes);
  }

  public static KZGCommitment fromArray(final byte[] bytes) {
    return fromBytesCompressed(Bytes48.wrap(bytes));
  }

  private final Bytes48 bytesCompressed;

  public KZGCommitment(final Bytes48 bytesCompressed) {
    this.bytesCompressed = bytesCompressed;
  }

  /**
   * Returns the SSZ serialization of the <em>compressed</em> form of the commitment
   *
   * @return the serialization of the compressed form of the commitment.
   */
  public Bytes toSSZBytes() {
    return SSZ.encode(writer -> writer.writeFixedBytes(getBytesCompressed()));
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

    if (obj instanceof final KZGCommitment other) {
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

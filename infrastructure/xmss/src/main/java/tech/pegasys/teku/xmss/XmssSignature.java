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

package tech.pegasys.teku.xmss;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A leanXMSS signature: the leaf index (4 bytes, little-endian), then the bottom and top layers
 * (1200 bytes each).
 */
public final class XmssSignature {

  private static final byte STUB_FILL = 0x11;

  private final Bytes bytes;

  private XmssSignature(final Bytes bytes) {
    this.bytes = bytes;
  }

  public static XmssSignature fromBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == XmssConstants.SIGNATURE_SIZE,
        "Expected %s bytes for an XMSS signature but got %s",
        XmssConstants.SIGNATURE_SIZE,
        bytes.size());
    return new XmssSignature(Bytes.wrap(bytes.toArray()));
  }

  public static XmssSignature fromHexString(final String hex) {
    return fromBytes(Bytes.fromHexString(hex));
  }

  /** The all-zero signature, which the spec uses where no signature is required. */
  public static XmssSignature empty() {
    return new XmssSignature(Bytes.wrap(new byte[XmssConstants.SIGNATURE_SIZE]));
  }

  /**
   * The spec's stub signature for when XMSS is inactive: the leaf index, then {@code 0x11} bytes.
   * It carries a real leaf index so leaf checks still run, but it never verifies.
   */
  public static XmssSignature stub(final UInt64 leafIndex) {
    final byte[] signature = new byte[XmssConstants.SIGNATURE_SIZE];
    Arrays.fill(signature, STUB_FILL);
    final Bytes leaf = encodeLeafIndex(leafIndex);
    System.arraycopy(leaf.toArrayUnsafe(), 0, signature, 0, XmssConstants.LEAF_INDEX_SIZE);
    return new XmssSignature(Bytes.wrap(signature));
  }

  /** The leaf index this signature was made at, from its first four bytes. */
  public UInt64 getLeafIndex() {
    return UInt64.fromLongBits(
        Integer.toUnsignedLong(
            bytes.slice(0, XmssConstants.LEAF_INDEX_SIZE).toInt(ByteOrder.LITTLE_ENDIAN)));
  }

  public boolean isEmpty() {
    return bytes.isZero();
  }

  public Bytes toBytes() {
    return bytes;
  }

  static Bytes encodeLeafIndex(final UInt64 leafIndex) {
    checkArgument(
        leafIndex.isLessThanOrEqualTo(XmssConstants.MAX_LEAF_INDEX),
        "XMSS leaf index %s is above %s",
        leafIndex,
        XmssConstants.MAX_LEAF_INDEX);
    return Bytes.ofUnsignedInt(leafIndex.longValue(), ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof XmssSignature that && Objects.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  @Override
  public String toString() {
    return "XmssSignature{leafIndex=" + getLeafIndex() + ", bytes=" + bytes.slice(0, 8) + "...}";
  }
}

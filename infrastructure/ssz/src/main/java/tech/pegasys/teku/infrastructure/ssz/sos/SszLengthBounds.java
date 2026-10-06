/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.sos;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class SszLengthBounds {
  public static final SszLengthBounds ZERO = new SszLengthBounds(0, 0);
  private final long min;
  private final long max;

  public static SszLengthBounds ofBits(final long fixedSize) {
    return new SszLengthBounds(fixedSize, fixedSize);
  }

  public static SszLengthBounds ofBits(final long min, final long max) {
    return new SszLengthBounds(min, max);
  }

  public static SszLengthBounds ofBytes(final long fixedSize) {
    return new SszLengthBounds(saturatingMul(fixedSize, 8), saturatingMul(fixedSize, 8));
  }

  public static SszLengthBounds ofBytes(final long min, final long max) {
    return new SszLengthBounds(saturatingMul(min, 8), saturatingMul(max, 8));
  }

  private SszLengthBounds(final long min, final long max) {
    this.min = min;
    this.max = max;
  }

  public long getMinBits() {
    return min;
  }

  public long getMaxBits() {
    return max;
  }

  public long getMinBytes() {
    return saturatingBitsCeilToBytes(min);
  }

  public long getMaxBytes() {
    return saturatingBitsCeilToBytes(max);
  }

  public boolean isUnbounded() {
    return getMaxBits() == Long.MAX_VALUE;
  }

  /**
   * Caps the maximum size with a network-level bound. The raw bound may already be finite: the
   * Gloas {@code DataColumnSidecar} lists are limited by {@code MAX_BLOB_COMMITMENTS_PER_BLOCK},
   * yet {@code compute_max_data_column_sidecar_size} caps the message far lower, at the blob
   * schedule's largest {@code max_blobs_per_block}. The override must be at least as tight as the
   * raw SSZ bound: a looser one is a misconfiguration and is rejected rather than ignored.
   */
  public SszLengthBounds withMaxBytesUpperBound(final long maxBytes) {
    checkArgument(
        maxBytes >= getMinBytes(),
        "SSZ upper bound override %s is below raw minimum %s",
        maxBytes,
        getMinBytes());
    checkArgument(
        maxBytes <= getMaxBytes(),
        "SSZ upper bound override %s is above raw maximum %s",
        maxBytes,
        getMaxBytes());
    return SszLengthBounds.ofBytes(getMinBytes(), maxBytes);
  }

  public SszLengthBounds ceilToBytes() {
    return SszLengthBounds.ofBytes(getMinBytes(), getMaxBytes());
  }

  public SszLengthBounds add(final SszLengthBounds other) {
    return new SszLengthBounds(
        saturatingAdd(this.min, other.min), saturatingAdd(this.max, other.max));
  }

  public SszLengthBounds addBytes(final int moreBytes) {
    return addBits(moreBytes * 8);
  }

  public SszLengthBounds addBits(final int moreBits) {
    return new SszLengthBounds(
        saturatingAdd(this.min, moreBits), saturatingAdd(this.max, moreBits));
  }

  public SszLengthBounds mul(final long factor) {
    return new SszLengthBounds(saturatingMul(this.min, factor), saturatingMul(this.max, factor));
  }

  public SszLengthBounds or(final SszLengthBounds other) {
    return new SszLengthBounds(Long.min(this.min, other.min), Long.max(this.max, other.max));
  }

  public boolean isWithinBounds(final long lengthBytes) {
    return lengthBytes >= getMinBytes() && lengthBytes <= getMaxBytes();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SszLengthBounds that = (SszLengthBounds) o;
    return getMinBits() == that.getMinBits() && getMaxBits() == that.getMaxBits();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMinBits(), getMaxBits());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("min", fromBits(getMinBits()))
        .add("max", fromBits(getMaxBits()))
        .toString();
  }

  private static String fromBits(final long bits) {
    long bytes = bits / 8;
    return "" + bytes + ((bits & 7) == 0 ? "" : "(+" + (bits - bytes * 8) + " bits)");
  }

  // Saturating arithmetic helpers — all assume non-negative arguments

  private static long saturatingBitsCeilToBytes(final long bits) {
    if (bits > Long.MAX_VALUE - 7) {
      return Long.MAX_VALUE / 8 + 1;
    }
    return (bits + 7) / 8;
  }

  private static long saturatingAdd(final long a, final long b) {
    final long sum = a + b;
    // Since a, b >= 0, overflow has occurred if sum wraps to negative
    if (sum < 0) {
      return Long.MAX_VALUE;
    }
    return sum;
  }

  private static long saturatingMul(final long a, final long b) {
    final long result = a * b;
    // Check for overflow: if a != 0 and result / a != b, overflow occurred
    if (a != 0 && result / a != b) {
      return Long.MAX_VALUE;
    }
    return result;
  }
}

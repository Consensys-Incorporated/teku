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

package tech.pegasys.teku.infrastructure.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class Bytes4 {
  public static final int SIZE = 4;
  public static final Bytes4 ZERO = new Bytes4(Bytes.wrap(new byte[SIZE]));

  private final Bytes bytes;

  public Bytes4(final Bytes bytes) {
    checkArgument(bytes.size() == 4, "Bytes4 should be 4 bytes, but was %s bytes.", bytes.size());
    this.bytes = bytes;
  }

  public static Bytes4 fromHexString(final String value) {
    return new Bytes4(Bytes.fromHexString(value));
  }

  public static Bytes4 fromHexStringLenient(final String value) {
    return new Bytes4(Bytes.fromHexStringLenient(value, 4));
  }

  public String toHexString() {
    return bytes.toHexString();
  }

  /**
   * Left pad a {@link Bytes} value with zero bytes to create a {@link Bytes4}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes4} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() > 4}.
   */
  public static Bytes4 leftPad(final Bytes value) {
    checkNotNull(value);
    if (value instanceof final Bytes4 bytes4) {
      return bytes4;
    }
    checkArgument(value.size() <= 4, "Expected at most %s bytes but got %s", 4, value.size());
    MutableBytes result = MutableBytes.create(4);
    value.copyTo(result, 4 - value.size());
    return new Bytes4(result);
  }

  /**
   * Right pad a {@link Bytes} value with zero bytes to create a {@link Bytes4}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes4} that exposes the right-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() > 4}.
   */
  public static Bytes4 rightPad(final Bytes value) {
    checkNotNull(value);
    if (value instanceof final Bytes4 bytes4) {
      return bytes4;
    }
    checkArgument(value.size() <= 4, "Expected at most %s bytes but got %s", 4, value.size());
    MutableBytes result = MutableBytes.create(4);
    value.copyTo(result, 0);
    return new Bytes4(result);
  }

  public Bytes getWrappedBytes() {
    return bytes;
  }

  public Bytes4 copy() {
    return new Bytes4(bytes.copy());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Bytes4 bytes4 = (Bytes4) o;
    return bytes.equals(bytes4.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes);
  }

  public String toUnprefixedHexString() {
    return bytes.toUnprefixedHexString();
  }

  @Override
  public String toString() {
    return bytes.toString();
  }
}

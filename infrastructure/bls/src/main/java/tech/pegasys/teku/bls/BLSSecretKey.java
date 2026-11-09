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

package tech.pegasys.teku.bls;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.impl.SecretKey;

public final class BLSSecretKey {
  /**
   * Creates a secret key instance from bytes
   *
   * @param bytes Should be in range [0, )
   * @throws IllegalArgumentException if bytes are not in the valid range
   */
  public static BLSSecretKey fromBytes(final Bytes32 bytes) throws IllegalArgumentException {
    if (bytes.compareTo(BLSConstants.CURVE_ORDER_BYTES) >= 0) {
      throw new IllegalArgumentException(
          "Invalid bytes for secret key (0 <= SK < r, where r is "
              + BLSConstants.CURVE_ORDER_BYTES
              + "): "
              + bytes);
    } else {
      return new BLSSecretKey(BLS.getBlsImpl().secretKeyFromBytes(bytes));
    }
  }

  static BLSSecretKey fromBytesModR(final Bytes32 secretKeyBytes) {
    final Bytes32 keyBytes;
    if (secretKeyBytes.compareTo(BLSConstants.CURVE_ORDER_BYTES) >= 0) {
      BigInteger validSK =
          secretKeyBytes
              .toUnsignedBigInteger(ByteOrder.BIG_ENDIAN)
              .mod(BLSConstants.CURVE_ORDER_BI);
      keyBytes = Bytes32.leftPad(Bytes.wrap(validSK.toByteArray()));
    } else {
      keyBytes = secretKeyBytes;
    }
    return fromBytes(keyBytes);
  }

  private final SecretKey secretKey;

  /**
   * Construct from an implementation-specific SecretKey object.
   *
   * @param secretKey An implementation-specific SecretKey
   */
  public BLSSecretKey(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  SecretKey getSecretKey() {
    return secretKey;
  }

  public BLSPublicKey toPublicKey() {
    return new BLSPublicKey(getSecretKey().derivePublicKey());
  }

  public Bytes32 toBytes() {
    return secretKey.toBytes();
  }

  /** Overwrites the key with zeros so that it is no longer in memory */
  public void destroy() {
    secretKey.destroy();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BLSSecretKey that = (BLSSecretKey) o;
    return secretKey.equals(that.secretKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(secretKey);
  }
}

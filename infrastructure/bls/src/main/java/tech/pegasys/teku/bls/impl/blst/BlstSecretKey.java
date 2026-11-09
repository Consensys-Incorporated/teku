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

package tech.pegasys.teku.bls.impl.blst;

import java.util.Objects;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import supranational.blst.P1;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;

class BlstSecretKey implements SecretKey {
  static final BlstSecretKey ZERO_SK = BlstSecretKey.fromBytesRaw(Bytes32.ZERO);

  public static BlstSecretKey fromBytes(final Bytes32 bytes) {
    if (bytes.isZero()) {
      return ZERO_SK;
    } else {
      return fromBytesRaw(bytes);
    }
  }

  private static BlstSecretKey fromBytesRaw(final Bytes32 bytes) {
    supranational.blst.SecretKey secretKey = new supranational.blst.SecretKey();
    secretKey.from_bendian(bytes.toArrayUnsafe());
    return new BlstSecretKey(secretKey);
  }

  public static BlstSecretKey generateNew(final Random random) {
    byte[] ikm = new byte[128];
    random.nextBytes(ikm);
    supranational.blst.SecretKey sk = new supranational.blst.SecretKey();
    sk.keygen(ikm);
    return new BlstSecretKey(sk);
  }

  private final supranational.blst.SecretKey secretKey;

  public BlstSecretKey(final supranational.blst.SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public supranational.blst.SecretKey getKey() {
    return secretKey;
  }

  @Override
  public Bytes32 toBytes() {
    return Bytes32.wrap(secretKey.to_bendian());
  }

  @Override
  public Signature sign(final Bytes message) {
    return BlstBLS12381.sign(this, message);
  }

  @Override
  public Signature sign(final Bytes message, final String dst) {
    return BlstBLS12381.sign(this, message, dst);
  }

  @Override
  public BlstPublicKey derivePublicKey() {
    P1 pk = new P1(secretKey);
    return new BlstPublicKey(pk.to_affine());
  }

  @SuppressWarnings("ReferenceEquality")
  boolean isZero() {
    return this == ZERO_SK;
  }

  @Override
  public int hashCode() {
    return toBytes().hashCode();
  }

  @Override
  public void destroy() {
    secretKey.from_bendian(Bytes32.ZERO.toArrayUnsafe());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof final SecretKey secretKey) {
      return Objects.equals(toBytes(), secretKey.toBytes());
    } else {
      return false;
    }
  }
}

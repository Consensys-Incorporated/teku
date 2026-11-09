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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes48;
import supranational.blst.P1;
import supranational.blst.P1_Affine;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.bls.impl.PublicKey;

class BlstPublicKey implements PublicKey {
  private static final Bytes48 INFINITY_COMPRESSED_BYTES =
      Bytes48.fromHexString(
          "0x"
              + "c0000000000000000000000000000000"
              + "00000000000000000000000000000000"
              + "00000000000000000000000000000000");

  private static final BlstPublicKey INFINITE_PUBLIC_KEY = fromBytes(INFINITY_COMPRESSED_BYTES);

  public static BlstPublicKey fromBytes(final Bytes48 compressed) {
    try {
      P1_Affine ecPoint = new P1_Affine(compressed.toArrayUnsafe());
      return new BlstPublicKey(ecPoint);
    } catch (Exception e) {
      throw new BlsException("Deserialization of public key bytes failed: " + compressed, e);
    }
  }

  static BlstPublicKey fromPublicKey(final PublicKey publicKey) {
    if (publicKey instanceof final BlstPublicKey blstPublicKey) {
      return blstPublicKey;
    } else {
      return fromBytes(publicKey.toBytesCompressed());
    }
  }

  public static BlstPublicKey aggregate(final List<BlstPublicKey> publicKeys) {
    checkArgument(publicKeys.size() > 0);

    if (publicKeys.size() == 1) {
      // The sum of one key is the key itself: skip the projective addition and the affine
      // conversion (a field inversion), which dominate the cost of per-signature aggregation in
      // batch verification where nearly every signature carries a single key.
      final BlstPublicKey publicKey = publicKeys.get(0);
      return publicKey.isValid() ? publicKey : INFINITE_PUBLIC_KEY;
    }

    P1 sum = new P1();
    for (BlstPublicKey publicKey : publicKeys) {
      if (!publicKey.isValid()) {
        // Points not in the group and the point at infinity are not valid public keys. Aggregating
        // with other points should result in a non-valid pubkey, the point at infinity.
        return INFINITE_PUBLIC_KEY;
      }
      // At this point, we know that the public keys are in the G1 group, so we use `blst.P1.add()`
      // rather than `blst.P1.aggregate()` as the latter always performs the (slow) group check.
      sum.add(publicKey.ecPoint);
    }

    return new BlstPublicKey(sum.to_affine());
  }

  final P1_Affine ecPoint;
  private final Supplier<Boolean> isInfinity = Suppliers.memoize(this::checkForInfinity);
  private final Supplier<Boolean> isInGroup = Suppliers.memoize(this::checkGroupMembership);

  public BlstPublicKey(final P1_Affine ecPoint) {
    this.ecPoint = ecPoint;
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {
    if (!isValid()) {
      throw new IllegalArgumentException("Invalid PublicKey: " + toBytesCompressed());
    }
  }

  @Override
  public boolean isInGroup() {
    return isInGroup.get();
  }

  private boolean checkGroupMembership() {
    return ecPoint.in_group();
  }

  private boolean checkForInfinity() {
    return ecPoint.is_inf();
  }

  @Override
  public boolean isValid() {
    return !isInfinity.get() && isInGroup.get();
  }

  boolean isInfinity() {
    return isInfinity.get();
  }

  @Override
  public Bytes48 toBytesCompressed() {
    return Bytes48.wrap(ecPoint.compress());
  }

  @Override
  public int hashCode() {
    return toBytesCompressed().hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof PublicKey publicKey) {
      return Objects.equals(toBytesCompressed(), publicKey.toBytesCompressed());
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
  }
}

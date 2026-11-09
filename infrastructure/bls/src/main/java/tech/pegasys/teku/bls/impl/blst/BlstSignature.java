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

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import supranational.blst.BLST_ERROR;
import supranational.blst.P2;
import supranational.blst.P2_Affine;
import supranational.blst.Pairing;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;

public class BlstSignature implements Signature {
  private static final int COMPRESSED_SIG_SIZE = 96;

  static final BlstSignature INFINITY = new BlstSignature(new P2_Affine());

  public static BlstSignature fromBytes(final Bytes compressed) {
    try {
      checkArgument(
          compressed.size() == COMPRESSED_SIG_SIZE,
          "Expected %s bytes of input but got %s",
          COMPRESSED_SIG_SIZE,
          compressed.size());
      P2_Affine ec2Point = new P2_Affine(compressed.toArrayUnsafe());
      return new BlstSignature(ec2Point);
    } catch (Exception e) {
      throw new BlsException("Deserialization of signature bytes failed: " + compressed, e);
    }
  }

  static BlstSignature fromSignature(final Signature signature) {
    if (signature instanceof BlstSignature blstSignature) {
      return blstSignature;
    } else {
      return fromBytes(signature.toBytesCompressed());
    }
  }

  public static BlstSignature aggregate(final List<BlstSignature> signatures) {
    try {
      P2 sum = new P2();
      for (BlstSignature finiteSignature : signatures) {
        sum.aggregate(finiteSignature.ec2Point);
      }
      return new BlstSignature(sum.to_affine());
    } catch (IllegalArgumentException e) {
      // Blst performs a G2 group membership test on each signature. We end up here if it fails.
      throw new BlsException("Failed to aggregate signatures", e);
    }
  }

  private static void blstPrepareVerifyAggregated(
      final BlstPublicKey pubKey,
      final Bytes message,
      final Pairing ctx,
      final BlstSignature blstSignature) {

    BLST_ERROR ret =
        ctx.aggregate(
            pubKey.ecPoint,
            blstSignature == null ? null : blstSignature.ec2Point,
            message.toArrayUnsafe(),
            new byte[0]);
    if (ret != BLST_ERROR.BLST_SUCCESS) {
      throw new BlsException("Error in Blst, error code: " + ret);
    }
  }

  private static boolean blstCompleteVerifyAggregated(final Pairing ctx) {
    ctx.commit();
    return ctx.finalverify();
  }

  final P2_Affine ec2Point;

  public BlstSignature(final P2_Affine ec2Point) {
    this.ec2Point = ec2Point;
  }

  @Override
  public Bytes toBytesCompressed() {
    return Bytes.wrap(ec2Point.compress());
  }

  @Override
  public boolean verify(final List<PublicKeyMessagePair> keysToMessages) {

    boolean isAnyPublicKeyInfinity =
        keysToMessages.stream()
            .anyMatch(pair -> ((BlstPublicKey) pair.getPublicKey()).isInfinity());
    if (isAnyPublicKeyInfinity) {
      return false;
    }

    Pairing ctx = new Pairing(true, HashToCurve.ETH2_DST);

    for (int i = 0; i < keysToMessages.size(); i++) {
      BlstPublicKey publicKey = BlstPublicKey.fromPublicKey(keysToMessages.get(i).getPublicKey());
      Bytes message = keysToMessages.get(i).getMessage();
      BlstSignature signature = i == 0 ? this : null;
      blstPrepareVerifyAggregated(publicKey, message, ctx, signature);
    }
    return blstCompleteVerifyAggregated(ctx);
  }

  @Override
  public boolean verify(final List<PublicKey> publicKeys, final Bytes message) {
    return verify(
        BlstPublicKey.aggregate(publicKeys.stream().map(BlstPublicKey::fromPublicKey).toList()),
        message);
  }

  @Override
  public boolean verify(final PublicKey publicKey, final Bytes message) {
    return BlstBLS12381.verify(BlstPublicKey.fromPublicKey(publicKey), message, this);
  }

  @Override
  public boolean verify(final PublicKey publicKey, final Bytes message, final String dst) {
    return BlstBLS12381.verify(BlstPublicKey.fromPublicKey(publicKey), message, this, dst);
  }

  @Override
  public boolean isInfinity() {
    return ec2Point.is_inf();
  }

  @Override
  public boolean isInGroup() {
    return ec2Point.in_group();
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
    if (o instanceof final Signature signature) {
      return Objects.equals(toBytesCompressed(), signature.toBytesCompressed());
    } else {
      return false;
    }
  }
}

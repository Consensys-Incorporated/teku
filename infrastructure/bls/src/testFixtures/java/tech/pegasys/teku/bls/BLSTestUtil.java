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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigInteger;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BLSTestUtil {

  /**
   * Create a random, but valid, signature.
   *
   * <p>Generally prefer the seeded version.
   *
   * @return a random signature
   */
  public static BLSSignature randomSignature() {
    return randomSignature(new Random().nextInt());
  }

  /**
   * Creates a random, but valid, signature based on a seed.
   *
   * @param entropy to seed the key pair generation
   * @return the signature
   */
  public static BLSSignature randomSignature(final int entropy) {
    BLSKeyPair keyPair = randomKeyPair(entropy);
    byte[] message = "Hello, world!".getBytes(UTF_8);
    return BLS.sign(keyPair.getSecretKey(), Bytes.wrap(message));
  }

  /**
   * Generates a compressed, serialized, random, valid public key based on a seed.
   *
   * @return PublicKey The public key, not null
   */
  public static BLSPublicKey randomPublicKey(final int seed) {
    return randomKeyPair(seed).getPublicKey();
  }

  /**
   * Generate a key pair based on a secret key generated from a seed value.
   *
   * <p>This MUST NOT be used to generate production keys.
   *
   * @return a keypair generated from a seed
   */
  public static BLSKeyPair randomKeyPair(final int seed) {
    BLSSecretKey pseudoRandomSecretBytes =
        BLSSecretKey.fromBytesModR(Bytes32.random(new Random(seed)));
    return new BLSKeyPair(pseudoRandomSecretBytes);
  }

  /**
   * Returns the key pair whose secret key is {@code r - sk} (r being the curve order), so its
   * public key is the negation of the given one.
   *
   * <p>Both keys are individually valid, but they sum to the G1 point at infinity, so signatures by
   * both over the same message aggregate to the G2 point at infinity.
   *
   * <p>This MUST NOT be used to generate production keys.
   *
   * @param keyPair the key pair to negate
   * @return the key pair with the negated secret key
   */
  public static BLSKeyPair negate(final BLSKeyPair keyPair) {
    final BigInteger secretKey = keyPair.getSecretKey().toBytes().toUnsignedBigInteger();
    return new BLSKeyPair(secretKeyFromInteger(BLSConstants.CURVE_ORDER_BI.subtract(secretKey)));
  }

  /**
   * Creates a secret key from its integer value, which must be in the range [1, r).
   *
   * <p>This MUST NOT be used to generate production keys.
   */
  public static BLSSecretKey secretKeyFromInteger(final BigInteger value) {
    return BLSSecretKey.fromBytes(Bytes32.leftPad(Bytes.wrap(value.toByteArray())));
  }

  /**
   * Creates the key pairs P, -P (see {@link #negate(BLSKeyPair)}) and an unrelated Q: signatures by
   * P and -P over the same message aggregate to the point at infinity, and P + (-P) + Q == Q.
   *
   * <p>This MUST NOT be used to generate production keys.
   *
   * @param seed seed of P; Q is generated from {@code seed + 1}
   */
  public static NegatedKeyPairs negatedKeyPairs(final int seed) {
    final BLSKeyPair p = randomKeyPair(seed);
    return new NegatedKeyPairs(p, negate(p), randomKeyPair(seed + 1));
  }

  public record NegatedKeyPairs(BLSKeyPair p, BLSKeyPair minusP, BLSKeyPair q) {}
}

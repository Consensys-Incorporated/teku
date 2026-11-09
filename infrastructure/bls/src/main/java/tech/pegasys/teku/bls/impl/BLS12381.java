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

package tech.pegasys.teku.bls.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BatchSemiAggregate;

/**
 * This Boneh-Lynn-Shacham (BLS) signature implementation is constructed from a pairing friendly
 * BLS12-381 elliptic curve. It implements a subset of the functions from the proposed IETF
 * standard.
 *
 * <p>The basic reference is https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-02, but note
 * that this implementation deviates from the standard in some edge cases (such as aggregating empty
 * lists of signatures, or verifying with empty lists of public keys).
 */
public interface BLS12381 {

  /**
   * Generate a new random key pair
   *
   * @param random Source of randomness WARNING: pass only {@link java.security.SecureRandom}
   *     instance in production code non secure {@link Random} implementations can be used in
   *     testing code only
   * @return a new random key pair
   */
  KeyPair generateKeyPair(Random random);

  /**
   * Generate a new random key pair given entropy
   *
   * <p>Use this only for testing.
   *
   * @param seed to seed the key pair generation
   * @return a new random key pair
   */
  default KeyPair generateKeyPair(final long seed) {
    return generateKeyPair(new Random(seed));
  }

  /**
   * Create a PublicKey from byte array
   *
   * @param compressedPublicKeyBytes 48 bytes to read the public key from
   * @return a public key. Note that implementation may lazily evaluate passed bytes so the method
   *     may not immediately fail if the supplied bytes are invalid. Use {@link
   *     PublicKey#forceValidation()} to validate immediately
   * @throws BlsException If the supplied bytes are not a valid public key However if implementing
   *     class lazily parses bytes the exception might not be thrown on invalid input but throw on
   *     later usage. Use {@link PublicKey#forceValidation()} if need to immediately ensure input
   *     validity
   */
  PublicKey publicKeyFromCompressed(Bytes48 compressedPublicKeyBytes) throws BlsException;

  /**
   * Decode a signature from its <em>compressed</em> form serialized representation.
   *
   * @param compressedSignatureBytes 96 bytes of the signature
   * @return the signature
   */
  Signature signatureFromCompressed(Bytes compressedSignatureBytes);

  /**
   * Create a private key from bytes
   *
   * @param secretKeyBytes 32 bytes of the private key
   * @return a new SecretKey object
   */
  SecretKey secretKeyFromBytes(Bytes32 secretKeyBytes);

  /**
   * Aggregates list of PublicKeys, returns the public key that corresponds to G1 point at infinity
   * if list is empty
   *
   * @param publicKeys The list of public keys to aggregate
   * @return PublicKey The public key
   */
  PublicKey aggregatePublicKeys(List<? extends PublicKey> publicKeys);

  /**
   * Aggregates a list of Signatures, returning the signature that corresponds to G2 point at
   * infinity if list is empty.
   *
   * @param signatures The list of signatures to aggregate
   * @return Signature
   * @throws IllegalArgumentException if any of supplied signatures is invalid
   */
  Signature aggregateSignatures(List<? extends Signature> signatures)
      throws IllegalArgumentException;

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>For above batch verification method pre-calculates and returns two values: <code>S * r
   * </code> and <code>e(M * r, P)</code>
   *
   * @return the pair of values above in an opaque instance
   */
  BatchSemiAggregate prepareBatchVerify(
      int index, List<? extends PublicKey> publicKeys, Bytes message, Signature signature);

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Slightly more efficient variant of {@link #prepareBatchVerify(int, List, Bytes, Signature)}
   * when 2 signatures are aggregated with a faster ate2 pairing
   *
   * <p>For above batch verification method pre-calculates and returns two values: <code>
   * S1 * r1 + S2 * r2</code> and <code>e(M1 * r1, P1) * e(M2 * r2, P2)</code>
   *
   * @return the pair of values above in an opaque instance
   */
  BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<? extends PublicKey> publicKeys1,
      Bytes message1,
      Signature signature1,
      List<? extends PublicKey> publicKeys2,
      Bytes message2,
      Signature signature2);

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Variant of {@link #prepareBatchVerify(int, List, Bytes, Signature)} for several signatures
   * over the <b>same</b> message. Since <code>e(P1 * r1, M) * e(P2 * r2, M) * ... = e(P1 * r1 + P2
   * * r2 + ..., M)</code> the message is hashed to the curve and paired only once, while the random
   * multipliers are still applied to each signature and aggregated public key individually.
   *
   * <p>For above batch verification method pre-calculates and returns two values: <code>
   * S1 * r1 + S2 * r2 + ...</code> and <code>e(P1 * r1 + P2 * r2 + ..., M)</code>
   *
   * @param publicKeys one list of public keys per signature (aggregated per signature)
   * @param message the message all signatures are over
   * @param signatures the signatures, same size as publicKeys
   * @param parallel whether per-signature work may be spread over the common ForkJoinPool
   * @return the pair of values above in an opaque instance
   */
  BatchSemiAggregate prepareBatchVerifySameMessage(
      List<? extends List<? extends PublicKey>> publicKeys,
      Bytes message,
      List<? extends Signature> signatures,
      boolean parallel);

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Prepares several (public keys, message, signature) entries sequentially in one pairing
   * context. Each entry gets its own random multiplier, exactly as in {@link
   * #prepareBatchVerify(int, List, Bytes, Signature)}, but the pairs share one multi-pair Miller
   * loop. The result can be mixed with the results of the other prepare methods.
   *
   * @param publicKeys one list of public keys per entry (aggregated per entry)
   * @param messages one message per entry, same size as publicKeys
   * @param signatures one signature per entry, same size as publicKeys
   * @return the accumulated values in an opaque instance
   */
  BatchSemiAggregate prepareBatchVerifyChunk(
      List<? extends List<? extends PublicKey>> publicKeys,
      List<Bytes> messages,
      List<? extends Signature> signatures);

  /**
   * https://ethresear.ch/t/fast-verification-of-multiple-bls-signatures/5407
   *
   * <p>Does the final job of batch verification: calculates the final product and sum, does final
   * pairing and exponentiation
   *
   * @param preparedList the list of instances returned by {@link #prepareBatchVerify(int, List,
   *     Bytes, Signature)}, {@link #prepareBatchVerify2(int, List, Bytes, Signature, List, Bytes,
   *     Signature)}, {@link #prepareBatchVerifySameMessage(List, Bytes, List, boolean)} or {@link
   *     #prepareBatchVerifyChunk(List, List, List)}, or mixed from any of them
   * @return True if the verification is successful, false otherwise
   */
  boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList);

  default Signature randomSignature(final int seed) {
    KeyPair keyPair = generateKeyPair(seed);
    byte[] message = "Hello, world!".getBytes(UTF_8);
    return keyPair.getSecretKey().sign(Bytes.wrap(message));
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSTestUtil.NegatedKeyPairs;

/**
 * Two individually valid public keys P and -P (secret keys sk and r - sk) sum to the G1 point at
 * infinity; checks how the verification entry points handle the infinity signature over them.
 */
class InfinityAggregateTest {

  private static final Bytes MESSAGE = Bytes32.repeat((byte) 1);
  private static final Bytes OTHER_MESSAGE = Bytes32.repeat((byte) 2);

  private static final NegatedKeyPairs KEYS = BLSTestUtil.negatedKeyPairs(1);
  private static final BLSKeyPair P = KEYS.p();
  private static final BLSKeyPair MINUS_P = KEYS.minusP();
  private static final BLSKeyPair Q = KEYS.q();
  private static final BLSKeyPair R = BLSTestUtil.randomKeyPair(3);

  private static final List<BLSPublicKey> P_MINUS_P =
      List.of(P.getPublicKey(), MINUS_P.getPublicKey());
  private static final BLSSignature INFINITY = BLSSignature.infinity();

  @Test
  void keysAreIndividuallyValidButSumToInfinity() {
    assertThat(P.getPublicKey().isValid()).isTrue();
    assertThat(MINUS_P.getPublicKey().isValid()).isTrue();
    assertThat(P.getPublicKey()).isNotEqualTo(MINUS_P.getPublicKey());
    assertThat(BLSPublicKey.aggregate(P_MINUS_P).isValid()).isFalse();
    assertThat(BLSPublicKey.aggregate(P_MINUS_P).toBytesCompressed().toHexString())
        .isEqualTo("0xc0" + "00".repeat(47));
  }

  @Test
  void fastAggregateVerifyRejectsInfinitySignatureOverNegatedKeys() {
    assertThat(BLS.fastAggregateVerify(P_MINUS_P, MESSAGE, INFINITY)).isFalse();
  }

  @Test
  void aggregateVerifyAcceptsInfinitySignatureOverNegatedKeys() {
    // CoreAggregateVerify (IETF) only KeyValidates each key, never their sum, so
    // e(H(m), P) * e(H(m), -P) == 1 == e(inf, G2) passes. Unused by the beacon chain, which only
    // calls FastAggregateVerify for attestations; documented here so a change is noticed.
    assertThat(BLS.aggregateVerify(P_MINUS_P, List.of(MESSAGE, MESSAGE), INFINITY)).isTrue();
  }

  @Test
  void legacyBatchVerifyRejectsInfinitySignatureOverNegatedKeys() {
    final List<List<BLSPublicKey>> keys = List.of(P_MINUS_P, List.of(Q.getPublicKey()));
    final List<Bytes> messages = List.of(MESSAGE, OTHER_MESSAGE);
    final List<BLSSignature> sigs = List.of(INFINITY, sign(Q, OTHER_MESSAGE));
    assertThat(BLS.batchVerify(keys, messages, sigs)).isFalse();
    assertThat(BLS.batchVerify(keys, messages, sigs, true, false)).isFalse();
    assertThat(BLS.batchVerify(keys, messages, sigs, false, false)).isFalse();
    assertThat(BLS.batchVerify(keys, messages, sigs, true, true)).isFalse();
    // sanity: the batch passes once the bad entry is removed
    assertThat(
            BLS.batchVerify(
                List.of(List.of(Q.getPublicKey())),
                List.of(OTHER_MESSAGE),
                List.of(sign(Q, OTHER_MESSAGE))))
        .isTrue();
  }

  @Test
  void sameMessageGroupRejectsInfinitySignatureOverNegatedKeys() {
    // all three share MESSAGE -> grouped path (ScaledSum); the bad entry must not vanish in the sum
    final List<List<BLSPublicKey>> keys =
        List.of(List.of(Q.getPublicKey()), P_MINUS_P, List.of(R.getPublicKey()));
    final List<Bytes> messages = List.of(MESSAGE, MESSAGE, MESSAGE);
    final List<BLSSignature> sigs = List.of(sign(Q, MESSAGE), INFINITY, sign(R, MESSAGE));
    assertThat(SameMessageBatchVerifier.batchVerify(keys, messages, sigs, false)).isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(keys, messages, sigs, true)).isFalse();
  }

  @Test
  void singletonChunkRejectsInfinitySignatureOverNegatedKeys() {
    // distinct messages -> chunked singleton path
    final List<List<BLSPublicKey>> keys = List.of(List.of(Q.getPublicKey()), P_MINUS_P);
    final List<Bytes> messages = List.of(OTHER_MESSAGE, MESSAGE);
    final List<BLSSignature> sigs = List.of(sign(Q, OTHER_MESSAGE), INFINITY);
    assertThat(SameMessageBatchVerifier.batchVerify(keys, messages, sigs, false)).isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(keys, messages, sigs, true)).isFalse();
  }

  @Test
  void prepareCompleteBatchVerifyRejectsInfinitySignatureOverNegatedKeys() {
    final BatchSemiAggregate bad = BLS.prepareBatchVerify(0, P_MINUS_P, MESSAGE, INFINITY);
    final BatchSemiAggregate good =
        BLS.prepareBatchVerify(1, List.of(Q.getPublicKey()), OTHER_MESSAGE, sign(Q, OTHER_MESSAGE));
    assertThat(BLS.completeBatchVerify(List.of(good, bad))).isFalse();
    assertThat(BLS.completeBatchVerify(List.of(bad, good))).isFalse();
    assertThat(BLS.completeBatchVerify(List.of(good))).isTrue();
  }

  @Test
  void aggregateOfNegatedKeysSignaturesIsRejected() {
    // The aggregate of P's and -P's signatures over one message is the infinity signature over
    // the infinity key, which is rejected.
    final BLSSignature aggregated =
        BLS.aggregate(List.of(sign(P, MESSAGE), sign(MINUS_P, MESSAGE)));
    assertThat(aggregated.isInfinity()).isTrue();
    assertThat(BLS.fastAggregateVerify(P_MINUS_P, MESSAGE, aggregated)).isFalse();
  }

  @Test
  void negatedKeysInsideLargerAggregateVerify() {
    // P + (-P) + Q == Q: a signature by Q alone verifies for the key set {P, -P, Q}, as the
    // aggregate key is a valid point.
    final List<BLSPublicKey> keys =
        List.of(P.getPublicKey(), MINUS_P.getPublicKey(), Q.getPublicKey());
    assertThat(BLS.fastAggregateVerify(keys, MESSAGE, sign(Q, MESSAGE))).isTrue();
  }

  private static BLSSignature sign(final BLSKeyPair keyPair, final Bytes message) {
    return BLS.sign(keyPair.getSecretKey(), message);
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.LeanVmAggregator.LeanVmUnavailableException;
import tech.pegasys.teku.xmss.XmssAggregator.Claim;
import tech.pegasys.teku.xmss.XmssAggregator.ProofOfClaims;
import tech.pegasys.teku.xmss.XmssAggregator.SignedClaim;

/** Proves, merges and verifies with the real leanVM, inside the test's process. */
class LeanVmAggregatorTest {

  private static final Bytes32 MESSAGE = Bytes32.fromHexString("0x" + "ab".repeat(32));
  private static final Bytes32 OTHER_MESSAGE = Bytes32.fromHexString("0x" + "cd".repeat(32));

  // Built once: loading a key and proving take seconds each
  private static LeanVmAggregator leanVm;
  private static List<SignedClaim> signedMessage;
  private static List<SignedClaim> signedOtherMessage;
  private static Bytes proofOfMessage;
  private static Bytes proofOfOtherMessage;

  @BeforeAll
  static void proveOnce() {
    leanVm = LeanVmAggregator.start(0);
    final List<XmssSigningKey> keys =
        Stream.of("01", "02")
            .parallel()
            .map(seed -> XmssSecretKey.fromBytes(Bytes32.fromHexString("0x" + seed.repeat(32))))
            .map(Xmss::loadSigningKey)
            .toList();
    try {
      signedMessage = sign(keys, 0, MESSAGE);
      signedOtherMessage = sign(keys, 1, OTHER_MESSAGE);
    } finally {
      keys.forEach(XmssSigningKey::close);
    }
    proofOfMessage = leanVm.prove(signedMessage);
    proofOfOtherMessage = leanVm.prove(signedOtherMessage);
  }

  @Test
  void proof_verifiesAgainstItsClaims() {
    final List<Claim> claims = claimsOf(signedMessage);

    assertThat(leanVm.verify(claims, proofOfMessage)).isTrue();
    // A proof is of the set of its claims: order and repetition don't matter
    assertThat(leanVm.verify(List.of(claims.get(1), claims.get(0), claims.get(1)), proofOfMessage))
        .isTrue();
  }

  @Test
  void proof_doesNotVerifyAgainstOtherClaims() {
    final List<Claim> claims = claimsOf(signedMessage);

    assertThat(leanVm.verify(List.of(claims.get(0)), proofOfMessage)).isFalse();
    assertThat(leanVm.verify(claimsOf(signedOtherMessage), proofOfMessage)).isFalse();
  }

  @Test
  void bytesThatAreNotAProofDoNotVerify() {
    final List<Claim> claims = claimsOf(signedMessage);

    assertThat(leanVm.verify(claims, Bytes.EMPTY)).isFalse();
    assertThat(leanVm.verify(claims, Bytes.fromHexString("0x" + "5a".repeat(1000)))).isFalse();
    assertThat(leanVm.verify(claims, proofOfMessage.slice(0, proofOfMessage.size() / 2))).isFalse();
  }

  @Test
  void noClaimsNeedAnEmptyProof() {
    assertThat(leanVm.verify(List.of(), Bytes.EMPTY)).isTrue();
    assertThat(leanVm.verify(List.of(), proofOfMessage)).isFalse();
  }

  @Test
  void prove_failsForASignatureThatDoesNotSignItsClaim() {
    final SignedClaim wrongMessage =
        new SignedClaim(
            new Claim(signedMessage.get(0).claim().publicKey(), OTHER_MESSAGE),
            signedMessage.get(0).signature());

    assertThatThrownBy(() -> leanVm.prove(List.of(wrongMessage)))
        .isInstanceOf(LeanVmUnavailableException.class);
  }

  @Test
  void merge_provesTheUnionOfItsChildren() {
    final List<Claim> claims = claimsOf(signedMessage);
    final List<Claim> otherClaims = claimsOf(signedOtherMessage);

    final Bytes merged =
        leanVm.merge(
            List.of(
                new ProofOfClaims(claims, proofOfMessage),
                new ProofOfClaims(otherClaims, proofOfOtherMessage)));

    assertThat(leanVm.verify(Stream.concat(claims.stream(), otherClaims.stream()).toList(), merged))
        .isTrue();
    assertThat(leanVm.verify(claims, merged)).isFalse();
  }

  @Test
  void merge_returnsASingleChildAsItIsAndSkipsChildrenWithoutClaims() {
    final ProofOfClaims child = new ProofOfClaims(claimsOf(signedMessage), proofOfMessage);
    final ProofOfClaims noClaims = new ProofOfClaims(List.of(), Bytes.EMPTY);

    assertThat(leanVm.merge(List.of(noClaims, child))).isEqualTo(proofOfMessage);
    assertThat(leanVm.merge(List.of(noClaims))).isEqualTo(Bytes.EMPTY);
    assertThat(leanVm.merge(List.of())).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void merge_failsForAChildThatDoesNotProveItsClaims() {
    final ProofOfClaims child = new ProofOfClaims(claimsOf(signedMessage), proofOfMessage);
    final ProofOfClaims wrongProof =
        new ProofOfClaims(claimsOf(signedOtherMessage), proofOfMessage);

    assertThatThrownBy(() -> leanVm.merge(List.of(child, wrongProof)))
        .isInstanceOf(LeanVmUnavailableException.class);
    assertThatThrownBy(
            () -> leanVm.merge(List.of(child, new ProofOfClaims(List.of(), proofOfMessage))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static List<SignedClaim> sign(
      final List<XmssSigningKey> keys, final long leaf, final Bytes32 message) {
    return keys.stream()
        .map(
            key ->
                new SignedClaim(
                    new Claim(key.getPublicKey().toBytes(), message),
                    key.sign(UInt64.valueOf(leaf), message).toBytes()))
        .toList();
  }

  private static List<Claim> claimsOf(final List<SignedClaim> signedClaims) {
    return signedClaims.stream().map(SignedClaim::claim).toList();
  }
}

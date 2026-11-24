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

package tech.pegasys.teku.spec.logic.versions.pq.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.xmss.XmssAggregator;
import tech.pegasys.teku.xmss.XmssAggregator.Claim;
import tech.pegasys.teku.xmss.XmssAggregator.ProofOfClaims;
import tech.pegasys.teku.xmss.XmssAggregator.SignedClaim;

class LeanVmStarkTest {

  private static final Bytes32 KEY_A = Bytes32.fromHexStringLenient("0xa1");
  private static final Bytes32 KEY_B = Bytes32.fromHexStringLenient("0xb2");
  private static final Bytes32 MESSAGE_1 = Bytes32.fromHexStringLenient("0x01");
  private static final Bytes32 MESSAGE_2 = Bytes32.fromHexStringLenient("0x02");

  private final RecordingAggregator aggregator = new RecordingAggregator();
  private final LeanVmStark stark = new LeanVmStark(aggregator);

  @AfterEach
  void resetBackend() {
    StarkBackend.useConcatenation();
  }

  @Test
  void proveAggregates_provesOneClaimPerPublicKeyWithItsSignature() {
    final Bytes signatureA = Bytes.fromHexString("0xaa");
    final Bytes signatureB = Bytes.fromHexString("0xbb");

    final Bytes proof =
        stark.proveAggregates(
            List.of(statement(MESSAGE_1, KEY_A, KEY_B)), List.of(List.of(signatureA, signatureB)));

    assertThat(proof).isEqualTo(RecordingAggregator.PROOF);
    // The leaf index is not part of a claim
    assertThat(aggregator.proved)
        .containsExactly(
            new SignedClaim(new Claim(KEY_A, MESSAGE_1), signatureA),
            new SignedClaim(new Claim(KEY_B, MESSAGE_1), signatureB));
  }

  @Test
  void proveAggregates_ofNothingIsEmpty() {
    assertThat(stark.proveAggregates(List.of(), List.of())).isEqualTo(Bytes.EMPTY);
    assertThat(aggregator.proved).isEmpty();
  }

  @Test
  void proveAggregates_rejectsSignaturesThatDontMatchTheStatements() {
    assertThatThrownBy(
            () ->
                stark.proveAggregates(
                    List.of(statement(MESSAGE_1, KEY_A, KEY_B)),
                    List.of(List.of(Bytes.fromHexString("0xaa")))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mergeProofs_passesEachProofWithItsClaims() {
    final Bytes proof1 = Bytes.fromHexString("0x1111");
    final Bytes proof2 = Bytes.fromHexString("0x2222");

    stark.mergeProofs(
        List.of(
            new ProvenStatements(List.of(statement(MESSAGE_1, KEY_A)), proof1),
            new ProvenStatements(List.of(statement(MESSAGE_2, KEY_A, KEY_B)), proof2)));

    assertThat(aggregator.merged)
        .containsExactly(
            new ProofOfClaims(List.of(new Claim(KEY_A, MESSAGE_1)), proof1),
            new ProofOfClaims(
                List.of(new Claim(KEY_A, MESSAGE_2), new Claim(KEY_B, MESSAGE_2)), proof2));
  }

  @Test
  void verifyAggregates_checksTheStatementsClaims() {
    aggregator.valid = false;

    assertThat(
            stark.verifyAggregates(
                List.of(statement(MESSAGE_1, KEY_A), statement(MESSAGE_2, KEY_B)),
                RecordingAggregator.PROOF))
        .isFalse();
    assertThat(aggregator.verified)
        .containsExactly(new Claim(KEY_A, MESSAGE_1), new Claim(KEY_B, MESSAGE_2));
  }

  @Test
  void mergedProofSizeIsNotTheSumOfItsChildren() {
    assertThat(stark.isMergedProofSizeTheSumOfChildren()).isFalse();
    assertThat(StarkProver.CONCATENATED.isMergedProofSizeTheSumOfChildren()).isTrue();
  }

  @Test
  void onlyRecursiveProofsAreIncrementallyMergeable() {
    assertThat(stark.isIncrementallyMergeable()).isTrue();
    assertThat(StarkProver.CONCATENATED.isIncrementallyMergeable()).isFalse();
  }

  @Test
  void backend_switchesVerifiersAndProversMadeBeforeTheSwitch() {
    // The spec builds its verifier before the node starts leanVM
    final StarkProofVerifier verifier =
        StarkBackend.verifier((pubkey, leafIndex, message, signature) -> false);
    final StarkProver prover = StarkBackend.prover();
    final List<XmssAggregateStatement> statements = List.of(statement(MESSAGE_1, KEY_A));

    // The concatenation stand-in: a signature that doesn't verify
    assertThat(verifier.verifyAggregates(statements, Bytes.wrap(new byte[2404]))).isFalse();
    assertThat(prover.isMergedProofSizeTheSumOfChildren()).isTrue();

    StarkBackend.useLeanVm(aggregator);

    assertThat(StarkBackend.isLeanVm()).isTrue();
    assertThat(verifier.verifyAggregates(statements, RecordingAggregator.PROOF)).isTrue();
    assertThat(prover.isMergedProofSizeTheSumOfChildren()).isFalse();
    assertThat(prover.isIncrementallyMergeable()).isTrue();
    assertThat(prover.proveAggregates(statements, List.of(List.of(Bytes.fromHexString("0xaa")))))
        .isEqualTo(RecordingAggregator.PROOF);

    StarkBackend.useConcatenation();

    assertThat(StarkBackend.isLeanVm()).isFalse();
    assertThat(prover.isMergedProofSizeTheSumOfChildren()).isTrue();
    assertThat(prover.isIncrementallyMergeable()).isFalse();
  }

  private static XmssAggregateStatement statement(final Bytes32 message, final Bytes32... keys) {
    return new XmssAggregateStatement(List.of(keys), UInt64.valueOf(7), message);
  }

  private static class RecordingAggregator implements XmssAggregator {
    static final Bytes PROOF = Bytes.fromHexString("0x5afe");

    final List<SignedClaim> proved = new ArrayList<>();
    final List<ProofOfClaims> merged = new ArrayList<>();
    final List<Claim> verified = new ArrayList<>();
    boolean valid = true;

    @Override
    public Bytes prove(final List<SignedClaim> signedClaims) {
      proved.addAll(signedClaims);
      return PROOF;
    }

    @Override
    public Bytes merge(final List<ProofOfClaims> children) {
      merged.addAll(children);
      return PROOF;
    }

    @Override
    public boolean verify(final List<Claim> claims, final Bytes proof) {
      verified.addAll(claims);
      return valid && proof.equals(PROOF);
    }
  }
}

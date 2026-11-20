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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;

class StarkProofVerifierTest {

  private static final int SIGNATURE_SIZE = 2404;

  private final Bytes32 key1 = Bytes32.fromHexString("0x01");
  private final Bytes32 key2 = Bytes32.fromHexString("0x02");
  private final Bytes32 key3 = Bytes32.fromHexString("0x03");
  private final XmssAggregateStatement statement1 =
      new XmssAggregateStatement(
          List.of(key1, key2), UInt64.valueOf(5), Bytes32.fromHexString("0xaa"));
  private final XmssAggregateStatement statement2 =
      new XmssAggregateStatement(List.of(key3), UInt64.valueOf(6), Bytes32.fromHexString("0xbb"));

  private final List<String> verified = new ArrayList<>();

  /** Accepts a signature whose first byte is the last byte of the public key. */
  private final XmssSignatureVerifier xmssVerifier =
      (pubkey, leafIndex, message, signature) -> {
        verified.add(pubkey.get(31) + "@" + leafIndex + ":" + message.get(31));
        return signature.get(0) == pubkey.get(31);
      };

  private final StarkProofVerifier verifier = StarkProofVerifier.concatenated(xmssVerifier);

  @Test
  void acceptsTheSignaturesOfEachStatementInOrder() {
    final Bytes proof = Bytes.concatenate(signature(1), signature(2), signature(3));

    assertThat(verifier.verifyAggregates(List.of(statement1, statement2), proof)).isTrue();
    assertThat(verified).containsExactly("1@5:-86", "2@5:-86", "3@6:-69");
  }

  @Test
  void rejectsAnInvalidOrMisorderedSignature() {
    assertThat(
            verifier.verifyAggregates(
                List.of(statement1, statement2),
                Bytes.concatenate(signature(2), signature(1), signature(3))))
        .isFalse();
  }

  @Test
  void rejectsAShortOrOverlongProof() {
    assertThat(
            verifier.verifyAggregates(
                List.of(statement1, statement2), Bytes.concatenate(signature(1), signature(2))))
        .isFalse();
    assertThat(
            verifier.verifyAggregates(
                List.of(statement1, statement2),
                Bytes.concatenate(signature(1), signature(2), signature(3), Bytes.of(0))))
        .isFalse();
  }

  @Test
  void noStatementsNeedAnEmptyProof() {
    assertThat(verifier.verifyAggregates(List.of(), Bytes.EMPTY)).isTrue();
    assertThat(verifier.verifyAggregates(List.of(), signature(1))).isFalse();
  }

  @Test
  void readsTheLeafIndexLittleEndian() {
    assertThat(XmssSignatureVerifier.getLeafIndex(Bytes.fromHexString("0x0100000099")))
        .isEqualTo(UInt64.ONE);
    assertThat(XmssSignatureVerifier.getLeafIndex(Bytes.fromHexString("0x00800040")))
        .isEqualTo(UInt64.valueOf(0x40008000L));
    assertThat(XmssSignatureVerifier.getLeafIndex(Bytes.fromHexString("0xffffffff")))
        .isEqualTo(UInt64.valueOf(0xffffffffL));
  }

  private static Bytes signature(final int firstByte) {
    final byte[] signature = new byte[SIGNATURE_SIZE];
    signature[0] = (byte) firstByte;
    return Bytes.wrap(signature);
  }
}

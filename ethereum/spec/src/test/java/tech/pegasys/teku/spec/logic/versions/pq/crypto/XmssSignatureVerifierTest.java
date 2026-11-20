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

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssConstants;

class XmssSignatureVerifierTest {

  private final Bytes32 pubkey = Bytes32.repeat((byte) 3);
  private final Bytes32 message = Bytes32.repeat((byte) 4);
  private final UInt64 leafIndex = UInt64.valueOf(1234);

  @AfterEach
  void enableVerification() {
    XmssConstants.enableXmssVerification();
  }

  @Test
  void leanXmssRejectsASignatureAtAnotherLeaf() {
    assertThat(
            XmssSignatureVerifier.LEAN_XMSS.verify(
                pubkey, leafIndex, message, signatureAtLeaf(leafIndex.plus(1))))
        .isFalse();
  }

  @Test
  void leanXmssRejectsAMalformedSignature() {
    assertThat(XmssSignatureVerifier.LEAN_XMSS.verify(pubkey, leafIndex, message, Bytes.of(1, 2)))
        .isFalse();
  }

  @Test
  void leanXmssRejectsAnInvalidSignature() {
    assertThat(
            XmssSignatureVerifier.LEAN_XMSS.verify(
                pubkey, leafIndex, message, signatureAtLeaf(leafIndex)))
        .isFalse();
  }

  @Test
  void leanXmssAcceptsEverySignatureWhileVerificationIsDisabled() {
    XmssConstants.disableXmssVerification();
    assertThat(
            XmssSignatureVerifier.LEAN_XMSS.verify(
                pubkey, leafIndex, message, signatureAtLeaf(leafIndex)))
        .isTrue();
  }

  @Test
  void readsTheLeafIndexFromTheSignature() {
    assertThat(XmssSignatureVerifier.getLeafIndex(signatureAtLeaf(leafIndex))).isEqualTo(leafIndex);
  }

  private static Bytes signatureAtLeaf(final UInt64 leaf) {
    final byte[] signature = new byte[XmssConstants.SIGNATURE_SIZE];
    final byte[] leafBytes =
        Bytes.ofUnsignedInt(leaf.longValue(), ByteOrder.LITTLE_ENDIAN).toArrayUnsafe();
    System.arraycopy(leafBytes, 0, signature, 0, XmssConstants.LEAF_INDEX_SIZE);
    for (int i = XmssConstants.LEAF_INDEX_SIZE; i < signature.length; i++) {
      signature[i] = 0x11;
    }
    return Bytes.wrap(signature);
  }
}

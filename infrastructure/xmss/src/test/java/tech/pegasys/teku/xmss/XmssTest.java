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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Covers {@link Xmss} with the consensus-specs known answer (see {@code ethlib/README.md}). */
class XmssTest {

  private static XmssSecretKey secretKey;
  private static XmssPublicKey publicKey;
  private static UInt64 leafIndex;
  private static Bytes32 message;
  private static XmssSignature signature;

  @BeforeAll
  static void loadKnownAnswer() throws Exception {
    final JsonNode kat = new ObjectMapper().readTree(XmssTest.class.getResource("xmss_kat.json"));
    secretKey = XmssSecretKey.fromBytes(Bytes.fromHexString(kat.get("seed").asText()));
    publicKey = XmssPublicKey.fromHexString(kat.get("pubkey").asText());
    leafIndex = UInt64.valueOf(kat.get("leaf_index").asLong());
    message = Bytes32.fromHexString(kat.get("message").asText());
    signature = XmssSignature.fromHexString(kat.get("signature").asText());
  }

  @AfterEach
  void enableVerification() {
    XmssConstants.enableXmssVerification();
  }

  @Test
  void shouldMatchSpecKnownAnswer() {
    try (XmssSigningKey key = Xmss.loadSigningKey(secretKey)) {
      assertThat(key.getPublicKey()).isEqualTo(publicKey);
      assertThat(key.sign(leafIndex, message)).isEqualTo(signature);
    }
    assertThat(signature.getLeafIndex()).isEqualTo(leafIndex);
    assertThat(Xmss.verify(publicKey, leafIndex, message, signature)).isTrue();
  }

  @Test
  void shouldRejectSignatureAtUnexpectedLeafIndex() {
    // The signature is valid, but carries a different leaf from the one the caller expects
    assertThat(Xmss.verify(publicKey, leafIndex.plus(1), message, signature)).isFalse();
  }

  @Test
  void shouldRejectWrongMessage() {
    final Bytes32 otherMessage = Bytes32.wrap(message.not());
    assertThat(Xmss.verify(publicKey, leafIndex, otherMessage, signature)).isFalse();
  }

  @Test
  void shouldRejectStubAndEmptySignatures() {
    assertThat(Xmss.verify(publicKey, leafIndex, message, XmssSignature.stub(leafIndex))).isFalse();
    assertThat(Xmss.verify(publicKey, UInt64.ZERO, message, XmssSignature.empty())).isFalse();
  }

  @Test
  void shouldAcceptAnythingWhenVerificationDisabled() {
    XmssConstants.disableXmssVerification();
    assertThat(Xmss.verify(publicKey, leafIndex.plus(1), message, XmssSignature.empty())).isTrue();
  }

  @Test
  void shouldSignAndVerifyAtAnotherLeaf() {
    final UInt64 otherLeaf = UInt64.valueOf(42);
    try (XmssSigningKey key = Xmss.loadSigningKey(secretKey)) {
      key.prepare(otherLeaf);
      final XmssSignature otherSignature = key.sign(otherLeaf, message);
      assertThat(otherSignature.getLeafIndex()).isEqualTo(otherLeaf);
      assertThat(Xmss.verify(publicKey, otherLeaf, message, otherSignature)).isTrue();
    }
  }

  @Test
  void shouldSignWhileAnotherBottomTreeIsBeingPrepared() throws Exception {
    final UInt64 leafInFirstTree = UInt64.valueOf(7);
    final UInt64 leafInSecondTree = XmssSigningKey.firstLeafOfBottomTree(1);
    try (XmssSigningKey key = Xmss.loadSigningKey(secretKey)) {
      key.prepare(leafInFirstTree);
      final Thread prepareSecondTree = new Thread(() -> key.prepare(leafInSecondTree));
      prepareSecondTree.start();
      // Wait until the build is under way in the native key, past any lock prepare takes
      while (!isInNativePrepare(prepareSecondTree)) {
        assertThat(prepareSecondTree.isAlive()).isTrue();
        Thread.onSpinWait();
      }

      final XmssSignature signed = key.sign(leafInFirstTree, message);

      // Building a tree takes seconds and signing a fraction of that, so signing finished first
      // unless it had to wait
      assertThat(prepareSecondTree.isAlive()).isTrue();
      assertThat(Xmss.verify(publicKey, leafInFirstTree, message, signed)).isTrue();
      prepareSecondTree.join();
    }
  }

  private static boolean isInNativePrepare(final Thread thread) {
    return Arrays.stream(thread.getStackTrace())
        .anyMatch(
            frame ->
                frame.getClassName().equals(org.ethereum.leanpq.XmssSecretKey.class.getName())
                    && frame.getMethodName().equals("prepare"));
  }

  @Test
  void shouldGroupLeavesIntoBottomTreesOf65536() {
    assertThat(XmssSigningKey.bottomTreeOf(UInt64.ZERO)).isZero();
    assertThat(XmssSigningKey.bottomTreeOf(UInt64.valueOf(65535))).isZero();
    assertThat(XmssSigningKey.bottomTreeOf(UInt64.valueOf(65536))).isEqualTo(1);
    assertThat(XmssSigningKey.bottomTreeOf(XmssConstants.MAX_LEAF_INDEX)).isEqualTo(65535);
    assertThat(XmssSigningKey.firstLeafOfBottomTree(1)).isEqualTo(UInt64.valueOf(65536));
    assertThat(XmssSigningKey.firstLeafOfBottomTree(65535))
        .isEqualTo(XmssConstants.MAX_LEAF_INDEX.minus(65535));
  }

  @Test
  void prepare_reportsWhetherItBuiltTheTree() {
    final UInt64 leaf = UInt64.valueOf(3);
    try (XmssSigningKey key = Xmss.loadSigningKey(secretKey)) {
      assertThat(key.prepare(leaf)).isTrue();
      assertThat(key.prepare(leaf.plus(1))).isFalse();
    }
  }

  @Test
  void shouldRejectLeafIndexAboveUint32() {
    final UInt64 tooHigh = XmssConstants.MAX_LEAF_INDEX.plus(1);
    try (XmssSigningKey key = Xmss.loadSigningKey(secretKey)) {
      assertThatThrownBy(() -> key.sign(tooHigh, message))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> key.prepare(tooHigh)).isInstanceOf(IllegalArgumentException.class);
    }
  }
}

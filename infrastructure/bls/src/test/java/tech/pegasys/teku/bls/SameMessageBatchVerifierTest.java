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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.bls.impl.BlsException;

public class SameMessageBatchVerifierTest {

  private static final Bytes OTHER_MESSAGE = Bytes.wrap("other message".getBytes(UTF_8));

  /**
   * A fixed 96-byte encoding that blst cannot decode: the compression and infinity flags are set
   * but the remaining bits are not all zero.
   */
  private static final Bytes UNDECODABLE_SIGNATURE_BYTES =
      Bytes.fromHexString(
          "0x"
              + "c000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000001");

  /** Indices 0-2 share one message (a same-message group), indices 3-5 have distinct messages. */
  private static final int INDEX_IN_GROUP = 1;

  private static final int INDEX_OF_SINGLETON = 4;

  @Test
  void verifiesManySignaturesOverTheSameMessage() {
    final Bytes message = Bytes.wrap("same message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(i);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(keyPair.getSecretKey(), message));
    }

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isTrue();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isTrue();

    signatures.set(17, BLS.sign(BLSTestUtil.randomKeyPair(17).getSecretKey(), OTHER_MESSAGE));

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isFalse();
  }

  @Test
  void verifiesMixOfRepeatedAndDistinctMessages() {
    final Bytes m1 = Bytes.wrap("m1".getBytes(UTF_8));
    final Bytes m2 = Bytes.wrap("m2".getBytes(UTF_8));
    final Bytes m3 = Bytes.wrap("m3".getBytes(UTF_8));
    final Bytes m4 = Bytes.wrap("m4".getBytes(UTF_8));
    final List<Bytes> messages = List.of(m1, m2, m3, m1, m4, m2, m1);
    final List<BLSKeyPair> keyPairs = new ArrayList<>();
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < messages.size(); i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(100 + i);
      keyPairs.add(keyPair);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      signatures.add(BLS.sign(keyPair.getSecretKey(), messages.get(i)));
    }

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isTrue();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isTrue();

    // An invalid signature inside the m1 group
    final List<BLSSignature> invalidInGroup = new ArrayList<>(signatures);
    invalidInGroup.set(3, BLS.sign(keyPairs.get(3).getSecretKey(), OTHER_MESSAGE));
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidInGroup, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidInGroup, true))
        .isFalse();

    // An invalid signature for the singleton m4
    final List<BLSSignature> invalidSingleton = new ArrayList<>(signatures);
    invalidSingleton.set(4, BLS.sign(keyPairs.get(4).getSecretKey(), OTHER_MESSAGE));
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidSingleton, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidSingleton, true))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {7, 8, 9, 16, 17, 64})
  void verifiesDistinctMessagesAcrossChunkBoundaries(final int size) {
    final List<BLSKeyPair> keyPairs = new ArrayList<>();
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(300 + i);
      final Bytes message = Bytes.wrap(("distinct message " + i).getBytes(UTF_8));
      keyPairs.add(keyPair);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(keyPair.getSecretKey(), message));
    }

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isTrue();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isTrue();

    final int last = size - 1;
    signatures.set(last, BLS.sign(keyPairs.get(last).getSecretKey(), OTHER_MESSAGE));

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isFalse();
  }

  @Test
  void returnsFalseForEmptyInput() {
    assertThat(SameMessageBatchVerifier.batchVerify(List.of(), List.of(), List.of(), false))
        .isFalse();
  }

  @Test
  void routesASingleEntryThroughFastAggregateVerify() {
    final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(7);
    final Bytes message = Bytes.wrap("single".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = List.of(List.of(keyPair.getPublicKey()));

    assertThat(
            SameMessageBatchVerifier.batchVerify(
                publicKeys,
                List.of(message),
                List.of(BLS.sign(keyPair.getSecretKey(), message)),
                false))
        .isTrue();
    assertThat(
            SameMessageBatchVerifier.batchVerify(
                publicKeys,
                List.of(message),
                List.of(BLS.sign(keyPair.getSecretKey(), OTHER_MESSAGE)),
                false))
        .isFalse();
  }

  @Test
  void throwsBlsExceptionOnMismatchedListSizes() {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final Bytes message = Bytes.wrap("message".getBytes(UTF_8));

    assertThatThrownBy(
            () ->
                SameMessageBatchVerifier.batchVerify(
                    List.of(List.of(keyPair1.getPublicKey()), List.of(keyPair2.getPublicKey())),
                    List.of(message),
                    List.of(
                        BLS.sign(keyPair1.getSecretKey(), message),
                        BLS.sign(keyPair2.getSecretKey(), message)),
                    false))
        .isInstanceOf(BlsException.class);
  }

  @Test
  void returnsTrueWhenVerificationIsDisabled() {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final Bytes message = Bytes.wrap("message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys =
        List.of(List.of(keyPair1.getPublicKey()), List.of(keyPair2.getPublicKey()));
    final List<Bytes> messages = List.of(message, message);
    final List<BLSSignature> wrongSignatures =
        List.of(
            BLS.sign(keyPair1.getSecretKey(), OTHER_MESSAGE),
            BLS.sign(keyPair2.getSecretKey(), OTHER_MESSAGE));

    BLSConstants.disableBLSVerification();
    try {
      assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, wrongSignatures, false))
          .isTrue();
    } finally {
      BLSConstants.enableBLSVerification();
    }
  }

  @Test
  void returnsFalseForAnUndecodableSignature() {
    final Bytes message = Bytes.wrap("same message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(200 + i);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(keyPair.getSecretKey(), message));
    }
    signatures.set(2, undecodableSignature());

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isFalse();
  }

  @Test
  void invalidSignatureFixturesFailWhereExpected() {
    assertThatThrownBy(() -> undecodableSignature().getSignature())
        .isInstanceOf(BlsException.class);
    assertThatCode(() -> BLSTest.notInG2().getSignature()).doesNotThrowAnyException();
    assertThat(BLSTest.notInG2().isValid()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void returnsFalseForASignatureNotInG2InsideASameMessageGroup(final boolean parallel) {
    assertRejectedWithoutException(INDEX_IN_GROUP, BLSTest.notInG2(), parallel);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void returnsFalseForASignatureNotInG2AsADistinctMessageSingleton(final boolean parallel) {
    assertRejectedWithoutException(INDEX_OF_SINGLETON, BLSTest.notInG2(), parallel);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void returnsFalseForAnUndecodableSignatureInsideASameMessageGroup(final boolean parallel) {
    assertRejectedWithoutException(INDEX_IN_GROUP, undecodableSignature(), parallel);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void returnsFalseForAnUndecodableSignatureAsADistinctMessageSingleton(final boolean parallel) {
    assertRejectedWithoutException(INDEX_OF_SINGLETON, undecodableSignature(), parallel);
  }

  private static BLSSignature undecodableSignature() {
    return BLSSignature.fromBytesCompressed(UNDECODABLE_SIGNATURE_BYTES);
  }

  /**
   * Builds a batch with one same-message group and three distinct-message singletons, checks it
   * verifies, then replaces the signature at {@code index} and checks the batch is rejected by
   * returning false rather than by throwing.
   */
  private static void assertRejectedWithoutException(
      final int index, final BLSSignature invalidSignature, final boolean parallel) {
    final Bytes sharedMessage = Bytes.wrap("shared message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(400 + i);
      final Bytes message =
          i < 3 ? sharedMessage : Bytes.wrap(("distinct message " + i).getBytes(UTF_8));
      publicKeys.add(List.of(keyPair.getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(keyPair.getSecretKey(), message));
    }
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, parallel))
        .isTrue();

    signatures.set(index, invalidSignature);

    final AtomicReference<Boolean> result = new AtomicReference<>();
    assertThatCode(
            () ->
                result.set(
                    SameMessageBatchVerifier.batchVerify(
                        publicKeys, messages, signatures, parallel)))
        .doesNotThrowAnyException();
    assertThat(result.get()).isFalse();
  }
}

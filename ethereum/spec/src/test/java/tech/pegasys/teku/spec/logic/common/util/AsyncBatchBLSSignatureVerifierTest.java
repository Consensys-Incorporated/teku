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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class AsyncBatchBLSSignatureVerifierTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final AsyncBLSSignatureVerifier delegate = mock(AsyncBLSSignatureVerifier.class);
  private final SafeFuture<Boolean> delegateVerifyResult = new SafeFuture<>();

  private final AsyncBatchBLSSignatureVerifier verifier =
      new AsyncBatchBLSSignatureVerifier(delegate);

  @BeforeEach
  void setUp() {
    when(delegate.verify(anyList(), anyList(), anyList())).thenReturn(delegateVerifyResult);
  }

  @Test
  void shouldOnlyVerifySingleAttestationsAtEnd() {
    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    final Bytes32 message1 = dataStructureUtil.randomBytes32();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();

    final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();
    final Bytes32 message2 = dataStructureUtil.randomBytes32();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();

    assertThat(verifier.verify(publicKey1, message1, signature1)).isTrue();
    verifyNoInteractions(delegate);

    assertThat(verifier.verify(publicKey2, message2, signature2)).isTrue();
    verifyNoInteractions(delegate);

    final SafeFuture<Boolean> result = verifier.batchVerify();
    assertThat(result).isSameAs(delegateVerifyResult);
    verify(delegate)
        .verify(
            List.of(List.of(publicKey1), List.of(publicKey2)),
            List.of(message1, message2),
            List.of(signature1, signature2));
  }

  @Test
  void shouldOnlyVerifyCombinedAttestationsAtEnd() {
    final List<BLSPublicKey> publicKeys1 =
        List.of(
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomPublicKey());
    final List<Bytes> messages1 =
        List.of(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
    final List<BLSSignature> signatures1 =
        List.of(
            dataStructureUtil.randomSignature(),
            dataStructureUtil.randomSignature(),
            dataStructureUtil.randomSignature());

    final List<BLSPublicKey> publicKeys2 =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    final List<Bytes> messages2 =
        List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());
    final List<BLSSignature> signatures2 =
        List.of(dataStructureUtil.randomSignature(), dataStructureUtil.randomSignature());

    assertThat(verifier.verify(List.of(publicKeys1), messages1, signatures1)).isTrue();
    verifyNoInteractions(delegate);

    assertThat(verifier.verify(List.of(publicKeys2), messages2, signatures2)).isTrue();
    verifyNoInteractions(delegate);

    final SafeFuture<Boolean> result = verifier.batchVerify();
    assertThat(result).isSameAs(delegateVerifyResult);
    verify(delegate)
        .verify(
            concat(List.of(publicKeys1), List.of(publicKeys2)),
            concat(messages1, messages2),
            concat(signatures1, signatures2));
  }

  @Test
  void shouldVerifySingleAndCombinedAttestationsAtEnd() {
    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    final Bytes32 message1 = dataStructureUtil.randomBytes32();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();

    final List<BLSPublicKey> publicKeys2 =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    final List<Bytes> messages2 =
        List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());
    final List<BLSSignature> signatures2 =
        List.of(dataStructureUtil.randomSignature(), dataStructureUtil.randomSignature());

    assertThat(verifier.verify(publicKey1, message1, signature1)).isTrue();
    verifyNoInteractions(delegate);

    assertThat(verifier.verify(List.of(publicKeys2), messages2, signatures2)).isTrue();
    verifyNoInteractions(delegate);

    final SafeFuture<Boolean> result = verifier.batchVerify();
    assertThat(result).isSameAs(delegateVerifyResult);
    verify(delegate)
        .verify(
            concat(List.of(List.of(publicKey1)), List.of(publicKeys2)),
            concat(List.of(message1), messages2),
            concat(List.of(signature1), signatures2));
  }

  @Test
  void shouldCombineVerificationsMadeViaAsyncBLSSignatureVerifierInterface() {
    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    final Bytes32 message1 = dataStructureUtil.randomBytes32();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();

    final List<BLSPublicKey> publicKeys2 =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    final List<Bytes> messages2 =
        List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());
    final List<BLSSignature> signatures2 =
        List.of(dataStructureUtil.randomSignature(), dataStructureUtil.randomSignature());

    final BLSPublicKey publicKey3 = dataStructureUtil.randomPublicKey();
    final Bytes32 message3 = dataStructureUtil.randomBytes32();
    final BLSSignature signature3 = dataStructureUtil.randomSignature();

    final AsyncBLSSignatureVerifier asyncVerifier = verifier.asAsyncBSLSSignatureVerifier();
    assertThat(asyncVerifier.verify(publicKey1, message1, signature1)).isCompletedWithValue(true);
    assertThat(asyncVerifier.verify(List.of(publicKeys2), messages2, signatures2))
        .isCompletedWithValue(true);
    assertThat(verifier.verify(publicKey3, message3, signature3)).isTrue();

    verifyNoInteractions(delegate);

    final SafeFuture<Boolean> result = verifier.batchVerify();
    assertThat(result).isSameAs(delegateVerifyResult);
    verify(delegate)
        .verify(
            concat(
                List.of(List.of(publicKey1)), List.of(publicKeys2), List.of(List.of(publicKey3))),
            concat(List.of(message1), messages2, List.of(message3)),
            concat(List.of(signature1), signatures2, List.of(signature3)));
  }

  @SafeVarargs
  private <T> List<T> concat(final List<T>... lists) {
    final List<T> result = new ArrayList<>();
    for (List<T> list : lists) {
      result.addAll(list);
    }
    return result;
  }
}

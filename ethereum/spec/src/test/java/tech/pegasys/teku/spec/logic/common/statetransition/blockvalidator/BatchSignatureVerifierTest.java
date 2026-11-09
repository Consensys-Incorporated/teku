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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;

public class BatchSignatureVerifierTest {

  @Test
  public void shouldRaiseExceptionIfNoValidPublicKeys() {
    BatchSignatureVerifier verifier = new BatchSignatureVerifierImpl();

    verifier.verify(
        List.of(BLSPublicKey.empty()),
        Bytes.wrap("Hello, world!".getBytes(UTF_8)),
        BLSTestUtil.randomSignature(43));

    assertThat(verifier.batchVerify()).isFalse();
  }

  @Test
  public void shouldRaiseExceptionIfNoSuppliedPublicKeys() {
    BatchSignatureVerifier verifier = new BatchSignatureVerifierImpl();

    assertThatThrownBy(
            () ->
                verifier.verify(
                    List.of(),
                    Bytes.wrap("Hello, world!".getBytes(UTF_8)),
                    BLSTestUtil.randomSignature(42)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testParallel() throws Exception {
    BatchSignatureVerifierImpl verifier = new BatchSignatureVerifierImpl();

    BLSPublicKey publicKey = BLSTestUtil.randomPublicKey(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSSignature signature = BLSTestUtil.randomSignature(42);

    int nThreads = 64;
    int iterations = 64;
    ExecutorService executor = Executors.newFixedThreadPool(nThreads);
    CountDownLatch latch = new CountDownLatch(nThreads);
    List<Future<Boolean>> futures =
        IntStream.range(0, nThreads)
            .mapToObj(
                __ ->
                    executor.submit(
                        () -> {
                          latch.countDown();
                          latch.await();
                          boolean ret = true;
                          for (int i = 0; i < iterations; i++) {
                            ret &=
                                verifier.verify(
                                    Collections.singletonList(publicKey), message, signature);
                          }
                          return ret;
                        }))
            .toList();

    for (Future<Boolean> future : futures) {
      assertThat(future.get()).isTrue();
    }
    assertThat(verifier.toVerify.size()).isEqualTo(nThreads * iterations);
    assertThat(verifier.toVerify).doesNotContainNull();
  }

  @Test
  void shouldBeValidWhenNothingVerified() {
    final BatchSignatureVerifier verifier = new BatchSignatureVerifierImpl();
    assertThat(verifier.batchVerify()).isTrue();
  }
}

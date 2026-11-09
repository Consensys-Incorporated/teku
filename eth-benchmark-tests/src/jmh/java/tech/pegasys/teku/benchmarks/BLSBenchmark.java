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

package tech.pegasys.teku.benchmarks;

import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;

@Fork(1)
@State(Scope.Thread)
public class BLSBenchmark {

  @Param({"1", "2", "4", "8", "16", "32", "64", "128"})
  int sigCnt = 128;

  List<BLSKeyPair> keyPairs =
      IntStream.range(0, sigCnt).mapToObj(BLSTestUtil::randomKeyPair).collect(Collectors.toList());
  List<Bytes> messages =
      Stream.generate(Bytes32::random).limit(sigCnt).collect(Collectors.toList());
  List<BLSSignature> signatures =
      Streams.zip(
              keyPairs.stream(),
              messages.stream(),
              (keyPair, msg) -> BLS.sign(keyPair.getSecretKey(), msg))
          .collect(Collectors.toList());

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureSimple() {
    for (int i = 0; i < sigCnt; i++) {
      boolean res = BLS.verify(keyPairs.get(i).getPublicKey(), messages.get(i), signatures.get(i));
      if (!res) {
        throw new IllegalStateException();
      }
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureBatchedNonParallelSinglePairing() {
    boolean res =
        BLS.batchVerify(
            keyPairs.stream()
                .map(kp -> Collections.singletonList(kp.getPublicKey()))
                .limit(sigCnt)
                .collect(Collectors.toList()),
            messages.subList(0, sigCnt),
            signatures.subList(0, sigCnt),
            false,
            false);
    if (!res) {
      throw new IllegalStateException();
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureBatchedNonParallelDoublePairing() {
    boolean res =
        BLS.batchVerify(
            keyPairs.stream()
                .map(kp -> Collections.singletonList(kp.getPublicKey()))
                .limit(sigCnt)
                .collect(Collectors.toList()),
            messages.subList(0, sigCnt),
            signatures.subList(0, sigCnt),
            true,
            false);
    if (!res) {
      throw new IllegalStateException();
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureBatchedParallelDoublePairing() {
    boolean res =
        BLS.batchVerify(
            keyPairs.stream()
                .map(kp -> Collections.singletonList(kp.getPublicKey()))
                .limit(sigCnt)
                .collect(Collectors.toList()),
            messages.subList(0, sigCnt),
            signatures.subList(0, sigCnt),
            true,
            true);
    if (!res) {
      throw new IllegalStateException();
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatureBatchedParallelSinglePairing() {
    boolean res =
        BLS.batchVerify(
            keyPairs.stream()
                .map(kp -> Collections.singletonList(kp.getPublicKey()))
                .limit(sigCnt)
                .collect(Collectors.toList()),
            messages.subList(0, sigCnt),
            signatures.subList(0, sigCnt),
            false,
            true);
    if (!res) {
      throw new IllegalStateException();
    }
  }
}

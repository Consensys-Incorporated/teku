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

package tech.pegasys.teku.statetransition.validation.signatures;

import com.google.common.collect.Streams;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

@Fork(1)
@State(Scope.Thread)
public class AggregatingSignatureVerificationServiceBenchmark {

  private final int sigCnt = 1000;

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

  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metricsSystem));
  private final AggregatingSignatureVerificationService service =
      new AggregatingSignatureVerificationService(
          metricsSystem,
          asyncRunnerFactory,
          asyncRunnerFactory.create("completion", 10),
          2,
          15_000,
          250,
          false,
          SignatureVerificationMode.GATHER_AND_GROUP,
          Duration.ofMillis(10));

  private final int signaturesToVerify = 10_000;

  @Setup
  public void setup() {
    service.start().join();
  }

  @TearDown
  public void tearDown() {
    service.stop().join();
    asyncRunnerFactory.shutdown();
  }

  @SuppressWarnings("unchecked")
  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void verifySignatures(Blackhole bh) {
    final SafeFuture<Boolean>[] results = new SafeFuture[signaturesToVerify];
    for (int i = 0; i < signaturesToVerify; i++) {
      int idx = i % sigCnt;
      results[i] =
          service.verify(keyPairs.get(idx).getPublicKey(), messages.get(idx), signatures.get(idx));
    }
    bh.consume(SafeFuture.allOf(results).join());
  }
}

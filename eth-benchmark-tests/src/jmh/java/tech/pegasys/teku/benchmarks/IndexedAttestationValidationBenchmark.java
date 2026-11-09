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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.util.CustomRunner;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class IndexedAttestationValidationBenchmark {
  Spec spec;
  BeaconState beaconState;
  IndexedAttestationLight indexedAttestation;
  Fork fork;
  AsyncBLSSignatureVerifier asyncBLSSignatureVerifier;

  @Setup(Level.Trial)
  public void init() throws Exception {
    spec = TestSpecFactory.createMainnetDeneb();

    final byte[] stateBytes = Files.readAllBytes(Path.of("/Users/tbenr/state.ssz"));
    final byte[] indexedAttestationBytes =
        Files.readAllBytes(Path.of("/Users/tbenr/attestation.ssz"));

    beaconState = spec.deserializeBeaconState(Bytes.of(stateBytes));
    final IndexedAttestation sszIndexedAttestation =
        spec.atSlot(beaconState.getSlot())
            .getSchemaDefinitions()
            .getIndexedAttestationSchema()
            .sszDeserialize(Bytes.of(indexedAttestationBytes));
    indexedAttestation = IndexedAttestationLight.fromSsz(sszIndexedAttestation);

    fork = spec.getForkSchedule().getFork(spec.computeEpochAtSlot(beaconState.getSlot()));
    asyncBLSSignatureVerifier = AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP);
  }

  @Benchmark
  public void validateIndexedAttestation(Blackhole bh) {
    bh.consume(
        spec.atSlot(beaconState.getSlot())
            .getAttestationUtil()
            .isValidIndexedAttestationAsync(
                fork, beaconState, indexedAttestation, asyncBLSSignatureVerifier)
            .join());
  }

  public static void main(String[] args) throws Exception {
    IndexedAttestationValidationBenchmark benchmark = new IndexedAttestationValidationBenchmark();
    benchmark.init();
    new CustomRunner(2, 10000).withBench(benchmark::validateIndexedAttestation).run();
  }
}

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

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.util.CustomRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

@Fork(3)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Thread)
public class ShuffleBenchmark {

  @Param({"16384", "32768"})
  int indexCount;

  Bytes32 seed = Bytes32.ZERO;
  private final Spec spec = TestSpecFactory.createMainnetPhase0();
  private final MiscHelpers miscHelpers = spec.atSlot(UInt64.ZERO).miscHelpers();

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 5)
  public void shuffledIndexBench(Blackhole bh) {
    for (int i = 0; i < indexCount; i++) {
      int index = miscHelpers.computeShuffledIndex(i, indexCount, seed);
      bh.consume(index);
    }
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 5)
  public void shuffledListBench(Blackhole bh) {
    int[] indices = IntStream.range(0, indexCount).toArray();
    miscHelpers.shuffleList(indices, seed);
    bh.consume(indices);
  }

  public static void main(String[] args) {
    ShuffleBenchmark benchmark = new ShuffleBenchmark();

    benchmark.indexCount = 32768;

    new CustomRunner(2, 2).withBench(benchmark::shuffledIndexBench).run();
    new CustomRunner(2, 2).withBench(benchmark::shuffledListBench).run();
  }
}

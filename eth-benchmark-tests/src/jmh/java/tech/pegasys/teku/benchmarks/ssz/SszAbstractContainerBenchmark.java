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

package tech.pegasys.teku.benchmarks.ssz;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

@Threads(1)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public abstract class SszAbstractContainerBenchmark<TView extends SszData> {
  protected final Blackhole blackhole =
      new Blackhole(
          "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");

  private final TView aContainer = createContainer();
  private final Bytes aContainerSsz = aContainer.sszSerialize();

  protected abstract TView createContainer();

  protected abstract SszSchema<TView> getContainerType();

  protected abstract void iterateData(TView container, Blackhole bh);

  @Benchmark
  public void benchCreate(Blackhole bh) {
    bh.consume(createContainer());
  }

  @Benchmark
  public void benchIterate(Blackhole bh) {
    iterateData(aContainer, bh);
  }

  @Benchmark
  public void benchCreateAndIterate(Blackhole bh) {
    TView container = createContainer();
    iterateData(container, bh);
  }

  @Benchmark
  public void benchSerialize(Blackhole bh) {
    bh.consume(aContainer.sszSerialize());
  }

  @Benchmark
  public void benchCreateAndSerialize(Blackhole bh) {
    TView container = createContainer();
    bh.consume(container.sszSerialize());
  }

  @Benchmark
  public void benchDeserialize(Blackhole bh) {
    bh.consume(getContainerType().sszDeserialize(aContainerSsz));
  }

  @Benchmark
  public void benchDeserializeAndIterate(Blackhole bh) {
    TView container1 = getContainerType().sszDeserialize(aContainerSsz);
    iterateData(container1, bh);
  }

  @Benchmark
  public void benchDeserializeAndSerialize(Blackhole bh) {
    TView container = getContainerType().sszDeserialize(aContainerSsz);
    bh.consume(container.sszSerialize());
  }

  public void customRun(int runs, int runLength) {
    Map<String, Consumer<Blackhole>> benches = new LinkedHashMap<>();
    benches.put("benchCreate", this::benchCreate);
    benches.put("benchIterate", this::benchIterate);
    benches.put("benchCreateAndIterate", this::benchCreateAndIterate);
    benches.put("benchSerialize", this::benchSerialize);
    benches.put("benchDeserialize", this::benchDeserialize);
    benches.put("benchDeserializeAndIterate", this::benchDeserializeAndIterate);

    for (Map.Entry<String, Consumer<Blackhole>> entry : benches.entrySet()) {

      Consumer<Blackhole> bench = entry.getValue();
      System.out.print("Bench #" + entry.getKey() + ": ");
      for (int j = 0; j < runs; j++) {
        long s = System.nanoTime();
        for (int k = 0; k < runLength; k++) {
          bench.accept(blackhole);
        }
        long t = (System.nanoTime() - s) / 1_000_000;
        System.out.print(t + " ");
      }
      System.out.println();
    }
  }
}

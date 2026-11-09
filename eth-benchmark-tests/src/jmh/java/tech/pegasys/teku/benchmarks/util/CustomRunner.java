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

package tech.pegasys.teku.benchmarks.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.openjdk.jmh.infra.Blackhole;

public class CustomRunner {

  public static class RunResult {
    private final long nanos;
    private final long operationsCount;

    public RunResult(long nanos, long operationsCount) {
      this.nanos = nanos;
      this.operationsCount = operationsCount;
    }

    public double getOperationsPerSecond() {
      return operationsCount * 1_000_000_000d / nanos;
    }

    public double getNanosPerOperations() {
      return ((double) nanos) / operationsCount;
    }
  }

  private final Blackhole blackhole =
      new Blackhole(
          "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
  private final Map<String, Consumer<Blackhole>> benches = new LinkedHashMap<>();
  private final int runIterations;
  private final int runsCount;

  public CustomRunner(int runIterations, int runsCount) {
    this.runIterations = runIterations;
    this.runsCount = runsCount;
  }

  public CustomRunner withBench(String name, Consumer<Blackhole> bench) {
    benches.put(name, bench);
    return this;
  }

  public CustomRunner withBench(Consumer<Blackhole> bench) {
    benches.put("bench-" + benches.size(), bench);
    return this;
  }

  public void run() {
    benches.forEach(
        (name, bench) -> {
          System.out.println(name);
          for (int i = 0; i < runsCount; i++) {
            RunResult result = runSingle(bench);
            System.out.printf("  %.2f ops/sec\n", result.getOperationsPerSecond());
          }
        });
  }

  private RunResult runSingle(Consumer<Blackhole> bench) {
    long start = System.nanoTime();
    int iterations = runIterations;
    while (true) {
      bench.accept(blackhole);

      if (iterations-- == 0) {
        break;
      }
    }
    return new RunResult(System.nanoTime() - start, runIterations);
  }
}

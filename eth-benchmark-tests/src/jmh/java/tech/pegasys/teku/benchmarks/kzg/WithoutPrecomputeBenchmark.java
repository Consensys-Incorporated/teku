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

package tech.pegasys.teku.benchmarks.kzg;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10)
public class WithoutPrecomputeBenchmark {
  @State(Scope.Benchmark)
  public static class ExecutionPlan {
    public SidecarBenchmarkConfig config;

    @Param({"true", "false"})
    public boolean isRustEnabled;

    @Setup(Level.Invocation)
    public void setup() {
      config = new SidecarBenchmarkConfig(false, isRustEnabled);
    }
  }

  @Benchmark
  public void verifyDataColumnSidecarKzgProofsBatch(final ExecutionPlan plan) {
    plan.config.miscHelpersFulu.verifyDataColumnSidecarKzgProofsBatch(
        plan.config.dataColumnSidecars);
  }

  @Benchmark
  public void verifyDataColumnSidecarKzgProofs(final ExecutionPlan plan) {
    plan.config.miscHelpersFulu.verifyDataColumnSidecarKzgProofs(
        plan.config.dataColumnSidecars.stream().findAny().orElseThrow());
  }
}

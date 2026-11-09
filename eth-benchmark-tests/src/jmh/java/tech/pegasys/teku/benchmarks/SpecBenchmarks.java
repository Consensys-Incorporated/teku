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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

@State(Scope.Thread)
public class SpecBenchmarks {

  UInt64 n = UInt64.valueOf(32L * (32 * 1024) * 1_000_000_000);

  @Benchmark
  @Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void integerSquareRoot(Blackhole bh) {
    n = n.plus(UInt64.ONE);
    bh.consume(MathHelpers.integerSquareRoot(n));
  }
}

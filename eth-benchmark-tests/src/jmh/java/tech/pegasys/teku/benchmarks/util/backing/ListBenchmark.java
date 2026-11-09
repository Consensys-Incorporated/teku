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

package tech.pegasys.teku.benchmarks.util.backing;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@State(Scope.Thread)
public class ListBenchmark {

  @Param({"1024"})
  int listMaxSizeM;

  private int getListMaxSize() {
    return listMaxSizeM * 1024 * 1024;
  }

  SszMutableList<SszUInt64> l1w;
  SszList<SszUInt64> l2r;

  public ListBenchmark() {
    SszListSchema<SszUInt64, ?> type =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 100_000_000);
    SszList<SszUInt64> l1 = type.getDefault();

    SszMutableList<SszUInt64> l2w = l1.createWritableCopy();
    for (int i = 0; i < 1000000; i++) {
      l2w.append(SszUInt64.of(UInt64.valueOf(1121212)));
    }
    l2r = l2w.commitChanges();

    long s = System.nanoTime();
    l2r.hashTreeRoot();
    System.out.println("Initial hash calc: " + (System.nanoTime() - s) / 1000 + " us");
  }

  @Setup(Level.Iteration)
  public void init() {
    SszListSchema<SszUInt64, ?> type =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, getListMaxSize());
    SszList<SszUInt64> l1 = type.getDefault();
    l1w = l1.createWritableCopy();
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void createDefaultUIntList(Blackhole bh) {
    SszListSchema<SszUInt64, ?> type =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, getListMaxSize());
    SszList<SszUInt64> l1 = type.getDefault();
    bh.consume(l1);
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void append(Blackhole bh) {
    l1w.append(SszUInt64.of(UInt64.valueOf(1121212)));
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void incrementalHash(Blackhole bh) {
    SszMutableList<SszUInt64> l2w = l2r.createWritableCopy();
    l2w.set(12345, SszUInt64.of(UInt64.valueOf(77777)));
    SszList<SszUInt64> l2r_ = l2w.commitChanges();
    l2r_.hashTreeRoot();
  }
}

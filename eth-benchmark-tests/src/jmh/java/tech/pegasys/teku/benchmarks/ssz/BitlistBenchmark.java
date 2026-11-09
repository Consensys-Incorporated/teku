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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;

@State(Scope.Thread)
public class BitlistBenchmark {
  private static final int BITLIST_SIZE = 128; // MainNet target committee size
  private static final SszBitlistSchema<SszBitlist> BITLIST_SCHEMA =
      SszBitlistSchema.create(BITLIST_SIZE);
  private static final SszBitlist LAST_BIT_SET = BITLIST_SCHEMA.ofBits(128, 127);

  private static final SszBitlist MANY_BITS_SET =
      createBitlist(
          1, 2, 6, 16, 23, 33, 65, 87, 96, 100, 101, 102, 103, 104, 110, 115, 120, 121, 125);

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void intersects(Blackhole bh) {
    bh.consume(LAST_BIT_SET.intersects(LAST_BIT_SET));
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void setAllBits(Blackhole bh) {
    final SszBitlist target = createBitlist().or(MANY_BITS_SET);
    bh.consume(target);
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void getAttestingIndices(Blackhole bh) {
    bh.consume(MANY_BITS_SET.getAllSetBits());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void countSetBits(Blackhole bh) {
    bh.consume(MANY_BITS_SET.getBitCount());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void createBitlistAndSerialize(Blackhole bh) {
    SszBitlist bitlist = BITLIST_SCHEMA.ofBits(BITLIST_SIZE, 42);
    bh.consume(bitlist.sszSerialize());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void createBitlistAndOr(Blackhole bh) {
    SszBitlist single = BITLIST_SCHEMA.ofBits(BITLIST_SIZE, 42);
    bh.consume(MANY_BITS_SET.or(single));
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void createBitlistAndHashTreeRoot(Blackhole bh) {
    SszBitlist bitlist = BITLIST_SCHEMA.ofBits(BITLIST_SIZE, 42);
    bh.consume(bitlist.hashTreeRoot());
  }

  private static SszBitlist createBitlist(final int... setBits) {
    return BITLIST_SCHEMA.ofBits(BITLIST_SIZE, setBits);
  }
}

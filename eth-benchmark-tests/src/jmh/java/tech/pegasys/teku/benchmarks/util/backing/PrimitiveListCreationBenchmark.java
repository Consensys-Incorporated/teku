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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xmx1g", "-Xms1g"})
public class PrimitiveListCreationBenchmark {

  @Param({"1", "4", "16", "64"})
  int listSize;

  private SszUInt64ListSchema<?> uint64Schema;
  private List<UInt64> uint64Elements;

  private SszByteListSchema<?> byteSchema;
  private List<SszByte> byteElements;

  @Setup
  public void setup() {
    uint64Schema = SszUInt64ListSchema.create(2048);
    uint64Elements =
        IntStream.range(0, listSize).mapToObj(i -> UInt64.valueOf(100_000L + i)).toList();

    byteSchema = SszByteListSchema.create(2048);
    byteElements =
        IntStream.range(0, listSize).mapToObj(i -> SszByte.of((byte) (i & 0xFF))).toList();
  }

  @Benchmark
  public void createUInt64ListViaOf(final Blackhole bh) {
    final SszUInt64List list = uint64Schema.of(uint64Elements);
    bh.consume(list);
  }

  @Benchmark
  public void createByteListViaOf(final Blackhole bh) {
    final SszByteList list = byteSchema.createFromElements(byteElements);
    bh.consume(list);
  }
}

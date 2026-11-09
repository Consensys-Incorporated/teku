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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.crypto.Hash;

@State(Scope.Thread)
public class Sha256Benchmark {

  private MutableBytes data = Bytes.wrap(new byte[33]).mutableCopy();
  private byte[] dataArray = new byte[33];
  private int cnt = 0;

  @Benchmark
  @Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void sha256of33bytes(Blackhole bh) {
    int idx = cnt++ % data.size();
    data.set(idx, (byte) (data.get(idx) + 1));
    Bytes32 hash = Hash.sha256(data);
    bh.consume(hash);
  }

  @Benchmark
  @Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void sha256of33byteArray(Blackhole bh) {
    int idx = cnt++ % data.size();
    dataArray[idx]++;
    byte[] hash = Hash.sha256(dataArray).toArrayUnsafe();
    bh.consume(hash);
  }
}

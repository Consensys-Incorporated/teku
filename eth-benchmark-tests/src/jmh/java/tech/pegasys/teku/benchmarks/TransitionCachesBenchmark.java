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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;

@Fork(1)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
public class TransitionCachesBenchmark {

  private static final IntList SOME_INT_LIST = IntList.of(1, 2, 3, 43, 4, 5);

  @Param({"1048576"})
  int validatorsCount;

  long counter;

  private final TransitionCaches fullCache = TransitionCaches.createNewEmpty();

  @Setup(Level.Trial)
  public void init() {

    for (int slot = 10000; slot < 10064; slot++) {
      for (int committeeIndex = 0; committeeIndex < 64; committeeIndex++) {
        fullCache
            .getBeaconCommittee()
            .invalidateWithNewValue(
                TekuPair.of(UInt64.valueOf(slot), UInt64.valueOf(committeeIndex)), SOME_INT_LIST);
      }
    }

    for (int slot = 0; slot < 4096; slot++) {
      fullCache
          .getAttestersTotalBalance()
          .invalidateWithNewValue(UInt64.valueOf(slot), UInt64.ZERO);
    }

    for (int epoch = 0; epoch < 8; epoch++) {
      fullCache.getActiveValidators().invalidateWithNewValue(UInt64.valueOf(epoch), SOME_INT_LIST);
    }

    for (int validatorIdx = 0; validatorIdx < validatorsCount; validatorIdx++) {
      BLSPublicKey publicKey =
          BLSPublicKey.fromBytesCompressed(Bytes48.leftPad(Bytes.ofUnsignedInt(validatorIdx)));
      fullCache
          .getValidatorsPubKeys()
          .invalidateWithNewValue(UInt64.valueOf(validatorIdx), publicKey);
      fullCache.getValidatorIndexCache().invalidateWithNewValue(publicKey, validatorIdx);
    }

    fullCache.getBeaconProposerIndex().invalidateWithNewValue(UInt64.ONE, 0x777);
    fullCache.getTotalActiveBalance().invalidateWithNewValue(UInt64.ZERO, UInt64.ZERO);
    fullCache.getTotalActiveBalance().invalidateWithNewValue(UInt64.ONE, UInt64.ZERO);
    fullCache.getCommitteeShuffle().invalidateWithNewValue(Bytes32.random(), SOME_INT_LIST);
    fullCache.getCommitteeShuffle().invalidateWithNewValue(Bytes32.random(), SOME_INT_LIST);
    fullCache.getEffectiveBalances().invalidateWithNewValue(UInt64.ONE, List.of(UInt64.ONE));
  }

  @Benchmark
  public void getCommitteeHitBench(Blackhole bh) {
    counter++;
    if (counter >= 10064) {
      counter = 10000;
    }
    Cache<TekuPair<UInt64, UInt64>, IntList> cache = fullCache.getBeaconCommittee();
    List<Integer> res =
        cache.get(TekuPair.of(UInt64.valueOf(counter), UInt64.ZERO), __ -> SOME_INT_LIST);
    bh.consume(res);
  }

  @Benchmark
  public void getCommitteeMissBench(Blackhole bh) {
    if (counter < 10064) {
      counter = 10064;
    }
    counter++;
    Cache<TekuPair<UInt64, UInt64>, IntList> cache = fullCache.getBeaconCommittee();
    List<Integer> res =
        cache.get(TekuPair.of(UInt64.valueOf(counter), UInt64.ZERO), __ -> SOME_INT_LIST);
    bh.consume(res);
  }

  @Benchmark
  public void copyBeaconCommitteeCacheBench(Blackhole bh) {
    bh.consume(fullCache.getBeaconCommittee().copy());
  }

  @Benchmark
  public void copyCachesBench(Blackhole bh) {
    bh.consume(fullCache.copy());
  }
}

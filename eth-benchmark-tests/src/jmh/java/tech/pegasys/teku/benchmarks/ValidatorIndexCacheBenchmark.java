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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.ValidatorIndexCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@Fork(1)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
public class ValidatorIndexCacheBenchmark {
  static final int VALIDATORS_MAX_IDX = 399_999;
  private static final Spec SPEC = TestSpecFactory.createMinimalDeneb();

  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, SPEC);
  private static final BeaconState STATE =
      dataStructureUtil.randomBeaconState(VALIDATORS_MAX_IDX + 1);
  private static final ValidatorIndexCache CACHE = new ValidatorIndexCache();
  private static final BLSPublicKey RANDOM_KEY = dataStructureUtil.randomPublicKey();

  @Setup(Level.Trial)
  public void doSetup() {
    CACHE.getValidatorIndex(STATE, STATE.getValidators().get(VALIDATORS_MAX_IDX).getPublicKey());
  }

  @Benchmark
  public void cacheHit(Blackhole bh) {
    bh.consume(
        CACHE.getValidatorIndex(
            STATE,
            STATE
                .getValidators()
                .get(dataStructureUtil.randomPositiveInt(VALIDATORS_MAX_IDX))
                .getPublicKey()));
  }

  @Benchmark
  public void cacheMiss(Blackhole bh) {
    bh.consume(CACHE.getValidatorIndex(STATE, RANDOM_KEY));
  }
}

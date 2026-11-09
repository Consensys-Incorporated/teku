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
import org.apache.tuweni.bytes.Bytes48;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@Fork(1)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 5)
public class LargeSszStateBenchmark {
  private static final int VALIDATOR_COUNT = 2_000_000;
  private static final BLSPublicKey fixedKey = BLSPublicKey.fromBytesCompressed(Bytes48.ZERO);
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(spec).withPubKeyGenerator(() -> fixedKey);
  private final SszList<Validator> validators =
      dataStructureUtil.randomSszList(
          dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
          VALIDATOR_COUNT,
          () -> dataStructureUtil.validatorBuilder().withRandomBlsWithdrawalCredentials().build());
  private final BeaconState state =
      dataStructureUtil.stateBuilderDeneb(VALIDATOR_COUNT, 1).validators(validators).build();

  @Benchmark
  @Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MICROSECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MICROSECONDS)
  public void BeaconStateSerialization(final Blackhole blackhole) {
    blackhole.consume(state.sszSerialize());
  }
}
